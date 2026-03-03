package org.example.project.core.cli

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.example.project.core.bootstrap.AppBootstrap
import org.example.project.core.persistence.DatabaseProvider
import org.example.project.feature.weeklyparts.infrastructure.parsePartTypeFromJson
import org.example.project.feature.weeklyparts.domain.SexRule
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import kotlin.math.max
import kotlin.random.Random

private const val DEFAULT_PAST_MONTHS = 10
private const val DEFAULT_FUTURE_MONTHS = 2
private const val RANDOM_SEED = 20260226L
private const val DEFAULT_CATALOG_PATH = ".worktrees/efficaci-nel-ministero-data/schemas-catalog.json"

fun main(args: Array<String>) {
    val config = parseArgs(args)
    require(config.pastMonths >= 0) { "--past-months deve essere >= 0" }
    require(config.futureMonths >= 0) { "--future-months deve essere >= 0" }
    require(config.catalogFile.exists()) { "Catalogo non trovato: ${config.catalogFile.absolutePath}" }

    AppBootstrap.initialize()
    val db = DatabaseProvider.database()
    val queries = db.ministeroDatabaseQueries
    val random = Random(RANDOM_SEED)
    val now = LocalDate.now()
    val referenceMonth = YearMonth.from(now)
    val targetMonths = buildTargetMonths(
        referenceMonth = referenceMonth,
        pastMonths = config.pastMonths,
        includeCurrent = config.includeCurrent,
        futureMonths = config.futureMonths,
    )
    require(targetMonths.isNotEmpty()) {
        "Finestra mesi vuota: imposta --past-months > 0, --future-months > 0 o mantieni il mese corrente"
    }
    val firstMonth = targetMonths.first()
    val lastMonth = targetMonths.last()
    val rangeStart = firstMondayInMonth(firstMonth)
    val rangeEnd = endSundayForMonth(lastMonth)

    cleanupSeedArtifacts(queries)
    val seedPartTypes = ensureSeedPartTypes(queries, config.catalogFile)
    val seedPeople = seedPeople()
    upsertPeople(queries, seedPeople)
    val eligibilityMap = upsertMixedEligibility(queries, seedPeople, seedPartTypes)
    val deletedWeeks = deleteWeeksInRange(queries, rangeStart, rangeEnd)
    val deletedPrograms = deleteProgramsForMonths(queries, targetMonths)
    val schemaWeeksInserted = seedPastSchemaCatalog(queries, seedPartTypes, rangeStart, rangeEnd)
    val report = seedPastProgramsAndAssignments(
        queries = queries,
        months = targetMonths,
        partTypes = seedPartTypes,
        people = seedPeople,
        eligibilityMap = eligibilityMap,
        random = random,
    )
    cleanupOrphans(queries)

    println("Seed demo completato.")
    println("Catalogo part types: ${config.catalogFile.absolutePath}")
    println("Mesi seed: ${firstMonth.monthValue}/${firstMonth.year} .. ${lastMonth.monthValue}/${lastMonth.year}")
    println("Config finestra: past=${config.pastMonths}, current=${config.includeCurrent}, future=${config.futureMonths}")
    println("Intervallo settimane: $rangeStart .. $rangeEnd")
    println("Proclamatori seed upserted: ${seedPeople.size}")
    println("Idoneita' (canLead) aggiornate: ${seedPeople.size * seedPartTypes.size}")
    println("Settimane catalogo storico inserite: $schemaWeeksInserted")
    println("Settimane eliminate nel range (reset): $deletedWeeks")
    println("Programmi eliminati nel range (reset): $deletedPrograms")
    println("Programmi inseriti: ${report.programsInserted}")
    println("Settimane programma inserite: ${report.weeksInserted}")
    println("Parti settimanali inserite: ${report.partsInserted}")
    println("Assegnazioni inserite: ${report.assignmentsInserted}")
}

private data class SeedCliConfig(
    val pastMonths: Int,
    val includeCurrent: Boolean,
    val futureMonths: Int,
    val catalogFile: File,
)

private fun parseArgs(args: Array<String>): SeedCliConfig {
    var pastMonths = DEFAULT_PAST_MONTHS
    var includeCurrent = true
    var futureMonths = DEFAULT_FUTURE_MONTHS
    var catalogPath = DEFAULT_CATALOG_PATH
    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--past-months" -> {
                val value = args.getOrNull(i + 1)?.toIntOrNull()
                if (value != null) pastMonths = value
                i += 2
            }
            "--future-months" -> {
                val value = args.getOrNull(i + 1)?.toIntOrNull()
                if (value != null) futureMonths = value
                i += 2
            }
            "--include-current" -> {
                includeCurrent = true
                i += 1
            }
            "--exclude-current" -> {
                includeCurrent = false
                i += 1
            }
            "--months" -> {
                // Backward-compatible alias: --months controlla la finestra nel passato.
                val value = args.getOrNull(i + 1)?.toIntOrNull()
                if (value != null) pastMonths = value
                i += 2
            }
            "--catalog-path" -> {
                val value = args.getOrNull(i + 1)
                if (!value.isNullOrBlank()) catalogPath = value
                i += 2
            }
            else -> i += 1
        }
    }
    return SeedCliConfig(
        pastMonths = pastMonths,
        includeCurrent = includeCurrent,
        futureMonths = futureMonths,
        catalogFile = File(catalogPath),
    )
}

internal fun buildTargetMonths(
    referenceMonth: YearMonth,
    pastMonths: Int,
    includeCurrent: Boolean,
    futureMonths: Int,
): List<YearMonth> {
    val result = mutableListOf<YearMonth>()
    for (offset in pastMonths downTo 1) {
        result += referenceMonth.minusMonths(offset.toLong())
    }
    if (includeCurrent) {
        result += referenceMonth
    }
    for (offset in 1..futureMonths) {
        result += referenceMonth.plusMonths(offset.toLong())
    }
    return result.distinct()
}

private fun cleanupSeedArtifacts(
    queries: org.example.project.db.MinisteroDatabaseQueries,
) {
    queries.transaction {
        queries.deleteSeedAssignmentsLike("seed-assignment-%")
        queries.deleteSeedWeeklyPartsLike("seed-weekly-part-%")
        queries.deleteSeedWeekPlansLike("seed-week-plan-%")
        queries.deleteSeedProgramsLike("seed-program-%")
        queries.deleteSeedSchemaWeekPartsLike("seed-schema-week-part-%")
        queries.deleteSeedSchemaWeeksLike("seed-schema-week-%")
        queries.deleteOrphanAssignments()
        queries.deleteOrphanWeeklyParts()
    }
}

private fun cleanupOrphans(
    queries: org.example.project.db.MinisteroDatabaseQueries,
) {
    queries.transaction {
        queries.deleteOrphanAssignments()
        queries.deleteOrphanWeeklyParts()
    }
}

private data class SeedPerson(
    val id: String,
    val firstName: String,
    val lastName: String,
    val sex: String,
    val active: Boolean,
    val suspended: Boolean,
    val canAssist: Boolean,
)

private data class SeedPartType(
    val id: String,
    val code: String,
    val label: String,
    val peopleCount: Int,
    val sexRule: SexRule,
    val fixed: Boolean,
    val sortOrder: Int,
)

private data class SeedReport(
    val programsInserted: Int,
    val weeksInserted: Int,
    val partsInserted: Int,
    val assignmentsInserted: Int,
)

private fun seedPeople(): List<SeedPerson> {
    return listOf(
        SeedPerson("seed-person-001", "Marco", "Rossi", "M", active = true, suspended = false, canAssist = true),
        SeedPerson("seed-person-002", "Luca", "Bianchi", "M", active = true, suspended = false, canAssist = true),
        SeedPerson("seed-person-003", "Paolo", "Verdi", "M", active = true, suspended = false, canAssist = false),
        SeedPerson("seed-person-004", "Giovanni", "Neri", "M", active = true, suspended = false, canAssist = true),
        SeedPerson("seed-person-005", "Davide", "Gallo", "M", active = true, suspended = false, canAssist = false),
        SeedPerson("seed-person-006", "Stefano", "Greco", "M", active = true, suspended = true, canAssist = true),
        SeedPerson("seed-person-007", "Francesco", "Costa", "M", active = true, suspended = false, canAssist = true),
        SeedPerson("seed-person-008", "Michele", "Romano", "M", active = false, suspended = false, canAssist = true),
        SeedPerson("seed-person-009", "Andrea", "Marino", "M", active = true, suspended = false, canAssist = true),
        SeedPerson("seed-person-010", "Alessio", "Longo", "M", active = true, suspended = false, canAssist = false),
        SeedPerson("seed-person-011", "Chiara", "Ricci", "F", active = true, suspended = false, canAssist = true),
        SeedPerson("seed-person-012", "Sara", "Colombo", "F", active = true, suspended = false, canAssist = true),
        SeedPerson("seed-person-013", "Elena", "Conti", "F", active = true, suspended = false, canAssist = false),
        SeedPerson("seed-person-014", "Marta", "Giordano", "F", active = true, suspended = true, canAssist = true),
        SeedPerson("seed-person-015", "Giulia", "Mancini", "F", active = true, suspended = false, canAssist = true),
        SeedPerson("seed-person-016", "Valentina", "Rizzo", "F", active = true, suspended = false, canAssist = true),
        SeedPerson("seed-person-017", "Federica", "Moretti", "F", active = true, suspended = false, canAssist = false),
        SeedPerson("seed-person-018", "Martina", "Barbieri", "F", active = false, suspended = false, canAssist = true),
    )
}

private fun ensureSeedPartTypes(
    queries: org.example.project.db.MinisteroDatabaseQueries,
    catalogFile: File,
): List<SeedPartType> {
    val defs = loadPartTypesFromCatalog(catalogFile)
    queries.transaction {
        defs.forEach { part ->
            queries.upsertPartType(
                id = part.id,
                code = part.code,
                label = part.label,
                people_count = part.peopleCount.toLong(),
                sex_rule = part.sexRule.name,
                fixed = if (part.fixed) 1L else 0L,
                sort_order = part.sortOrder.toLong(),
            )
        }
    }

    return defs.map { def ->
        queries.findPartTypeByCode(def.code) { id, code, label, people_count, sex_rule, fixed, sort_order ->
            SeedPartType(
                id = id,
                code = code,
                label = label,
                peopleCount = people_count.toInt(),
                sexRule = runCatching { SexRule.valueOf(sex_rule) }.getOrDefault(SexRule.LIBERO),
                fixed = fixed == 1L,
                sortOrder = sort_order.toInt(),
            )
        }.executeAsOne()
    }.sortedBy { it.sortOrder }
}

private fun loadPartTypesFromCatalog(catalogFile: File): List<SeedPartType> {
    val root = Json.parseToJsonElement(catalogFile.readText()).jsonObject
    val partTypes = root["partTypes"]?.jsonArray
        ?: error("Campo 'partTypes' non trovato in ${catalogFile.absolutePath}")

    return partTypes.mapIndexed { index, element ->
        val parsed = parsePartTypeFromJson(element.jsonObject, index)
        val normalizedCode = parsed.code
            .lowercase()
            .replace("[^a-z0-9]+".toRegex(), "-")
            .trim('-')
        SeedPartType(
            id = "seed-pt-$normalizedCode",
            code = parsed.code,
            label = parsed.label,
            peopleCount = parsed.peopleCount,
            sexRule = parsed.sexRule,
            fixed = parsed.fixed,
            sortOrder = parsed.sortOrder,
        )
    }
}

private fun upsertPeople(
    queries: org.example.project.db.MinisteroDatabaseQueries,
    people: List<SeedPerson>,
) {
    queries.transaction {
        people.forEach { person ->
            queries.upsertProclaimer(
                id = person.id,
                first_name = person.firstName,
                last_name = person.lastName,
                sex = person.sex,
                active = if (person.active) 1L else 0L,
                suspended = if (person.suspended) 1L else 0L,
                can_assist = if (person.canAssist) 1L else 0L,
            )
        }
    }
}

private fun upsertMixedEligibility(
    queries: org.example.project.db.MinisteroDatabaseQueries,
    people: List<SeedPerson>,
    partTypes: List<SeedPartType>,
): Map<String, Set<String>> {
    val leadByPart = linkedMapOf<String, MutableSet<String>>()
    queries.transaction {
        people.forEachIndexed { personIndex, person ->
            partTypes.forEachIndexed { partIndex, part ->
                val canLead = when {
                    !person.active || person.suspended -> false
                    part.sexRule == SexRule.UOMO && person.sex != "M" -> false
                    else -> ((personIndex + partIndex) % 3) != 0
                }
                if (canLead) {
                    leadByPart.getOrPut(part.id) { linkedSetOf() }.add(person.id)
                }
                queries.upsertPersonPartTypeEligibility(
                    id = "seed-eligibility-${person.id}-${part.id}",
                    person_id = person.id,
                    part_type_id = part.id,
                    can_lead = if (canLead) 1L else 0L,
                )
            }
        }
    }

    // Garantisce almeno 3 idonei per parte (quando possibile) per evitare settimane totalmente bloccate.
    val activeCandidates = people.filter { it.active && !it.suspended }
    partTypes.forEach { part ->
        val current = leadByPart.getOrPut(part.id) { linkedSetOf() }
        val eligiblePool = activeCandidates.filter { candidate ->
            part.sexRule != SexRule.UOMO || candidate.sex == "M"
        }
        val needed = max(0, 3 - current.size)
        eligiblePool
            .filter { it.id !in current }
            .take(needed)
            .forEach { person ->
                current += person.id
                queries.upsertPersonPartTypeEligibility(
                    id = "seed-eligibility-${person.id}-${part.id}",
                    person_id = person.id,
                    part_type_id = part.id,
                    can_lead = 1L,
                )
            }
    }

    return leadByPart.mapValues { (_, value) -> value.toSet() }
}

private fun deleteWeeksInRange(
    queries: org.example.project.db.MinisteroDatabaseQueries,
    rangeStart: LocalDate,
    rangeEnd: LocalDate,
): Int {
    val weekIds = queries
        .weekPlansInRange(rangeStart.toString(), rangeEnd.toString())
        .executeAsList()
        .map { it.id }

    queries.transaction {
        weekIds.forEach { weekId ->
            queries.deleteWeekPlan(weekId)
        }
    }
    return weekIds.size
}

private fun deleteProgramsForMonths(
    queries: org.example.project.db.MinisteroDatabaseQueries,
    months: List<YearMonth>,
): Int {
    var deleted = 0
    queries.transaction {
        months.forEach { month ->
            val existingId = queries.findProgramByYearMonth(
                year = month.year.toLong(),
                month = month.monthValue.toLong(),
            ) { id, _, _, _, _, _, _ -> id }.executeAsOneOrNull()

            if (existingId != null) {
                queries.deleteProgramMonthly(existingId)
                deleted += 1
            }
        }
    }
    return deleted
}

private fun seedPastSchemaCatalog(
    queries: org.example.project.db.MinisteroDatabaseQueries,
    partTypes: List<SeedPartType>,
    rangeStart: LocalDate,
    rangeEnd: LocalDate,
): Int {
    var insertedWeeks = 0
    var weekCursor = rangeStart
    var weekIndex = 0
    queries.transaction {
        while (!weekCursor.isAfter(rangeEnd)) {
            val existing = queries.findSchemaWeekByDate(weekCursor.toString()).executeAsOneOrNull()
            if (existing == null) {
                val schemaWeekId = "seed-schema-week-${weekCursor}"
                queries.insertSchemaWeek(
                    id = schemaWeekId,
                    week_start_date = weekCursor.toString(),
                )
                val partSequence = partSequenceForWeek(partTypes, weekIndex)
                partSequence.forEachIndexed { index, part ->
                    queries.insertSchemaWeekPart(
                        id = "seed-schema-week-part-${weekCursor}-$index",
                        schema_week_id = schemaWeekId,
                        part_type_id = part.id,
                        sort_order = index.toLong(),
                    )
                }
                insertedWeeks += 1
            }
            weekCursor = weekCursor.plusWeeks(1)
            weekIndex += 1
        }
    }
    return insertedWeeks
}

private fun seedPastProgramsAndAssignments(
    queries: org.example.project.db.MinisteroDatabaseQueries,
    months: List<YearMonth>,
    partTypes: List<SeedPartType>,
    people: List<SeedPerson>,
    eligibilityMap: Map<String, Set<String>>,
    random: Random,
): SeedReport {
    var programsInserted = 0
    var weeksInserted = 0
    var partsInserted = 0
    var assignmentsInserted = 0
    var absoluteWeekIndex = 0

    val assignablePeople = people.filter { it.active && !it.suspended }
    val assistPoolByPart = partTypes.associate { part ->
        part.id to assignablePeople.filter { person ->
            person.canAssist && (part.sexRule != SexRule.UOMO || person.sex == "M")
        }
    }

    queries.transaction {
        months.forEach { month ->
            val programId = "seed-program-${month.year}-${month.monthValue.toString().padStart(2, '0')}"
            val startDate = firstMondayInMonth(month)
            val endDate = endSundayForMonth(month)
            val createdAt = startDate.minusDays(10).atTime(20, 0)

            queries.insertProgramMonthly(
                id = programId,
                year = month.year.toLong(),
                month = month.monthValue.toLong(),
                start_date = startDate.toString(),
                end_date = endDate.toString(),
                template_applied_at = createdAt.plusDays(2).toString(),
                created_at = createdAt.toString(),
            )
            programsInserted += 1

            var weekCursor = startDate
            while (!weekCursor.isAfter(endDate)) {
                val isSkipped = random.nextDouble() < 0.08
                val weekStatus = if (isSkipped) "SKIPPED" else "ACTIVE"
                val weekPlanId = "seed-week-plan-${programId}-$weekCursor"
                queries.insertWeekPlanWithProgram(
                    id = weekPlanId,
                    week_start_date = weekCursor.toString(),
                    program_id = programId,
                    status = weekStatus,
                )
                weeksInserted += 1

                val parts = partSequenceForWeek(partTypes, absoluteWeekIndex)
                val weeklyParts = parts.mapIndexed { index, part ->
                    val weeklyPartId = "seed-weekly-part-${programId}-$weekCursor-$index"
                    queries.insertWeeklyPart(
                        id = weeklyPartId,
                        week_plan_id = weekPlanId,
                        part_type_id = part.id,
                        sort_order = index.toLong(),
                    )
                    partsInserted += 1
                    weeklyPartId to part
                }

                if (!isSkipped) {
                    val usedInWeek = linkedSetOf<String>()
                    weeklyParts.forEachIndexed { index, (weeklyPartId, part) ->
                        for (slot in 1..part.peopleCount) {
                            val shouldLeaveUnassigned = random.nextDouble() < 0.14 && !(slot == 1 && random.nextDouble() < 0.5)
                            if (shouldLeaveUnassigned) continue

                            val candidatePool = if (slot == 1) {
                                val leadIds = eligibilityMap[part.id].orEmpty()
                                assignablePeople.filter { it.id in leadIds }
                            } else {
                                assistPoolByPart[part.id].orEmpty()
                            }
                            val person = pickCandidate(
                                pool = candidatePool,
                                usedInWeek = usedInWeek,
                                offset = absoluteWeekIndex + index + slot,
                            ) ?: continue

                            queries.upsertAssignment(
                                id = "seed-assignment-${programId}-$weekCursor-$index-$slot-${person.id}",
                                weekly_part_id = weeklyPartId,
                                person_id = person.id,
                                slot = slot.toLong(),
                            )
                            usedInWeek += person.id
                            assignmentsInserted += 1
                        }
                    }
                }

                weekCursor = weekCursor.plusWeeks(1)
                absoluteWeekIndex += 1
            }
        }
    }

    return SeedReport(
        programsInserted = programsInserted,
        weeksInserted = weeksInserted,
        partsInserted = partsInserted,
        assignmentsInserted = assignmentsInserted,
    )
}

private fun pickCandidate(
    pool: List<SeedPerson>,
    usedInWeek: Set<String>,
    offset: Int,
): SeedPerson? {
    if (pool.isEmpty()) return null
    val startIndex = if (pool.isEmpty()) 0 else (offset % pool.size).coerceAtLeast(0)
    for (step in pool.indices) {
        val candidate = pool[(startIndex + step) % pool.size]
        if (candidate.id !in usedInWeek) return candidate
    }
    return null
}

private fun partSequenceForWeek(
    partTypes: List<SeedPartType>,
    weekIndex: Int,
): List<SeedPartType> {
    val sorted = partTypes.sortedBy { it.sortOrder }
    val fixed = sorted.firstOrNull { it.fixed }
    val others = sorted.filterNot { it.fixed }

    if (others.isEmpty()) return listOfNotNull(fixed)

    val rotatingCount = minOf(5, others.size)
    val rotating = (0 until rotatingCount).map { offset ->
        others[(weekIndex + offset) % others.size]
    }

    val result = linkedSetOf<SeedPartType>()
    if (fixed != null) result += fixed
    rotating.forEach { result += it }

    val hasAssistPart = result.any { it.peopleCount > 1 }
    if (!hasAssistPart) {
        others.firstOrNull { it.peopleCount > 1 }?.let { result += it }
    }

    return result.toList().sortedBy { it.sortOrder }
}

private fun firstMondayInMonth(month: YearMonth): LocalDate {
    return month.atDay(1).with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY))
}

private fun endSundayForMonth(month: YearMonth): LocalDate {
    return month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
}

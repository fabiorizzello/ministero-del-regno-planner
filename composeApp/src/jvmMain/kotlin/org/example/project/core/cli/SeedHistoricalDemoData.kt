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
import kotlin.math.min
import kotlin.random.Random

private const val DEFAULT_PAST_MONTHS = 10
private const val DEFAULT_FUTURE_MONTHS = 2
private const val DEFAULT_SEED_PEOPLE_COUNT = 84
private const val RANDOM_SEED = 20260226L
private const val DEFAULT_CATALOG_PATH = ".worktrees/efficaci-nel-ministero-data/schemas-catalog.json"

fun main(args: Array<String>) {
    val config = parseArgs(args)
    require(config.pastMonths >= 0) { "--past-months deve essere >= 0" }
    require(config.futureMonths >= 0) { "--future-months deve essere >= 0" }

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
    val resolvedPartTypes = resolveSeedPartTypes(queries, config.catalogFile)
    val seedPartTypes = resolvedPartTypes.partTypes
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
        now = now,
    )
    cleanupOrphans(queries)

    println("Seed demo completato.")
    println("Catalogo part types: ${config.catalogFile.absolutePath}")
    println("Sorgente part types: ${resolvedPartTypes.source}")
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

private data class SeedPartTypeResolution(
    val source: String,
    val partTypes: List<SeedPartType>,
)

private data class ExistingSeedPartTypeRow(
    val partType: SeedPartType,
    val active: Boolean,
)

private val SEED_LAST_NAMES = listOf(
    "Rossi",
    "Bianchi",
    "Verdi",
    "Neri",
    "Gallo",
    "Greco",
    "Costa",
    "Romano",
    "Marino",
    "Longo",
    "Ricci",
    "Colombo",
    "Conti",
    "Giordano",
    "Mancini",
    "Rizzo",
    "Moretti",
    "Barbieri",
    "Lombardi",
    "Fontana",
    "Santoro",
    "Ferrara",
    "Rinaldi",
    "Caruso",
    "Leone",
    "Ferri",
    "Villa",
    "Martini",
    "DeLuca",
    "Esposito",
    "Farina",
    "Pellegrini",
    "Testa",
    "Monti",
    "Palumbo",
    "Donati",
    "Piras",
    "Sanna",
    "Vitali",
    "Parisi",
    "Bianco",
    "Orlando",
    "Damico",
    "Rossetti",
    "Sartori",
    "Bellini",
    "Negri",
    "Marchetti",
    "Amato",
    "Serra",
    "Fabbri",
    "DeSantis",
    "Moro",
    "Pace",
    "Bruno",
    "Coppola",
    "Fiore",
    "Valente",
    "DeRosa",
    "Caputo",
    "Ruggieri",
    "Benedetti",
    "Grassi",
    "Messina",
    "Vacca",
    "Damiani",
    "Cattaneo",
    "Pinto",
    "Sala",
    "Puglisi",
)

private val SEED_FIRST_NAMES_M = listOf(
    "Marco",
    "Luca",
    "Paolo",
    "Giovanni",
    "Davide",
    "Stefano",
    "Francesco",
    "Michele",
    "Andrea",
    "Alessio",
    "Gabriele",
    "Matteo",
    "Simone",
    "Riccardo",
    "Nicola",
    "Tommaso",
    "Filippo",
    "Daniele",
    "Christian",
    "Enrico",
    "Fabio",
    "Claudio",
    "Emanuele",
    "Salvatore",
    "Roberto",
    "Pierluigi",
    "Massimo",
    "Vincenzo",
)

private val SEED_FIRST_NAMES_F = listOf(
    "Chiara",
    "Sara",
    "Elena",
    "Marta",
    "Giulia",
    "Valentina",
    "Federica",
    "Martina",
    "Alessandra",
    "Francesca",
    "Laura",
    "Silvia",
    "Ilaria",
    "Barbara",
    "Monica",
    "Raffaella",
    "Serena",
    "Beatrice",
    "Arianna",
    "Noemi",
    "Veronica",
    "Eleonora",
    "Camilla",
    "Debora",
    "Claudia",
    "Giorgia",
    "Anna",
    "Lucia",
)

private fun seedPeople(): List<SeedPerson> {
    val maleTarget = DEFAULT_SEED_PEOPLE_COUNT / 2
    val femaleTarget = DEFAULT_SEED_PEOPLE_COUNT - maleTarget
    val malePeople = buildSeedPeopleForSex(
        sex = "M",
        firstNames = SEED_FIRST_NAMES_M,
        targetCount = maleTarget,
        lastNameOffset = 3,
    )
    val femalePeople = buildSeedPeopleForSex(
        sex = "F",
        firstNames = SEED_FIRST_NAMES_F,
        targetCount = femaleTarget,
        lastNameOffset = 11,
    )
    return (malePeople + femalePeople)
        .sortedWith(compareBy<SeedPerson>({ it.lastName }, { it.firstName }))
        .mapIndexed { index, person ->
            person.copy(id = "seed-person-${(index + 1).toString().padStart(3, '0')}")
        }
}

private fun buildSeedPeopleForSex(
    sex: String,
    firstNames: List<String>,
    targetCount: Int,
    lastNameOffset: Int,
): List<SeedPerson> {
    require(targetCount <= firstNames.size * SEED_LAST_NAMES.size) {
        "Target proclamatori troppo alto: $targetCount per sesso=$sex"
    }
    val result = mutableListOf<SeedPerson>()
    val usedNames = linkedSetOf<String>()
    var cursor = 0
    while (result.size < targetCount) {
        val firstName = firstNames[cursor % firstNames.size]
        val lastName = SEED_LAST_NAMES[(cursor * 7 + lastNameOffset) % SEED_LAST_NAMES.size]
        val fullNameKey = "$firstName|$lastName"
        if (usedNames.add(fullNameKey)) {
            val localIndex = result.size
            val suspended = (localIndex % 9 == 0)
            val canAssist = when {
                suspended -> (localIndex % 3) != 0
                else -> (localIndex % 4) != 0
            }
            result += SeedPerson(
                id = "seed-person-temp-$sex-${localIndex + 1}",
                firstName = firstName,
                lastName = lastName,
                sex = sex,
                suspended = suspended,
                canAssist = canAssist,
            )
        }
        cursor += 1
    }
    return result
}

private fun resolveSeedPartTypes(
    queries: org.example.project.db.MinisteroDatabaseQueries,
    catalogFile: File,
): SeedPartTypeResolution {
    val existingRows = queries.listAllPartTypesExtended { id, code, label, people_count, sex_rule, fixed, active, sort_order, _ ->
        ExistingSeedPartTypeRow(
            partType = SeedPartType(
                id = id,
                code = code,
                label = label,
                peopleCount = people_count.toInt(),
                sexRule = runCatching { SexRule.valueOf(sex_rule) }.getOrDefault(SexRule.STESSO_SESSO),
                fixed = fixed == 1L,
                sortOrder = sort_order.toInt(),
            ),
            active = active == 1L,
        )
    }.executeAsList()

    val existingActive = existingRows
        .asSequence()
        .filter { it.active }
        .map { it.partType }
        .sortedBy { it.sortOrder }
        .toList()

    if (existingActive.isNotEmpty()) {
        return SeedPartTypeResolution(
            source = "database (part_type attivi esistenti)",
            partTypes = existingActive,
        )
    }

    require(catalogFile.exists()) { "Catalogo non trovato: ${catalogFile.absolutePath}" }
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

    val seededFromCatalog = defs.map { def ->
        queries.findPartTypeByCode(def.code) { id, code, label, people_count, sex_rule, fixed, sort_order ->
            SeedPartType(
                id = id,
                code = code,
                label = label,
                peopleCount = people_count.toInt(),
                sexRule = runCatching { SexRule.valueOf(sex_rule) }.getOrDefault(SexRule.STESSO_SESSO),
                fixed = fixed == 1L,
                sortOrder = sort_order.toInt(),
            )
        }.executeAsOne()
    }.sortedBy { it.sortOrder }

    return SeedPartTypeResolution(
        source = "catalogo JSON (fallback, DB part_type vuoto)",
        partTypes = seededFromCatalog,
    )
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
    val leadByPerson = linkedMapOf<String, MutableSet<String>>()
    queries.transaction {
        people.forEachIndexed { personIndex, person ->
            partTypes.forEachIndexed { partIndex, part ->
                val canLead = shouldSeedCanLead(
                    personIndex = personIndex,
                    partIndex = partIndex,
                    suspended = person.suspended,
                    sex = person.sex,
                    canAssist = person.canAssist,
                    sexRule = part.sexRule,
                )
                if (canLead) {
                    leadByPart.getOrPut(part.id) { linkedSetOf() }.add(person.id)
                    leadByPerson.getOrPut(person.id) { linkedSetOf() }.add(part.id)
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

    // Garantisce una base ampia di idonei per parte per ridurre slot scoperti durante il seed storico.
    val activeCandidates = people.filter { !it.suspended }
    partTypes.forEach { part ->
        val current = leadByPart.getOrPut(part.id) { linkedSetOf() }
        val eligiblePool = activeCandidates.filter { candidate ->
            part.sexRule != SexRule.UOMO || candidate.sex == "M"
        }
        val targetLeads = min(
            eligiblePool.size,
            max(6, (eligiblePool.size * 3) / 4),
        )
        val needed = max(0, targetLeads - current.size)
        eligiblePool
            .filter { it.id !in current }
            .take(needed)
            .forEach { person ->
                current += person.id
                leadByPerson.getOrPut(person.id) { linkedSetOf() }.add(part.id)
                queries.upsertPersonPartTypeEligibility(
                    id = "seed-eligibility-${person.id}-${part.id}",
                    person_id = person.id,
                    part_type_id = part.id,
                    can_lead = 1L,
                )
            }
    }

    // Ogni proclamatore non sospeso riceve molte idoneità compatibili, non solo pochi casi sparsi.
    activeCandidates.forEach { person ->
        val compatibleParts = partTypes.filter { part ->
            part.sexRule != SexRule.UOMO || person.sex == "M"
        }
        val current = leadByPerson.getOrPut(person.id) { linkedSetOf() }
        val targetLeads = min(
            compatibleParts.size,
            max(5, (compatibleParts.size * 4) / 5),
        )
        val needed = max(0, targetLeads - current.size)
        compatibleParts
            .filter { it.id !in current }
            .take(needed)
            .forEach { part ->
                current += part.id
                leadByPart.getOrPut(part.id) { linkedSetOf() }.add(person.id)
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

internal fun shouldSeedCanLead(
    personIndex: Int,
    partIndex: Int,
    suspended: Boolean,
    sex: String,
    canAssist: Boolean,
    sexRule: SexRule,
): Boolean {
    if (suspended) return false
    if (sexRule == SexRule.UOMO && sex != "M") return false

    val rotation = (personIndex * 7 + partIndex * 5 + if (canAssist) 3 else 0) % 19
    return if (canAssist) {
        rotation != 0
    } else {
        rotation != 0 && rotation != 7 && rotation != 13
    }
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
    now: LocalDate,
): SeedReport {
    var programsInserted = 0
    var weeksInserted = 0
    var partsInserted = 0
    var assignmentsInserted = 0
    var absoluteWeekIndex = 0

    val assignablePeople = people.filter { !it.suspended }
    val assistPoolByPart = partTypes.associate { part ->
        part.id to assignablePeople.filter { person ->
            person.canAssist && (part.sexRule != SexRule.UOMO || person.sex == "M")
        }
    }
    val cooldownAnchorIds = assignablePeople
        .sortedWith(compareBy<SeedPerson>({ it.lastName }, { it.firstName }))
        .take(10)
        .map { it.id }
        .toSet()
    val lastGlobalAssignmentWeekByPerson = mutableMapOf<String, Int>()
    val lastPartTypeAssignmentWeekByPerson = mutableMapOf<String, MutableMap<String, Int>>()

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
                val isSkipped = random.nextDouble() < skipChanceForWeek(weekCursor, now)
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

                val weekMode = assignmentModeForWeek(
                    weekStartDate = weekCursor,
                    today = now,
                    absoluteWeekIndex = absoluteWeekIndex,
                )
                if (!isSkipped && weekMode != WeekAssignmentMode.EMPTY) {
                    val usedInWeek = linkedSetOf<String>()
                    val preferCooldownAnchors = weekCursor.isAfter(now.minusWeeks(7)) && weekCursor.isBefore(now)
                    weeklyParts.forEachIndexed { index, (weeklyPartId, part) ->
                        for (slot in 1..part.peopleCount) {
                            val shouldLeaveUnassigned = shouldLeaveSlotUnassigned(
                                mode = weekMode,
                                slot = slot,
                                random = random,
                            )
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
                                partTypeId = part.id,
                                weekIndex = absoluteWeekIndex + index,
                                random = random,
                                lastGlobalAssignmentWeekByPerson = lastGlobalAssignmentWeekByPerson,
                                lastPartTypeAssignmentWeekByPerson = lastPartTypeAssignmentWeekByPerson,
                                cooldownAnchorIds = cooldownAnchorIds,
                                preferCooldownAnchors = preferCooldownAnchors,
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
    partTypeId: String,
    weekIndex: Int,
    random: Random,
    lastGlobalAssignmentWeekByPerson: MutableMap<String, Int>,
    lastPartTypeAssignmentWeekByPerson: MutableMap<String, MutableMap<String, Int>>,
    cooldownAnchorIds: Set<String>,
    preferCooldownAnchors: Boolean,
): SeedPerson? {
    if (pool.isEmpty()) return null
    val partHistory = lastPartTypeAssignmentWeekByPerson.getOrPut(partTypeId) { mutableMapOf() }
    val selected = pool
        .asSequence()
        .filter { it.id !in usedInWeek }
        .map { person ->
            val lastGlobal = lastGlobalAssignmentWeekByPerson[person.id]
            val lastForPart = partHistory[person.id]
            val globalGap = if (lastGlobal == null) 1_000 else weekIndex - lastGlobal
            val partGap = if (lastForPart == null) 1_000 else weekIndex - lastForPart
            val assistBias = if (person.canAssist) 2 else 0
            val cooldownBias = when {
                preferCooldownAnchors && person.id in cooldownAnchorIds -> 16
                !preferCooldownAnchors && person.id in cooldownAnchorIds -> -2
                else -> 0
            }
            val score = globalGap * 6 + partGap * 4 + assistBias + cooldownBias + random.nextDouble(0.0, 2.0)
            person to score
        }
        .maxByOrNull { it.second }
        ?.first
        ?: return null

    lastGlobalAssignmentWeekByPerson[selected.id] = weekIndex
    partHistory[selected.id] = weekIndex
    return selected
}

private enum class WeekAssignmentMode {
    DENSE,
    PARTIAL,
    SPARSE,
    EMPTY,
}

private fun assignmentModeForWeek(
    weekStartDate: LocalDate,
    today: LocalDate,
    absoluteWeekIndex: Int,
): WeekAssignmentMode {
    val phase = absoluteWeekIndex.mod(6)
    return when {
        weekStartDate.isAfter(today.plusMonths(1)) -> when (phase) {
            0 -> WeekAssignmentMode.EMPTY
            1, 2 -> WeekAssignmentMode.SPARSE
            else -> WeekAssignmentMode.PARTIAL
        }
        weekStartDate.isAfter(today.minusWeeks(1)) -> when (phase) {
            0 -> WeekAssignmentMode.EMPTY
            1, 2 -> WeekAssignmentMode.SPARSE
            3 -> WeekAssignmentMode.PARTIAL
            else -> WeekAssignmentMode.DENSE
        }
        weekStartDate.isAfter(today.minusMonths(2)) -> when (phase) {
            0 -> WeekAssignmentMode.SPARSE
            1, 2 -> WeekAssignmentMode.PARTIAL
            else -> WeekAssignmentMode.DENSE
        }
        else -> when (phase) {
            0 -> WeekAssignmentMode.PARTIAL
            else -> WeekAssignmentMode.DENSE
        }
    }
}

private fun shouldLeaveSlotUnassigned(
    mode: WeekAssignmentMode,
    slot: Int,
    random: Random,
): Boolean {
    if (mode == WeekAssignmentMode.EMPTY) return true
    val leaveChance = when (mode) {
        WeekAssignmentMode.DENSE -> 0.05
        WeekAssignmentMode.PARTIAL -> 0.30
        WeekAssignmentMode.SPARSE -> 0.62
        WeekAssignmentMode.EMPTY -> 1.0
    }
    val leaderKeepChance = when (mode) {
        WeekAssignmentMode.DENSE -> 0.90
        WeekAssignmentMode.PARTIAL -> 0.68
        WeekAssignmentMode.SPARSE -> 0.38
        WeekAssignmentMode.EMPTY -> 0.0
    }
    if (slot == 1 && random.nextDouble() < leaderKeepChance) return false
    return random.nextDouble() < leaveChance
}

private fun skipChanceForWeek(weekStartDate: LocalDate, today: LocalDate): Double {
    return when {
        weekStartDate.isAfter(today.plusMonths(1)) -> 0.16
        weekStartDate.isAfter(today) -> 0.12
        weekStartDate.isBefore(today.minusMonths(4)) -> 0.05
        else -> 0.08
    }
}

private fun partSequenceForWeek(
    partTypes: List<SeedPartType>,
    weekIndex: Int,
): List<SeedPartType> {
    val sorted = partTypes.sortedBy { it.sortOrder }
    val fixed = sorted.firstOrNull { it.fixed }
    val others = sorted.filterNot { it.fixed }

    if (others.isEmpty()) return listOfNotNull(fixed)

    val minRotating = minOf(4, others.size)
    val maxRotating = minOf(7, others.size)
    val rotatingCount = minRotating + if (maxRotating > minRotating) {
        weekIndex % (maxRotating - minRotating + 1)
    } else {
        0
    }
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

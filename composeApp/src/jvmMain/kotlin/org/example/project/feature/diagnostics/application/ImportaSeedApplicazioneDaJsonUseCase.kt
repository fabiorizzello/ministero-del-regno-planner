package org.example.project.feature.diagnostics.application

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.either
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionRunner
import org.example.project.core.persistence.TransactionScope
import org.example.project.feature.people.application.EligibilityStore
import org.example.project.feature.people.application.ProclamatoriAggregateStore
import org.example.project.feature.people.application.ProclamatoriQuery
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.domain.Sesso
import org.example.project.feature.weeklyparts.application.WeekPlanStore
import org.example.project.feature.weeklyparts.application.PartTypeStore
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.SexRule
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanAggregate
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeeklyPart
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import org.example.project.feature.assignments.domain.Assignment
import org.example.project.feature.assignments.domain.AssignmentId
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAdjusters
import java.util.UUID

@Serializable
private data class SeedImportDto(
    val version: Int = 0,
    val partTypes: List<SeedPartTypeDto> = emptyList(),
    val students: List<SeedStudentDto> = emptyList(),
)

@Serializable
private data class SeedPartTypeDto(
    val code: String = "",
    val label: String = "",
    val peopleCount: Int = 0,
    val sexRule: String = "",
    val fixed: Boolean = false,
    val sortOrder: Int? = null,
)

@Serializable
private data class SeedStudentDto(
    val nome: String = "",
    val cognome: String = "",
    val sesso: String = "",
    val sospeso: Boolean = false,
    val puoAssistere: Boolean = false,
    val canLeadPartTypeCodes: List<String> = emptyList(),
    @SerialName("ultimaParte")
    val ultimaParte: List<SeedStudentLastAssignmentDto> = emptyList(),
    @SerialName("ultimaAssistenza")
    val ultimaAssistenza: String? = null,
)

@Serializable
private data class SeedStudentLastAssignmentDto(
    @SerialName("data")
    val data: String = "",
    @SerialName("tipo")
    val tipo: String = "",
)

private data class ParsedSeedStudent(
    val person: Proclamatore,
    val canLeadPartTypeCodes: Set<String>,
    val lastAssignments: List<ParsedSeedStudentLastAssignment>,
    val lastAssistance: LocalDate?,
)

private data class ParsedSeedStudentLastAssignment(
    val date: LocalDate,
    val partTypeCode: String,
)

private data class ParsedSeedImport(
    val partTypes: List<PartType>,
    val students: List<ParsedSeedStudent>,
    val assistanceHostPartTypeCode: String?,
)

class ImportaSeedApplicazioneDaJsonUseCase(
    private val proclamatoriQuery: ProclamatoriQuery,
    private val proclamatoriStore: ProclamatoriAggregateStore,
    private val partTypeStore: PartTypeStore,
    private val eligibilityStore: EligibilityStore,
    private val weekPlanStore: WeekPlanStore,
    private val transactionRunner: TransactionRunner,
) {
    data class Result(
        val importedPartTypes: Int,
        val importedStudents: Int,
        val importedLeadEligibility: Int,
        val importedHistoricalAssignments: Int,
        val importedAssistanceLastDates: Int,
    )

    private val json = Json { ignoreUnknownKeys = true }

    suspend operator fun invoke(
        jsonContent: String,
        referenceDate: LocalDate,
    ): Either<DomainError, Result> = either {
        val existingStudents = proclamatoriQuery.cerca(termine = null)
        if (existingStudents.isNotEmpty()) {
            raise(DomainError.ImportArchivioNonVuoto)
        }

        val parsed = parseAndValidate(jsonContent, referenceDate).bind()

        transactionRunner.runInTransactionEither {
            either {
                partTypeStore.upsertAll(parsed.partTypes)
                partTypeStore.deactivateMissingCodes(parsed.partTypes.map { it.code }.toSet())

                val storedPartTypesByCode = mutableMapOf<String, PartType>()
                parsed.partTypes.forEach { imported ->
                    val stored = partTypeStore.findByCode(imported.code)
                        ?: raise(DomainError.ImportSalvataggioFallito("Tipo parte non trovato dopo il salvataggio: ${imported.code}"))
                    storedPartTypesByCode[imported.code] = stored
                }

                proclamatoriStore.persistAll(parsed.students.map { it.person })

                parsed.students.forEach { student ->
                    student.canLeadPartTypeCodes.forEach { code ->
                        val storedPartType = storedPartTypesByCode[code]
                            ?: raise(DomainError.ImportSalvataggioFallito("Tipo parte non disponibile per idoneita': $code"))
                        eligibilityStore.setCanLead(
                            personId = student.person.id,
                            partTypeId = storedPartType.id,
                            canLead = true,
                        )
                    }
                }

                parsed.students.forEach { student ->
                    student.lastAssignments.forEach { lastAssignment ->
                        val storedPartType = storedPartTypesByCode[lastAssignment.partTypeCode]
                            ?: raise(
                                DomainError.ImportSalvataggioFallito(
                                    "Tipo parte non disponibile per storico assegnazioni: ${lastAssignment.partTypeCode}",
                                ),
                            )
                        persistHistoricalAssignment(
                            personId = student.person.id,
                            partType = storedPartType,
                            assignmentDate = lastAssignment.date,
                            slot = 1,
                        ).bind()
                    }
                }

                val assistanceHostPartType: PartType? = parsed.assistanceHostPartTypeCode?.let { code ->
                    storedPartTypesByCode[code]
                        ?: raise(DomainError.ImportSalvataggioFallito("Tipo parte assistenza non disponibile dopo il salvataggio: $code"))
                }

                parsed.students.forEach { student ->
                    val lastAssistance = student.lastAssistance ?: return@forEach
                    val hostPartType = assistanceHostPartType
                        ?: raise(DomainError.ImportSalvataggioFallito("Tipo parte assistenza non risolto"))
                    persistHistoricalAssignment(
                        personId = student.person.id,
                        partType = hostPartType,
                        assignmentDate = lastAssistance,
                        slot = 2,
                    ).bind()
                }

                Result(
                    importedPartTypes = parsed.partTypes.size,
                    importedStudents = parsed.students.size,
                    importedLeadEligibility = parsed.students.sumOf { it.canLeadPartTypeCodes.size },
                    importedHistoricalAssignments = parsed.students.sumOf { it.lastAssignments.size },
                    importedAssistanceLastDates = parsed.students.count { it.lastAssistance != null },
                )
            }
        }.bind()
    }

    private fun parseAndValidate(
        jsonContent: String,
        referenceDate: LocalDate,
    ): Either<DomainError, ParsedSeedImport> = either {
        val dto = try {
            json.decodeFromString<SeedImportDto>(jsonContent)
        } catch (_: SerializationException) {
            raise(DomainError.ImportJsonNonValido)
        }

        if (dto.version != 1) {
            raise(DomainError.ImportVersioneSchemaNonSupportata(dto.version))
        }
        if (dto.partTypes.isEmpty()) {
            raise(DomainError.ImportContenutoNonValido("Il file non contiene tipi parte da importare"))
        }
        if (dto.students.isEmpty()) {
            raise(DomainError.ImportContenutoNonValido("Il file non contiene studenti da importare"))
        }

        val errors = mutableListOf<String>()
        val partTypes = mutableListOf<PartType>()
        val partTypeByCode = linkedMapOf<String, PartType>()
        val fixedPartCodes = mutableListOf<String>()
        val seenPartTypeCodes = mutableSetOf<String>()

        dto.partTypes.forEachIndexed { index, item ->
            validatePartType(index, item, seenPartTypeCodes).fold(
                ifLeft = { errors += it },
                ifRight = { partType ->
                    partTypes += partType
                    partTypeByCode[partType.code] = partType
                    if (partType.fixed) fixedPartCodes += partType.code
                },
            )
        }

        if (fixedPartCodes.size > 1) {
            errors += "Sono presenti piu' parti fisse nel file: ${fixedPartCodes.joinToString(", ")}"
        }

        val students = mutableListOf<ParsedSeedStudent>()
        val seenStudents = mutableSetOf<String>()
        dto.students.forEachIndexed { index, item ->
            validateStudent(index, item, seenStudents, partTypeByCode, referenceDate).fold(
                ifLeft = { errors += it },
                ifRight = { students += it },
            )
        }

        // Risoluzione del PartType "host" per le ghost week di ultimaAssistenza:
        // primo PartType del catalogo importato con peopleCount >= 2, in sortOrder.
        // Necessario solo se almeno uno studente ha ultimaAssistenza valorizzato.
        val assistanceHostPartTypeCode: String? = if (students.any { it.lastAssistance != null }) {
            val candidate = partTypes
                .filter { it.peopleCount >= 2 }
                .minByOrNull { it.sortOrder }
            if (candidate == null) {
                errors += "ultimaAssistenza: nessun tipo parte con peopleCount >= 2 nel catalogo importato"
                null
            } else {
                candidate.code
            }
        } else null

        if (errors.isNotEmpty()) {
            val preview = errors.take(5).joinToString(" | ")
            val suffix = if (errors.size > 5) " | ..." else ""
            raise(DomainError.ImportContenutoNonValido("Import seed non completato. Errori (${errors.size}): $preview$suffix"))
        }

        ParsedSeedImport(
            partTypes = partTypes,
            students = students,
            assistanceHostPartTypeCode = assistanceHostPartTypeCode,
        )
    }

    private fun validatePartType(
        index: Int,
        item: SeedPartTypeDto,
        seenCodes: MutableSet<String>,
    ): Either<String, PartType> = either {
        val position = index + 1
        val code = normalizePartTypeCode(item.code)
            .takeIf { it.isNotBlank() }
            ?: raise("partTypes[$position]: campo code obbligatorio")

        if (!seenCodes.add(code)) {
            raise("partTypes[$position]: code duplicato nel file ($code)")
        }

        val sexRule = parseSexRule(item.sexRule).getOrElse { message ->
            raise("partTypes[$position]: $message")
        }

        PartType.of(
            id = PartTypeId(UUID.randomUUID().toString()),
            code = code,
            label = item.label.trim(),
            peopleCount = item.peopleCount,
            sexRule = sexRule,
            fixed = item.fixed,
            sortOrder = item.sortOrder ?: index,
        ).fold(
            ifLeft = { error ->
                val detail = (error as? DomainError.Validation)?.message ?: error.toString()
                raise("partTypes[$position]: $detail")
            },
            ifRight = { it },
        )
    }

    private fun validateStudent(
        index: Int,
        item: SeedStudentDto,
        seenStudents: MutableSet<String>,
        partTypeByCode: Map<String, PartType>,
        referenceDate: LocalDate,
    ): Either<String, ParsedSeedStudent> = either {
        val position = index + 1
        val nome = item.nome.trim()
            .takeIf { it.isNotBlank() }
            ?: raise("students[$position]: campo nome obbligatorio")
        val cognome = item.cognome.trim()
            .takeIf { it.isNotBlank() }
            ?: raise("students[$position]: campo cognome obbligatorio")
        val sesso = parseSesso(item.sesso).getOrElse { message ->
            raise("students[$position]: $message")
        }

        val duplicateKey = "${nome.lowercase()}|${cognome.lowercase()}"
        if (!seenStudents.add(duplicateKey)) {
            raise("students[$position]: studente duplicato nel file ($nome $cognome)")
        }

        val canLeadCodes = linkedSetOf<String>()
        item.canLeadPartTypeCodes.forEachIndexed { codeIndex, rawCode ->
            val code = normalizePartTypeCode(rawCode)
                .takeIf { it.isNotBlank() }
                ?: raise("students[$position].canLeadPartTypeCodes[${codeIndex + 1}]: codice tipo parte obbligatorio")
            if (!canLeadCodes.add(code)) {
                raise("students[$position]: codice tipo parte duplicato nelle idoneita' ($code)")
            }

            val partType = partTypeByCode[code]
                ?: raise("students[$position]: tipo parte non definito nel catalogo importato ($code)")
            if (!canLeadForSex(sesso, partType.sexRule)) {
                raise("students[$position]: $nome $cognome non puo' condurre $code con sesso ${sesso.name}")
            }
        }

        val person = Proclamatore.of(
            id = ProclamatoreId(UUID.randomUUID().toString()),
            nome = nome,
            cognome = cognome,
            sesso = sesso,
            sospeso = item.sospeso,
            puoAssistere = item.puoAssistere,
        ).fold(
            ifLeft = { error ->
                val detail = (error as? DomainError.Validation)?.message ?: error.toString()
                raise("students[$position]: $detail")
            },
            ifRight = { it },
        )

        val lastAssistance: LocalDate? = item.ultimaAssistenza?.trim()?.takeIf { it.isNotBlank() }?.let { raw ->
            val parsed = try {
                LocalDate.parse(raw)
            } catch (_: DateTimeParseException) {
                raise("students[$position].ultimaAssistenza: data non valida ($raw), atteso formato ISO yyyy-MM-dd")
            }
            if (parsed.isAfter(referenceDate)) {
                raise("students[$position].ultimaAssistenza: data nel futuro ($parsed) non ammessa per dati storici")
            }
            // Skip silently se l'assistenza cade nel mese corrente o nel mese precedente:
            // i programmi di questi mesi sono tipicamente gia' in carico nell'app, quindi
            // una ghost week creerebbe conflitto o duplicato con dati reali.
            val parsedMonth = YearMonth.from(parsed)
            val cutoffMonth = YearMonth.from(referenceDate).minusMonths(1)
            if (!parsedMonth.isBefore(cutoffMonth)) {
                null
            } else {
                parsed
            }
        }

        ParsedSeedStudent(
            person = person,
            canLeadPartTypeCodes = canLeadCodes,
            lastAssignments = validateLastAssignments(
                position = position,
                items = item.ultimaParte,
                partTypeByCode = partTypeByCode,
                referenceDate = referenceDate,
            ).getOrElse { message ->
                raise(message)
            },
            lastAssistance = lastAssistance,
        )
    }

    private fun validateLastAssignments(
        position: Int,
        items: List<SeedStudentLastAssignmentDto>,
        partTypeByCode: Map<String, PartType>,
        referenceDate: LocalDate,
    ): Either<String, List<ParsedSeedStudentLastAssignment>> = either {
        val seenPartTypes = mutableSetOf<String>()
        val cutoffMonth = YearMonth.from(referenceDate).minusMonths(1)
        val kept = mutableListOf<ParsedSeedStudentLastAssignment>()
        items.forEachIndexed { index, item ->
            val entryPosition = index + 1
            val date = try {
                LocalDate.parse(item.data.trim())
            } catch (_: DateTimeParseException) {
                raise(
                    "students[$position].ultimaParte[$entryPosition]: data non valida (${item.data}), atteso formato ISO yyyy-MM-dd",
                )
            }

            if (date.isAfter(referenceDate)) {
                raise(
                    "students[$position].ultimaParte[$entryPosition]: data nel futuro ($date) non ammessa per dati storici",
                )
            }

            // Skip silently se nel mese corrente o nel mese precedente: stessa motivazione di
            // ultimaAssistenza — i programmi di questi mesi sono tipicamente gia' in carico,
            // quindi una ghost week creerebbe conflitto o duplicato con dati reali.
            // Anche tipo/duplicati non vengono validati per le entry scartate, cosi' il file
            // puo' essere importato anche se contiene rumore nelle entry recenti.
            if (!YearMonth.from(date).isBefore(cutoffMonth)) {
                return@forEachIndexed
            }

            val partTypeCode = normalizePartTypeCode(item.tipo)
                .takeIf { it.isNotBlank() }
                ?: raise("students[$position].ultimaParte[$entryPosition]: tipo obbligatorio")
            if (partTypeByCode[partTypeCode] == null) {
                raise(
                    "students[$position].ultimaParte[$entryPosition]: tipo parte non definito nel catalogo importato ($partTypeCode)",
                )
            }
            if (!seenPartTypes.add(partTypeCode)) {
                raise("students[$position].ultimaParte[$entryPosition]: tipo duplicato ($partTypeCode)")
            }

            kept += ParsedSeedStudentLastAssignment(
                date = date,
                partTypeCode = partTypeCode,
            )
        }
        kept
    }

    private fun parseSexRule(raw: String): Either<String, SexRule> = either {
        SexRule.entries.firstOrNull { it.name == raw.trim().uppercase() }
            ?: raise("sexRule non valido ($raw), valori ammessi: ${SexRule.entries.joinToString { it.name }}")
    }

    private fun parseSesso(raw: String): Either<String, Sesso> = either {
        when (raw.trim().uppercase()) {
            "M" -> Sesso.M
            "F" -> Sesso.F
            else -> raise("sesso non valido ($raw), valori ammessi: M, F")
        }
    }

    private fun normalizePartTypeCode(raw: String): String = raw.trim().uppercase()

    private fun canLeadForSex(sesso: Sesso, sexRule: SexRule): Boolean = when (sexRule) {
        SexRule.UOMO -> sesso == Sesso.M
        SexRule.STESSO_SESSO -> true
    }

    context(tx: TransactionScope)
    private suspend fun persistHistoricalAssignment(
        personId: ProclamatoreId,
        partType: PartType,
        assignmentDate: LocalDate,
        slot: Int,
    ): Either<DomainError, Unit> = either {
        val weekStartDate = assignmentDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val existingAggregate = weekPlanStore.loadAggregateByDate(weekStartDate)

        if (existingAggregate != null && existingAggregate.weekPlan.programId != null) {
            raise(DomainError.ImportConflittoProgrammaEsistente)
        }

        val newPart = WeeklyPart(
            id = WeeklyPartId(UUID.randomUUID().toString()),
            partType = partType,
            sortOrder = existingAggregate?.weekPlan?.nextSortOrder() ?: 0,
        )
        // R15-005 esteso: le assegnazioni storiche derivate da ultimaParte usano slot=1
        // (conduttore), quelle derivate da ultimaAssistenza usano slot=2 (assistente).
        // La query SQL lastAssistantAssignmentDateForPerson filtra slot>=2 e raccoglie
        // automaticamente le ghost week create con slot=2.
        val assignment = Assignment.of(
            id = AssignmentId(UUID.randomUUID().toString()),
            weeklyPartId = newPart.id,
            personId = personId,
            slot = slot,
        ).fold(
            ifLeft = { raise(it) },
            ifRight = { it },
        )

        val aggregateToSave = if (existingAggregate == null) {
            val weekPlan = WeekPlan.of(
                id = WeekPlanId(UUID.randomUUID().toString()),
                weekStartDate = weekStartDate,
                parts = listOf(newPart),
            ).bind()
            WeekPlanAggregate(
                weekPlan = weekPlan,
                assignments = listOf(assignment),
            )
        } else {
            // Bypass intenzionale di WeekPlanAggregate.addPart: i fragment storici creati
            // ex-novo dall'import non rispondono al gate canBeEditedManually() perche' esistono
            // solo per registrare l'ultima assegnazione di un proclamatore prima dell'introduzione
            // dell'app. La guardia R15-001 (programId != null -> ImportConflittoProgrammaEsistente)
            // garantisce che questo branch non venga mai esercitato su settimane di programmi reali.
            val aggregateWithNewPart = existingAggregate.copy(
                weekPlan = existingAggregate.weekPlan.copy(parts = existingAggregate.weekPlan.parts + newPart),
            )
            aggregateWithNewPart.addAssignment(
                assignment = assignment,
                personSuspended = false,
            ).bind()
        }

        weekPlanStore.saveAggregate(aggregateToSave)
    }
}

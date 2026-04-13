package org.example.project.feature.programs.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionRunner
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.schemas.application.SchemaTemplateStore
import org.example.project.feature.weeklyparts.application.PartTypeStore
import org.example.project.feature.weeklyparts.application.WeekPlanStore
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanAggregate
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeekPlanStatus
import org.example.project.feature.assignments.domain.Assignment
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.weeklyparts.domain.WeeklyPart
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class GeneraSettimaneProgrammaUseCase(
    private val programStore: ProgramStore,
    private val weekPlanStore: WeekPlanStore,
    private val schemaTemplateStore: SchemaTemplateStore,
    private val partTypeStore: PartTypeStore,
    private val transactionRunner: TransactionRunner,
) {
    private data class WeekSpec(
        val weekStartDate: LocalDate,
        val orderedPartTypes: List<Pair<PartType, String?>>,
        val status: WeekPlanStatus,
    )

    /*
     * Restore strategy: durante la (ri)generazione delle settimane di un programma manteniamo
     * le assegnazioni esistenti mappandole sulla nuova struttura di parti tramite una chiave
     * posizionale (partTypeId, occurrenceIndex, slot). In questo modo gli stessi proclamatori
     * restano assegnati alle stesse "slot logiche" anche quando il template del ProgrammaMese
     * cambia: l'occurrenceIndex gestisce il caso di piu' parti dello stesso tipo nella stessa
     * settimana, e lo slot discrimina i ruoli (studente / assistente) all'interno della parte.
     */
    private data class AssignmentRestoreKey(
        val partTypeId: PartTypeId,
        val occurrenceIndex: Int,
        val slot: Int,
    )

    suspend operator fun invoke(
        programId: ProgramMonthId,
        skippedWeeks: Set<LocalDate> = emptySet(),
    ): Either<DomainError, Unit> = either {
        val program = programStore.findById(programId)
            ?: raise(DomainError.NotFound("Programma"))

        val fixedPart = partTypeStore.findFixed()
        val partTypesById = partTypeStore.all().associateBy { partType -> partType.id }

        val weekSpecs = buildList {
            var currentWeek = program.startDate
            while (!currentWeek.isAfter(program.endDate)) {
                val template = schemaTemplateStore.findByWeekStartDate(currentWeek)
                val partTypeIds = template?.partTypeIds ?: listOfNotNull(fixedPart?.id)
                if (partTypeIds.isEmpty()) {
                    raise(DomainError.SettimanaSenzaTemplateENessunaParteFissa(currentWeek))
                }

                val orderedPartTypes = partTypeIds.map { partTypeId ->
                    val partType = partTypesById[partTypeId]
                        ?: raise(DomainError.NotFound("Tipo parte"))
                    partType to partTypeStore.getLatestRevisionId(partTypeId)
                }

                add(
                    WeekSpec(
                        weekStartDate = currentWeek,
                        orderedPartTypes = orderedPartTypes,
                        status = if (currentWeek in skippedWeeks) WeekPlanStatus.SKIPPED else WeekPlanStatus.ACTIVE,
                    ),
                )
                currentWeek = currentWeek.plusWeeks(1)
            }
        }

        val aggregates = weekSpecs.map { spec ->
            val existingAggregate = weekPlanStore.loadAggregateByDate(spec.weekStartDate)
            if (existingAggregate != null) {
                val existingProgramId = existingAggregate.weekPlan.programId
                if (existingProgramId != null && existingProgramId != programId) {
                    raise(
                        DomainError.Validation(
                            "Esiste gia' una settimana collegata a un altro programma per ${spec.weekStartDate}",
                        ),
                    )
                }
            }

            buildAggregateForSpec(
                spec = spec,
                programId = programId,
                existingAggregate = existingAggregate,
            ).bind()
        }

        transactionRunner.runInTransactionEither {
            either {
                weekPlanStore.replaceProgramAggregates(programId, aggregates)
                programStore.updateTemplateAppliedAt(programId, LocalDateTime.now())
            }
        }.bind()
    }

    private fun buildAggregateForSpec(
        spec: WeekSpec,
        programId: ProgramMonthId,
        existingAggregate: WeekPlanAggregate?,
    ): Either<DomainError, WeekPlanAggregate> = either {
        val generatedParts = spec.orderedPartTypes.mapIndexed { index, (partType, revisionId) ->
            WeeklyPart(
                id = WeeklyPartId(UUID.randomUUID().toString()),
                partType = partType,
                partTypeRevisionId = revisionId,
                sortOrder = index,
            )
        }

        val week = WeekPlan.of(
            id = existingAggregate?.weekPlan?.id ?: WeekPlanId(UUID.randomUUID().toString()),
            weekStartDate = spec.weekStartDate,
            parts = generatedParts,
            programId = programId,
            status = spec.status,
        ).bind()

        val restoredAssignments = restoreAssignments(
            existingAggregate = existingAggregate,
            generatedParts = generatedParts,
        ).bind()

        WeekPlanAggregate(
            weekPlan = week,
            assignments = restoredAssignments,
        )
    }

    private fun restoreAssignments(
        existingAggregate: WeekPlanAggregate?,
        generatedParts: List<WeeklyPart>,
    ): Either<DomainError, List<Assignment>> = either {
        if (existingAggregate == null) return@either emptyList()

        val assignmentsByKey = snapshotAssignmentsByRestoreKey(existingAggregate)
        val generatedKeys = generatedParts.associateByRestoreKey()

        buildList {
            generatedKeys.forEach { (key, part) ->
                assignmentsByKey[key].orEmpty().forEach { assignment ->
                    add(
                        Assignment.of(
                            id = AssignmentId(UUID.randomUUID().toString()),
                            weeklyPartId = part.id,
                            personId = assignment.personId,
                            slot = assignment.slot,
                        ).bind(),
                    )
                }
            }
        }
    }

    private fun snapshotAssignmentsByRestoreKey(
        aggregate: WeekPlanAggregate,
    ): Map<AssignmentRestoreKey, List<Assignment>> {
        val partOccurrenceById = aggregate.weekPlan.parts.associateRestoreKeys()
        return aggregate.assignments.groupBy { assignment ->
            val part = partOccurrenceById[assignment.weeklyPartId]
                ?: error("Assignment references unknown part ${assignment.weeklyPartId.value}")
            AssignmentRestoreKey(
                partTypeId = part.first.partType.id,
                occurrenceIndex = part.second,
                slot = assignment.slot,
            )
        }
    }

    private fun List<WeeklyPart>.associateByRestoreKey(): Map<AssignmentRestoreKey, WeeklyPart> {
        val occurrenceByType = mutableMapOf<PartTypeId, Int>()
        return associateBy { part ->
            val occurrence = occurrenceByType.getOrDefault(part.partType.id, 0)
            occurrenceByType[part.partType.id] = occurrence + 1
            AssignmentRestoreKey(
                partTypeId = part.partType.id,
                occurrenceIndex = occurrence,
                slot = 1,
            )
        }.flatMapValuesPerSlot()
    }

    private fun List<WeeklyPart>.associateRestoreKeys(): Map<WeeklyPartId, Pair<WeeklyPart, Int>> {
        val occurrenceByType = mutableMapOf<PartTypeId, Int>()
        return sortedBy { it.sortOrder }.associateBy(
            keySelector = { part -> part.id },
            valueTransform = { part ->
            val occurrence = occurrenceByType.getOrDefault(part.partType.id, 0)
            occurrenceByType[part.partType.id] = occurrence + 1
            part to occurrence
            },
        )
    }

    private fun Map<AssignmentRestoreKey, WeeklyPart>.flatMapValuesPerSlot(): Map<AssignmentRestoreKey, WeeklyPart> =
        entries.flatMap { (baseKey, part) ->
            (1..part.partType.peopleCount).map { slot ->
                baseKey.copy(slot = slot) to part
            }
        }.toMap()
}

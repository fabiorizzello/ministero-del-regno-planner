package org.example.project.feature.programs.application

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionScope
import org.example.project.core.persistence.TransactionRunner
import org.example.project.feature.assignments.domain.Assignment
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.schemas.application.SchemaTemplateStore
import org.example.project.feature.weeklyparts.application.PartTypeStore
import org.example.project.feature.weeklyparts.application.WeekPlanStore
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.WeekPlanAggregate
import org.example.project.feature.weeklyparts.domain.WeekPlanStatus
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class WeekRefreshDetail(
    val weekStartDate: LocalDate,
    val partsAdded: Int,
    val partsRemoved: Int,
    val partsKept: Int,
    val assignmentsPreserved: Int,
    val assignmentsRemoved: Int,
)

data class SchemaRefreshReport(
    val weeksUpdated: Int,
    val assignmentsPreserved: Int,
    val assignmentsRemoved: Int,
    val weekDetails: List<WeekRefreshDetail> = emptyList(),
)

private typealias AssignmentKey = Pair<PartTypeId, Int>

private data class WeekRefreshCandidate(
    val aggregate: WeekPlanAggregate,
    val orderedPartTypes: List<Pair<PartType, String?>>, // ordered by sort
    val assignmentSnapshot: Map<AssignmentKey, List<Assignment>>,
)

class AggiornaProgrammaDaSchemiUseCase(
    private val programStore: ProgramStore,
    private val weekPlanStore: WeekPlanStore,
    private val schemaTemplateStore: SchemaTemplateStore,
    private val partTypeStore: PartTypeStore,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(
        programId: ProgramMonthId,
        referenceDate: LocalDate = LocalDate.now(),
        dryRun: Boolean = false,
    ): Either<DomainError, SchemaRefreshReport> = either {
        val program = programStore.findById(programId)
            ?: raise(DomainError.NotFound("Programma"))

        val partTypesById = partTypeStore.all().associateBy { partType -> partType.id }
        val refreshCandidates = mutableListOf<WeekRefreshCandidate>()

        for (weekAggregate in weekPlanStore.listAggregatesByProgram(programId)) {
            val week = weekAggregate.weekPlan
            if (week.weekStartDate < referenceDate) continue
            if (week.status != WeekPlanStatus.ACTIVE) continue

            val template = schemaTemplateStore.findByWeekStartDate(week.weekStartDate)
                ?: continue
            if (template.partTypeIds.isEmpty()) continue

            val orderedPartTypes = template.partTypeIds.map { partTypeId ->
                val partType = partTypesById[partTypeId]
                    ?: raise(DomainError.NotFound("Tipo parte"))
                partType to partTypeStore.getLatestRevisionId(partTypeId)
            }

            refreshCandidates += WeekRefreshCandidate(
                aggregate = weekAggregate,
                orderedPartTypes = orderedPartTypes,
                assignmentSnapshot = snapshotAssignmentsByKey(weekAggregate),
            )
        }

        var assignmentsPreserved = 0
        var assignmentsRemoved = 0
        val weekDetails = mutableListOf<WeekRefreshDetail>()

        for (candidate in refreshCandidates) {
            val (preserved, removed) = calculateAssignmentDelta(
                assignmentSnapshot = candidate.assignmentSnapshot,
                orderedPartTypes = candidate.orderedPartTypes,
            )
            assignmentsPreserved += preserved
            assignmentsRemoved += removed

            val oldKeys = candidate.aggregate.weekPlan.parts
                .map { it.partType.id to it.sortOrder }.toSet()
            val newKeys = candidate.orderedPartTypes.mapIndexed { index, (partType, _) ->
                partType.id to index
            }.toSet()
            weekDetails += WeekRefreshDetail(
                weekStartDate = candidate.aggregate.weekPlan.weekStartDate,
                partsAdded = (newKeys - oldKeys).size,
                partsRemoved = (oldKeys - newKeys).size,
                partsKept = (oldKeys intersect newKeys).size,
                assignmentsPreserved = preserved,
                assignmentsRemoved = removed,
            )
        }

        if (!dryRun) {
            Either.catch {
                transactionRunner.runInTransaction {
                    for (candidate in refreshCandidates) {
                        applyRefreshCandidate(candidate, referenceDate)
                            .getOrElse { e -> error("Schema refresh failed: $e") }
                    }
                    programStore.updateTemplateAppliedAt(program.id, LocalDateTime.now())
                }
            }.mapLeft { DomainError.Validation(it.message ?: "Errore aggiornamento schema") }.bind()
        }

        SchemaRefreshReport(
            weeksUpdated = refreshCandidates.size,
            assignmentsPreserved = assignmentsPreserved,
            assignmentsRemoved = assignmentsRemoved,
            weekDetails = weekDetails,
        )
    }

    private fun snapshotAssignmentsByKey(aggregate: WeekPlanAggregate): Map<AssignmentKey, List<Assignment>> {
        val partsById = aggregate.weekPlan.parts.associateBy { part -> part.id }
        val assignmentsByKey = mutableMapOf<AssignmentKey, MutableList<Assignment>>()
        aggregate.assignments.forEach { assignment ->
            val part = partsById[assignment.weeklyPartId] ?: return@forEach
            val key = part.partType.id to part.sortOrder
            assignmentsByKey.getOrPut(key) { mutableListOf() }.add(assignment)
        }
        return assignmentsByKey.mapValues { (_, assignments) -> assignments.toList() }
    }

    private fun calculateAssignmentDelta(
        assignmentSnapshot: Map<AssignmentKey, List<Assignment>>,
        orderedPartTypes: List<Pair<PartType, String?>>,
    ): Pair<Int, Int> {
        var preserved = 0
        orderedPartTypes.forEachIndexed { sortOrder, (partType, _) ->
            preserved += assignmentSnapshot[partType.id to sortOrder]?.size ?: 0
        }
        val total = assignmentSnapshot.values.sumOf { assignments -> assignments.size }
        return preserved to (total - preserved)
    }

    context(tx: TransactionScope)
    private suspend fun applyRefreshCandidate(
        candidate: WeekRefreshCandidate,
        referenceDate: LocalDate,
    ): Either<DomainError, Unit> = either {
        val refreshedAggregate = candidate.aggregate.replaceParts(candidate.orderedPartTypes, referenceDate) {
            WeeklyPartId(UUID.randomUUID().toString())
        }.bind()

        val restoredAssignments = buildList {
            refreshedAggregate.weekPlan.parts.forEach { newPart ->
                val key = newPart.partType.id to newPart.sortOrder
                val assignmentsToRestore = candidate.assignmentSnapshot[key].orEmpty()
                assignmentsToRestore.forEach { old ->
                    add(
                        Assignment.of(
                            id = AssignmentId(UUID.randomUUID().toString()),
                            weeklyPartId = newPart.id,
                            personId = old.personId,
                            slot = old.slot,
                        ).bind(),
                    )
                }
            }
        }

        weekPlanStore.saveAggregate(
            refreshedAggregate.copy(assignments = restoredAssignments),
        )
    }
}

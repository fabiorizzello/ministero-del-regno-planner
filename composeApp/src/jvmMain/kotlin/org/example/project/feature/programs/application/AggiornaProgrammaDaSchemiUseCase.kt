package org.example.project.feature.programs.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionRunner
import org.example.project.feature.assignments.application.AssignmentRepository
import org.example.project.feature.assignments.domain.Assignment
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.schemas.application.SchemaTemplateStore
import org.example.project.feature.weeklyparts.application.PartTypeStore
import org.example.project.feature.weeklyparts.application.WeekPlanStore
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeekPlanStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class SchemaRefreshReport(
    val weeksUpdated: Int,
    val assignmentsPreserved: Int,
    val assignmentsRemoved: Int,
)

private typealias AssignmentKey = Pair<PartTypeId, Int>

private data class WeekRefreshCandidate(
    val weekId: WeekPlanId,
    val weekStartDate: LocalDate,
    val partTypeIds: List<PartTypeId>,
    val revisionIds: List<String?>,
    val assignmentSnapshot: Map<AssignmentKey, List<Assignment>>,
)

class AggiornaProgrammaDaSchemiUseCase(
    private val programStore: ProgramStore,
    private val weekPlanStore: WeekPlanStore,
    private val schemaTemplateStore: SchemaTemplateStore,
    private val partTypeStore: PartTypeStore,
    private val assignmentRepository: AssignmentRepository,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(
        programId: ProgramMonthId,
        referenceDate: LocalDate = LocalDate.now(),
        dryRun: Boolean = false,
    ): Either<DomainError, SchemaRefreshReport> = either {
        val program = programStore.findById(programId)
            ?: raise(DomainError.Validation("Programma non trovato"))

        val refreshCandidates = mutableListOf<WeekRefreshCandidate>()
        for (week in weekPlanStore.listByProgram(programId)) {
            if (week.weekStartDate < referenceDate) continue
            if (week.status != WeekPlanStatus.ACTIVE) continue
            val candidate = buildRefreshCandidate(week) ?: continue
            refreshCandidates += candidate
        }

        var assignmentsPreserved = 0
        var assignmentsRemoved = 0

        for (candidate in refreshCandidates) {
            val (preserved, removed) = calculateAssignmentDelta(
                assignmentSnapshot = candidate.assignmentSnapshot,
                partTypeIds = candidate.partTypeIds,
            )
            assignmentsPreserved += preserved
            assignmentsRemoved += removed
        }

        if (!dryRun) {
            transactionRunner.runInTransaction {
                for (candidate in refreshCandidates) {
                    applyRefreshCandidate(programId, candidate)
                }

                programStore.updateTemplateAppliedAt(program.id, LocalDateTime.now())
            }
        }

        SchemaRefreshReport(
            weeksUpdated = refreshCandidates.size,
            assignmentsPreserved = assignmentsPreserved,
            assignmentsRemoved = assignmentsRemoved,
        )
    }

    private suspend fun buildRefreshCandidate(week: WeekPlan): WeekRefreshCandidate? {
        val template = schemaTemplateStore.findByWeekStartDate(week.weekStartDate)
            ?: return null
        if (template.partTypeIds.isEmpty()) return null

        val revisionIds = template.partTypeIds.map { partTypeStore.getLatestRevisionId(it) }
        return WeekRefreshCandidate(
            weekId = week.id,
            weekStartDate = week.weekStartDate,
            partTypeIds = template.partTypeIds,
            revisionIds = revisionIds,
            assignmentSnapshot = snapshotAssignmentsByKey(week),
        )
    }

    private suspend fun snapshotAssignmentsByKey(week: WeekPlan): Map<AssignmentKey, List<Assignment>> {
        val partsById = week.parts.associateBy { part -> part.id }
        val assignmentsByKey = mutableMapOf<AssignmentKey, MutableList<Assignment>>()
        assignmentRepository.listByWeek(week.id).forEach { awp ->
            val part = partsById[awp.weeklyPartId] ?: return@forEach
            val key = part.partType.id to part.sortOrder
            assignmentsByKey.getOrPut(key) { mutableListOf() }.add(
                Assignment(
                    id = awp.id,
                    weeklyPartId = awp.weeklyPartId,
                    personId = awp.personId,
                    slot = awp.slot,
                )
            )
        }
        return assignmentsByKey.mapValues { (_, assignments) -> assignments.toList() }
    }

    private fun calculateAssignmentDelta(
        assignmentSnapshot: Map<AssignmentKey, List<Assignment>>,
        partTypeIds: List<PartTypeId>,
    ): Pair<Int, Int> {
        var preserved = 0
        partTypeIds.forEachIndexed { sortOrder, partTypeId ->
            preserved += assignmentSnapshot[partTypeId to sortOrder]?.size ?: 0
        }
        val total = assignmentSnapshot.values.sumOf { assignments -> assignments.size }
        return preserved to (total - preserved)
    }

    private suspend fun applyRefreshCandidate(
        programId: ProgramMonthId,
        candidate: WeekRefreshCandidate,
    ) {
        weekPlanStore.replaceAllParts(candidate.weekId, candidate.partTypeIds, candidate.revisionIds)
        val refreshedWeek = checkNotNull(weekPlanStore.findByDateAndProgram(candidate.weekStartDate, programId)) {
            "Settimana non trovata dopo aggiornamento parti: ${candidate.weekStartDate}"
        }

        refreshedWeek.parts.forEach { newPart ->
            val key = newPart.partType.id to newPart.sortOrder
            val assignmentsToRestore = candidate.assignmentSnapshot[key].orEmpty()
            assignmentsToRestore.forEach { old ->
                assignmentRepository.save(
                    Assignment(
                        id = AssignmentId(UUID.randomUUID().toString()),
                        weeklyPartId = newPart.id,
                        personId = old.personId,
                        slot = old.slot,
                    )
                )
            }
        }
    }
}

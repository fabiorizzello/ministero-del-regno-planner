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
import org.example.project.feature.weeklyparts.application.WeekPlanStore
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.WeekPlan
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class SchemaRefreshReport(
    val weeksUpdated: Int,
    val assignmentsPreserved: Int,
    val assignmentsRemoved: Int,
)

class AggiornaProgrammaDaSchemiUseCase(
    private val programStore: ProgramStore,
    private val weekPlanStore: WeekPlanStore,
    private val schemaTemplateStore: SchemaTemplateStore,
    private val assignmentRepository: AssignmentRepository,
    private val transactionRunner: TransactionRunner,
) {
    private data class WeekSnapshot(
        val week: WeekPlan,
        val partTypeIds: List<PartTypeId>,
        val assignmentsByKey: Map<Pair<PartTypeId, Int>, List<Assignment>>,
    )

    suspend operator fun invoke(
        programId: String,
        referenceDate: LocalDate = LocalDate.now(),
        dryRun: Boolean = false,
    ): Either<DomainError, SchemaRefreshReport> = either {
        val program = programStore.findById(ProgramMonthId(programId))
            ?: raise(DomainError.Validation("Programma non trovato"))

        val weeks = weekPlanStore.listByProgram(programId)
            .filter { it.weekStartDate >= referenceDate }

        val snapshots = buildWeekSnapshots(weeks)
        val report = analyzeSnapshots(snapshots)

        if (!dryRun) {
            transactionRunner.runInTransaction {
                for (snapshot in snapshots) {
                    // 1. Replace all parts from template
                    weekPlanStore.replaceAllParts(snapshot.week.id, snapshot.partTypeIds)

                    // 2. Re-load week to get new part IDs
                    val refreshedWeek = weekPlanStore.listByProgram(programId)
                        .find { it.id == snapshot.week.id }
                        ?: continue

                    // 3. Re-associate assignments that still match by (partTypeId, sortOrder)
                    val remainingByKey = snapshot.assignmentsByKey.toMutableMap()
                    for (newPart in refreshedWeek.parts) {
                        val key = newPart.partType.id to newPart.sortOrder
                        val savedAssignments = remainingByKey.remove(key) ?: continue

                        for (old in savedAssignments) {
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

                // Update template_applied_at timestamp
                programStore.updateTemplateAppliedAt(program.id, LocalDateTime.now())
            }
        }

        report
    }

    private suspend fun buildWeekSnapshots(weeks: List<WeekPlan>): List<WeekSnapshot> {
        val snapshots = mutableListOf<WeekSnapshot>()

        for (week in weeks) {
            val template = schemaTemplateStore.findByWeekStartDate(week.weekStartDate)
                ?: continue
            val newPartTypeIds = template.partTypeIds
            if (newPartTypeIds.isEmpty()) continue

            val currentAssignments = assignmentRepository.listByWeek(week.id)
            val assignmentsByKey = mutableMapOf<Pair<PartTypeId, Int>, MutableList<Assignment>>()
            for (awp in currentAssignments) {
                val part = week.parts.find { it.id == awp.weeklyPartId } ?: continue
                val key = part.partType.id to part.sortOrder
                assignmentsByKey.getOrPut(key) { mutableListOf() }.add(
                    Assignment(id = awp.id, weeklyPartId = awp.weeklyPartId, personId = awp.personId, slot = awp.slot)
                )
            }

            snapshots.add(WeekSnapshot(week = week, partTypeIds = newPartTypeIds, assignmentsByKey = assignmentsByKey))
        }

        return snapshots
    }

    private fun analyzeSnapshots(snapshots: List<WeekSnapshot>): SchemaRefreshReport {
        var weeksUpdated = 0
        var assignmentsPreserved = 0
        var assignmentsRemoved = 0

        for (snapshot in snapshots) {
            val remainingByKey = snapshot.assignmentsByKey.toMutableMap()

            snapshot.partTypeIds.forEachIndexed { index, ptId ->
                val key = ptId to index
                val matched = remainingByKey.remove(key)
                if (matched != null) assignmentsPreserved += matched.size
            }
            assignmentsRemoved += remainingByKey.values.sumOf { it.size }
            weeksUpdated++
        }

        return SchemaRefreshReport(
            weeksUpdated = weeksUpdated,
            assignmentsPreserved = assignmentsPreserved,
            assignmentsRemoved = assignmentsRemoved,
        )
    }
}

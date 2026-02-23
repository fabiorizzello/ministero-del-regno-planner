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
import org.example.project.feature.weeklyparts.domain.WeekPlanStatus
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
    suspend operator fun invoke(
        programId: String,
        referenceDate: LocalDate = LocalDate.now(),
    ): Either<DomainError, SchemaRefreshReport> = either {
        val program = programStore.findById(ProgramMonthId(programId))
            ?: raise(DomainError.Validation("Programma non trovato"))

        val weeks = weekPlanStore.listByProgram(programId)
            .filter { it.weekStartDate >= referenceDate }

        var weeksUpdated = 0
        var assignmentsPreserved = 0
        var assignmentsRemoved = 0

        transactionRunner.runInTransaction {
            for (week in weeks) {
                val template = schemaTemplateStore.findByWeekStartDate(week.weekStartDate)
                    ?: continue // no template for this week, skip

                val newPartTypeIds = template.partTypeIds
                if (newPartTypeIds.isEmpty()) continue

                // 1. Snapshot current assignments keyed by (partTypeId, sortOrder)
                val currentAssignments = assignmentRepository.listByWeek(week.id)
                val assignmentsByKey = mutableMapOf<Pair<PartTypeId, Int>, MutableList<Assignment>>()
                for (awp in currentAssignments) {
                    val part = week.parts.find { it.id == awp.weeklyPartId } ?: continue
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

                // 2. Replace all parts from template
                weekPlanStore.replaceAllParts(week.id, newPartTypeIds)

                // 3. Re-load week to get new part IDs
                val refreshedWeek = weekPlanStore.listByProgram(programId)
                    .find { it.id == week.id }
                    ?: continue

                // 4. Re-associate assignments that still match by (partTypeId, sortOrder)
                for (newPart in refreshedWeek.parts) {
                    val key = newPart.partType.id to newPart.sortOrder
                    val savedAssignments = assignmentsByKey.remove(key) ?: continue

                    for (old in savedAssignments) {
                        assignmentRepository.save(
                            Assignment(
                                id = AssignmentId(UUID.randomUUID().toString()),
                                weeklyPartId = newPart.id,
                                personId = old.personId,
                                slot = old.slot,
                            )
                        )
                        assignmentsPreserved++
                    }
                }

                // 5. Count removed assignments (unmatched keys)
                assignmentsRemoved += assignmentsByKey.values.sumOf { it.size }
                weeksUpdated++
            }

            // Update template_applied_at timestamp
            programStore.updateTemplateAppliedAt(program.id, LocalDateTime.now())
        }

        SchemaRefreshReport(
            weeksUpdated = weeksUpdated,
            assignmentsPreserved = assignmentsPreserved,
            assignmentsRemoved = assignmentsRemoved,
        )
    }
}

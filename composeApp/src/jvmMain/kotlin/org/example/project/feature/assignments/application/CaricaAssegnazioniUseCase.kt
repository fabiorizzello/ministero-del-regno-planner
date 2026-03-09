package org.example.project.feature.assignments.application

import org.example.project.feature.assignments.domain.AssignmentWithPerson
import org.example.project.feature.weeklyparts.application.WeekPlanQueries
import java.time.LocalDate

class CaricaAssegnazioniUseCase(
    private val weekPlanStore: WeekPlanQueries,
    private val assignmentStore: AssignmentRepository,
) {
    suspend operator fun invoke(weekStartDate: LocalDate): List<AssignmentWithPerson> {
        val plan = weekPlanStore.findByDate(weekStartDate) ?: return emptyList()
        return assignmentStore.listByWeek(plan.id)
    }
}

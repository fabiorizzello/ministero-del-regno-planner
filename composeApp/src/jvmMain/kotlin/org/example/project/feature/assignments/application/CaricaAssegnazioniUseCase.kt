package org.example.project.feature.assignments.application

import org.example.project.feature.assignments.domain.AssignmentWithPerson
import org.example.project.feature.weeklyparts.application.WeekPlanStore
import java.time.LocalDate

class CaricaAssegnazioniUseCase(
    private val weekPlanStore: WeekPlanStore,
    private val assignmentStore: AssignmentStore,
) {
    suspend operator fun invoke(weekStartDate: LocalDate): List<AssignmentWithPerson> {
        val plan = weekPlanStore.findByDate(weekStartDate) ?: return emptyList()
        return assignmentStore.listByWeek(plan.id)
    }
}

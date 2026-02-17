package org.example.project.feature.weeklyparts.application

import org.example.project.feature.weeklyparts.domain.WeekPlan
import java.time.LocalDate

class CaricaSettimanaUseCase(
    private val weekPlanStore: WeekPlanStore,
) {
    suspend operator fun invoke(weekStartDate: LocalDate): WeekPlan? {
        return weekPlanStore.findByDate(weekStartDate)
    }
}

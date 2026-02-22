package org.example.project.feature.weeklyparts.domain

import java.time.DayOfWeek
import java.time.LocalDate

@JvmInline
value class WeekPlanId(val value: String)

enum class WeekPlanStatus {
    ACTIVE,
    SKIPPED,
}

data class WeekPlan(
    val id: WeekPlanId,
    val weekStartDate: LocalDate,
    val parts: List<WeeklyPart>,
    val programId: String? = null,
    val status: WeekPlanStatus = WeekPlanStatus.ACTIVE,
) {
    init {
        require(weekStartDate.dayOfWeek == DayOfWeek.MONDAY) {
            "weekStartDate deve essere un lunedì, ricevuto: $weekStartDate (${weekStartDate.dayOfWeek})"
        }
    }
}

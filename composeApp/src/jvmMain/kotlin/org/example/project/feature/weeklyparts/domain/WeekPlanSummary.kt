package org.example.project.feature.weeklyparts.domain

import java.time.LocalDate

/** Lightweight week plan reference for range queries. */
data class WeekPlanSummary(
    val id: WeekPlanId,
    val weekStartDate: LocalDate,
)

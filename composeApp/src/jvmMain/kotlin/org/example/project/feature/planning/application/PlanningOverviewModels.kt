package org.example.project.feature.planning.application

import org.example.project.feature.planning.domain.PlanningAlert
import org.example.project.feature.planning.domain.PlanningProgress
import org.example.project.feature.planning.domain.WeekPlanningStatus
import java.time.LocalDate

data class PlanningWeekStatus(
    val weekStartDate: LocalDate,
    val status: WeekPlanningStatus,
    val totalSlots: Int,
    val assignedSlots: Int,
)

data class PlanningOverview(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val weeks: List<PlanningWeekStatus>,
    val progress: PlanningProgress,
    val alerts: List<PlanningAlert>,
)

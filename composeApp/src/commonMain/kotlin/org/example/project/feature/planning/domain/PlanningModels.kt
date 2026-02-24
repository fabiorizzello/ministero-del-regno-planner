package org.example.project.feature.planning.domain

enum class WeekPlanningStatus { DA_ORGANIZZARE, PARZIALE, PIANIFICATA }

enum class AlertType {
    COVERAGE,
    COOLDOWN_VIOLATION,
    DUPLICATE_ASSIGNMENT,
    INELIGIBLE_ASSIGNMENT
}

data class WeekCoverageSnapshot(
    val weekKey: String,
    val hasWeekPlan: Boolean,
    val totalSlots: Int,
    val assignedSlots: Int,
)

data class WeekCoverageStatus(
    val weekKey: String,
    val status: WeekPlanningStatus,
    val totalSlots: Int,
    val assignedSlots: Int,
)

data class PlanningProgress(
    val plannedThroughWeekKey: String?,
    val plannedWeeks: Int,
    val totalWeeks: Int,
)

data class PlanningAlert(
    val type: AlertType,
    val weekKeys: List<String>,
    val personName: String? = null,
    val partTypeName: String? = null,
)

object PlanningCalculations {
    fun computeWeekStatus(snapshot: WeekCoverageSnapshot): WeekCoverageStatus {
        val status = when {
            !snapshot.hasWeekPlan || snapshot.totalSlots <= 0 -> WeekPlanningStatus.DA_ORGANIZZARE
            snapshot.assignedSlots >= snapshot.totalSlots -> WeekPlanningStatus.PIANIFICATA
            snapshot.assignedSlots > 0 -> WeekPlanningStatus.PARZIALE
            else -> WeekPlanningStatus.DA_ORGANIZZARE
        }
        return WeekCoverageStatus(
            weekKey = snapshot.weekKey,
            status = status,
            totalSlots = snapshot.totalSlots,
            assignedSlots = snapshot.assignedSlots,
        )
    }

    fun computeProgress(weeks: List<WeekCoverageStatus>): PlanningProgress {
        if (weeks.isEmpty()) {
            return PlanningProgress(null, 0, 0)
        }
        var plannedThroughKey: String? = null
        var consecutivePlanned = 0
        for (week in weeks) {
            if (week.status == WeekPlanningStatus.PIANIFICATA) {
                consecutivePlanned += 1
                plannedThroughKey = week.weekKey
            } else {
                break
            }
        }
        val plannedWeeks = weeks.count { it.status == WeekPlanningStatus.PIANIFICATA }
        return PlanningProgress(plannedThroughKey, plannedWeeks, weeks.size)
    }

    fun computeCoverageAlerts(weeks: List<WeekCoverageStatus>, weeksToCheck: Int): List<PlanningAlert> {
        if (weeksToCheck <= 0) return emptyList()
        val window = weeks.take(weeksToCheck)
        val missing = window.filter { it.status != WeekPlanningStatus.PIANIFICATA }
        if (missing.isEmpty()) return emptyList()
        return listOf(PlanningAlert(AlertType.COVERAGE, missing.map { it.weekKey }))
    }
}

package org.example.project.feature.planning.application

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.project.feature.assignments.application.AssignmentRepository
import org.example.project.feature.planning.domain.PlanningCalculations
import org.example.project.feature.planning.domain.WeekCoverageSnapshot
import org.example.project.feature.planning.domain.WeekCoverageStatus
import org.example.project.feature.weeklyparts.application.WeekPlanStore
import org.example.project.feature.weeklyparts.domain.WeekPlanSummary
import java.time.LocalDate

class CaricaPanoramicaPianificazioneFutura(
    private val weekPlanStore: WeekPlanStore,
    private val assignmentRepository: AssignmentRepository,
    private val calcolaProgressoPianificazione: CalcolaProgressoPianificazione,
    private val generaAlertCoperturaSettimane: GeneraAlertCoperturaSettimane,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend operator fun invoke(
        startDate: LocalDate,
        weeks: Int,
        alertWeeks: Int = 4,
    ): PlanningOverview = withContext(dispatcher) {
        val normalizedWeeks = weeks.coerceAtLeast(0)
        val endDate = if (normalizedWeeks == 0) startDate else startDate.plusWeeks((normalizedWeeks - 1).toLong())

        val weekPlans = if (normalizedWeeks == 0) emptyList() else weekPlanStore.listInRange(startDate, endDate)
        val totalSlotsMap = if (normalizedWeeks == 0) emptyMap() else weekPlanStore.totalSlotsByWeekInRange(startDate, endDate)
        val assignmentCountsMap = if (normalizedWeeks == 0) emptyMap() else assignmentRepository.countAssignmentsByWeekInRange(startDate, endDate)

        val weekPlansByDate = weekPlans.associateBy { it.weekStartDate }

        val snapshots = buildList {
            if (normalizedWeeks > 0) {
                var current = startDate
                repeat(normalizedWeeks) {
                    val plan: WeekPlanSummary? = weekPlansByDate[current]
                    val planId = plan?.id
                    val totalSlots = planId?.let { totalSlotsMap[it] } ?: 0
                    val assignedSlots = planId?.let { assignmentCountsMap[it] } ?: 0
                    add(
                        WeekCoverageSnapshot(
                            weekKey = current.toString(),
                            hasWeekPlan = plan != null,
                            totalSlots = totalSlots,
                            assignedSlots = assignedSlots,
                        ),
                    )
                    current = current.plusWeeks(1)
                }
            }
        }

        val weekStatuses = snapshots.map { PlanningCalculations.computeWeekStatus(it) }
        val progress = calcolaProgressoPianificazione(weekStatuses)
        val alerts = generaAlertCoperturaSettimane(weekStatuses, alertWeeks)

        PlanningOverview(
            startDate = startDate,
            endDate = endDate,
            weeks = weekStatuses.map {
                PlanningWeekStatus(
                    weekStartDate = LocalDate.parse(it.weekKey),
                    status = it.status,
                    totalSlots = it.totalSlots,
                    assignedSlots = it.assignedSlots,
                )
            },
            progress = progress,
            alerts = alerts,
        )
    }
}

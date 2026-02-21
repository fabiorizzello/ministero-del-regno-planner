package org.example.project.feature.planning.application

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.project.feature.planning.domain.PlanningAlert
import org.example.project.feature.planning.domain.PlanningCalculations
import org.example.project.feature.planning.domain.WeekCoverageStatus

class GeneraAlertCoperturaSettimane(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    suspend operator fun invoke(
        weeks: List<WeekCoverageStatus>,
        weeksToCheck: Int,
    ): List<PlanningAlert> = withContext(dispatcher) {
        PlanningCalculations.computeCoverageAlerts(weeks, weeksToCheck)
    }
}

package org.example.project.feature.planning.application

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.project.feature.planning.domain.PlanningCalculations
import org.example.project.feature.planning.domain.PlanningProgress
import org.example.project.feature.planning.domain.WeekCoverageStatus

class CalcolaProgressoPianificazione(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    suspend operator fun invoke(weeks: List<WeekCoverageStatus>): PlanningProgress = withContext(dispatcher) {
        PlanningCalculations.computeProgress(weeks)
    }
}

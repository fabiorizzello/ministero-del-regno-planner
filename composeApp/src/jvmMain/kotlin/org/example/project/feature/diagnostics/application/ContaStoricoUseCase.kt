package org.example.project.feature.diagnostics.application

import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class StoricoPreview(
    val weekPlans: Int = 0,
    val weeklyParts: Int = 0,
    val assignments: Int = 0,
) {
    val hasData: Boolean get() = weekPlans > 0 || weeklyParts > 0 || assignments > 0
}

class ContaStoricoUseCase(
    private val store: DiagnosticsStore,
) {
    suspend operator fun invoke(cutoffDate: LocalDate): StoricoPreview = withContext(Dispatchers.IO) {
        val weekPlans = store.countWeekPlansBeforeDate(cutoffDate).toInt()
        val weeklyParts = store.countWeeklyPartsBeforeDate(cutoffDate).toInt()
        val assignments = store.countAssignmentsBeforeDate(cutoffDate).toInt()
        StoricoPreview(
            weekPlans = weekPlans,
            weeklyParts = weeklyParts,
            assignments = assignments,
        )
    }
}

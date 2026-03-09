package org.example.project.feature.diagnostics.application

import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.project.db.MinisteroDatabase

data class StoricoPreview(
    val weekPlans: Int = 0,
    val weeklyParts: Int = 0,
    val assignments: Int = 0,
) {
    val hasData: Boolean get() = weekPlans > 0 || weeklyParts > 0 || assignments > 0
}

class ContaStoricoUseCase(
    private val database: MinisteroDatabase,
) {
    suspend operator fun invoke(cutoffDate: LocalDate): StoricoPreview = withContext(Dispatchers.IO) {
        val cutoff = cutoffDate.toString()
        val weekPlans = database.ministeroDatabaseQueries.countWeekPlansBeforeDate(cutoff).executeAsOne().toInt()
        val weeklyParts = database.ministeroDatabaseQueries.countWeeklyPartsBeforeDate(cutoff).executeAsOne().toInt()
        val assignments = database.ministeroDatabaseQueries.countAssignmentsBeforeDate(cutoff).executeAsOne().toInt()
        StoricoPreview(
            weekPlans = weekPlans,
            weeklyParts = weeklyParts,
            assignments = assignments,
        )
    }
}

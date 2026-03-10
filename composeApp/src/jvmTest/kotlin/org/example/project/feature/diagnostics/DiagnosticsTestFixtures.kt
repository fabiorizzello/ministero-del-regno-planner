package org.example.project.feature.diagnostics

import java.time.LocalDate
import org.example.project.core.persistence.TransactionScope
import org.example.project.feature.diagnostics.application.DiagnosticsStore

internal open class FakeDiagnosticsStore(
    var weekPlansCount: Long = 0L,
    var weeklyPartsCount: Long = 0L,
    var assignmentsCount: Long = 0L,
) : DiagnosticsStore {

    val countCalls = mutableListOf<LocalDate>()
    val deleteCalls = mutableListOf<LocalDate>()

    override suspend fun countWeekPlansBeforeDate(cutoffDate: LocalDate): Long {
        countCalls += cutoffDate
        return weekPlansCount
    }

    override suspend fun countWeeklyPartsBeforeDate(cutoffDate: LocalDate): Long =
        weeklyPartsCount

    override suspend fun countAssignmentsBeforeDate(cutoffDate: LocalDate): Long =
        assignmentsCount

    context(tx: TransactionScope)
    override suspend fun deleteWeekPlansBeforeDate(cutoffDate: LocalDate) {
        deleteCalls += cutoffDate
    }
}


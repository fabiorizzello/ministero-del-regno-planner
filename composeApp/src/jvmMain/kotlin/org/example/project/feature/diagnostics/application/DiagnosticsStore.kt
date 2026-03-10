package org.example.project.feature.diagnostics.application

import java.time.LocalDate
import org.example.project.core.persistence.TransactionScope

interface DiagnosticsStore {
    suspend fun countWeekPlansBeforeDate(cutoffDate: LocalDate): Long
    suspend fun countWeeklyPartsBeforeDate(cutoffDate: LocalDate): Long
    suspend fun countAssignmentsBeforeDate(cutoffDate: LocalDate): Long
    context(tx: TransactionScope) suspend fun deleteWeekPlansBeforeDate(cutoffDate: LocalDate)
}

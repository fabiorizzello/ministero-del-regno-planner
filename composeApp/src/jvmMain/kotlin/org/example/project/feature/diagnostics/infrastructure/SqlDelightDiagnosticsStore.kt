package org.example.project.feature.diagnostics.infrastructure

import java.time.LocalDate
import org.example.project.core.persistence.TransactionScope
import org.example.project.db.MinisteroDatabase
import org.example.project.feature.diagnostics.application.DiagnosticsStore

class SqlDelightDiagnosticsStore(
    private val database: MinisteroDatabase,
) : DiagnosticsStore {

    override suspend fun countWeekPlansBeforeDate(cutoffDate: LocalDate): Long =
        database.ministeroDatabaseQueries.countWeekPlansBeforeDate(cutoffDate.toString()).executeAsOne()

    override suspend fun countWeeklyPartsBeforeDate(cutoffDate: LocalDate): Long =
        database.ministeroDatabaseQueries.countWeeklyPartsBeforeDate(cutoffDate.toString()).executeAsOne()

    override suspend fun countAssignmentsBeforeDate(cutoffDate: LocalDate): Long =
        database.ministeroDatabaseQueries.countAssignmentsBeforeDate(cutoffDate.toString()).executeAsOne()

    context(tx: TransactionScope)
    override suspend fun deleteWeekPlansBeforeDate(cutoffDate: LocalDate) {
        database.ministeroDatabaseQueries.deleteWeekPlansBeforeDate(cutoffDate.toString())
    }
}

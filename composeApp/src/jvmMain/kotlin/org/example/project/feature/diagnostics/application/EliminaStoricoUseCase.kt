package org.example.project.feature.diagnostics.application

import arrow.core.Either
import arrow.core.raise.either
import java.sql.DriverManager
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.project.core.config.AppRuntime
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionRunner
import org.example.project.db.MinisteroDatabase

data class EliminazioneStoricoResult(
    val vacuumExecuted: Boolean,
)

class EliminaStoricoUseCase(
    private val database: MinisteroDatabase,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(cutoffDate: LocalDate): Either<DomainError, EliminazioneStoricoResult> = either {
        val cutoff = cutoffDate.toString()

        transactionRunner.runInTransaction {
            database.ministeroDatabaseQueries.deleteWeekPlansBeforeDate(cutoff)
        }

        val vacuumOk = runVacuum()
        EliminazioneStoricoResult(vacuumExecuted = vacuumOk)
    }

    private suspend fun runVacuum(): Boolean = withContext(Dispatchers.IO) {
        val dbPath = AppRuntime.paths().dbFile.toAbsolutePath().toString()
        runCatching {
            DriverManager.getConnection("jdbc:sqlite:$dbPath").use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("VACUUM;")
                }
            }
            true
        }.getOrDefault(false)
    }
}

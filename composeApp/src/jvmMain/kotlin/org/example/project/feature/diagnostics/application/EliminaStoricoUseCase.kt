package org.example.project.feature.diagnostics.application

import arrow.core.Either
import arrow.core.raise.either
import java.time.LocalDate
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionRunner

data class EliminazioneStoricoResult(
    val vacuumExecuted: Boolean,
)

class EliminaStoricoUseCase(
    private val store: DiagnosticsStore,
    private val transactionRunner: TransactionRunner,
    private val vacuumDatabase: suspend () -> Boolean = { false },
) {
    suspend operator fun invoke(cutoffDate: LocalDate): Either<DomainError, EliminazioneStoricoResult> = either {
        Either.catch {
            transactionRunner.runInTransaction {
                store.deleteWeekPlansBeforeDate(cutoffDate)
            }
        }.mapLeft { DomainError.Validation(it.message ?: "Errore eliminazione storico") }.bind()

        val vacuumOk = vacuumDatabase()
        EliminazioneStoricoResult(vacuumExecuted = vacuumOk)
    }
}

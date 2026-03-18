package org.example.project.feature.assignments.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionRunner

class SalvaImpostazioniAssegnatoreUseCase(
    private val store: AssignmentSettingsStore,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(settings: AssignmentSettings): Either<DomainError, Unit> =
        transactionRunner.runInTransactionEither {
            either {
                store.save(settings.normalized())
            }
        }
}

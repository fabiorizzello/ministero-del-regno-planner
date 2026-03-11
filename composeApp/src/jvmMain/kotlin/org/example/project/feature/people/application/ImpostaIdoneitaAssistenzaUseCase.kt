package org.example.project.feature.people.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionRunner
import org.example.project.feature.people.domain.ProclamatoreId

class ImpostaIdoneitaAssistenzaUseCase(
    private val eligibilityStore: EligibilityStore,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(
        personId: ProclamatoreId,
        canAssist: Boolean,
    ): Either<DomainError, Unit> = either {
        Either.catch {
            transactionRunner.runInTransaction { eligibilityStore.setCanAssist(personId, canAssist) }
        }.mapLeft { DomainError.Validation(it.message ?: "Errore salvataggio idoneità") }.bind()
    }
}

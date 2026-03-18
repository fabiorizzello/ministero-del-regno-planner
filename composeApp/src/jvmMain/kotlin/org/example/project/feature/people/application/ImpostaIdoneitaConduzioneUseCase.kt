package org.example.project.feature.people.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionRunner
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.weeklyparts.domain.PartTypeId

class ImpostaIdoneitaConduzioneUseCase(
    private val eligibilityStore: EligibilityStore,
    private val transactionRunner: TransactionRunner,
) {
    data class EligibilityChange(
        val partTypeId: PartTypeId,
        val canLead: Boolean,
    )

    suspend operator fun invoke(
        personId: ProclamatoreId,
        partTypeId: PartTypeId,
        canLead: Boolean,
    ): Either<DomainError, Unit> = either {
        Either.catch {
            transactionRunner.runInTransaction { eligibilityStore.setCanLead(personId, partTypeId, canLead) }
        }.mapLeft { DomainError.Validation(it.message ?: "Errore salvataggio idoneità") }.bind()
    }

    /**
     * Persists all [changes] for [personId] atomically in a single transaction.
     * If any write fails, no changes are committed.
     */
    suspend fun batch(
        personId: ProclamatoreId,
        changes: List<EligibilityChange>,
    ): Either<DomainError, Unit> = Either.catch {
        transactionRunner.runInTransaction {
            changes.forEach { change ->
                eligibilityStore.setCanLead(personId, change.partTypeId, change.canLead)
            }
        }
    }.mapLeft { DomainError.Validation(it.message ?: "Errore salvataggio idoneità") }
}

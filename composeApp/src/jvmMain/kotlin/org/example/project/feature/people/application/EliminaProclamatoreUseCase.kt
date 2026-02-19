package org.example.project.feature.people.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionRunner
import org.example.project.feature.assignments.application.AssignmentStore
import org.example.project.feature.people.domain.ProclamatoreId

class EliminaProclamatoreUseCase(
    private val store: ProclamatoriAggregateStore,
    private val assignmentStore: AssignmentStore,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(id: ProclamatoreId): Either<DomainError, Unit> = either {
        store.load(id) ?: raise(DomainError.Validation("Proclamatore non trovato"))
        try {
            transactionRunner.runInTransaction {
                assignmentStore.removeAllForPerson(id)
                store.remove(id)
            }
        } catch (e: Exception) {
            raise(DomainError.Validation("Errore nell'eliminazione: ${e.message}"))
        }
    }
}

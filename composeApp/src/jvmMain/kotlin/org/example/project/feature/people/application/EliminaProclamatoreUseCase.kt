package org.example.project.feature.people.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.feature.people.domain.ProclamatoreId

class EliminaProclamatoreUseCase(
    private val store: ProclamatoriAggregateStore,
) {
    suspend operator fun invoke(id: ProclamatoreId): Either<DomainError, Unit> = either {
        store.load(id) ?: raise(DomainError.Validation("Proclamatore non trovato"))
        store.remove(id)
    }
}

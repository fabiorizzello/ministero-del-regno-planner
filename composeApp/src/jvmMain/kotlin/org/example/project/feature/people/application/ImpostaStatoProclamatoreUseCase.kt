package org.example.project.feature.people.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.feature.people.domain.ProclamatoreId

class ImpostaStatoProclamatoreUseCase(
    private val store: ProclamatoriAggregateStore,
) {
    suspend operator fun invoke(proclamatoreId: ProclamatoreId, attivo: Boolean): Either<DomainError, Unit> = either {
        val esistente = store.load(proclamatoreId)
            ?: raise(DomainError.Validation("Proclamatore non trovato"))

        if (esistente.attivo != attivo) {
            store.persist(esistente.copy(attivo = attivo))
        }
    }
}

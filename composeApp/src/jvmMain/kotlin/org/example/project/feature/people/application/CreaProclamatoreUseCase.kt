package org.example.project.feature.people.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreAggregate
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.domain.Sesso
import java.util.UUID

class CreaProclamatoreUseCase(
    private val query: ProclamatoriQuery,
    private val store: ProclamatoriAggregateStore,
) {
    data class Command(
        val nome: String,
        val cognome: String,
        val sesso: Sesso,
        val sospeso: Boolean = false,
        val puoAssistere: Boolean = false,
    )

    suspend operator fun invoke(command: Command): Either<DomainError, Proclamatore> = either {
        val nome = command.nome.trim()
        val cognome = command.cognome.trim()

        val duplicato = query.esisteConNomeCognome(nome, cognome)
        if (duplicato) raise(DomainError.ProclamatoreDuplicato)

        val nuovo = ProclamatoreAggregate.create(
            id = ProclamatoreId(UUID.randomUUID().toString()),
            nome = nome,
            cognome = cognome,
            sesso = command.sesso,
            sospeso = command.sospeso,
            puoAssistere = command.puoAssistere,
        ).bind().person
        store.persist(nuovo)
        nuovo
    }
}

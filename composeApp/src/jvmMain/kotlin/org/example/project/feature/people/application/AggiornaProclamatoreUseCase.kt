package org.example.project.feature.people.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.domain.Sesso

class AggiornaProclamatoreUseCase(
    private val query: ProclamatoriQuery,
    private val store: ProclamatoriAggregateStore,
) {
    data class Command(
        val id: ProclamatoreId,
        val nome: String,
        val cognome: String,
        val sesso: Sesso,
    )

    suspend operator fun invoke(command: Command): Either<DomainError, Proclamatore> = either {
        val nome = command.nome.trim()
        val cognome = command.cognome.trim()

        if (nome.isBlank()) raise(DomainError.Validation("Il nome e' obbligatorio"))
        if (nome.length > 100) raise(DomainError.Validation("Il nome non puo' superare 100 caratteri"))
        if (cognome.isBlank()) raise(DomainError.Validation("Il cognome e' obbligatorio"))
        if (cognome.length > 100) raise(DomainError.Validation("Il cognome non puo' superare 100 caratteri"))

        val corrente = store.load(command.id)
            ?: raise(DomainError.Validation("Proclamatore non trovato"))

        val duplicato = query.esisteConNomeCognome(nome, cognome, esclusoId = command.id)
        if (duplicato) raise(DomainError.Validation("Esiste gia' un proclamatore con questo nome e cognome"))

        val aggiornato = corrente.copy(
            nome = nome,
            cognome = cognome,
            sesso = command.sesso,
        )
        store.persist(aggiornato)
        aggiornato
    }
}

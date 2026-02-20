package org.example.project.feature.people.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.feature.people.domain.Proclamatore
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
    )

    suspend operator fun invoke(command: Command): Either<DomainError, Proclamatore> = either {
        val nome = command.nome.trim()
        val cognome = command.cognome.trim()

        if (nome.isBlank()) raise(DomainError.Validation("Il nome e' obbligatorio"))
        if (nome.length > 100) raise(DomainError.Validation("Il nome non puo' superare 100 caratteri"))
        if (cognome.isBlank()) raise(DomainError.Validation("Il cognome e' obbligatorio"))
        if (cognome.length > 100) raise(DomainError.Validation("Il cognome non puo' superare 100 caratteri"))

        val duplicato = query.esisteConNomeCognome(nome, cognome)
        if (duplicato) raise(DomainError.Validation("Esiste gia' un proclamatore con questo nome e cognome"))

        val nuovo = Proclamatore(
            id = ProclamatoreId(UUID.randomUUID().toString()),
            nome = nome,
            cognome = cognome,
            sesso = command.sesso,
            attivo = true,
        )
        store.persist(nuovo)
        nuovo
    }
}

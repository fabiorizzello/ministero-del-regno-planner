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

        if (nome.isBlank()) raise(DomainError.NomeObbligatorio)
        if (nome.length > 100) raise(DomainError.NomeTroppoLungo(max = 100))
        if (cognome.isBlank()) raise(DomainError.CognomeObbligatorio)
        if (cognome.length > 100) raise(DomainError.CognomeTroppoLungo(max = 100))

        val duplicato = query.esisteConNomeCognome(nome, cognome)
        if (duplicato) raise(DomainError.ProclamatoreDuplicato)

        val nuovo = try {
            ProclamatoreAggregate.create(
                id = ProclamatoreId(UUID.randomUUID().toString()),
                nome = nome,
                cognome = cognome,
                sesso = command.sesso,
                sospeso = command.sospeso,
                puoAssistere = command.puoAssistere,
            ).person
        } catch (e: IllegalArgumentException) {
            raise(DomainError.Validation(e.message ?: "Dati proclamatore non validi"))
        }
        store.persist(nuovo)
        nuovo
    }
}

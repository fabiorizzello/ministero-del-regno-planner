package org.example.project.feature.people.domain

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError

private const val MAX_NAME_LENGTH = 100

data class ProclamatoreAggregate(
    val person: Proclamatore,
) {
    companion object {
        fun create(
            id: ProclamatoreId,
            nome: String,
            cognome: String,
            sesso: Sesso,
            sospeso: Boolean = false,
            puoAssistere: Boolean = false,
        ): Either<DomainError, ProclamatoreAggregate> = either {
            val trimmedNome = nome.trim()
            val trimmedCognome = cognome.trim()
            if (trimmedNome.isBlank()) raise(DomainError.NomeObbligatorio)
            if (trimmedNome.length > MAX_NAME_LENGTH) raise(DomainError.NomeTroppoLungo(MAX_NAME_LENGTH))
            if (trimmedCognome.isBlank()) raise(DomainError.CognomeObbligatorio)
            if (trimmedCognome.length > MAX_NAME_LENGTH) raise(DomainError.CognomeTroppoLungo(MAX_NAME_LENGTH))
            ProclamatoreAggregate(
                person = Proclamatore(
                    id = id,
                    nome = trimmedNome,
                    cognome = trimmedCognome,
                    sesso = sesso,
                    sospeso = sospeso,
                    puoAssistere = puoAssistere,
                ),
            )
        }
    }

    fun updateProfile(
        nome: String,
        cognome: String,
        sesso: Sesso,
        sospeso: Boolean,
        puoAssistere: Boolean,
    ): Either<DomainError, ProclamatoreAggregate> = either {
        val trimmedNome = nome.trim()
        val trimmedCognome = cognome.trim()
        if (trimmedNome.isBlank()) raise(DomainError.NomeObbligatorio)
        if (trimmedNome.length > MAX_NAME_LENGTH) raise(DomainError.NomeTroppoLungo(MAX_NAME_LENGTH))
        if (trimmedCognome.isBlank()) raise(DomainError.CognomeObbligatorio)
        if (trimmedCognome.length > MAX_NAME_LENGTH) raise(DomainError.CognomeTroppoLungo(MAX_NAME_LENGTH))
        copy(
            person = person.copy(
                nome = trimmedNome,
                cognome = trimmedCognome,
                sesso = sesso,
                sospeso = sospeso,
                puoAssistere = puoAssistere,
            ),
        )
    }

    fun suspendPerson(): ProclamatoreAggregate =
        if (person.sospeso) this else copy(person = person.copy(sospeso = true))

    fun reactivate(): ProclamatoreAggregate =
        if (!person.sospeso) this else copy(person = person.copy(sospeso = false))
}

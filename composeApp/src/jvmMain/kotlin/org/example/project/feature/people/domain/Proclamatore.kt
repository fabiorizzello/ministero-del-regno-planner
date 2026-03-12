package org.example.project.feature.people.domain

import arrow.core.Either
import org.example.project.core.domain.DomainError

@JvmInline
value class ProclamatoreId(val value: String)

enum class Sesso {
    M,
    F,
}

data class Proclamatore internal constructor(
    val id: ProclamatoreId,
    val nome: String,
    val cognome: String,
    val sesso: Sesso,
    val sospeso: Boolean = false,
    val puoAssistere: Boolean = false,
) {
    companion object {
        fun of(
            id: ProclamatoreId,
            nome: String,
            cognome: String,
            sesso: Sesso,
            sospeso: Boolean = false,
            puoAssistere: Boolean = false,
        ): Either<DomainError, Proclamatore> =
            ProclamatoreAggregate.create(id, nome, cognome, sesso, sospeso, puoAssistere)
                .map { it.person }
    }

    val fullName: String get() = "$nome $cognome"
}

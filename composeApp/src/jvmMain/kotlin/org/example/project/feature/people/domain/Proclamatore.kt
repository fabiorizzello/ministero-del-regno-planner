package org.example.project.feature.people.domain

import arrow.core.Either
import arrow.core.raise.either
import io.konform.validation.Validation
import io.konform.validation.constraints.maxLength
import org.example.project.core.domain.DomainError
import org.example.project.core.domain.validate

@JvmInline
value class ProclamatoreId(val value: String)

enum class Sesso {
    M,
    F,
}

private data class ProclamatoreValidationInput(
    val nome: String,
    val cognome: String,
)

private val proclamatoreValidator = Validation<ProclamatoreValidationInput> {
    ProclamatoreValidationInput::nome {
        constrain("nome non puo' essere vuoto") { it.trim().isNotEmpty() }
        maxLength(100)
    }
    ProclamatoreValidationInput::cognome {
        constrain("cognome non puo' essere vuoto") { it.trim().isNotEmpty() }
        maxLength(100)
    }
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
        ): Either<DomainError, Proclamatore> = either {
            proclamatoreValidator.validate(
                value = ProclamatoreValidationInput(nome = nome, cognome = cognome),
                context = "Proclamatore non valido",
            ).bind()
            Proclamatore(
                id = id,
                nome = nome,
                cognome = cognome,
                sesso = sesso,
                sospeso = sospeso,
                puoAssistere = puoAssistere,
            )
        }
    }

    val fullName: String get() = "${nome.trim()} ${cognome.trim()}"
}

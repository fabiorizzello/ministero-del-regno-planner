package org.example.project.feature.people.domain

import io.konform.validation.Validation
import io.konform.validation.constraints.maxLength
import org.example.project.core.domain.requireValid

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

data class Proclamatore(
    val id: ProclamatoreId,
    val nome: String,
    val cognome: String,
    val sesso: Sesso,
    val sospeso: Boolean = false,
    val puoAssistere: Boolean = false,
) {
    init {
        proclamatoreValidator.requireValid(
            value = ProclamatoreValidationInput(nome = nome, cognome = cognome),
            context = "Proclamatore non valido",
        )
    }

    val fullName: String get() = "${nome.trim()} ${cognome.trim()}"
}

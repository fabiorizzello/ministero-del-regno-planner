package org.example.project.feature.people.domain

@JvmInline
value class ProclamatoreId(val value: String)

enum class Sesso {
    M,
    F,
}

data class Proclamatore(
    val id: ProclamatoreId,
    val nome: String,
    val cognome: String,
    val sesso: Sesso,
    val attivo: Boolean,
    val sospeso: Boolean = false,
    val puoAssistere: Boolean = false,
) {
    init {
        require(nome.isNotBlank()) { "nome non può essere vuoto" }
        require(cognome.isNotBlank()) { "cognome non può essere vuoto" }
    }
}

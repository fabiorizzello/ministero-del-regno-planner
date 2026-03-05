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
    val sospeso: Boolean = false,
    val puoAssistere: Boolean = false,
) {
    init {
        require(nome.isNotBlank()) { "nome non può essere vuoto" }
        require(nome.length <= 100) { "nome non può superare 100 caratteri" }
        require(cognome.isNotBlank()) { "cognome non può essere vuoto" }
        require(cognome.length <= 100) { "cognome non può superare 100 caratteri" }
    }
}

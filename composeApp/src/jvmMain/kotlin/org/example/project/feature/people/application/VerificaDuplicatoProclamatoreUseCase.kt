package org.example.project.feature.people.application

import org.example.project.feature.people.domain.ProclamatoreId

class VerificaDuplicatoProclamatoreUseCase(
    private val query: ProclamatoriQuery,
) {
    suspend operator fun invoke(
        nome: String,
        cognome: String,
        esclusoId: ProclamatoreId? = null,
    ): Boolean {
        val trimmedNome = nome.trim()
        val trimmedCognome = cognome.trim()
        if (trimmedNome.isBlank() || trimmedCognome.isBlank()) return false
        return query.esisteConNomeCognome(trimmedNome, trimmedCognome, esclusoId)
    }
}

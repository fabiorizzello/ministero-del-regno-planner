package org.example.project.feature.people.application

import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId

interface ProclamatoriQuery {
    suspend fun cerca(termine: String? = null): List<Proclamatore>
    suspend fun esisteConNomeCognome(nome: String, cognome: String, esclusoId: ProclamatoreId? = null): Boolean
}

package org.example.project.feature.people.application

import org.example.project.feature.people.domain.Proclamatore

class CercaProclamatoriUseCase(
    private val query: ProclamatoriQuery,
) {
    suspend operator fun invoke(termine: String?): List<Proclamatore> {
        return query.cerca(termine)
    }
}

package org.example.project.feature.people.application

import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId

class CaricaProclamatoreUseCase(
    private val store: ProclamatoriAggregateStore,
) {
    suspend operator fun invoke(id: ProclamatoreId): Proclamatore? = store.load(id)
}

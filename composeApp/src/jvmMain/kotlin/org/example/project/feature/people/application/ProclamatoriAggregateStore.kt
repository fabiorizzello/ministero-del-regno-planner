package org.example.project.feature.people.application

import org.example.project.core.application.AggregateStore
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId

interface ProclamatoriAggregateStore : AggregateStore<ProclamatoreId, Proclamatore> {
    suspend fun persistAll(aggregateRoots: Collection<Proclamatore>)
    suspend fun remove(id: ProclamatoreId)
}

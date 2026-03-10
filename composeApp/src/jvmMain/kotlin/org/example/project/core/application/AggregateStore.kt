package org.example.project.core.application

import org.example.project.core.persistence.TransactionScope

interface AggregateStore<Id, AggregateRoot> {
    suspend fun load(id: Id): AggregateRoot?
    context(tx: TransactionScope) suspend fun persist(aggregateRoot: AggregateRoot)
}

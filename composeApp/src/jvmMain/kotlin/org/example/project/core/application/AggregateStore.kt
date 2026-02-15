package org.example.project.core.application

interface AggregateStore<Id, AggregateRoot> {
    suspend fun load(id: Id): AggregateRoot?
    suspend fun persist(aggregateRoot: AggregateRoot)
}

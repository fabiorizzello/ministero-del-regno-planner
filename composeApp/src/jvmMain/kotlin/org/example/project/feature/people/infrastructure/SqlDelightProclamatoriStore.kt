package org.example.project.feature.people.infrastructure

import org.example.project.db.MinisteroDatabase
import org.example.project.feature.people.application.ProclamatoriAggregateStore
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId

class SqlDelightProclamatoriStore(
    private val database: MinisteroDatabase,
) : ProclamatoriAggregateStore {

    override suspend fun load(id: ProclamatoreId): Proclamatore? {
        return database.ministeroDatabaseQueries
            .findProclaimerById(id.value, ::mapProclamatoreRow)
            .executeAsOneOrNull()
    }

    override suspend fun persist(aggregateRoot: Proclamatore) {
        persistInternal(aggregateRoot)
    }

    override suspend fun persistAll(aggregateRoots: Collection<Proclamatore>) {
        database.ministeroDatabaseQueries.transaction {
            aggregateRoots.forEach { aggregateRoot ->
                persistInternal(aggregateRoot)
            }
        }
    }

    private fun persistInternal(aggregateRoot: Proclamatore) {
        val id = aggregateRoot.id.value
        val active = if (aggregateRoot.attivo) 1L else 0L
        database.ministeroDatabaseQueries.upsertProclaimer(
            id,
            aggregateRoot.nome,
            aggregateRoot.cognome,
            aggregateRoot.sesso.name,
            active,
        )
    }

    override suspend fun remove(id: ProclamatoreId) {
        database.ministeroDatabaseQueries.deleteProclaimerById(id.value)
    }
}

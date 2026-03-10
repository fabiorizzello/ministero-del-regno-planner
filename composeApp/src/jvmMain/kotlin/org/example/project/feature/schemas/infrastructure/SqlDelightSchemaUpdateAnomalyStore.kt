package org.example.project.feature.schemas.infrastructure

import org.example.project.core.persistence.TransactionScope
import org.example.project.db.MinisteroDatabase
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.schemas.application.SchemaUpdateAnomaly
import org.example.project.feature.schemas.application.SchemaUpdateAnomalyDraft
import org.example.project.feature.schemas.application.SchemaUpdateAnomalyStore
import org.example.project.feature.weeklyparts.domain.PartTypeId

class SqlDelightSchemaUpdateAnomalyStore(
    private val database: MinisteroDatabase,
) : SchemaUpdateAnomalyStore {
    context(tx: TransactionScope)
    override suspend fun append(items: List<SchemaUpdateAnomalyDraft>) {
        if (items.isEmpty()) return
        items.forEach { item ->
            val deterministicId = "${item.personId.value}|${item.partTypeId.value}|${item.reason}".hashCode().toString()
            database.ministeroDatabaseQueries.insertSchemaUpdateAnomaly(
                id = deterministicId,
                person_id = item.personId.value,
                part_type_id = item.partTypeId.value,
                reason = item.reason,
                schema_version = item.schemaVersion,
                created_at = item.createdAt,
                dismissed = 0L,
            )
        }
    }

    override suspend fun listOpen(): List<SchemaUpdateAnomaly> {
        return database.ministeroDatabaseQueries
            .listOpenSchemaUpdateAnomalies()
            .executeAsList()
            .map { row ->
                SchemaUpdateAnomaly(
                    id = row.id,
                    personId = ProclamatoreId(row.person_id),
                    partTypeId = PartTypeId(row.part_type_id),
                    reason = row.reason,
                    schemaVersion = row.schema_version,
                    createdAt = row.created_at,
                )
            }
    }

    override suspend fun dismissAllOpen() {
        database.ministeroDatabaseQueries.dismissAllSchemaUpdateAnomalies()
    }
}

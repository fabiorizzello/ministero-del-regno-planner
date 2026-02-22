package org.example.project.feature.schemas.infrastructure

import org.example.project.db.MinisteroDatabase
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.schemas.application.SchemaUpdateAnomaly
import org.example.project.feature.schemas.application.SchemaUpdateAnomalyDraft
import org.example.project.feature.schemas.application.SchemaUpdateAnomalyStore
import org.example.project.feature.weeklyparts.domain.PartTypeId
import java.util.UUID

class SqlDelightSchemaUpdateAnomalyStore(
    private val database: MinisteroDatabase,
) : SchemaUpdateAnomalyStore {
    override suspend fun append(items: List<SchemaUpdateAnomalyDraft>) {
        if (items.isEmpty()) return
        database.ministeroDatabaseQueries.transaction {
            items.forEach { item ->
                database.ministeroDatabaseQueries.insertSchemaUpdateAnomaly(
                    id = UUID.randomUUID().toString(),
                    person_id = item.personId.value,
                    part_type_id = item.partTypeId.value,
                    reason = item.reason,
                    schema_version = item.schemaVersion,
                    created_at = item.createdAt,
                    dismissed = 0L,
                )
            }
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

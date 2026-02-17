package org.example.project.feature.weeklyparts.infrastructure

import org.example.project.db.MinisteroDatabase
import org.example.project.feature.weeklyparts.application.PartTypeStore
import org.example.project.feature.weeklyparts.domain.PartType
import java.util.UUID

class SqlDelightPartTypeStore(
    private val database: MinisteroDatabase,
) : PartTypeStore {

    override suspend fun all(): List<PartType> {
        return database.ministeroDatabaseQueries
            .allPartTypes(::mapPartTypeRow)
            .executeAsList()
    }

    override suspend fun findByCode(code: String): PartType? {
        return database.ministeroDatabaseQueries
            .findPartTypeByCode(code, ::mapPartTypeRow)
            .executeAsOneOrNull()
    }

    override suspend fun findFixed(): PartType? {
        return database.ministeroDatabaseQueries
            .findFixedPartType(::mapPartTypeRow)
            .executeAsOneOrNull()
    }

    override suspend fun upsertAll(partTypes: List<PartType>) {
        database.ministeroDatabaseQueries.transaction {
            partTypes.forEach { pt ->
                database.ministeroDatabaseQueries.upsertPartType(
                    id = pt.id.value.ifBlank { UUID.randomUUID().toString() },
                    code = pt.code,
                    label = pt.label,
                    people_count = pt.peopleCount.toLong(),
                    sex_rule = pt.sexRule.name,
                    fixed = if (pt.fixed) 1L else 0L,
                    sort_order = pt.sortOrder.toLong(),
                )
            }
        }
    }
}

package org.example.project.feature.weeklyparts.infrastructure

import org.example.project.db.MinisteroDatabase
import org.example.project.feature.weeklyparts.application.PartTypeStore
import org.example.project.feature.weeklyparts.application.PartTypeWithStatus
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import java.time.LocalDateTime
import java.util.UUID

class SqlDelightPartTypeStore(
    private val database: MinisteroDatabase,
) : PartTypeStore {

    override suspend fun all(): List<PartType> {
        return database.ministeroDatabaseQueries
            .allPartTypes(::mapPartTypeRow)
            .executeAsList()
    }

    override suspend fun allWithStatus(): List<PartTypeWithStatus> {
        return database.ministeroDatabaseQueries
            .listAllPartTypesExtended(::mapPartTypeExtendedRow)
            .executeAsList()
            .map { row ->
                PartTypeWithStatus(
                    partType = row.partType,
                    active = row.active,
                )
            }
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
                val ptRow = database.ministeroDatabaseQueries
                    .findPartTypeByCode(code = pt.code, mapper = { id, _, _, _, _, _, _ -> id })
                    .executeAsOneOrNull() ?: return@forEach
                val latestNumber = database.ministeroDatabaseQueries
                    .latestPartTypeRevisionByPartType(ptRow) { _, _, _, _, _, _, revision_number, _ -> revision_number }
                    .executeAsOneOrNull() ?: 0L
                val revisionId = UUID.randomUUID().toString()
                database.ministeroDatabaseQueries.insertPartTypeRevision(
                    id = revisionId,
                    part_type_id = ptRow,
                    label = pt.label,
                    people_count = pt.peopleCount.toLong(),
                    sex_rule = pt.sexRule.name,
                    fixed = if (pt.fixed) 1L else 0L,
                    revision_number = latestNumber + 1L,
                    created_at = LocalDateTime.now().toString(),
                )
                database.ministeroDatabaseQueries.updatePartTypeCurrentRevision(
                    current_revision_id = revisionId,
                    label = pt.label,
                    people_count = pt.peopleCount.toLong(),
                    sex_rule = pt.sexRule.name,
                    fixed = if (pt.fixed) 1L else 0L,
                    active = 1L,
                    sort_order = pt.sortOrder.toLong(),
                    id = ptRow,
                )
            }
        }
    }

    override suspend fun getLatestRevisionId(partTypeId: PartTypeId): String? {
        data class RevId(val value: String?)
        return database.ministeroDatabaseQueries
            .findPartTypeById(partTypeId.value) { _, _, _, _, _, _, _, _, current_revision_id: String? -> RevId(current_revision_id) }
            .executeAsOneOrNull()
            ?.value
    }

    override suspend fun deactivateMissingCodes(codes: Set<String>) {
        database.ministeroDatabaseQueries.transaction {
            if (codes.isEmpty()) {
                database.ministeroDatabaseQueries.deactivateAllPartTypes()
            } else {
                database.ministeroDatabaseQueries.deactivatePartTypesMissingCodes(codes.toList())
            }
        }
    }
}

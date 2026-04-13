package org.example.project.feature.weeklyparts.infrastructure

import org.example.project.core.persistence.TransactionScope
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

    override suspend fun findById(id: PartTypeId): PartType? {
        return database.ministeroDatabaseQueries
            .findPartTypeById(id.value) { rowId, code, label, people_count, sex_rule, fixed, _, sort_order, _ ->
                mapPartTypeRow(rowId, code, label, people_count, sex_rule, fixed, sort_order)
            }
            .executeAsOneOrNull()
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

    context(tx: TransactionScope)
    override suspend fun upsertAll(partTypes: List<PartType>) {
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
                .executeAsOneOrNull() ?: error("Part type not found after upsert for code: ${pt.code}")
            val latest = database.ministeroDatabaseQueries
                .latestPartTypeRevisionByPartType(ptRow) { id, _, label, people_count, sex_rule, fixed, revision_number, _ ->
                    LatestRevisionSnapshot(
                        id = id,
                        label = label,
                        peopleCount = people_count,
                        sexRule = sex_rule,
                        fixed = fixed,
                        number = revision_number,
                    )
                }
                .executeAsOneOrNull()
            val currentRevisionId = if (latest != null && latest.matches(pt)) {
                latest.id
            } else {
                val newRevisionId = UUID.randomUUID().toString()
                database.ministeroDatabaseQueries.insertPartTypeRevision(
                    id = newRevisionId,
                    part_type_id = ptRow,
                    label = pt.label,
                    people_count = pt.peopleCount.toLong(),
                    sex_rule = pt.sexRule.name,
                    fixed = if (pt.fixed) 1L else 0L,
                    revision_number = (latest?.number ?: 0L) + 1L,
                    created_at = LocalDateTime.now().toString(),
                )
                newRevisionId
            }
            database.ministeroDatabaseQueries.updatePartTypeCurrentRevision(
                current_revision_id = currentRevisionId,
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

    private data class LatestRevisionSnapshot(
        val id: String,
        val label: String,
        val peopleCount: Long,
        val sexRule: String,
        val fixed: Long,
        val number: Long,
    ) {
        fun matches(pt: PartType): Boolean =
            label == pt.label &&
                peopleCount == pt.peopleCount.toLong() &&
                sexRule == pt.sexRule.name &&
                fixed == (if (pt.fixed) 1L else 0L)
    }

    override suspend fun getLatestRevisionId(partTypeId: PartTypeId): String? {
        data class RevId(val value: String?)
        return database.ministeroDatabaseQueries
            .findPartTypeById(partTypeId.value) { _, _, _, _, _, _, _, _, current_revision_id: String? -> RevId(current_revision_id) }
            .executeAsOneOrNull()
            ?.value
    }

    context(tx: TransactionScope)
    override suspend fun deactivateMissingCodes(codes: Set<String>) {
        if (codes.isEmpty()) {
            database.ministeroDatabaseQueries.deactivateAllPartTypes()
        } else {
            database.ministeroDatabaseQueries.deactivatePartTypesMissingCodes(codes.toList())
        }
    }
}

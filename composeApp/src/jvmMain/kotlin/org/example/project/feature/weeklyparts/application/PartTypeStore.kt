package org.example.project.feature.weeklyparts.application

import org.example.project.core.persistence.TransactionScope
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.PartTypeSnapshot
import org.example.project.feature.weeklyparts.domain.SexRule
import java.time.LocalDateTime

data class PartTypeWithStatus(
    val partType: PartType,
    val active: Boolean,
)

data class PartTypeRevisionRow(
    val revisionNumber: Int,
    val createdAt: LocalDateTime,
    val snapshot: PartTypeSnapshot,
)

interface PartTypeStore {
    suspend fun all(): List<PartType>
    suspend fun allWithStatus(): List<PartTypeWithStatus> = all().map { PartTypeWithStatus(it, active = true) }
    suspend fun findById(id: PartTypeId): PartType? = all().firstOrNull { it.id == id }
    suspend fun findByCode(code: String): PartType?
    suspend fun findFixed(): PartType?
    context(tx: TransactionScope) suspend fun upsertAll(partTypes: List<PartType>)
    suspend fun getLatestRevisionId(partTypeId: PartTypeId): String? = null
    suspend fun allRevisionsForPartType(partTypeId: PartTypeId): List<PartTypeRevisionRow> = emptyList()
    context(tx: TransactionScope) suspend fun deactivateMissingCodes(codes: Set<String>) {}
}

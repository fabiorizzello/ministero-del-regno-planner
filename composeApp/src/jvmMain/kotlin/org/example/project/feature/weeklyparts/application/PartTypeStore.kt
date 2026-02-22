package org.example.project.feature.weeklyparts.application

import org.example.project.feature.weeklyparts.domain.PartType

data class PartTypeWithStatus(
    val partType: PartType,
    val active: Boolean,
)

interface PartTypeStore {
    suspend fun all(): List<PartType>
    suspend fun allWithStatus(): List<PartTypeWithStatus> = all().map { PartTypeWithStatus(it, active = true) }
    suspend fun findByCode(code: String): PartType?
    suspend fun findFixed(): PartType?
    suspend fun upsertAll(partTypes: List<PartType>)
    suspend fun deactivateMissingCodes(codes: Set<String>) {}
}

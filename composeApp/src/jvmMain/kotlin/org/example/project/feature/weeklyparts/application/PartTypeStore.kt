package org.example.project.feature.weeklyparts.application

import org.example.project.feature.weeklyparts.domain.PartType

interface PartTypeStore {
    suspend fun all(): List<PartType>
    suspend fun findByCode(code: String): PartType?
    suspend fun findFixed(): PartType?
    suspend fun upsertAll(partTypes: List<PartType>)
    suspend fun deactivateMissingCodes(codes: Set<String>) {}
}

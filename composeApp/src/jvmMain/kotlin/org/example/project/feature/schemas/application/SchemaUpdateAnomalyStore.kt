package org.example.project.feature.schemas.application

import org.example.project.core.persistence.TransactionScope
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.weeklyparts.domain.PartTypeId

data class SchemaUpdateAnomaly(
    val id: String,
    val personId: ProclamatoreId,
    val partTypeId: PartTypeId,
    val reason: String,
    val schemaVersion: String?,
    val createdAt: String,
)

data class SchemaUpdateAnomalyDraft(
    val personId: ProclamatoreId,
    val partTypeId: PartTypeId,
    val reason: String,
    val schemaVersion: String?,
    val createdAt: String,
)

interface SchemaUpdateAnomalyStore {
    context(tx: TransactionScope)
    suspend fun append(items: List<SchemaUpdateAnomalyDraft>)
    suspend fun listOpen(): List<SchemaUpdateAnomaly>
    context(tx: TransactionScope)
    suspend fun dismissAllOpen()
}

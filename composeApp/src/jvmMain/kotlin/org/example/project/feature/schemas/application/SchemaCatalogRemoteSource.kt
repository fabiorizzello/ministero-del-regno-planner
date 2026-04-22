package org.example.project.feature.schemas.application

import arrow.core.Either
import org.example.project.core.domain.DomainError
import org.example.project.feature.weeklyparts.domain.PartType

data class RemoteWeekSchemaTemplate(
    val weekStartDate: String,
    val partTypeCodes: List<String>,
)

data class SkippedPart(
    val weekStartDate: String,      // ISO date
    val mepsDocumentId: Long,
    val label: String,
    val detailLine: String?,
)

data class RemoteSchemaCatalog(
    val version: String?,
    val partTypes: List<PartType>,
    val weeks: List<RemoteWeekSchemaTemplate>,
    val skippedUnknownParts: List<SkippedPart> = emptyList(),
    val downloadedIssues: List<String> = emptyList(),
)

interface SchemaCatalogRemoteSource {
    suspend fun fetchCatalog(): Either<DomainError, RemoteSchemaCatalog>
}

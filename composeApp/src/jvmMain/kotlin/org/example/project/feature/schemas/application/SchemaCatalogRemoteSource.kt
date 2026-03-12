package org.example.project.feature.schemas.application

import arrow.core.Either
import org.example.project.core.domain.DomainError
import org.example.project.feature.weeklyparts.domain.PartType

data class RemoteWeekSchemaTemplate(
    val weekStartDate: String,
    val partTypeCodes: List<String>,
)

data class RemoteSchemaCatalog(
    val version: String?,
    val partTypes: List<PartType>,
    val weeks: List<RemoteWeekSchemaTemplate>,
)

interface SchemaCatalogRemoteSource {
    suspend fun fetchCatalog(): Either<DomainError, RemoteSchemaCatalog>
}

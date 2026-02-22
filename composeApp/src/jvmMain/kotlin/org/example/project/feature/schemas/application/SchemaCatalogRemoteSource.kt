package org.example.project.feature.schemas.application

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
    suspend fun fetchCatalog(): RemoteSchemaCatalog
}

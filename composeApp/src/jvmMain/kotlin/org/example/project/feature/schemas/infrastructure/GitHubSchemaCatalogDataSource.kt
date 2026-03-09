package org.example.project.feature.schemas.infrastructure

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.example.project.feature.schemas.application.RemoteSchemaCatalog
import org.example.project.feature.schemas.application.RemoteWeekSchemaTemplate
import org.example.project.feature.schemas.application.SchemaCatalogRemoteSource
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.infrastructure.parsePartTypeFromJson
import java.io.IOException

@Serializable
private data class CatalogDto(
    val version: String? = null,
    val partTypes: List<JsonObject> = emptyList(),
    val weeks: List<WeekDto> = emptyList(),
)

@Serializable
private data class WeekDto(
    val weekStartDate: String,
    val parts: List<PartDto>,
)

@Serializable
private data class PartDto(
    val partTypeCode: String,
)

class GitHubSchemaCatalogDataSource(
    private val httpClient: HttpClient,
    private val schemasCatalogUrl: String,
) : SchemaCatalogRemoteSource {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun fetchCatalog(): RemoteSchemaCatalog {
        val response = httpClient.get(schemasCatalogUrl)
        if (!response.status.isSuccess()) {
            throw IOException("HTTP ${response.status.value} fetching $schemasCatalogUrl")
        }
        val dto = json.decodeFromString<CatalogDto>(response.bodyAsText())

        val partTypes: List<PartType> = dto.partTypes.mapIndexed { index, obj ->
            parsePartTypeFromJson(obj, index).fold(
                ifLeft = { error -> throw IOException(error.details) },
                ifRight = { parsed -> parsed },
            )
        }
        val weeks = dto.weeks.map { week ->
            RemoteWeekSchemaTemplate(
                weekStartDate = week.weekStartDate,
                partTypeCodes = week.parts.map { it.partTypeCode },
            )
        }

        return RemoteSchemaCatalog(
            version = dto.version,
            partTypes = partTypes,
            weeks = weeks,
        )
    }
}

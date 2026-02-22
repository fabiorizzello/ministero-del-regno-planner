package org.example.project.feature.schemas.infrastructure

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.example.project.feature.schemas.application.RemoteSchemaCatalog
import org.example.project.feature.schemas.application.RemoteWeekSchemaTemplate
import org.example.project.feature.schemas.application.SchemaCatalogRemoteSource
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.infrastructure.parsePartTypeFromJson
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class GitHubSchemaCatalogDataSource(
    private val schemasCatalogUrl: String,
) : SchemaCatalogRemoteSource {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build()

    override suspend fun fetchCatalog(): RemoteSchemaCatalog = withContext(Dispatchers.IO) {
        val body = httpGet(schemasCatalogUrl)
        val root = Json.parseToJsonElement(body).jsonObject
        val version = root["version"]?.jsonPrimitive?.content

        val partTypes = parsePartTypes(root)
        val weeks = parseWeeks(root)

        RemoteSchemaCatalog(
            version = version,
            partTypes = partTypes,
            weeks = weeks,
        )
    }

    private fun parsePartTypes(root: kotlinx.serialization.json.JsonObject): List<PartType> {
        val arr = root["partTypes"]?.jsonArray ?: return emptyList()
        return arr.mapIndexed { index, element ->
            parsePartTypeFromJson(element.jsonObject, index)
        }
    }

    private fun parseWeeks(root: kotlinx.serialization.json.JsonObject): List<RemoteWeekSchemaTemplate> {
        val arr = root["weeks"]?.jsonArray ?: return emptyList()
        return arr.mapIndexed { index, element ->
            val obj = element.jsonObject
            val weekStartDate = obj["weekStartDate"]?.jsonPrimitive?.content
                ?: error("weeks[$index]: campo 'weekStartDate' mancante")
            val partsArr = obj["parts"]?.jsonArray
                ?: error("weeks[$index]: campo 'parts' mancante")
            val codes = partsArr.mapIndexed { pIdx, partEl ->
                partEl.jsonObject["partTypeCode"]?.jsonPrimitive?.content
                    ?: error("weeks[$index].parts[$pIdx]: campo 'partTypeCode' mancante")
            }
            RemoteWeekSchemaTemplate(
                weekStartDate = weekStartDate,
                partTypeCodes = codes,
            )
        }
    }

    private fun httpGet(url: String): String {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw RuntimeException("HTTP ${response.statusCode()} fetching $url")
        }
        return response.body()
    }
}

package org.example.project.feature.weeklyparts.infrastructure

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.example.project.feature.weeklyparts.application.RemoteDataSource
import org.example.project.feature.weeklyparts.application.RemoteWeekSchema
import org.example.project.feature.weeklyparts.domain.PartType
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class GitHubDataSource(
    private val partTypesUrl: String,
    private val weeklySchemasUrl: String,
) : RemoteDataSource {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build()

    override suspend fun fetchPartTypes(): List<PartType> = withContext(Dispatchers.IO) {
        val body = httpGet(partTypesUrl)
        val root = Json.parseToJsonElement(body).jsonObject
        val arr = root["partTypes"]?.jsonArray ?: return@withContext emptyList()
        arr.mapIndexed { index, element ->
            parsePartTypeFromJson(element.jsonObject, index)
        }
    }

    override suspend fun fetchWeeklySchemas(): List<RemoteWeekSchema> = withContext(Dispatchers.IO) {
        val body = httpGet(weeklySchemasUrl)
        val root = Json.parseToJsonElement(body).jsonObject
        val arr = root["weeks"]?.jsonArray ?: return@withContext emptyList()
        arr.mapIndexed { index, element ->
            val obj = element.jsonObject
            val weekStartDate = obj["weekStartDate"]?.jsonPrimitive?.content
                ?: error("weeks[$index]: campo 'weekStartDate' mancante")
            val partsArr = obj["parts"]?.jsonArray
                ?: error("weeks[$index]: campo 'parts' mancante")
            val parts = partsArr.mapIndexed { pIdx, partEl ->
                partEl.jsonObject["partTypeCode"]?.jsonPrimitive?.content
                    ?: error("weeks[$index].parts[$pIdx]: campo 'partTypeCode' mancante")
            }
            RemoteWeekSchema(
                weekStartDate = weekStartDate,
                partTypeCodes = parts,
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

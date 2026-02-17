package org.example.project.feature.weeklyparts.infrastructure

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.example.project.feature.weeklyparts.application.RemoteDataSource
import org.example.project.feature.weeklyparts.application.RemoteWeekSchema
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.SexRule
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID

class GitHubDataSource(
    private val partTypesUrl: String,
    private val weeklySchemasUrl: String,
) : RemoteDataSource {
    private val client = HttpClient.newHttpClient()

    override suspend fun fetchPartTypes(): List<PartType> = withContext(Dispatchers.IO) {
        val body = httpGet(partTypesUrl)
        val root = Json.parseToJsonElement(body).jsonObject
        val arr = root["partTypes"]?.jsonArray ?: return@withContext emptyList()
        arr.mapIndexed { index, element ->
            val obj = element.jsonObject
            PartType(
                id = PartTypeId(UUID.randomUUID().toString()),
                code = obj["code"]!!.jsonPrimitive.content,
                label = obj["label"]!!.jsonPrimitive.content,
                peopleCount = obj["peopleCount"]!!.jsonPrimitive.int,
                sexRule = SexRule.valueOf(obj["sexRule"]!!.jsonPrimitive.content),
                fixed = obj["fixed"]?.jsonPrimitive?.boolean ?: false,
                sortOrder = index,
            )
        }
    }

    override suspend fun fetchWeeklySchemas(): List<RemoteWeekSchema> = withContext(Dispatchers.IO) {
        val body = httpGet(weeklySchemasUrl)
        val root = Json.parseToJsonElement(body).jsonObject
        val arr = root["weeks"]?.jsonArray ?: return@withContext emptyList()
        arr.map { element ->
            val obj = element.jsonObject
            val parts = obj["parts"]!!.jsonArray.map { partEl ->
                partEl.jsonObject["partTypeCode"]!!.jsonPrimitive.content
            }
            RemoteWeekSchema(
                weekStartDate = obj["weekStartDate"]!!.jsonPrimitive.content,
                partTypeCodes = parts,
            )
        }
    }

    private fun httpGet(url: String): String {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw RuntimeException("HTTP ${response.statusCode()} fetching $url")
        }
        return response.body()
    }
}

package org.example.project.feature.weeklyparts.infrastructure

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.SexRule
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID

data class RemoteWeekSchema(
    val weekStartDate: String,
    val partTypeCodes: List<String>,
)

class GitHubDataSource(
    private val partTypesUrl: String,
    private val weeklySchemasUrl: String,
) {
    private val client = HttpClient.newHttpClient()

    fun fetchPartTypes(): List<PartType> {
        val body = httpGet(partTypesUrl)
        val root = Json.parseToJsonElement(body).jsonObject
        val arr = root["partTypes"]?.jsonArray ?: return emptyList()
        return arr.mapIndexed { index, element ->
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

    fun fetchWeeklySchemas(): List<RemoteWeekSchema> {
        val body = httpGet(weeklySchemasUrl)
        val root = Json.parseToJsonElement(body).jsonObject
        val arr = root["weeks"]?.jsonArray ?: return emptyList()
        return arr.map { element ->
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

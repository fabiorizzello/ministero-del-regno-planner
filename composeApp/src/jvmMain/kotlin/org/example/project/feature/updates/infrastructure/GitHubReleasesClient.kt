package org.example.project.feature.updates.infrastructure

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.example.project.core.config.RemoteConfig
import org.example.project.feature.updates.application.UpdateAsset
import org.example.project.feature.updates.application.UpdateChannel
import org.example.project.feature.updates.application.UpdateRelease
import org.slf4j.LoggerFactory

class GitHubReleasesClient(
    private val httpClient: HttpClient = HttpClient.newBuilder().build(),
) {
    private val logger = LoggerFactory.getLogger(GitHubReleasesClient::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    fun fetchLatestRelease(channel: UpdateChannel): UpdateRelease? {
        val repo = RemoteConfig.UPDATE_REPO
        val endpoint = if (channel == UpdateChannel.STABLE) {
            "https://api.github.com/repos/$repo/releases/latest"
        } else {
            "https://api.github.com/repos/$repo/releases"
        }

        val response = executeGet(endpoint) ?: return null
        return if (channel == UpdateChannel.STABLE) {
            parseRelease(response)
        } else {
            parseReleaseList(response).firstOrNull()
        }
    }

    private fun executeGet(url: String): String? {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "EfficaciNelMinistero")
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            logger.warn("GitHub API risposta non valida {} per {}", response.statusCode(), url)
            return null
        }
        return response.body()
    }

    private fun parseRelease(payload: String): UpdateRelease? {
        val obj = json.parseToJsonElement(payload).jsonObject
        return parseReleaseObject(obj)
    }

    private fun parseReleaseList(payload: String): List<UpdateRelease> {
        val arr = json.parseToJsonElement(payload).jsonArray
        return arr.mapNotNull { element ->
            parseReleaseObject(element.jsonObject)
        }
    }

    private fun parseReleaseObject(obj: kotlinx.serialization.json.JsonObject): UpdateRelease? {
        val tagName = obj["tag_name"]?.jsonPrimitive?.content
        if (tagName.isNullOrBlank()) return null
        val title = obj["name"]?.jsonPrimitive?.content
        val notes = obj["body"]?.jsonPrimitive?.content
        val assetsArray = obj["assets"]?.jsonArray ?: emptyList()
        val assets = assetsArray.mapNotNull { assetEl ->
            val assetObj = assetEl.jsonObject
            val name = assetObj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val url = assetObj["browser_download_url"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val size = assetObj["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            UpdateAsset(name, url, size)
        }
        val asset = assets.firstOrNull { it.name.endsWith(".msi", ignoreCase = true) }
            ?: assets.firstOrNull { it.name.endsWith(".exe", ignoreCase = true) }
            ?: assets.firstOrNull()
        return UpdateRelease(
            version = tagName,
            title = title,
            notes = notes,
            asset = asset,
        )
    }
}

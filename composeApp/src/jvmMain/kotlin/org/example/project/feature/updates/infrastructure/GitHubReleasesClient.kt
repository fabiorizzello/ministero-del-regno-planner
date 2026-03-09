package org.example.project.feature.updates.infrastructure

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import io.github.oshai.kotlinlogging.KotlinLogging
import org.example.project.core.config.RemoteConfig
import org.example.project.feature.updates.application.UpdateAsset
import org.example.project.core.config.UpdateChannel
import org.example.project.feature.updates.application.UpdateRelease

@Serializable
private data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String? = null,
    val name: String? = null,
    val body: String? = null,
    val assets: List<GitHubAssetDto> = emptyList(),
)

@Serializable
private data class GitHubAssetDto(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    val size: Long = 0L,
)

class GitHubReleasesClient(
    private val httpClient: HttpClient,
) {
    private val logger = KotlinLogging.logger {}
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchLatestRelease(channel: UpdateChannel): UpdateRelease? {
        val repo = RemoteConfig.UPDATE_REPO
        val endpoint = if (channel == UpdateChannel.STABLE) {
            "https://api.github.com/repos/$repo/releases/latest"
        } else {
            "https://api.github.com/repos/$repo/releases"
        }

        val body = executeGet(endpoint) ?: return null
        return if (channel == UpdateChannel.STABLE) {
            parseRelease(body)
        } else {
            parseReleaseList(body).firstOrNull()
        }
    }

    private suspend fun executeGet(url: String): String? {
        val response = httpClient.get(url) {
            header("Accept", "application/vnd.github+json")
        }
        if (!response.status.isSuccess()) {
            logger.warn { "GitHub API risposta non valida ${response.status.value} per $url" }
            return null
        }
        return response.bodyAsText()
    }

    private fun parseRelease(payload: String): UpdateRelease? =
        runCatching { json.decodeFromString<GitHubReleaseDto>(payload) }
            .getOrNull()
            ?.toUpdateRelease()

    private fun parseReleaseList(payload: String): List<UpdateRelease> =
        runCatching { json.decodeFromString<List<GitHubReleaseDto>>(payload) }
            .getOrElse { emptyList() }
            .mapNotNull { it.toUpdateRelease() }

    private fun GitHubReleaseDto.toUpdateRelease(): UpdateRelease? {
        if (tagName.isNullOrBlank()) return null
        val asset = assets
            .firstOrNull { it.name.endsWith(".msi", ignoreCase = true) }
            ?: assets.firstOrNull { it.name.endsWith(".exe", ignoreCase = true) }
            ?: assets.firstOrNull()
        return UpdateRelease(
            version = tagName,
            title = name,
            notes = body,
            asset = asset?.let { UpdateAsset(it.name, it.browserDownloadUrl, it.size) },
        )
    }
}

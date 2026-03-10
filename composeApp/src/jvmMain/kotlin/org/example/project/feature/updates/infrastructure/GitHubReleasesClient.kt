package org.example.project.feature.updates.infrastructure

import arrow.core.Either
import arrow.core.raise.either
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
import org.example.project.core.domain.DomainError
import org.example.project.feature.updates.application.UpdateAsset
import org.example.project.core.config.UpdateChannel
import org.example.project.feature.updates.application.UpdateRelease
import org.example.project.feature.updates.application.UpdateReleaseSource

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
) : UpdateReleaseSource {
    private val logger = KotlinLogging.logger {}
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun fetchLatestRelease(channel: UpdateChannel): Either<DomainError, UpdateRelease?> = either {
        val repo = RemoteConfig.UPDATE_REPO
        val endpoint = if (channel == UpdateChannel.STABLE) {
            "https://api.github.com/repos/$repo/releases/latest"
        } else {
            "https://api.github.com/repos/$repo/releases"
        }

        val body = executeGet(endpoint).bind()
        if (channel == UpdateChannel.STABLE) {
            parseRelease(body).bind()
        } else {
            parseReleaseList(body).bind().firstOrNull()
        }
    }

    private suspend fun executeGet(url: String): Either<DomainError, String> = either {
        val response = Either.catch {
            val response = httpClient.get(url) {
                header("Accept", "application/vnd.github+json")
            }
            response
        }.mapLeft { error ->
            DomainError.Network("Errore verifica aggiornamenti: ${error.message ?: "Connessione fallita"}")
        }.bind()
        if (!response.status.isSuccess()) {
            logger.warn { "GitHub API risposta non valida ${response.status.value} per $url" }
            raise(DomainError.Network("Errore verifica aggiornamenti: HTTP ${response.status.value}"))
        }
        Either.catch { response.bodyAsText() }
            .mapLeft { error ->
                DomainError.Network("Errore verifica aggiornamenti: ${error.message ?: "Connessione fallita"}")
            }
            .bind()
    }

    private fun parseRelease(payload: String): Either<DomainError, UpdateRelease?> =
        Either.catch {
            json.decodeFromString<GitHubReleaseDto>(payload).toUpdateRelease()
        }.mapLeft { error ->
            logger.warn(error) { "Parsing release GitHub fallito: ${error.message}" }
            DomainError.Network("Risposta GitHub non valida: ${error.message ?: "payload malformato"}")
        }

    private fun parseReleaseList(payload: String): Either<DomainError, List<UpdateRelease>> =
        Either.catch {
            json.decodeFromString<List<GitHubReleaseDto>>(payload)
                .mapNotNull { it.toUpdateRelease() }
        }.mapLeft { error ->
            logger.warn(error) { "Parsing lista release GitHub fallito: ${error.message}" }
            DomainError.Network("Risposta GitHub non valida: ${error.message ?: "payload malformato"}")
        }

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

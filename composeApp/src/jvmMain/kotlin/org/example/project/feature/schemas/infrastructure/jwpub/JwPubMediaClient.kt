package org.example.project.feature.schemas.infrastructure.jwpub

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.example.project.core.domain.DomainError

class JwPubMediaClient(
    private val httpClient: HttpClient,
    private val baseUrl: String = "https://b.jw-cdn.org/apis/pub-media/GETPUBMEDIALINKS",
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    suspend fun fetchMediaLinks(
        pub: String,
        issue: String,
        lang: String,
    ): Either<DomainError, JwPubMediaInfo?> = Either.catch {
        val response = httpClient.get(baseUrl) {
            parameter("output", "json")
            parameter("pub", pub)
            parameter("issue", issue)
            parameter("fileformat", "JWPUB")
            parameter("alllangs", "0")
            parameter("langwritten", lang)
        }
        if (response.status == HttpStatusCode.NotFound) return@catch null
        if (!response.status.isSuccess()) {
            throw java.io.IOException(
                "GETPUBMEDIALINKS $issue: HTTP ${response.status.value}",
            )
        }
        val dto = json.decodeFromString<MediaLinksDto>(response.bodyAsText())
        val jwpubEntry = dto.files[lang]?.jwpub?.firstOrNull()
            ?: throw java.io.IOException("GETPUBMEDIALINKS $issue: no JWPUB entry")
        JwPubMediaInfo(
            url = jwpubEntry.file.url,
            checksum = jwpubEntry.file.checksum,
            modifiedDatetime = jwpubEntry.file.modifiedDatetime,
            filesize = jwpubEntry.filesize,
        )
    }.mapLeft { DomainError.Network(it.message ?: "Errore GETPUBMEDIALINKS") }

    private fun HttpStatusCode.isSuccess(): Boolean = value in 200..299

    @Serializable
    private data class MediaLinksDto(val files: Map<String, LangFiles> = emptyMap())

    @Serializable
    private data class LangFiles(
        @kotlinx.serialization.SerialName("JWPUB")
        val jwpub: List<JwPubEntry> = emptyList(),
    )

    @Serializable
    private data class JwPubEntry(val file: FileDto, val filesize: Long)

    @Serializable
    private data class FileDto(
        val url: String,
        val modifiedDatetime: String,
        val checksum: String,
    )
}

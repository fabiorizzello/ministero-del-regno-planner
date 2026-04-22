package org.example.project.feature.schemas.infrastructure.jwpub

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import org.example.project.core.domain.DomainError

class JwPubDownloader(private val httpClient: HttpClient) {

    suspend fun download(url: String): Either<DomainError, ByteArray> = Either.catch {
        val response = httpClient.get(url)
        if (response.status.value !in 200..299) {
            throw java.io.IOException("Download $url: HTTP ${response.status.value}")
        }
        response.readRawBytes()
    }.mapLeft { DomainError.Network(it.message ?: "Errore download jwpub") }
}

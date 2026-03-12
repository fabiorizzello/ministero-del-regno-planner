package org.example.project.feature.updates

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.example.project.core.config.RemoteConfig
import org.example.project.core.config.UpdateChannel
import org.example.project.core.domain.DomainError
import org.example.project.feature.updates.infrastructure.GitHubReleasesClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GitHubReleasesClientTest {

    @Test
    fun `stable channel fetches latest release from configured repository`() = runTest {
        var requestedUrl: String? = null
        val httpClient = HttpClient(MockEngine { request ->
            requestedUrl = request.url.toString()
            respond(
                content = """
                    {
                      "tag_name": "v0.1.6",
                      "name": "Scuola di Ministero v0.1.6",
                      "body": "Fix update endpoint",
                      "assets": [
                        {
                          "name": "scuola-di-ministero-0.1.6.msi",
                          "browser_download_url": "https://example.test/scuola-di-ministero-0.1.6.msi",
                          "size": 123456
                        }
                      ]
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        })

        val result = GitHubReleasesClient(httpClient).fetchLatestRelease(UpdateChannel.STABLE)

        val release = assertIs<Either.Right<*>>(result).value
        val typedRelease = assertNotNull(release as? org.example.project.feature.updates.application.UpdateRelease)
        assertEquals(
            "https://api.github.com/repos/${RemoteConfig.UPDATE_REPO}/releases/latest",
            requestedUrl,
        )
        assertEquals("v0.1.6", typedRelease.version)
        assertEquals("scuola-di-ministero-0.1.6.msi", typedRelease.asset?.name)
    }

    @Test
    fun `non successful GitHub response is surfaced as network error`() = runTest {
        val httpClient = HttpClient(MockEngine {
            respond(
                content = """{"message":"Not Found"}""",
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        })

        val result = GitHubReleasesClient(httpClient).fetchLatestRelease(UpdateChannel.STABLE)

        val error = assertIs<Either.Left<DomainError>>(result).value
        val networkError = assertIs<DomainError.Network>(error)
        assertTrue(networkError.message.contains("HTTP 404"))
    }
}

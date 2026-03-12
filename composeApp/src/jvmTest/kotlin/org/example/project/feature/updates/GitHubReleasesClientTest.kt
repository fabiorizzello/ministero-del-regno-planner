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
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
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

    @Test
    fun `local override bypasses GitHub and returns local asset`() = runTest {
        val localMsi = Files.createTempFile("local-update-", ".msi")
        Files.writeString(localMsi, "fake-msi")
        val httpClient = HttpClient(MockEngine {
            error("GitHub should not be called when local override is active")
        })
        try {
            val client = GitHubReleasesClient(
                httpClient = httpClient,
                systemPropertyReader = { key ->
                    when (key) {
                        RemoteConfig.UPDATE_LOCAL_MSI_PATH_PROPERTY -> localMsi.toString()
                        RemoteConfig.UPDATE_LOCAL_VERSION_PROPERTY -> "v9.9.9-local"
                        else -> null
                    }
                },
                environmentReader = { null },
            )

            val result = client.fetchLatestRelease(UpdateChannel.STABLE)

            val release = assertIs<Either.Right<*>>(result).value
            val typedRelease = assertNotNull(release as? org.example.project.feature.updates.application.UpdateRelease)
            assertEquals("v9.9.9-local", typedRelease.version)
            assertEquals(localMsi.fileName.toString(), typedRelease.asset?.name)
            assertEquals(localMsi.toUri().toString(), typedRelease.asset?.downloadUrl)
        } finally {
            Files.deleteIfExists(localMsi)
            httpClient.close()
        }
    }

    @Test
    fun `local build flag auto discovers latest project msi`() = runTest {
        val projectRoot = Files.createTempDirectory("local-update-project-")
        val msiDir = projectRoot.resolve(Path.of("composeApp", "build", "compose", "binaries", "main", "msi"))
        Files.createDirectories(msiDir)
        Files.writeString(projectRoot.resolve("settings.gradle.kts"), "rootProject.name = \"ministero-del-regno-planner\"")
        val olderMsi = msiDir.resolve("scuola-di-ministero-0.1.7.msi")
        val latestMsi = msiDir.resolve("scuola-di-ministero-0.1.8.msi")
        Files.writeString(olderMsi, "old")
        Files.writeString(latestMsi, "new")
        Files.setLastModifiedTime(olderMsi, java.nio.file.attribute.FileTime.fromMillis(1_000))
        Files.setLastModifiedTime(latestMsi, java.nio.file.attribute.FileTime.fromMillis(2_000))

        val httpClient = HttpClient(MockEngine {
            error("GitHub should not be called when local build flag is active")
        })
        try {
            val client = GitHubReleasesClient(
                httpClient = httpClient,
                systemPropertyReader = { key ->
                    when (key) {
                        RemoteConfig.UPDATE_USE_LOCAL_BUILD_PROPERTY -> "true"
                        "user.dir" -> projectRoot.toString()
                        else -> null
                    }
                },
                environmentReader = { null },
            )

            val result = client.fetchLatestRelease(UpdateChannel.STABLE)

            val release = assertIs<Either.Right<*>>(result).value
            val typedRelease = assertNotNull(release as? org.example.project.feature.updates.application.UpdateRelease)
            assertEquals("0.1.9", typedRelease.version)
            assertEquals(latestMsi.fileName.toString(), typedRelease.asset?.name)
            assertEquals(latestMsi.toUri().toString(), typedRelease.asset?.downloadUrl)
        } finally {
            Files.walk(projectRoot)
                .sorted(Comparator.reverseOrder())
                .forEach(Files::deleteIfExists)
            httpClient.close()
        }
    }
}

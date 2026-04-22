package org.example.project.feature.schemas.infrastructure.jwpub

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.fullPath
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import org.example.project.feature.schemas.application.RemoteSchemaCatalog
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JwPubSchemaCatalogDataSourceTest {

    @get:Rule val tempFolder = TemporaryFolder()

    @Test fun `fetchCatalog downloads parses returns weeks with downloadedIssues`() = runBlocking {
        val fixtureBytes = Files.readAllBytes(
            Paths.get("src/jvmTest/resources/fixtures/jwpub/mwb_I_202601.jwpub").toAbsolutePath(),
        )
        val sampleJson = """
{"files":{"I":{"JWPUB":[{"file":{
  "url":"https://cfp2.jw-cdn.org/mwb_I_202601.jwpub",
  "modifiedDatetime":"2025-07-09 10:20:40",
  "checksum":"fakechecksum202601"},
  "filesize":${fixtureBytes.size}}]}}}
""".trimIndent()

        val engine = MockEngine { request ->
            val path = request.url.fullPath
            when {
                "GETPUBMEDIALINKS" in path && "issue=202601" in path ->
                    respond(
                        ByteReadChannel(sampleJson),
                        HttpStatusCode.OK,
                        headersOf("Content-Type", "application/json"),
                    )
                "GETPUBMEDIALINKS" in path ->
                    respondError(HttpStatusCode.NotFound)
                request.url.toString().endsWith("mwb_I_202601.jwpub") ->
                    respond(ByteReadChannel(fixtureBytes), HttpStatusCode.OK)
                else ->
                    respondError(HttpStatusCode.InternalServerError)
            }
        }

        val source = JwPubSchemaCatalogDataSource(
            httpClient = HttpClient(engine),
            cacheDir = tempFolder.root.toPath(),
            clock = Clock.fixed(Instant.parse("2026-01-05T09:00:00Z"), ZoneOffset.UTC),
            staticPartTypes = StaticPartTypesFixture.all(),
        )

        val result = source.fetchCatalog()
        val right = assertIs<Either.Right<RemoteSchemaCatalog>>(result)
        val catalog = right.value

        assertEquals(listOf("202601"), catalog.downloadedIssues)
        assertTrue(catalog.weeks.size >= 8, "Expected at least 8 weeks in bimester 202601")
        assertTrue(
            catalog.weeks.first().partTypeCodes.contains("LETTURA_DELLA_BIBBIA"),
            "Expected LETTURA_DELLA_BIBBIA in first week",
        )
        assertEquals(7, catalog.partTypes.size)
        assertTrue(catalog.partTypes.any { it.code == "LETTURA_DELLA_BIBBIA" })
    }
}

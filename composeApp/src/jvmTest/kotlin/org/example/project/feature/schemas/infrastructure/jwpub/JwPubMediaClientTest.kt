package org.example.project.feature.schemas.infrastructure.jwpub

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class JwPubMediaClientTest {

    private val sampleJson = """
{"files":{"I":{"JWPUB":[{"title":"Edizione normale",
"file":{"url":"https://cfp2.jw-cdn.org/a/mwb_I_202601.jwpub",
"modifiedDatetime":"2025-07-09 10:20:40",
"checksum":"8b35423df852a905ac2feea6d758ac7b"},
"filesize":3569429}]}}}
""".trimIndent()

    @Test fun `parses successful response`() = runBlocking {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(sampleJson),
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val client = JwPubMediaClient(HttpClient(engine))
        val result = client.fetchMediaLinks("mwb", "202601", "I")
        val right = assertIs<arrow.core.Either.Right<JwPubMediaInfo?>>(result)
        val value = assertNotNull(right.value)
        assertEquals("https://cfp2.jw-cdn.org/a/mwb_I_202601.jwpub", value.url)
        assertEquals("8b35423df852a905ac2feea6d758ac7b", value.checksum)
        assertEquals(3569429L, value.filesize)
    }

    @Test fun `404 maps to Right null`() = runBlocking {
        val engine = MockEngine { respondError(HttpStatusCode.NotFound) }
        val client = JwPubMediaClient(HttpClient(engine))
        val result = client.fetchMediaLinks("mwb", "299901", "I")
        assertEquals(arrow.core.Either.Right(null), result)
    }

    @Test fun `500 maps to Left Network`() = runBlocking {
        val engine = MockEngine { respondError(HttpStatusCode.InternalServerError) }
        val client = JwPubMediaClient(HttpClient(engine))
        val result = client.fetchMediaLinks("mwb", "202601", "I")
        assertIs<arrow.core.Either.Left<org.example.project.core.domain.DomainError>>(result)
        Unit
    }
}

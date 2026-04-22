package org.example.project.feature.schemas.infrastructure.jwpub

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class JwPubDownloaderTest {

    @Test fun `download returns bytes on 200`() = runBlocking {
        val expected = ByteArray(64) { it.toByte() }
        val engine = MockEngine { respond(ByteReadChannel(expected), HttpStatusCode.OK) }
        val res = JwPubDownloader(HttpClient(engine)).download("https://ex.com/mwb.jwpub")
        val right = assertIs<Either.Right<ByteArray>>(res)
        assertEquals(expected.toList(), right.value.toList())
    }

    @Test fun `500 maps to Left Network`() = runBlocking {
        val engine = MockEngine { respondError(HttpStatusCode.InternalServerError) }
        val res = JwPubDownloader(HttpClient(engine)).download("https://ex.com/mwb.jwpub")
        assertIs<Either.Left<org.example.project.core.domain.DomainError>>(res)
        Unit
    }
}

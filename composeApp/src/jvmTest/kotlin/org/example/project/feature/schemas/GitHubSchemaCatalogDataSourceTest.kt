package org.example.project.feature.schemas

import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import kotlinx.coroutines.runBlocking
import org.example.project.feature.schemas.infrastructure.GitHubSchemaCatalogDataSource
import java.io.IOException
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GitHubSchemaCatalogDataSourceTest {

    @Test
    fun `fetchCatalog throws IOException when HTTP status is not success`() = runBlocking {
        val body = """{"version":"v1","partTypes":[],"weeks":[]}"""
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/catalog.json") { exchange ->
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(500, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()

        val client = HttpClient(Java)
        try {
            val url = "http://127.0.0.1:${server.address.port}/catalog.json"
            val dataSource = GitHubSchemaCatalogDataSource(client, url)

            val error = assertFailsWith<IOException> {
                dataSource.fetchCatalog()
            }
            assertTrue(error.message?.contains("HTTP 500") == true)
        } finally {
            client.close()
            server.stop(0)
        }
    }
}

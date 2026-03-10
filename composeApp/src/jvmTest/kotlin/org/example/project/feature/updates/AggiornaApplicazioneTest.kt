package org.example.project.feature.updates

import arrow.core.Either
import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.test.runTest
import org.example.project.core.config.AppPaths
import org.example.project.core.config.AppRuntime
import org.example.project.core.domain.DomainError
import org.example.project.feature.updates.application.AggiornaApplicazione
import org.example.project.feature.updates.application.UpdateAsset
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class AggiornaApplicazioneTest {

    private var tempRoot: Path? = null

    @AfterTest
    fun tearDown() {
        resetAppRuntime()
        tempRoot?.let(::deleteRecursively)
        tempRoot = null
    }

    @Test
    fun `returns Network when download endpoint responds with HTTP error`() = runTest {
        initializeRuntime()
        val server = HttpServer.create(InetSocketAddress(0), 0).apply {
            createContext("/installer.msi") { exchange ->
                val body = "ko".toByteArray()
                exchange.sendResponseHeaders(500, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        val client = HttpClient(Java)
        try {
            val useCase = AggiornaApplicazione(client)

            val result = useCase(
                UpdateAsset(
                    name = "planner.msi",
                    downloadUrl = "http://127.0.0.1:${server.address.port}/installer.msi",
                    sizeBytes = 2,
                ),
            )

            val error = assertIs<Either.Left<DomainError>>(result).value
            assertIs<DomainError.Network>(error)
            assertEquals("Download aggiornamento fallito: HTTP 500", error.message)
            assertFalse(runtimeUpdatesDir().resolve("planner.msi").exists())
        } finally {
            client.close()
            server.stop(0)
        }
    }

    private fun initializeRuntime() {
        resetAppRuntime()
        val root = Files.createTempDirectory("aggiorna-app-test")
        tempRoot = root
        val dataDir = root.resolve("data").createDirectories()
        val logsDir = root.resolve("logs").createDirectories()
        val exportsDir = root.resolve("exports").createDirectories()
        AppRuntime.initialize(
            AppPaths(
                rootDir = root,
                dbFile = dataDir.resolve("ministero.sqlite"),
                logsDir = logsDir,
                exportsDir = exportsDir,
            ),
        )
    }

    private fun runtimeUpdatesDir(): Path = requireNotNull(tempRoot).resolve("exports").resolve("updates")

    private fun resetAppRuntime() {
        val field = AppRuntime::class.java.getDeclaredField("runtimePaths")
        field.isAccessible = true
        field.set(AppRuntime, null)
    }

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path)
            .sorted(Comparator.reverseOrder())
            .forEach { current -> current.deleteIfExists() }
    }
}

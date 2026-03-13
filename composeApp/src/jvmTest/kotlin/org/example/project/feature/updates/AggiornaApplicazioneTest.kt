package org.example.project.feature.updates

import arrow.core.Either
import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.java.Java
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.test.runTest
import org.example.project.core.config.AppPaths
import org.example.project.core.config.AppRuntime
import org.example.project.core.domain.DomainError
import org.example.project.feature.updates.application.AggiornaApplicazione
import org.example.project.feature.updates.application.UpdateAsset
import org.example.project.feature.updates.application.UpdateInstallResult
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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

    @Test
    fun `reuses cached installer when file already matches expected size`() = runTest {
        initializeRuntime()
        val cachedInstaller = runtimeUpdatesDir().createDirectories().resolve("planner.msi")
        Files.writeString(cachedInstaller, "cached-msi")
        var requestCount = 0
        val client = HttpClient(MockEngine {
            requestCount += 1
            respond("unexpected", HttpStatusCode.OK)
        })
        try {
            val useCase = AggiornaApplicazione(client)

            val result = useCase.downloadInstaller(
                UpdateAsset(
                    name = "planner.msi",
                    downloadUrl = "https://example.test/planner.msi",
                    sizeBytes = Files.size(cachedInstaller),
                ),
            )

            val installerPath = assertIs<Either.Right<Path>>(result).value
            assertEquals(cachedInstaller, installerPath)
            assertEquals(0, requestCount)
            assertEquals("cached-msi", Files.readString(cachedInstaller))
        } finally {
            client.close()
        }
    }

    @Test
    fun `prepares external updater and launches only on explicit restart`() = runTest {
        initializeRuntime()
        val installedResources = requireNotNull(tempRoot).resolve("installed").resolve("resources").createDirectories()
        val installerPath = runtimeUpdatesDir().createDirectories().resolve("planner.msi")
        val appExecutable = requireNotNull(tempRoot).resolve("installed").resolve("scuola-di-ministero.exe")
        Files.writeString(installerPath, "fake-msi")
        Files.writeString(appExecutable, "fake-exe")
        Files.writeString(installedResources.resolve("external-updater.ps1"), "Write-Output 'installed updater'")
        var launchedCommand: List<String>? = null
        val client = HttpClient(MockEngine {
            error("HTTP client should not be used in preparaInstallazione test")
        })
        try {
            val useCase = AggiornaApplicazione(
                httpClient = client,
                externalUpdaterLauncher = { command -> launchedCommand = command },
                currentProcessIdProvider = { 4242L },
                bundledResourcesDirProvider = { installedResources },
                appExecutableProvider = { appExecutable },
                updaterScriptBytesProvider = { "Write-Output 'fallback updater'".toByteArray() },
            )

            val result = useCase.preparaInstallazione(installerPath)

            val installResult = assertIs<Either.Right<UpdateInstallResult>>(result).value
            assertTrue(installResult.restartRequired)
            assertEquals(installerPath, installResult.installerPath)
            assertEquals(null, launchedCommand)
            val command = installResult.updaterCommand
            assertEquals("powershell.exe", command.first())
            assertTrue(command.contains("-STA"))
            assertTrue(command.contains("-InstallerPath"))
            assertTrue(command.contains(installerPath.toAbsolutePath().toString()))
            assertTrue(command.contains(appExecutable.toAbsolutePath().toString()))
            assertTrue(command.contains("4242"))
            val launchResult = useCase.avviaInstallazionePreparata(installResult)
            assertIs<Either.Right<Unit>>(launchResult)
            assertEquals(command, assertNotNull(launchedCommand))
            val stagedScript = runtimeUpdatesDir().resolve("external-updater.ps1")
            assertTrue(stagedScript.exists())
            assertEquals("Write-Output 'installed updater'", stagedScript.readText())
        } finally {
            client.close()
        }
    }

    @Test
    fun `copies installer from local file url without network download`() = runTest {
        initializeRuntime()
        val sourceInstaller = requireNotNull(tempRoot).resolve("source").createDirectories().resolve("planner-local.msi")
        Files.writeString(sourceInstaller, "local-msi")
        val progressEvents = mutableListOf<org.example.project.feature.updates.application.UpdateDownloadProgress>()
        val client = HttpClient(MockEngine {
            error("HTTP client should not be used for local file asset")
        })
        try {
            val useCase = AggiornaApplicazione(client)

            val result = useCase.downloadInstaller(
                UpdateAsset(
                    name = "planner-local.msi",
                    downloadUrl = sourceInstaller.toUri().toString(),
                    sizeBytes = Files.size(sourceInstaller),
                ),
                onProgress = { progressEvents += it },
            )

            val installerPath = assertIs<Either.Right<Path>>(result).value
            assertEquals(runtimeUpdatesDir().resolve("planner-local.msi"), installerPath)
            assertEquals("local-msi", Files.readString(installerPath))
            assertTrue(progressEvents.isNotEmpty())
            assertEquals(Files.size(sourceInstaller), progressEvents.last().downloadedBytes)
        } finally {
            client.close()
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

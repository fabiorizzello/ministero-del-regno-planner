package org.example.project.ui.updates

import arrow.core.Either
import com.sun.net.httpserver.HttpServer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import org.example.project.core.config.AppPaths
import org.example.project.core.config.AppRuntime
import org.example.project.core.config.UpdateSettingsStore
import org.example.project.core.domain.DomainError
import org.example.project.feature.updates.application.AggiornaApplicazione
import org.example.project.feature.updates.application.UpdateAsset
import org.example.project.feature.updates.application.UpdateCheckResult
import org.example.project.feature.updates.application.UpdateSource
import org.example.project.feature.updates.application.UpdateStatusStore
import org.example.project.feature.updates.application.VerificaAggiornamenti
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateCenterViewModelTest {

    @Test
    fun `format progress percent keeps decimals for early download`() {
        assertEquals("0.5%", formatProgressPercent(0.005f))
        assertEquals("12%", formatProgressPercent(0.12f))
        assertEquals("2.0 MB/s", formatTransferRate(2L * 1024L * 1024L))
    }

    @Test
    fun `failed update recheck clears stale asset and availability`() = runTest {
        val aggiornaApplicazione = mockk<AggiornaApplicazione>(relaxed = true)
        val updateSettingsStore = mockk<UpdateSettingsStore>()
        val verificaAggiornamenti = mockk<VerificaAggiornamenti>(relaxed = true)
        io.mockk.every { updateSettingsStore.loadLastCheck() } returns null
        val vmScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))

        val vm = try {
            UpdateCenterViewModel(
                scope = vmScope,
                verificaAggiornamenti = verificaAggiornamenti,
                aggiornaApplicazione = aggiornaApplicazione,
                updateStatusStore = UpdateStatusStore(),
                updateSettingsStore = updateSettingsStore,
            )
        } catch (error: Throwable) {
            vmScope.cancel()
            throw error
        }

        try {
            applyUpdateResult(
                vm,
                Either.Right(
                    UpdateCheckResult(
                        currentVersion = "1.0.0",
                        latestVersion = "v999.0.0",
                        updateAvailable = true,
                        asset = UpdateAsset("planner.msi", "https://example.test/planner.msi", 100),
                        releaseTitle = "Nuova release",
                        releaseNotes = "Note",
                        source = UpdateSource.GITHUB,
                        checkedAt = java.time.Instant.parse("2026-03-10T10:00:00Z"),
                    ),
                ),
            )
            assertEquals("v999.0.0", vm.state.value.latestVersion)
            assertTrue(vm.state.value.updateAvailable)

            applyUpdateResult(vm, Either.Left(DomainError.Network("timeout GitHub")))

            val state = vm.state.value
            assertFalse(state.updateAvailable)
            assertNull(state.latestVersion)
            assertNull(state.updateAsset)
            assertEquals("Non riesco a controllare gli aggiornamenti in questo momento.", state.statusText)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun `successful update requires restart and clears available action`() = runTest {
        val root = Files.createTempDirectory("update-center-vm-test")
        initializeRuntime(root)
        val vmScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val server = HttpServer.create(InetSocketAddress(0), 0).apply {
            createContext("/planner.msi") { exchange ->
                val body = "fake-msi".toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        val client = HttpClient(Java)
        var launchedCommand: List<String>? = null
        var restartTriggered = false
        try {
            val asset = UpdateAsset(
                name = "planner.msi",
                downloadUrl = "http://127.0.0.1:${server.address.port}/planner.msi",
                sizeBytes = 8,
            )
            val verificaAggiornamenti = mockk<VerificaAggiornamenti>(relaxed = true)
            val updateSettingsStore = mockk<UpdateSettingsStore>()
            io.mockk.every { updateSettingsStore.loadLastCheck() } returns null
            val aggiornaApplicazione = AggiornaApplicazione(
                httpClient = client,
                dispatcher = StandardTestDispatcher(testScheduler),
                externalUpdaterLauncher = { command -> launchedCommand = command },
                currentProcessIdProvider = { 4242L },
                bundledResourcesDirProvider = { root.resolve("installed").resolve("resources").createDirectories() },
                appExecutableProvider = {
                    root.resolve("installed").resolve("scuola-di-ministero.exe").also {
                        Files.createDirectories(it.parent)
                        Files.writeString(it, "fake-exe")
                    }
                },
                updaterScriptBytesProvider = { "Write-Output 'test updater'".toByteArray() },
            )

            val vm = UpdateCenterViewModel(
                scope = vmScope,
                verificaAggiornamenti = verificaAggiornamenti,
                aggiornaApplicazione = aggiornaApplicazione,
                updateStatusStore = UpdateStatusStore(),
                updateSettingsStore = updateSettingsStore,
            )

            applyUpdateResult(
                vm,
                Either.Right(
                    UpdateCheckResult(
                        currentVersion = "1.0.0",
                        latestVersion = "v1.1.0",
                        updateAvailable = true,
                        asset = asset,
                        releaseTitle = "v1.1.0",
                        releaseNotes = "Migliorie",
                        source = UpdateSource.GITHUB,
                        checkedAt = java.time.Instant.parse("2026-03-10T10:00:00Z"),
                    ),
                ),
            )
            vm.startUpdate()
            waitForUpdateCompletion(vm) { advanceUntilIdle() }

            val state = vm.state.value
            assertTrue(state.restartRequired)
            assertFalse(state.updateAvailable)
            assertNull(state.updateAsset)
            assertEquals("v1.1.0", state.installedVersion)
            assertNull(launchedCommand)
            assertEquals(
                "Aggiornamento pronto. Premi \"Riavvia per installare\" per continuare.",
                state.statusText,
            )

            vm.restartToInstall { restartTriggered = true }

            assertTrue(restartTriggered)
            assertNotNull(launchedCommand)
        } finally {
            vmScope.cancel()
            client.close()
            server.stop(0)
            resetAppRuntime()
            deleteRecursively(root)
        }
    }

    @Test
    fun `download progress updates status with percentage`() = runTest {
        val aggiornaApplicazione = mockk<AggiornaApplicazione>()
        val updateSettingsStore = mockk<UpdateSettingsStore>()
        val verificaAggiornamenti = mockk<VerificaAggiornamenti>(relaxed = true)
        io.mockk.every { updateSettingsStore.loadLastCheck() } returns null
        val vmScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))

        val vm = UpdateCenterViewModel(
            scope = vmScope,
            verificaAggiornamenti = verificaAggiornamenti,
            aggiornaApplicazione = aggiornaApplicazione,
            updateStatusStore = UpdateStatusStore(),
            updateSettingsStore = updateSettingsStore,
        )

        try {
            val asset = UpdateAsset("planner.msi", "https://example.test/planner.msi", 100)
            applyUpdateResult(
                vm,
                Either.Right(
                    UpdateCheckResult(
                        currentVersion = "1.0.0",
                        latestVersion = "v1.1.0",
                        updateAvailable = true,
                        asset = asset,
                        releaseTitle = "v1.1.0",
                        releaseNotes = "Migliorie",
                        source = UpdateSource.GITHUB,
                        checkedAt = java.time.Instant.parse("2026-03-10T10:00:00Z"),
                    ),
                ),
            )

            coEvery { aggiornaApplicazione.downloadInstaller(asset, any()) } coAnswers {
                val onProgress = arg<(org.example.project.feature.updates.application.UpdateDownloadProgress) -> Unit>(1)
                onProgress(org.example.project.feature.updates.application.UpdateDownloadProgress(50, 100))
                Either.Left(DomainError.Network("timed out"))
            }

            vm.startUpdate()
            advanceUntilIdle()

            val state = vm.state.value
            assertEquals("Il download sta impiegando troppo tempo. Controlla la connessione e riprova.", state.statusText)
            assertNull(state.downloadProgress)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun `download progress status includes transfer rate when timing is available`() = runTest {
        val aggiornaApplicazione = mockk<AggiornaApplicazione>()
        val updateSettingsStore = mockk<UpdateSettingsStore>()
        val verificaAggiornamenti = mockk<VerificaAggiornamenti>(relaxed = true)
        io.mockk.every { updateSettingsStore.loadLastCheck() } returns null
        val vmScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        var now = 0L
        var observedStatus: String? = null

        val vm = UpdateCenterViewModel(
            scope = vmScope,
            verificaAggiornamenti = verificaAggiornamenti,
            aggiornaApplicazione = aggiornaApplicazione,
            updateStatusStore = UpdateStatusStore(),
            updateSettingsStore = updateSettingsStore,
            nanoTimeProvider = { now },
        )

        try {
            val asset = UpdateAsset("planner.msi", "https://example.test/planner.msi", 8L * 1024L * 1024L)
            applyUpdateResult(
                vm,
                Either.Right(
                    UpdateCheckResult(
                        currentVersion = "1.0.0",
                        latestVersion = "v1.1.0",
                        updateAvailable = true,
                        asset = asset,
                        releaseTitle = "v1.1.0",
                        releaseNotes = "Migliorie",
                        source = UpdateSource.GITHUB,
                        checkedAt = java.time.Instant.parse("2026-03-10T10:00:00Z"),
                    ),
                ),
            )

            coEvery { aggiornaApplicazione.downloadInstaller(asset, any()) } coAnswers {
                val onProgress = arg<(org.example.project.feature.updates.application.UpdateDownloadProgress) -> Unit>(1)
                now = 2_000_000_000L
                onProgress(org.example.project.feature.updates.application.UpdateDownloadProgress(4L * 1024L * 1024L, 8L * 1024L * 1024L))
                observedStatus = vm.state.value.statusText
                Either.Left(DomainError.Network("timed out"))
            }

            vm.startUpdate()
            advanceUntilIdle()

            assertEquals(
                "Download aggiornamento in corso: 50% (4.0 MB di 8.0 MB) - 2.0 MB/s",
                observedStatus,
            )
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun `download validation error shows local actionable message`() = runTest {
        val aggiornaApplicazione = mockk<AggiornaApplicazione>()
        val updateSettingsStore = mockk<UpdateSettingsStore>()
        val verificaAggiornamenti = mockk<VerificaAggiornamenti>(relaxed = true)
        io.mockk.every { updateSettingsStore.loadLastCheck() } returns null
        val vmScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))

        val vm = UpdateCenterViewModel(
            scope = vmScope,
            verificaAggiornamenti = verificaAggiornamenti,
            aggiornaApplicazione = aggiornaApplicazione,
            updateStatusStore = UpdateStatusStore(),
            updateSettingsStore = updateSettingsStore,
        )

        try {
            val asset = UpdateAsset("planner.msi", "https://example.test/planner.msi", 100)
            applyUpdateResult(
                vm,
                Either.Right(
                    UpdateCheckResult(
                        currentVersion = "1.0.0",
                        latestVersion = "v1.1.0",
                        updateAvailable = true,
                        asset = asset,
                        releaseTitle = "v1.1.0",
                        releaseNotes = "Migliorie",
                        source = UpdateSource.GITHUB,
                        checkedAt = java.time.Instant.parse("2026-03-10T10:00:00Z"),
                    ),
                ),
            )

            coEvery { aggiornaApplicazione.downloadInstaller(asset, any()) } returns
                Either.Left(DomainError.Validation("Errore salvataggio aggiornamento: Access is denied"))

            vm.startUpdate()
            advanceUntilIdle()

            val state = vm.state.value
            assertEquals(
                "Non riesco a salvare l'aggiornamento su questo computer. Controlla spazio disponibile e permessi, poi riprova.",
                state.statusText,
            )
            assertNull(state.downloadProgress)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun `download succeeds but preparation fails leaves error state without restart`() = runTest {
        val aggiornaApplicazione = mockk<AggiornaApplicazione>()
        val updateSettingsStore = mockk<UpdateSettingsStore>()
        val verificaAggiornamenti = mockk<VerificaAggiornamenti>(relaxed = true)
        io.mockk.every { updateSettingsStore.loadLastCheck() } returns null
        val vmScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))

        val vm = UpdateCenterViewModel(
            scope = vmScope,
            verificaAggiornamenti = verificaAggiornamenti,
            aggiornaApplicazione = aggiornaApplicazione,
            updateStatusStore = UpdateStatusStore(),
            updateSettingsStore = updateSettingsStore,
        )

        try {
            val asset = UpdateAsset("planner.msi", "https://example.test/planner.msi", 100)
            applyUpdateResult(
                vm,
                Either.Right(
                    UpdateCheckResult(
                        currentVersion = "1.0.0",
                        latestVersion = "v1.1.0",
                        updateAvailable = true,
                        asset = asset,
                        releaseTitle = "v1.1.0",
                        releaseNotes = "Migliorie",
                        source = UpdateSource.GITHUB,
                        checkedAt = java.time.Instant.parse("2026-03-10T10:00:00Z"),
                    ),
                ),
            )

            val fakePath = Path.of("/tmp/fake-installer.msi")
            coEvery { aggiornaApplicazione.downloadInstaller(asset, any()) } returns Either.Right(fakePath)
            coEvery { aggiornaApplicazione.preparaInstallazione(fakePath) } returns
                Either.Left(DomainError.Validation("Impossibile preparare installazione"))

            vm.startUpdate()
            advanceUntilIdle()

            val state = vm.state.value
            assertTrue(state.hasError)
            assertFalse(state.restartRequired)
            assertFalse(state.isInstalling)
            assertEquals(
                "Non riesco a preparare l'installazione automatica su questo computer.",
                state.statusText,
            )
            // pendingInstall should be null — verify indirectly: restartToInstall should be a no-op
            var exitCalled = false
            vm.restartToInstall { exitCalled = true }
            assertFalse(exitCalled, "onExit should not be called when pendingInstall is null after prep failure")
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun `restartToInstall without pendingInstall is a no-op`() = runTest {
        val aggiornaApplicazione = mockk<AggiornaApplicazione>(relaxed = true)
        val updateSettingsStore = mockk<UpdateSettingsStore>()
        val verificaAggiornamenti = mockk<VerificaAggiornamenti>(relaxed = true)
        io.mockk.every { updateSettingsStore.loadLastCheck() } returns null
        val vmScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))

        val vm = try {
            UpdateCenterViewModel(
                scope = vmScope,
                verificaAggiornamenti = verificaAggiornamenti,
                aggiornaApplicazione = aggiornaApplicazione,
                updateStatusStore = UpdateStatusStore(),
                updateSettingsStore = updateSettingsStore,
            )
        } catch (error: Throwable) {
            vmScope.cancel()
            throw error
        }

        try {
            val stateBefore = vm.state.value

            var exitCalled = false
            vm.restartToInstall { exitCalled = true }

            assertFalse(exitCalled, "onExit should not be called when pendingInstall is null")
            val stateAfter = vm.state.value
            assertFalse(stateAfter.hasError, "State should remain unchanged — no error")
            assertEquals(stateBefore, stateAfter, "State should be completely unchanged")
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun `second startUpdate during active download is rejected by isBusy guard`() = runTest {
        val aggiornaApplicazione = mockk<AggiornaApplicazione>()
        val updateSettingsStore = mockk<UpdateSettingsStore>()
        val verificaAggiornamenti = mockk<VerificaAggiornamenti>(relaxed = true)
        io.mockk.every { updateSettingsStore.loadLastCheck() } returns null
        val vmScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))

        val vm = UpdateCenterViewModel(
            scope = vmScope,
            verificaAggiornamenti = verificaAggiornamenti,
            aggiornaApplicazione = aggiornaApplicazione,
            updateStatusStore = UpdateStatusStore(),
            updateSettingsStore = updateSettingsStore,
        )

        try {
            val asset = UpdateAsset("planner.msi", "https://example.test/planner.msi", 100)
            applyUpdateResult(
                vm,
                Either.Right(
                    UpdateCheckResult(
                        currentVersion = "1.0.0",
                        latestVersion = "v1.1.0",
                        updateAvailable = true,
                        asset = asset,
                        releaseTitle = "v1.1.0",
                        releaseNotes = "Migliorie",
                        source = UpdateSource.GITHUB,
                        checkedAt = java.time.Instant.parse("2026-03-10T10:00:00Z"),
                    ),
                ),
            )

            // First download stays in progress via CompletableDeferred
            val downloadGate = CompletableDeferred<Either<DomainError, Path>>()
            coEvery { aggiornaApplicazione.downloadInstaller(asset, any()) } coAnswers {
                downloadGate.await()
            }

            // Start first download
            vm.startUpdate()
            advanceUntilIdle()

            // Verify download is in progress
            assertTrue(vm.state.value.isDownloading, "First download should be in progress")

            // Attempt second download — should be rejected
            vm.startUpdate()
            advanceUntilIdle()

            // Only one download call should have been made
            coVerify(exactly = 1) { aggiornaApplicazione.downloadInstaller(asset, any()) }

            // Complete the first download so the coroutine can finish
            downloadGate.complete(Either.Left(DomainError.Network("cancelled")))
            advanceUntilIdle()
        } finally {
            vmScope.cancel()
        }
    }

    private fun applyUpdateResult(vm: UpdateCenterViewModel, result: Either<DomainError, UpdateCheckResult>) {
        val method = UpdateCenterViewModel::class.java.getDeclaredMethod("applyUpdateResult", Either::class.java)
        method.isAccessible = true
        method.invoke(vm, result)
    }

    private fun initializeRuntime(root: Path) {
        resetAppRuntime()
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

    private fun waitForUpdateCompletion(vm: UpdateCenterViewModel, advance: () -> Unit) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            advance()
            val state = vm.state.value
            if (state.restartRequired || state.hasError) return
            Thread.sleep(25)
        }
        error("Timeout attesa completamento update test: ${vm.state.value}")
    }
}

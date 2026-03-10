package org.example.project.ui.diagnostics

import arrow.core.Either
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.example.project.core.config.UpdateChannel
import org.example.project.core.config.UpdateSettingsStore
import org.example.project.core.domain.DomainError
import org.example.project.feature.updates.application.AggiornaApplicazione
import org.example.project.feature.updates.application.UpdateAsset
import org.example.project.feature.updates.application.UpdateCheckResult
import org.example.project.feature.updates.application.UpdateStatusStore
import org.example.project.feature.updates.application.VerificaAggiornamenti
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class DiagnosticsViewModelTest {

    @Test
    fun `failed update recheck clears stale asset and availability`() = runTest {
        val updateSettingsStore = mockk<UpdateSettingsStore>()
        every { updateSettingsStore.loadChannel() } returns UpdateChannel.STABLE
        every { updateSettingsStore.loadLastCheck() } returns null
        val vm = DiagnosticsViewModel(
            scope = backgroundScope,
            contaStorico = mockk(relaxed = true),
            eliminaStorico = mockk(relaxed = true),
            verificaAggiornamenti = mockk<VerificaAggiornamenti>(relaxed = true),
            aggiornaApplicazione = mockk<AggiornaApplicazione>(relaxed = true),
            updateStatusStore = UpdateStatusStore(),
            updateSettingsStore = updateSettingsStore,
        )

        applyUpdateResult(
            vm,
            Either.Right(
                UpdateCheckResult(
                    currentVersion = "1.0.0",
                    latestVersion = "v999.0.0",
                    updateAvailable = true,
                    asset = UpdateAsset("planner.msi", "https://example.test/planner.msi", 100),
                    checkedAt = java.time.Instant.parse("2026-03-10T10:00:00Z"),
                ),
            ),
        )
        assertEquals("v999.0.0", vm.state.value.updateLatestVersion)

        applyUpdateResult(vm, Either.Left(DomainError.Network("timeout GitHub")))

        val state = vm.state.value
        assertFalse(state.updateAvailable)
        assertNull(state.updateLatestVersion)
        assertNull(state.updateAsset)
        assertEquals("Errore verifica: timeout GitHub", state.updateStatusText)
    }

    private fun applyUpdateResult(vm: DiagnosticsViewModel, result: Either<DomainError, UpdateCheckResult>) {
        val method = DiagnosticsViewModel::class.java.getDeclaredMethod("applyUpdateResult", Either::class.java)
        method.isAccessible = true
        method.invoke(vm, result)
    }
}

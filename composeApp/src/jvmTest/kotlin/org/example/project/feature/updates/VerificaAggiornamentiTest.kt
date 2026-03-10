package org.example.project.feature.updates

import arrow.core.Either
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.example.project.core.config.UpdateChannel
import org.example.project.core.config.UpdateSettingsStore
import org.example.project.core.domain.DomainError
import org.example.project.feature.updates.application.UpdateAsset
import org.example.project.feature.updates.application.UpdateCheckResult
import org.example.project.feature.updates.application.UpdateRelease
import org.example.project.feature.updates.application.UpdateReleaseSource
import org.example.project.feature.updates.application.UpdateStatusStore
import org.example.project.feature.updates.application.VerificaAggiornamenti
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class VerificaAggiornamentiTest {

    @Test
    fun `propagates release source failures as Left and stores the status`() = runTest {
        val settingsStore = mockk<UpdateSettingsStore>()
        every { settingsStore.loadChannel() } returns UpdateChannel.STABLE
        every { settingsStore.saveLastCheck(any()) } returns Unit
        val statusStore = UpdateStatusStore()
        val useCase = VerificaAggiornamenti(
            releaseSource = object : UpdateReleaseSource {
                override suspend fun fetchLatestRelease(channel: UpdateChannel): Either<DomainError, UpdateRelease?> =
                    Either.Left(DomainError.Network("GitHub non raggiungibile"))
            },
            settingsStore = settingsStore,
            statusStore = statusStore,
        )

        val result = useCase()

        val error = assertIs<Either.Left<DomainError>>(result).value
        assertIs<DomainError.Network>(error)
        assertEquals("GitHub non raggiungibile", error.message)
        assertEquals(result, statusStore.state.value)
    }

    @Test
    fun `saveLastCheck failure does not abort a successful check`() = runTest {
        val settingsStore = mockk<UpdateSettingsStore>()
        every { settingsStore.loadChannel() } returns UpdateChannel.STABLE
        every { settingsStore.saveLastCheck(any()) } throws IllegalStateException("settings offline")
        val statusStore = UpdateStatusStore()
        val useCase = VerificaAggiornamenti(
            releaseSource = object : UpdateReleaseSource {
                override suspend fun fetchLatestRelease(channel: UpdateChannel): Either<DomainError, UpdateRelease?> =
                    Either.Right(
                        UpdateRelease(
                            version = "v999.0.0",
                            title = "Future build",
                            notes = null,
                            asset = UpdateAsset("planner.msi", "https://example.test/planner.msi", 42),
                        ),
                    )
            },
            settingsStore = settingsStore,
            statusStore = statusStore,
        )

        val result = useCase()

        val success = assertIs<Either.Right<UpdateCheckResult>>(result).value
        assertEquals("v999.0.0", success.latestVersion)
        assertNotNull(statusStore.state.value)
    }
}

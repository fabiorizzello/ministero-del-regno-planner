package org.example.project.feature.updates.application

import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.project.core.config.AppVersion
import org.example.project.core.config.UpdateSettingsStore
import org.example.project.feature.updates.UpdateVersionComparator
import org.example.project.feature.updates.infrastructure.GitHubReleasesClient
import org.slf4j.LoggerFactory

class VerificaAggiornamenti(
    private val client: GitHubReleasesClient,
    private val settingsStore: UpdateSettingsStore,
    private val statusStore: UpdateStatusStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val logger = LoggerFactory.getLogger(VerificaAggiornamenti::class.java)

    suspend operator fun invoke(): UpdateCheckResult = withContext(dispatcher) {
        val now = Instant.now()
        val channel = settingsStore.loadChannel()
        return@withContext runCatching {
            val release = client.fetchLatestRelease(channel)
            val currentVersion = AppVersion.current
            val latestVersion = release?.version
            val updateAvailable = if (latestVersion.isNullOrBlank() || currentVersion == "unknown") {
                false
            } else {
                UpdateVersionComparator.isNewer(currentVersion, latestVersion)
            }
            val result = UpdateCheckResult(
                currentVersion = currentVersion,
                latestVersion = latestVersion,
                updateAvailable = updateAvailable,
                asset = release?.asset,
                checkedAt = now,
                error = null,
            )
            settingsStore.saveLastCheck(now)
            statusStore.update(result)
            logger.info("Check aggiornamenti completato. Update disponibile: {}", updateAvailable)
            result
        }.getOrElse { error ->
            val result = UpdateCheckResult(
                currentVersion = AppVersion.current,
                latestVersion = null,
                updateAvailable = false,
                asset = null,
                checkedAt = now,
                error = error.message,
            )
            settingsStore.saveLastCheck(now)
            statusStore.update(result)
            logger.warn("Check aggiornamenti fallito: {}", error.message)
            result
        }
    }
}

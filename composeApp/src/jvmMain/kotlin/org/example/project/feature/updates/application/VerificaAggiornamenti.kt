package org.example.project.feature.updates.application

import arrow.core.Either
import arrow.core.raise.either
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.project.core.config.AppVersion
import org.example.project.core.config.UpdateSettingsStore
import org.example.project.core.domain.DomainError
import org.example.project.core.domain.toMessage
import org.example.project.feature.updates.UpdateVersionComparator
import org.example.project.feature.updates.infrastructure.GitHubReleasesClient
import io.github.oshai.kotlinlogging.KotlinLogging

class VerificaAggiornamenti(
    private val client: GitHubReleasesClient,
    private val settingsStore: UpdateSettingsStore,
    private val statusStore: UpdateStatusStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val logger = KotlinLogging.logger {}

    suspend operator fun invoke(): Either<DomainError, UpdateCheckResult> = withContext(dispatcher) {
        val now = Instant.now()
        val channel = settingsStore.loadChannel()
        val result = either<DomainError, UpdateCheckResult> {
            val release = runCatching { client.fetchLatestRelease(channel) }
                .getOrElse { raise(DomainError.Network(it.message ?: "Connessione fallita")) }
            val currentVersion = AppVersion.current
            val latestVersion = release?.version
            val updateAvailable = if (latestVersion.isNullOrBlank() || currentVersion == "unknown") {
                false
            } else {
                UpdateVersionComparator.isNewer(currentVersion, latestVersion)
            }
            UpdateCheckResult(
                currentVersion = currentVersion,
                latestVersion = latestVersion,
                updateAvailable = updateAvailable,
                asset = release?.asset,
                checkedAt = now,
            )
        }
        settingsStore.saveLastCheck(now)
        statusStore.update(result)
        if (result.isRight()) {
            logger.info { "Check aggiornamenti completato. Update disponibile: ${result.getOrNull()?.updateAvailable}" }
        } else {
            logger.warn { "Check aggiornamenti fallito: ${result.leftOrNull()?.toMessage()}" }
        }
        result
    }
}

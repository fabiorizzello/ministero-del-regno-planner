package org.example.project.ui.updates

import arrow.core.Either
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.core.config.AppVersion
import org.example.project.core.config.UpdateSettingsStore
import org.example.project.core.domain.DomainError
import org.example.project.core.domain.toMessage
import org.example.project.feature.updates.application.AggiornaApplicazione
import org.example.project.feature.updates.application.UpdateAsset
import org.example.project.feature.updates.application.UpdateCheckResult
import org.example.project.feature.updates.application.UpdateDownloadProgress
import org.example.project.feature.updates.application.UpdateInstallResult
import org.example.project.feature.updates.application.UpdateSource
import org.example.project.feature.updates.application.UpdateStatusStore
import org.example.project.feature.updates.application.VerificaAggiornamenti

internal data class UpdateCenterUiState(
    val currentVersion: String = AppVersion.current,
    val lastCheck: Instant? = null,
    val updateAvailable: Boolean = false,
    val latestVersion: String? = null,
    val releaseTitle: String? = null,
    val releaseNotes: String? = null,
    val updateAsset: UpdateAsset? = null,
    val updateSource: UpdateSource? = null,
    val statusText: String = "Nessun controllo eseguito",
    val isChecking: Boolean = false,
    val isDownloading: Boolean = false,
    val isInstalling: Boolean = false,
    val downloadProgress: Float? = null,
    val downloadedBytes: Long = 0L,
    val downloadTotalBytes: Long? = null,
    val restartRequired: Boolean = false,
    val installedVersion: String? = null,
    val hasError: Boolean = false,
) {
    val isBusy: Boolean get() = isChecking || isDownloading || isInstalling
}

internal class UpdateCenterViewModel(
    private val scope: CoroutineScope,
    private val verificaAggiornamenti: VerificaAggiornamenti,
    private val aggiornaApplicazione: AggiornaApplicazione,
    private val updateStatusStore: UpdateStatusStore,
    private val updateSettingsStore: UpdateSettingsStore,
) {
    private var pendingInstall: UpdateInstallResult? = null
    private val _state = MutableStateFlow(
        UpdateCenterUiState(
            lastCheck = updateSettingsStore.loadLastCheck(),
        ),
    )
    val state: StateFlow<UpdateCenterUiState> = _state.asStateFlow()

    init {
        scope.launch {
            updateStatusStore.state.collect { result ->
                if (result != null) applyUpdateResult(result)
            }
        }
    }

    fun checkUpdates() {
        if (_state.value.isBusy || _state.value.restartRequired) return
        scope.launch {
            _state.update { it.copy(isChecking = true, hasError = false) }
            val result = verificaAggiornamenti()
            applyUpdateResult(result)
            _state.update { it.copy(isChecking = false) }
        }
    }

    fun startUpdate() {
        val asset = _state.value.updateAsset ?: return
        val targetVersion = _state.value.latestVersion
        if (_state.value.isBusy || _state.value.restartRequired) return
        pendingInstall = null

        scope.launch {
            _state.update {
                it.copy(
                    isDownloading = true,
                    hasError = false,
                    downloadProgress = 0f,
                    downloadedBytes = 0L,
                    downloadTotalBytes = asset.sizeBytes.takeIf { it > 0L },
                    statusText = "Download aggiornamento in corso...",
                )
            }
            aggiornaApplicazione.downloadInstaller(asset) { progress ->
                applyDownloadProgress(progress)
            }.fold(
                ifLeft = { error ->
                    _state.update {
                        it.copy(
                            isDownloading = false,
                            hasError = true,
                            downloadProgress = null,
                            statusText = updateFailureMessage(error, UpdatePhase.DOWNLOAD),
                        )
                    }
                },
                ifRight = { installerPath ->
                    _state.update {
                        it.copy(
                            isDownloading = false,
                            isInstalling = true,
                            downloadProgress = 1f,
                            statusText = "Preparazione installazione in corso...",
                        )
                    }
                    aggiornaApplicazione.preparaInstallazione(installerPath).fold(
                        ifLeft = { error ->
                            pendingInstall = null
                            _state.update {
                                it.copy(
                                    isInstalling = false,
                                    hasError = true,
                                    downloadProgress = null,
                                    statusText = updateFailureMessage(error, UpdatePhase.PREPARE),
                                )
                            }
                        },
                        ifRight = { installResult ->
                            pendingInstall = installResult
                            _state.update { current ->
                                current.copy(
                                    isInstalling = false,
                                    updateAvailable = false,
                                    updateAsset = null,
                                    restartRequired = installResult.restartRequired,
                                    installedVersion = targetVersion,
                                    hasError = false,
                                    downloadProgress = null,
                                    statusText = "Aggiornamento pronto. Premi \"Riavvia per installare\" per continuare.",
                                )
                            }
                        },
                    )
                },
            )
        }
    }

    fun restartToInstall(onExit: () -> Unit) {
        val installResult = pendingInstall ?: return
        aggiornaApplicazione.avviaInstallazionePreparata(installResult).fold(
            ifLeft = { error ->
                _state.update {
                    it.copy(
                        hasError = true,
                        statusText = updateFailureMessage(error, UpdatePhase.LAUNCH),
                    )
                }
            },
            ifRight = {
                onExit()
            },
        )
    }

    private fun applyUpdateResult(result: Either<DomainError, UpdateCheckResult>) {
        _state.update { state ->
            when (result) {
                is Either.Left -> state.copy(
                    updateAvailable = false,
                    latestVersion = null,
                    releaseTitle = null,
                    releaseNotes = null,
                    updateAsset = null,
                    updateSource = null,
                    hasError = true,
                    statusText = updateFailureMessage(result.value, UpdatePhase.CHECK),
                )

                is Either.Right -> {
                    val value = result.value
                    state.copy(
                        updateAvailable = value.updateAvailable,
                        latestVersion = value.latestVersion,
                        releaseTitle = value.releaseTitle,
                        releaseNotes = value.releaseNotes,
                        updateAsset = value.asset,
                        updateSource = value.source,
                        lastCheck = value.checkedAt,
                        hasError = false,
                        statusText = when {
                            value.latestVersion.isNullOrBlank() -> "Nessuna release trovata"
                            value.updateAvailable -> "Aggiornamento disponibile: ${displayVersion(value.latestVersion)}. Un clic scarica e prepara l'installazione."
                            else -> "App aggiornata (${displayVersion(value.currentVersion)})"
                        },
                    )
                }
            }
        }
    }

    private fun applyDownloadProgress(progress: UpdateDownloadProgress) {
        _state.update { state ->
            if (!state.isDownloading) state else {
                val status = progressStatusText(progress)
                state.copy(
                    downloadProgress = progress.fraction,
                    downloadedBytes = progress.downloadedBytes,
                    downloadTotalBytes = progress.totalBytes,
                    statusText = status,
                )
            }
        }
    }
}

private enum class UpdatePhase {
    CHECK,
    DOWNLOAD,
    PREPARE,
    LAUNCH,
}

private fun progressStatusText(progress: UpdateDownloadProgress): String {
    val downloaded = humanReadableBytes(progress.downloadedBytes)
    val total = progress.totalBytes?.let(::humanReadableBytes)
    val percent = progress.fraction?.let { "${(it * 100).toInt()}%" }
    return when {
        percent != null && total != null -> "Download aggiornamento in corso: $percent ($downloaded di $total)"
        total != null -> "Download aggiornamento in corso: $downloaded di $total"
        else -> "Download aggiornamento in corso: $downloaded scaricati"
    }
}

private fun updateFailureMessage(error: DomainError, phase: UpdatePhase): String = when (phase) {
    UpdatePhase.CHECK -> "Non riesco a controllare gli aggiornamenti in questo momento."
    UpdatePhase.DOWNLOAD -> when {
        error.toMessage().contains("timeout", ignoreCase = true) ||
            error.toMessage().contains("timed out", ignoreCase = true) ->
            "Il download sta impiegando troppo tempo. Controlla la connessione e riprova."
        else -> "Non riesco a scaricare l'aggiornamento. Controlla la connessione e riprova."
    }
    UpdatePhase.PREPARE -> "Non riesco a preparare l'installazione automatica su questo computer."
    UpdatePhase.LAUNCH -> "Non riesco ad avviare l'installazione automatica. Riprova."
}

private fun humanReadableBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = -1
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex += 1
    }
    return if (value >= 10 || unitIndex == 0) {
        "${value.toInt()} ${units[unitIndex]}"
    } else {
        "%.1f %s".format(java.util.Locale.US, value, units[unitIndex])
    }
}

private fun displayVersion(version: String?): String =
    normalizeVersion(version)?.let { "v$it" } ?: "versione sconosciuta"

private fun normalizeVersion(version: String?): String? =
    version
        ?.trim()
        ?.removePrefix("v")
        ?.takeIf(String::isNotBlank)

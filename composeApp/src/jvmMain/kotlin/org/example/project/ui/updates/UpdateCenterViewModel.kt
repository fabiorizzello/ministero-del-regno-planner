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
    val statusText: String = "Nessun controllo eseguito",
    val isChecking: Boolean = false,
    val isDownloading: Boolean = false,
    val isInstalling: Boolean = false,
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

        scope.launch {
            _state.update {
                it.copy(
                    isDownloading = true,
                    hasError = false,
                    statusText = "Download aggiornamento in corso...",
                )
            }
            aggiornaApplicazione.downloadInstaller(asset).fold(
                ifLeft = { error ->
                    _state.update {
                        it.copy(
                            isDownloading = false,
                            hasError = true,
                            statusText = "Aggiornamento non riuscito: ${error.toMessage()}",
                        )
                    }
                },
                ifRight = { installerPath ->
                    _state.update {
                        it.copy(
                            isDownloading = false,
                            isInstalling = true,
                            statusText = "Installazione aggiornamento in corso...",
                        )
                    }
                    aggiornaApplicazione.installaSilenzioso(installerPath).fold(
                        ifLeft = { error ->
                            _state.update {
                                it.copy(
                                    isInstalling = false,
                                    hasError = true,
                                    statusText = "Aggiornamento non riuscito: ${error.toMessage()}",
                                )
                            }
                        },
                        ifRight = { installResult ->
                            _state.update { current ->
                                current.copy(
                                    isInstalling = false,
                                    updateAvailable = false,
                                    updateAsset = null,
                                    restartRequired = installResult.restartRequired,
                                    installedVersion = targetVersion,
                                    hasError = false,
                                    statusText = "Aggiornamento installato. Riavvia l'app per applicare.",
                                )
                            }
                        },
                    )
                },
            )
        }
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
                    hasError = true,
                    statusText = "Errore verifica: ${result.value.toMessage()}",
                )

                is Either.Right -> {
                    val value = result.value
                    state.copy(
                        updateAvailable = value.updateAvailable,
                        latestVersion = value.latestVersion,
                        releaseTitle = value.releaseTitle,
                        releaseNotes = value.releaseNotes,
                        updateAsset = value.asset,
                        lastCheck = value.checkedAt,
                        hasError = false,
                        statusText = when {
                            value.latestVersion.isNullOrBlank() -> "Nessuna release trovata"
                            value.updateAvailable -> "Aggiornamento disponibile: ${value.latestVersion}"
                            else -> "App aggiornata (v${value.currentVersion})"
                        },
                    )
                }
            }
        }
    }
}

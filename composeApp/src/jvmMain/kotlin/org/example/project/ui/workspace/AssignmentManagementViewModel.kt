package org.example.project.ui.workspace

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.feature.assignments.application.AutoAssegnaProgrammaUseCase
import org.example.project.feature.assignments.application.AutoAssignUnresolvedSlot
import org.example.project.feature.assignments.application.CaricaImpostazioniAssegnatoreUseCase
import org.example.project.feature.assignments.application.RimuoviAssegnazioniSettimanaUseCase
import org.example.project.feature.assignments.application.SalvaImpostazioniAssegnatoreUseCase
import org.example.project.feature.assignments.application.SvuotaAssegnazioniProgrammaUseCase
import org.example.project.feature.output.application.StampaProgrammaUseCase
import org.example.project.ui.components.FeedbackBannerKind
import org.example.project.ui.components.FeedbackBannerModel
import org.example.project.ui.components.errorNotice
import org.example.project.ui.components.executeAsyncOperation
import org.example.project.ui.components.executeAsyncOperationWithNotice
import org.example.project.ui.components.successNotice
import java.time.LocalDate

internal data class AssignmentManagementUiState(
    val isAutoAssigning: Boolean = false,
    val isPrintingProgram: Boolean = false,
    val isSavingAssignmentSettings: Boolean = false,
    val assignmentSettings: AssignmentSettingsUiState = AssignmentSettingsUiState(),
    val autoAssignUnresolved: List<AutoAssignUnresolvedSlot> = emptyList(),
    val isClearingAssignments: Boolean = false,
    val clearAssignmentsConfirm: Int? = null,
    val isClearingWeekAssignments: Boolean = false,
    val clearWeekAssignmentsConfirm: Pair<String, Int>? = null,
    val notice: FeedbackBannerModel? = null,
)

internal class AssignmentManagementViewModel(
    private val scope: CoroutineScope,
    private val autoAssegnaProgramma: AutoAssegnaProgrammaUseCase,
    private val caricaImpostazioniAssegnatore: CaricaImpostazioniAssegnatoreUseCase,
    private val salvaImpostazioniAssegnatore: SalvaImpostazioniAssegnatoreUseCase,
    private val svuotaAssegnazioni: SvuotaAssegnazioniProgrammaUseCase,
    private val rimuoviAssegnazioniSettimana: RimuoviAssegnazioniSettimanaUseCase,
    private val stampaProgramma: StampaProgrammaUseCase,
) {
    private val _uiState = MutableStateFlow(AssignmentManagementUiState())
    val uiState: StateFlow<AssignmentManagementUiState> = _uiState.asStateFlow()

    fun onScreenEntered() {
        scope.launch {
            loadAssignmentSettings()
        }
    }

    fun dismissNotice() {
        _uiState.update { it.copy(notice = null) }
    }

    fun setNotice(notice: FeedbackBannerModel) {
        _uiState.update { it.copy(notice = notice) }
    }

    fun setStrictCooldown(value: Boolean) {
        _uiState.update { it.copy(assignmentSettings = it.assignmentSettings.copy(strictCooldown = value)) }
    }

    fun setLeadWeight(value: String) {
        _uiState.update { it.copy(assignmentSettings = it.assignmentSettings.copy(leadWeight = value)) }
    }

    fun setAssistWeight(value: String) {
        _uiState.update { it.copy(assignmentSettings = it.assignmentSettings.copy(assistWeight = value)) }
    }

    fun setLeadCooldownWeeks(value: String) {
        _uiState.update { it.copy(assignmentSettings = it.assignmentSettings.copy(leadCooldownWeeks = value)) }
    }

    fun setAssistCooldownWeeks(value: String) {
        _uiState.update { it.copy(assignmentSettings = it.assignmentSettings.copy(assistCooldownWeeks = value)) }
    }

    fun saveAssignmentSettings() {
        if (_uiState.value.isSavingAssignmentSettings) return
        val parsed = parseAssignmentSettings(_uiState.value.assignmentSettings)
        if (parsed == null) {
            _uiState.update {
                it.copy(
                    notice = FeedbackBannerModel(
                        "Impostazioni non valide: usa numeri interi >= 0 (peso >= 1)",
                        FeedbackBannerKind.ERROR,
                    ),
                )
            }
            return
        }

        scope.launch {
            _uiState.executeAsyncOperationWithNotice(
                loadingUpdate = { it.copy(isSavingAssignmentSettings = true) },
                noticeUpdate = { state, notice -> state.copy(isSavingAssignmentSettings = false, notice = notice) },
                successMessage = "Impostazioni assegnatore salvate",
                errorMessagePrefix = "Errore salvataggio impostazioni",
                operation = { salvaImpostazioniAssegnatore(parsed) },
            )
        }
    }

    fun autoAssignSelectedProgram(programId: String, referenceDate: LocalDate, onSuccess: () -> Unit) {
        if (_uiState.value.isAutoAssigning) return
        scope.launch {
            var shouldReload = false
            _uiState.executeAsyncOperation(
                loadingUpdate = { it.copy(isAutoAssigning = true) },
                successUpdate = { state, result ->
                    shouldReload = true
                    val noticeText = buildString {
                        append("Autoassegnazione completata: ${result.assignedCount} slot assegnati")
                        if (result.unresolved.isNotEmpty()) {
                            append(" | ${result.unresolved.size} slot non assegnati")
                        }
                    }
                    state.copy(
                        isAutoAssigning = false,
                        autoAssignUnresolved = result.unresolved,
                        notice = successNotice(noticeText),
                    )
                },
                errorUpdate = { state, error ->
                    state.copy(
                        isAutoAssigning = false,
                        notice = errorNotice("Errore autoassegnazione: ${error.message}"),
                    )
                },
                operation = {
                    autoAssegnaProgramma(
                        programId = programId,
                        referenceDate = referenceDate,
                    )
                },
            )
            if (shouldReload) {
                onSuccess()
            }
        }
    }

    fun printSelectedProgram(programId: String) {
        if (_uiState.value.isPrintingProgram) return
        scope.launch {
            _uiState.executeAsyncOperation(
                loadingUpdate = { it.copy(isPrintingProgram = true) },
                successUpdate = { state, path ->
                    state.copy(
                        isPrintingProgram = false,
                        notice = successNotice("Programma stampato: ${path.fileName}"),
                    )
                },
                errorUpdate = { state, error ->
                    state.copy(
                        isPrintingProgram = false,
                        notice = errorNotice("Errore stampa programma: ${error.message}"),
                    )
                },
                operation = { stampaProgramma(programId) },
            )
        }
    }

    fun requestClearAssignments(programId: String, referenceDate: LocalDate) {
        if (_uiState.value.isClearingAssignments) return
        scope.launch {
            _uiState.executeAsyncOperation(
                loadingUpdate = { it.copy(isClearingAssignments = true) },
                successUpdate = { state, count ->
                    state.copy(isClearingAssignments = false, clearAssignmentsConfirm = count)
                },
                errorUpdate = { state, error ->
                    state.copy(
                        isClearingAssignments = false,
                        notice = errorNotice("Errore conteggio: ${error.message}"),
                    )
                },
                operation = { svuotaAssegnazioni.count(programId, referenceDate) },
            )
        }
    }

    fun confirmClearAssignments(programId: String, referenceDate: LocalDate, onSuccess: () -> Unit) {
        scope.launch {
            _uiState.update { it.copy(clearAssignmentsConfirm = null) }
            var shouldReload = false
            _uiState.executeAsyncOperation(
                loadingUpdate = { it.copy(isClearingAssignments = true) },
                successUpdate = { state, count ->
                    shouldReload = true
                    state.copy(
                        isClearingAssignments = false,
                        notice = successNotice("$count assegnazioni rimosse"),
                    )
                },
                errorUpdate = { state, error ->
                    state.copy(
                        isClearingAssignments = false,
                        notice = errorNotice("Errore svuotamento: ${error.message}"),
                    )
                },
                operation = { svuotaAssegnazioni.execute(programId, referenceDate) },
            )
            if (shouldReload) {
                onSuccess()
            }
        }
    }

    fun dismissClearAssignments() {
        _uiState.update { it.copy(clearAssignmentsConfirm = null) }
    }

    fun requestClearWeekAssignments(weekId: String, weekStartDate: LocalDate) {
        if (_uiState.value.isClearingWeekAssignments) return
        scope.launch {
            _uiState.executeAsyncOperation(
                loadingUpdate = { it.copy(isClearingWeekAssignments = true) },
                successUpdate = { state, count ->
                    state.copy(isClearingWeekAssignments = false, clearWeekAssignmentsConfirm = weekId to count)
                },
                errorUpdate = { state, error ->
                    state.copy(
                        isClearingWeekAssignments = false,
                        notice = errorNotice("Errore conteggio: ${error.message}"),
                    )
                },
                operation = { rimuoviAssegnazioniSettimana.count(weekStartDate) },
            )
        }
    }

    fun confirmClearWeekAssignments(weekStartDate: LocalDate, assignmentCount: Int, onSuccess: () -> Unit) {
        scope.launch {
            _uiState.update { it.copy(clearWeekAssignmentsConfirm = null) }
            _uiState.executeAsyncOperation(
                loadingUpdate = { it.copy(isClearingWeekAssignments = true) },
                successUpdate = { state, _ ->
                    state.copy(
                        isClearingWeekAssignments = false,
                        notice = successNotice("$assignmentCount assegnazioni rimosse"),
                    )
                },
                errorUpdate = { state, error ->
                    state.copy(
                        isClearingWeekAssignments = false,
                        notice = errorNotice("Errore rimozione assegnazioni: ${error.message}"),
                    )
                },
                operation = { rimuoviAssegnazioniSettimana(weekStartDate) },
            )
            onSuccess()
        }
    }

    fun dismissClearWeekAssignments() {
        _uiState.update { it.copy(clearWeekAssignmentsConfirm = null) }
    }

    private suspend fun loadAssignmentSettings() {
        val settings = caricaImpostazioniAssegnatore()
        _uiState.update {
            it.copy(
                assignmentSettings = AssignmentSettingsUiState(
                    strictCooldown = settings.strictCooldown,
                    leadWeight = settings.leadWeight.toString(),
                    assistWeight = settings.assistWeight.toString(),
                    leadCooldownWeeks = settings.leadCooldownWeeks.toString(),
                    assistCooldownWeeks = settings.assistCooldownWeeks.toString(),
                ),
            )
        }
    }

    private fun parseAssignmentSettings(state: AssignmentSettingsUiState): org.example.project.feature.assignments.application.AssignmentSettings? {
        val leadWeight = state.leadWeight.trim().toIntOrNull() ?: return null
        val assistWeight = state.assistWeight.trim().toIntOrNull() ?: return null
        val leadCooldown = state.leadCooldownWeeks.trim().toIntOrNull() ?: return null
        val assistCooldown = state.assistCooldownWeeks.trim().toIntOrNull() ?: return null
        if (leadWeight < 1 || assistWeight < 1 || leadCooldown < 0 || assistCooldown < 0) return null
        return org.example.project.feature.assignments.application.AssignmentSettings(
            strictCooldown = state.strictCooldown,
            leadWeight = leadWeight,
            assistWeight = assistWeight,
            leadCooldownWeeks = leadCooldown,
            assistCooldownWeeks = assistCooldown,
        )
    }
}

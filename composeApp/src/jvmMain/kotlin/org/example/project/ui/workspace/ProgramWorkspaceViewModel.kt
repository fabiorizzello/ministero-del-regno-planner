package org.example.project.ui.workspace

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.core.application.SharedWeekState
import org.example.project.core.domain.toMessage
import org.example.project.feature.assignments.application.AutoAssegnaProgrammaUseCase
import org.example.project.feature.assignments.application.AutoAssignUnresolvedSlot
import org.example.project.feature.assignments.application.CaricaImpostazioniAssegnatoreUseCase
import org.example.project.feature.assignments.application.SalvaImpostazioniAssegnatoreUseCase
import org.example.project.feature.output.application.StampaProgrammaUseCase
import org.example.project.feature.programs.application.CaricaProgrammiAttiviUseCase
import org.example.project.feature.programs.application.CreaProssimoProgrammaUseCase
import org.example.project.feature.programs.application.EliminaProgrammaFuturoUseCase
import org.example.project.feature.programs.application.GeneraSettimaneProgrammaUseCase
import org.example.project.feature.programs.domain.ProgramMonth
import org.example.project.feature.schemas.application.AggiornaSchemiUseCase
import org.example.project.feature.schemas.application.SchemaTemplateStore
import org.example.project.feature.weeklyparts.application.WeekPlanStore
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanStatus
import org.example.project.ui.components.FeedbackBannerKind
import org.example.project.ui.components.FeedbackBannerModel
import java.time.LocalDate

data class AssignmentSettingsUiState(
    val strictCooldown: Boolean = true,
    val leadWeight: String = "2",
    val assistWeight: String = "1",
    val leadCooldownWeeks: String = "4",
    val assistCooldownWeeks: String = "2",
)

data class ProgramWorkspaceUiState(
    val today: LocalDate = LocalDate.now(),
    val isLoading: Boolean = true,
    val currentProgram: ProgramMonth? = null,
    val futureProgram: ProgramMonth? = null,
    val selectedProgramId: String? = null,
    val selectedProgramWeeks: List<WeekPlan> = emptyList(),
    val isRefreshingSchemas: Boolean = false,
    val isCreatingProgram: Boolean = false,
    val isDeletingFutureProgram: Boolean = false,
    val isAutoAssigning: Boolean = false,
    val isPrintingProgram: Boolean = false,
    val isSavingAssignmentSettings: Boolean = false,
    val assignmentSettings: AssignmentSettingsUiState = AssignmentSettingsUiState(),
    val autoAssignUnresolved: List<AutoAssignUnresolvedSlot> = emptyList(),
    val notice: FeedbackBannerModel? = null,
) {
    val hasPrograms: Boolean get() = currentProgram != null || futureProgram != null
}

class ProgramWorkspaceViewModel(
    private val scope: CoroutineScope,
    private val sharedWeekState: SharedWeekState,
    private val caricaProgrammiAttivi: CaricaProgrammiAttiviUseCase,
    private val creaProssimoProgramma: CreaProssimoProgrammaUseCase,
    private val eliminaProgrammaFuturo: EliminaProgrammaFuturoUseCase,
    private val generaSettimaneProgramma: GeneraSettimaneProgrammaUseCase,
    private val autoAssegnaProgramma: AutoAssegnaProgrammaUseCase,
    private val stampaProgramma: StampaProgrammaUseCase,
    private val aggiornaSchemi: AggiornaSchemiUseCase,
    private val schemaTemplateStore: SchemaTemplateStore,
    private val weekPlanStore: WeekPlanStore,
    private val caricaImpostazioniAssegnatore: CaricaImpostazioniAssegnatoreUseCase,
    private val salvaImpostazioniAssegnatore: SalvaImpostazioniAssegnatoreUseCase,
) {
    private val _state = MutableStateFlow(ProgramWorkspaceUiState())
    val state: StateFlow<ProgramWorkspaceUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    fun onScreenEntered() {
        loadProgramsAndWeeks()
    }

    fun refreshPrograms() {
        loadProgramsAndWeeks()
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun setStrictCooldown(value: Boolean) {
        _state.update { it.copy(assignmentSettings = it.assignmentSettings.copy(strictCooldown = value)) }
    }

    fun setLeadWeight(value: String) {
        _state.update { it.copy(assignmentSettings = it.assignmentSettings.copy(leadWeight = value)) }
    }

    fun setAssistWeight(value: String) {
        _state.update { it.copy(assignmentSettings = it.assignmentSettings.copy(assistWeight = value)) }
    }

    fun setLeadCooldownWeeks(value: String) {
        _state.update { it.copy(assignmentSettings = it.assignmentSettings.copy(leadCooldownWeeks = value)) }
    }

    fun setAssistCooldownWeeks(value: String) {
        _state.update { it.copy(assignmentSettings = it.assignmentSettings.copy(assistCooldownWeeks = value)) }
    }

    fun saveAssignmentSettings() {
        if (_state.value.isSavingAssignmentSettings) return
        val parsed = parseAssignmentSettings(_state.value.assignmentSettings)
        if (parsed == null) {
            _state.update {
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
            _state.update { it.copy(isSavingAssignmentSettings = true) }
            runCatching { salvaImpostazioniAssegnatore(parsed) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            isSavingAssignmentSettings = false,
                            notice = FeedbackBannerModel(
                                "Impostazioni assegnatore salvate",
                                FeedbackBannerKind.SUCCESS,
                            ),
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isSavingAssignmentSettings = false,
                            notice = FeedbackBannerModel(
                                "Errore salvataggio impostazioni: ${error.message}",
                                FeedbackBannerKind.ERROR,
                            ),
                        )
                    }
                }
        }
    }

    fun selectProgram(programId: String) {
        _state.update { it.copy(selectedProgramId = programId) }
        loadWeeksForSelectedProgram()
    }

    fun navigateToWeek(week: WeekPlan) {
        sharedWeekState.navigateToWeek(week.weekStartDate)
    }

    fun refreshSchemas() {
        if (_state.value.isRefreshingSchemas) return
        scope.launch {
            _state.update { it.copy(isRefreshingSchemas = true) }
            aggiornaSchemi().fold(
                ifLeft = { error ->
                    _state.update {
                        it.copy(
                            isRefreshingSchemas = false,
                            notice = FeedbackBannerModel(error.toMessage(), FeedbackBannerKind.ERROR),
                        )
                    }
                },
                ifRight = { result ->
                    _state.update {
                        it.copy(
                            isRefreshingSchemas = false,
                            notice = FeedbackBannerModel(
                                "Schemi aggiornati: ${result.partTypesImported} tipi, ${result.weekTemplatesImported} settimane, anomalie idoneita: ${result.eligibilityAnomalies}",
                                FeedbackBannerKind.SUCCESS,
                            ),
                        )
                    }
                },
            )
        }
    }

    fun createNextProgram() {
        if (_state.value.isCreatingProgram) return
        scope.launch {
            if (schemaTemplateStore.isEmpty()) {
                _state.update {
                    it.copy(
                        notice = FeedbackBannerModel(
                            "Aggiorna schemi prima di creare il programma",
                            FeedbackBannerKind.ERROR,
                        ),
                    )
                }
                return@launch
            }

            _state.update { it.copy(isCreatingProgram = true) }
            creaProssimoProgramma().fold(
                ifLeft = { error ->
                    _state.update {
                        it.copy(
                            isCreatingProgram = false,
                            notice = FeedbackBannerModel(error.toMessage(), FeedbackBannerKind.ERROR),
                        )
                    }
                },
                ifRight = { program ->
                    generaSettimaneProgramma(program.id.value).fold(
                        ifLeft = { error ->
                            _state.update {
                                it.copy(
                                    isCreatingProgram = false,
                                    notice = FeedbackBannerModel(error.toMessage(), FeedbackBannerKind.ERROR),
                                )
                            }
                        },
                        ifRight = {
                            _state.update {
                                it.copy(
                                    isCreatingProgram = false,
                                    notice = FeedbackBannerModel(
                                        "Programma ${program.month}/${program.year} creato",
                                        FeedbackBannerKind.SUCCESS,
                                    ),
                                )
                            }
                            loadProgramsAndWeeks()
                        },
                    )
                },
            )
        }
    }

    fun deleteFutureProgram() {
        val futureId = _state.value.futureProgram?.id ?: return
        if (_state.value.isDeletingFutureProgram) return

        scope.launch {
            _state.update { it.copy(isDeletingFutureProgram = true) }
            eliminaProgrammaFuturo(futureId).fold(
                ifLeft = { error ->
                    _state.update {
                        it.copy(
                            isDeletingFutureProgram = false,
                            notice = FeedbackBannerModel(error.toMessage(), FeedbackBannerKind.ERROR),
                        )
                    }
                },
                ifRight = {
                    _state.update {
                        it.copy(
                            isDeletingFutureProgram = false,
                            notice = FeedbackBannerModel("Programma futuro eliminato", FeedbackBannerKind.SUCCESS),
                        )
                    }
                    loadProgramsAndWeeks()
                },
            )
        }
    }

    fun autoAssignSelectedProgram() {
        val programId = _state.value.selectedProgramId ?: return
        if (_state.value.isAutoAssigning) return
        scope.launch {
            _state.update { it.copy(isAutoAssigning = true) }
            runCatching {
                autoAssegnaProgramma(
                    programId = programId,
                    referenceDate = _state.value.today,
                )
            }.onSuccess { result ->
                val noticeText = buildString {
                    append("Autoassegnazione completata: ${result.assignedCount} slot assegnati")
                    if (result.unresolved.isNotEmpty()) {
                        append(" | ${result.unresolved.size} slot non assegnati")
                    }
                }
                _state.update {
                    it.copy(
                        isAutoAssigning = false,
                        autoAssignUnresolved = result.unresolved,
                        notice = FeedbackBannerModel(noticeText, FeedbackBannerKind.SUCCESS),
                    )
                }
                loadWeeksForSelectedProgram()
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isAutoAssigning = false,
                        notice = FeedbackBannerModel(
                            "Errore autoassegnazione: ${error.message}",
                            FeedbackBannerKind.ERROR,
                        ),
                    )
                }
            }
        }
    }

    fun printSelectedProgram() {
        val programId = _state.value.selectedProgramId ?: return
        if (_state.value.isPrintingProgram) return
        scope.launch {
            _state.update { it.copy(isPrintingProgram = true) }
            runCatching { stampaProgramma(programId) }
                .onSuccess { path ->
                    _state.update {
                        it.copy(
                            isPrintingProgram = false,
                            notice = FeedbackBannerModel(
                                "Programma stampato: ${path.fileName}",
                                FeedbackBannerKind.SUCCESS,
                            ),
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isPrintingProgram = false,
                            notice = FeedbackBannerModel(
                                "Errore stampa programma: ${error.message}",
                                FeedbackBannerKind.ERROR,
                            ),
                        )
                    }
                }
        }
    }

    private fun loadProgramsAndWeeks() {
        loadJob?.cancel()
        loadJob = scope.launch {
            _state.update { it.copy(isLoading = true) }
            runCatching {
                val today = _state.value.today
                val snapshot = caricaProgrammiAttivi(today)
                val selectedProgramId = when {
                    _state.value.selectedProgramId != null -> _state.value.selectedProgramId
                    snapshot.current != null -> snapshot.current.id.value
                    else -> snapshot.future?.id?.value
                }
                val weeks = selectedProgramId?.let { weekPlanStore.listByProgram(it) }.orEmpty()
                val assignmentSettings = caricaImpostazioniAssegnatore()
                ProgramWorkspaceUiState(
                    today = today,
                    isLoading = false,
                    currentProgram = snapshot.current,
                    futureProgram = snapshot.future,
                    selectedProgramId = selectedProgramId,
                    selectedProgramWeeks = weeks,
                    assignmentSettings = AssignmentSettingsUiState(
                        strictCooldown = assignmentSettings.strictCooldown,
                        leadWeight = assignmentSettings.leadWeight.toString(),
                        assistWeight = assignmentSettings.assistWeight.toString(),
                        leadCooldownWeeks = assignmentSettings.leadCooldownWeeks.toString(),
                        assistCooldownWeeks = assignmentSettings.assistCooldownWeeks.toString(),
                    ),
                    autoAssignUnresolved = _state.value.autoAssignUnresolved,
                    notice = _state.value.notice,
                )
            }.onSuccess { state ->
                _state.value = state
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        notice = FeedbackBannerModel(
                            "Errore caricamento cruscotto programma: ${error.message}",
                            FeedbackBannerKind.ERROR,
                        ),
                    )
                }
            }
        }
    }

    private fun loadWeeksForSelectedProgram() {
        scope.launch {
            val selectedProgramId = _state.value.selectedProgramId ?: return@launch
            val weeks = weekPlanStore.listByProgram(selectedProgramId)
            _state.update { it.copy(selectedProgramWeeks = weeks) }
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

val WeekPlan.statusLabel: String
    get() = when (status) {
        WeekPlanStatus.ACTIVE -> "Attiva"
        WeekPlanStatus.SKIPPED -> "Saltata"
    }

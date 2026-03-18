package org.example.project.ui.workspace

import com.russhwolf.settings.Settings
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
import org.example.project.feature.output.application.AssignmentTicketImage
import org.example.project.feature.output.application.AnnullaConsegnaUseCase
import org.example.project.feature.output.application.CaricaRiepilogoConsegneProgrammaUseCase
import org.example.project.feature.output.application.CaricaStatoConsegneUseCase
import org.example.project.feature.output.application.GeneraImmaginiAssegnazioni
import org.example.project.feature.output.application.PartAssignmentWarning
import org.example.project.feature.output.application.SegnaComInviatoUseCase
import org.example.project.feature.output.application.StampaProgrammaUseCase
import org.example.project.feature.output.domain.ProgramDeliverySnapshot
import org.example.project.feature.output.domain.SlipDeliveryInfo
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import org.example.project.core.domain.toMessage
import org.example.project.ui.components.FeedbackBannerKind
import org.example.project.ui.components.FeedbackBannerModel
import org.example.project.ui.components.errorNotice
import org.example.project.ui.components.executeAsyncOperation
import org.example.project.ui.components.executeEitherOperation
import org.example.project.ui.components.successNotice
import java.time.LocalDate

private const val KEY_SKIP_REMOVE_CONFIRM = "skip_assignment_removal_confirm"

data class AssignmentSettingsUiState(
    val strictCooldown: Boolean = true,
    val leadCooldownWeeks: String = "4",
    val assistCooldownWeeks: String = "2",
    val leadCooldownError: String? = null,
    val assistCooldownError: String? = null,
)

internal data class AssignmentManagementUiState(
    val isAutoAssigning: Boolean = false,
    val isPrintingProgram: Boolean = false,
    val isLoadingAssignmentTickets: Boolean = false,
    val isAssignmentTicketsDialogOpen: Boolean = false,
    val assignmentTickets: List<AssignmentTicketImage> = emptyList(),
    val assignmentPartWarnings: List<PartAssignmentWarning> = emptyList(),
    val assignmentTicketsError: String? = null,
    val deliveryStatus: Map<Pair<WeeklyPartId, WeekPlanId>, SlipDeliveryInfo> = emptyMap(),
    val isMarkingDelivered: Boolean = false,
    val isCancellingDelivery: Boolean = false,
    val isSavingAssignmentSettings: Boolean = false,
    val assignmentSettings: AssignmentSettingsUiState = AssignmentSettingsUiState(),
    val autoAssignUnresolved: List<AutoAssignUnresolvedSlot> = emptyList(),
    val isClearingAssignments: Boolean = false,
    val clearAssignmentsConfirm: Int? = null,
    val isClearingWeekAssignments: Boolean = false,
    val clearWeekAssignmentsConfirm: Pair<String, Int>? = null,
    val settingsSaved: Boolean = false,
    val skipRemoveConfirm: Boolean = false,
    val notice: FeedbackBannerModel? = null,
    val deliverySnapshot: ProgramDeliverySnapshot? = null,
    val isLoadingDeliverySnapshot: Boolean = false,
)

internal class AssignmentManagementViewModel(
    private val scope: CoroutineScope,
    private val autoAssegnaProgramma: AutoAssegnaProgrammaUseCase,
    private val caricaImpostazioniAssegnatore: CaricaImpostazioniAssegnatoreUseCase,
    private val salvaImpostazioniAssegnatore: SalvaImpostazioniAssegnatoreUseCase,
    private val svuotaAssegnazioni: SvuotaAssegnazioniProgrammaUseCase,
    private val rimuoviAssegnazioniSettimana: RimuoviAssegnazioniSettimanaUseCase,
    private val stampaProgramma: StampaProgrammaUseCase,
    private val generaImmaginiAssegnazioni: GeneraImmaginiAssegnazioni,
    private val settings: Settings,
    private val segnaComInviato: SegnaComInviatoUseCase,
    private val annullaConsegna: AnnullaConsegnaUseCase,
    private val caricaStatoConsegne: CaricaStatoConsegneUseCase,
    private val caricaRiepilogo: CaricaRiepilogoConsegneProgrammaUseCase,
) {
    private val _uiState = MutableStateFlow(AssignmentManagementUiState())
    val uiState: StateFlow<AssignmentManagementUiState> = _uiState.asStateFlow()

    private var currentProgramId: ProgramMonthId? = null
    private var currentReferenceDate: LocalDate? = null

    fun loadDeliverySummary(programId: ProgramMonthId, referenceDate: LocalDate) {
        currentProgramId = programId
        currentReferenceDate = referenceDate
        scope.launch {
            _uiState.update { it.copy(isLoadingDeliverySnapshot = true) }
            caricaRiepilogo(programId, referenceDate).fold(
                ifLeft = { _uiState.update { it.copy(deliverySnapshot = null, isLoadingDeliverySnapshot = false) } },
                ifRight = { snapshot ->
                    _uiState.update { it.copy(deliverySnapshot = snapshot, isLoadingDeliverySnapshot = false) }
                },
            )
        }
    }

    fun clearDeliverySummary() {
        currentProgramId = null
        currentReferenceDate = null
        _uiState.update { it.copy(deliverySnapshot = null, isLoadingDeliverySnapshot = false) }
    }

    fun refreshDeliverySummary() {
        val pid = currentProgramId ?: return
        val ref = currentReferenceDate ?: return
        loadDeliverySummary(pid, ref)
    }

    fun onScreenEntered() {
        scope.launch {
            loadAssignmentSettings()
        }
        _uiState.update { it.copy(skipRemoveConfirm = settings.getBoolean(KEY_SKIP_REMOVE_CONFIRM, false)) }
    }

    fun setSkipRemoveConfirm(value: Boolean) {
        settings.putBoolean(KEY_SKIP_REMOVE_CONFIRM, value)
        _uiState.update { it.copy(skipRemoveConfirm = value) }
    }

    fun dismissNotice() {
        _uiState.update { it.copy(notice = null) }
    }

    fun setNotice(notice: FeedbackBannerModel) {
        _uiState.update { it.copy(notice = notice) }
    }

    fun setStrictCooldown(value: Boolean) {
        _uiState.update { it.copy(assignmentSettings = it.assignmentSettings.copy(strictCooldown = value)) }
        saveAssignmentSettings()
    }

    fun dismissSettingsSaved() {
        _uiState.update { it.copy(settingsSaved = false) }
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
                        "Impostazioni non valide: usa numeri interi >= 0",
                        FeedbackBannerKind.ERROR,
                    ),
                )
            }
            return
        }

        scope.launch {
            _uiState.executeAsyncOperation(
                loadingUpdate = { it.copy(isSavingAssignmentSettings = true) },
                successUpdate = { state, _ -> state.copy(isSavingAssignmentSettings = false, settingsSaved = true) },
                errorUpdate = { state, error ->
                    state.copy(
                        isSavingAssignmentSettings = false,
                        notice = errorNotice("Errore salvataggio impostazioni: ${error.message}"),
                    )
                },
                operation = { salvaImpostazioniAssegnatore(parsed) },
            )
        }
    }

    fun autoAssignSelectedProgram(programId: ProgramMonthId, referenceDate: LocalDate, onSuccess: () -> Unit) {
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
                refreshDeliverySummary()
            }
        }
    }

    fun printSelectedProgram(programId: ProgramMonthId) {
        if (_uiState.value.isPrintingProgram) return
        scope.launch {
            _uiState.executeEitherOperation(
                loadingUpdate = { it.copy(isPrintingProgram = true) },
                successUpdate = { state, _ ->
                    state.copy(
                        isPrintingProgram = false,
                    )
                },
                errorUpdate = { state, error ->
                    state.copy(
                        isPrintingProgram = false,
                        notice = errorNotice(error.toMessage()),
                    )
                },
                operation = { stampaProgramma(programId) },
            )
        }
    }

    fun openAssignmentTickets(programId: ProgramMonthId) {
        if (_uiState.value.isLoadingAssignmentTickets) return
        scope.launch {
            _uiState.executeEitherOperation(
                loadingUpdate = {
                    it.copy(
                        isAssignmentTicketsDialogOpen = true,
                        isLoadingAssignmentTickets = true,
                        assignmentTickets = emptyList(),
                        assignmentTicketsError = null,
                        deliveryStatus = emptyMap(),
                    )
                },
                successUpdate = { state, result ->
                    val cutoff = currentReferenceDate ?: LocalDate.now()
                    val futureTickets = result.tickets.filter { it.weekStart >= cutoff }
                    val futureWarnings = result.warnings.filter { it.weekStart >= cutoff }
                    scope.launch { loadDeliveryStatus(futureTickets) }
                    state.copy(
                        isAssignmentTicketsDialogOpen = true,
                        isLoadingAssignmentTickets = false,
                        assignmentTickets = futureTickets,
                        assignmentPartWarnings = futureWarnings,
                        assignmentTicketsError = null,
                    )
                },
                errorUpdate = { state, error ->
                    state.copy(
                        isAssignmentTicketsDialogOpen = true,
                        isLoadingAssignmentTickets = false,
                        assignmentTickets = emptyList(),
                        assignmentPartWarnings = emptyList(),
                        assignmentTicketsError = error.toMessage(),
                        notice = errorNotice(error.toMessage()),
                    )
                },
                operation = { generaImmaginiAssegnazioni.generateProgramTickets(programId) },
            )
        }
    }

    fun closeAssignmentTicketsDialog() {
        _uiState.update {
            it.copy(
                isAssignmentTicketsDialogOpen = false,
                isLoadingAssignmentTickets = false,
                assignmentTickets = emptyList(),
                assignmentPartWarnings = emptyList(),
                assignmentTicketsError = null,
                deliveryStatus = emptyMap(),
            )
        }
    }

    fun requestClearAssignments(programId: ProgramMonthId, referenceDate: LocalDate) {
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

    fun confirmClearAssignments(programId: ProgramMonthId, referenceDate: LocalDate, onSuccess: () -> Unit) {
        scope.launch {
            _uiState.update { it.copy(clearAssignmentsConfirm = null) }
            var shouldReload = false
            _uiState.executeEitherOperation(
                loadingUpdate = { it.copy(isClearingAssignments = true) },
                successUpdate = { state, _ ->
                    shouldReload = true
                    state.copy(
                        isClearingAssignments = false,
                        notice = null,
                    )
                },
                errorUpdate = { state, error ->
                    state.copy(
                        isClearingAssignments = false,
                        notice = errorNotice("Errore svuotamento: ${error.toMessage()}"),
                    )
                },
                operation = { svuotaAssegnazioni.execute(programId, referenceDate) },
            )
            if (shouldReload) {
                onSuccess()
                refreshDeliverySummary()
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

    fun confirmClearWeekAssignments(weekStartDate: LocalDate, onSuccess: () -> Unit) {
        scope.launch {
            _uiState.update { it.copy(clearWeekAssignmentsConfirm = null) }
            var succeeded = false
            _uiState.executeEitherOperation(
                loadingUpdate = { it.copy(isClearingWeekAssignments = true) },
                successUpdate = { state, _ ->
                    succeeded = true
                    state.copy(
                        isClearingWeekAssignments = false,
                        notice = null,
                    )
                },
                errorUpdate = { state, error ->
                    state.copy(
                        isClearingWeekAssignments = false,
                        notice = errorNotice("Errore rimozione assegnazioni: ${error.toMessage()}"),
                    )
                },
                operation = { rimuoviAssegnazioniSettimana(weekStartDate) },
            )
            if (succeeded) {
                onSuccess()
                refreshDeliverySummary()
            }
        }
    }

    fun dismissClearWeekAssignments() {
        _uiState.update { it.copy(clearWeekAssignmentsConfirm = null) }
    }

    fun markAsDelivered(ticket: AssignmentTicketImage) {
        if (_uiState.value.isMarkingDelivered) return
        scope.launch {
            _uiState.update { it.copy(isMarkingDelivered = true) }
            segnaComInviato(
                weeklyPartId = ticket.weeklyPartId,
                weekPlanId = ticket.weekPlanId,
                studentName = ticket.fullName,
                assistantName = ticket.assistantName,
            ).fold(
                ifLeft = { error ->
                    _uiState.update {
                        it.copy(isMarkingDelivered = false, notice = errorNotice(error.toMessage()))
                    }
                },
                ifRight = {
                    loadDeliveryStatus(_uiState.value.assignmentTickets)
                    _uiState.update { it.copy(isMarkingDelivered = false) }
                    refreshDeliverySummary()
                }
            )
        }
    }

    fun cancelDelivery(ticket: AssignmentTicketImage) {
        if (_uiState.value.isCancellingDelivery) return
        scope.launch {
            _uiState.update { it.copy(isCancellingDelivery = true) }
            annullaConsegna(
                weeklyPartId = ticket.weeklyPartId,
                weekPlanId = ticket.weekPlanId,
            ).fold(
                ifLeft = { error ->
                    _uiState.update {
                        it.copy(isCancellingDelivery = false, notice = errorNotice(error.toMessage()))
                    }
                },
                ifRight = {
                    loadDeliveryStatus(_uiState.value.assignmentTickets)
                    _uiState.update { it.copy(isCancellingDelivery = false) }
                    refreshDeliverySummary()
                }
            )
        }
    }

    private suspend fun loadDeliveryStatus(tickets: List<AssignmentTicketImage>) {
        val weekPlanIds = tickets.map { it.weekPlanId }.distinct()
        if (weekPlanIds.isEmpty()) return
        try {
            val status = caricaStatoConsegne(weekPlanIds)
            _uiState.update { it.copy(deliveryStatus = status) }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(notice = errorNotice("Errore caricamento stato consegne: ${e.message}"))
            }
        }
    }

    private suspend fun loadAssignmentSettings() {
        try {
            val settings = caricaImpostazioniAssegnatore()
            _uiState.update {
                it.copy(
                    assignmentSettings = AssignmentSettingsUiState(
                        strictCooldown = settings.strictCooldown,
                        leadCooldownWeeks = settings.leadCooldownWeeks.toString(),
                        assistCooldownWeeks = settings.assistCooldownWeeks.toString(),
                    ),
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(notice = errorNotice("Errore caricamento impostazioni assegnatore: ${e.message}"))
            }
        }
    }

    private fun parseAssignmentSettings(state: AssignmentSettingsUiState): org.example.project.feature.assignments.application.AssignmentSettings? {
        val leadCooldown = state.leadCooldownWeeks.trim().toIntOrNull() ?: return null
        val assistCooldown = state.assistCooldownWeeks.trim().toIntOrNull() ?: return null
        if (leadCooldown < 0 || assistCooldown < 0) return null
        return org.example.project.feature.assignments.application.AssignmentSettings(
            strictCooldown = state.strictCooldown,
            leadCooldownWeeks = leadCooldown,
            assistCooldownWeeks = assistCooldown,
        )
    }
}

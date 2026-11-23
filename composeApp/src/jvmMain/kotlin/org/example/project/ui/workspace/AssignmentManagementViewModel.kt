package org.example.project.ui.workspace

import arrow.core.Either
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.feature.assignments.application.AssignmentSettings
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
import org.example.project.feature.output.application.GeneraImmaginiAssegnazioniUseCase
import org.example.project.feature.output.application.PartAssignmentWarning
import org.example.project.feature.output.application.SegnaComInviatoUseCase
import org.example.project.feature.output.application.StampaProgrammaUseCase
import org.example.project.feature.output.domain.ProgramDeliverySnapshot
import org.example.project.feature.output.domain.SlipDeliveryInfo
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import org.example.project.core.domain.DomainError
import org.example.project.core.domain.toMessage
import org.example.project.ui.components.FeedbackBannerKind
import org.example.project.ui.components.FeedbackBannerModel
import org.example.project.ui.components.errorNotice
import org.example.project.ui.components.executeAsyncOperation
import org.example.project.ui.components.executeEitherOperation
import org.example.project.ui.components.successNotice
import java.nio.file.Path
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

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
    val assignmentTicketsMonthLabel: String = "",
    val assignmentTickets: List<AssignmentTicketImage> = emptyList(),
    val historicalAssignmentTickets: List<AssignmentTicketImage> = emptyList(),
    val assignmentTicketsFolder: Path? = null,
    val assignmentPartWarnings: List<PartAssignmentWarning> = emptyList(),
    val historicalAssignmentPartWarnings: List<PartAssignmentWarning> = emptyList(),
    val assignmentTicketsError: String? = null,
    val deliveryStatus: Map<Pair<WeeklyPartId, WeekPlanId>, SlipDeliveryInfo> = emptyMap(),
    val markingTicketKey: Pair<WeeklyPartId, WeekPlanId>? = null,
    val cancellingTicketKey: Pair<WeeklyPartId, WeekPlanId>? = null,
    val ticketDeliveryError: String? = null,
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
) {
    val isMarkingDelivered: Boolean get() = markingTicketKey != null
    val isCancellingDelivery: Boolean get() = cancellingTicketKey != null
}

internal class AssignmentManagementViewModel(
    private val scope: CoroutineScope,
    private val autoAssegnaProgramma: AutoAssegnaProgrammaUseCase,
    private val caricaImpostazioniAssegnatore: CaricaImpostazioniAssegnatoreUseCase,
    private val salvaImpostazioniAssegnatore: SalvaImpostazioniAssegnatoreUseCase,
    private val svuotaAssegnazioni: SvuotaAssegnazioniProgrammaUseCase,
    private val rimuoviAssegnazioniSettimana: RimuoviAssegnazioniSettimanaUseCase,
    private val stampaProgramma: StampaProgrammaUseCase,
    private val generaImmaginiAssegnazioni: GeneraImmaginiAssegnazioniUseCase,
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
    private var pendingAssignmentSettings: AssignmentSettings? = null
    private var ticketDialogVersion = 0L
    private var deliveryStatusVersion = 0L

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

        if (_uiState.value.isSavingAssignmentSettings) {
            pendingAssignmentSettings = parsed
            return
        }
        _uiState.update { it.copy(isSavingAssignmentSettings = true, settingsSaved = false) }
        scope.launch {
            var settingsToSave = pendingAssignmentSettings ?: parsed
            pendingAssignmentSettings = null
            while (true) {
                val result = salvaImpostazioniAssegnatore(settingsToSave)
                val pending = pendingAssignmentSettings
                pendingAssignmentSettings = null
                if (pending != null && (pending != settingsToSave || result.isLeft())) {
                    settingsToSave = pending
                    continue
                }
                _uiState.update { state ->
                    result.fold(
                        ifLeft = { error ->
                            state.copy(isSavingAssignmentSettings = false, notice = errorNotice(error.toMessage()))
                        },
                        ifRight = {
                            state.copy(isSavingAssignmentSettings = false, settingsSaved = true)
                        },
                    )
                }
                break
            }
        }
    }

    fun autoAssignSelectedProgram(programId: ProgramMonthId, referenceDate: LocalDate, onSuccess: () -> Unit) {
        if (_uiState.value.isAutoAssigning) return
        scope.launch {
            var shouldReload = false
            _uiState.executeEitherOperation(
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
                        notice = errorNotice(error.toMessage()),
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

    fun openAssignmentTickets(programId: ProgramMonthId, monthLabel: String = "") {
        if (_uiState.value.isLoadingAssignmentTickets) return
        val dialogVersion = ++ticketDialogVersion
        val cutoff = currentReferenceDate
            ?.takeIf { currentProgramId == programId }
            ?: LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        _uiState.update {
            it.copy(
                isAssignmentTicketsDialogOpen = true,
                assignmentTicketsMonthLabel = monthLabel,
                isLoadingAssignmentTickets = true,
                assignmentTickets = emptyList(),
                historicalAssignmentTickets = emptyList(),
                assignmentTicketsFolder = null,
                assignmentPartWarnings = emptyList(),
                historicalAssignmentPartWarnings = emptyList(),
                assignmentTicketsError = null,
                deliveryStatus = emptyMap(),
                ticketDeliveryError = null,
            )
        }
        scope.launch {
            val generated = generaImmaginiAssegnazioni.generateProgramTickets(programId, cutoff)
            if (dialogVersion != ticketDialogVersion) return@launch
            generated.fold(
                ifLeft = { error ->
                    _uiState.update { state ->
                        state.copy(
                            isLoadingAssignmentTickets = false,
                            assignmentTicketsError = error.toMessage(),
                            notice = errorNotice(error.toMessage()),
                        )
                    }
                },
                ifRight = { result ->
                    val (currentTickets, pastTickets) = result.tickets.partition { it.weekStart >= cutoff }
                    val (currentWarnings, pastWarnings) = result.warnings.partition { it.weekStart >= cutoff }
                    _uiState.update { state ->
                        state.copy(
                            assignmentTickets = currentTickets,
                            historicalAssignmentTickets = pastTickets,
                            assignmentTicketsFolder = result.exportFolder,
                            assignmentPartWarnings = currentWarnings,
                            historicalAssignmentPartWarnings = pastWarnings,
                        )
                    }
                    loadDeliveryStatus(dialogVersion, result.tickets)
                    if (dialogVersion == ticketDialogVersion) {
                        _uiState.update { it.copy(isLoadingAssignmentTickets = false) }
                    }
                },
            )
        }
    }

    fun closeAssignmentTicketsDialog() {
        ++ticketDialogVersion
        _uiState.update {
            it.copy(
                isAssignmentTicketsDialogOpen = false,
                assignmentTicketsMonthLabel = "",
                isLoadingAssignmentTickets = false,
                assignmentTickets = emptyList(),
                historicalAssignmentTickets = emptyList(),
                assignmentTicketsFolder = null,
                assignmentPartWarnings = emptyList(),
                historicalAssignmentPartWarnings = emptyList(),
                assignmentTicketsError = null,
                deliveryStatus = emptyMap(),
                ticketDeliveryError = null,
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
        runTicketDeliveryAction(ticket, cancelling = false) {
            segnaComInviato(
                weeklyPartId = ticket.weeklyPartId,
                weekPlanId = ticket.weekPlanId,
                studentName = ticket.fullName,
                assistantName = ticket.assistantName,
            )
        }
    }

    fun cancelDelivery(ticket: AssignmentTicketImage) {
        runTicketDeliveryAction(ticket, cancelling = true) {
            annullaConsegna(
                weeklyPartId = ticket.weeklyPartId,
                weekPlanId = ticket.weekPlanId,
            )
        }
    }

    private fun runTicketDeliveryAction(
        ticket: AssignmentTicketImage,
        cancelling: Boolean,
        operation: suspend () -> Either<DomainError, Unit>,
    ) {
        val state = _uiState.value
        if (state.isMarkingDelivered || state.isCancellingDelivery) return
        val dialogVersion = ticketDialogVersion
        val ticketKey = ticket.weeklyPartId to ticket.weekPlanId
        _uiState.update {
            it.copy(
                markingTicketKey = ticketKey.takeUnless { cancelling },
                cancellingTicketKey = ticketKey.takeIf { cancelling },
                ticketDeliveryError = null,
            )
        }
        scope.launch {
            try {
                operation().fold(
                    ifLeft = { error ->
                        if (dialogVersion == ticketDialogVersion && _uiState.value.isAssignmentTicketsDialogOpen) {
                            val action = if (cancelling) "annullare" else "registrare"
                            _uiState.update {
                                it.copy(ticketDeliveryError = "Impossibile $action l'invio per ${ticket.fullName}. ${error.toMessage()}")
                            }
                        }
                    },
                    ifRight = {
                        val current = _uiState.value
                        val tickets = current.assignmentTickets + current.historicalAssignmentTickets
                        if (tickets.any { it.weeklyPartId == ticket.weeklyPartId && it.weekPlanId == ticket.weekPlanId }) {
                            loadDeliveryStatus(ticketDialogVersion, tickets)
                        }
                        refreshDeliverySummary()
                    },
                )
            } finally {
                _uiState.update { it.copy(markingTicketKey = null, cancellingTicketKey = null) }
            }
        }
    }

    private suspend fun loadDeliveryStatus(dialogVersion: Long, tickets: List<AssignmentTicketImage>) {
        val weekPlanIds = tickets.map { it.weekPlanId }.distinct()
        if (weekPlanIds.isEmpty() || dialogVersion != ticketDialogVersion) return
        val requestVersion = ++deliveryStatusVersion
        try {
            val status = caricaStatoConsegne(weekPlanIds)
            if (dialogVersion == ticketDialogVersion && requestVersion == deliveryStatusVersion) {
                _uiState.update { it.copy(deliveryStatus = status) }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (dialogVersion == ticketDialogVersion && requestVersion == deliveryStatusVersion) {
                _uiState.update {
                    it.copy(ticketDeliveryError = "Impossibile leggere lo stato degli invii. ${error.message.orEmpty()}")
                }
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

    private fun parseAssignmentSettings(state: AssignmentSettingsUiState): AssignmentSettings? {
        val leadCooldown = state.leadCooldownWeeks.trim().toIntOrNull() ?: return null
        val assistCooldown = state.assistCooldownWeeks.trim().toIntOrNull() ?: return null
        if (leadCooldown < 0 || assistCooldown < 0) return null
        return AssignmentSettings(
            strictCooldown = state.strictCooldown,
            leadCooldownWeeks = leadCooldown,
            assistCooldownWeeks = assistCooldown,
        )
    }
}

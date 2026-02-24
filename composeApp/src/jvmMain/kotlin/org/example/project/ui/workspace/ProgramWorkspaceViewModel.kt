package org.example.project.ui.workspace

import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.core.domain.toMessage
import org.example.project.feature.assignments.application.AssegnaPersonaUseCase
import org.example.project.feature.assignments.application.AutoAssegnaProgrammaUseCase
import org.example.project.feature.assignments.application.AutoAssignUnresolvedSlot
import org.example.project.feature.assignments.application.CaricaAssegnazioniUseCase
import org.example.project.feature.assignments.application.CaricaImpostazioniAssegnatoreUseCase
import org.example.project.feature.assignments.application.RimuoviAssegnazioneUseCase
import org.example.project.feature.assignments.application.SalvaImpostazioniAssegnatoreUseCase
import org.example.project.feature.assignments.application.SuggerisciProclamatoriUseCase
import org.example.project.feature.assignments.application.SvuotaAssegnazioniProgrammaUseCase
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.assignments.domain.AssignmentWithPerson
import org.example.project.feature.assignments.domain.SuggestedProclamatore
import org.example.project.feature.output.application.StampaProgrammaUseCase
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.programs.application.AggiornaProgrammaDaSchemiUseCase
import org.example.project.feature.programs.application.CaricaProgrammiAttiviUseCase
import org.example.project.feature.programs.application.CreaProssimoProgrammaUseCase
import org.example.project.feature.programs.application.EliminaProgrammaFuturoUseCase
import org.example.project.feature.programs.application.GeneraSettimaneProgrammaUseCase
import org.example.project.feature.programs.application.SchemaRefreshReport
import org.example.project.feature.programs.domain.ProgramMonth
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.schemas.application.AggiornaSchemiResult
import org.example.project.feature.schemas.application.AggiornaSchemiUseCase
import org.example.project.feature.schemas.application.SchemaTemplateStore
import org.example.project.feature.weeklyparts.application.CercaTipiParteUseCase
import org.example.project.feature.weeklyparts.application.WeekPlanStore
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeekPlanStatus
import org.example.project.feature.weeklyparts.domain.WeeklyPart
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import org.example.project.ui.components.FeedbackBannerKind
import org.example.project.ui.components.FeedbackBannerModel
import org.example.project.ui.components.formatMonthYearLabel
import org.example.project.ui.components.formatWeekRangeLabel
import java.time.LocalDate
import java.util.UUID

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
    val selectedProgramAssignments: Map<String, List<AssignmentWithPerson>> = emptyMap(),
    val partTypes: List<PartType> = emptyList(),
    val isRefreshingSchemas: Boolean = false,
    val isCreatingProgram: Boolean = false,
    val isDeletingSelectedProgram: Boolean = false,
    val isAutoAssigning: Boolean = false,
    val isPrintingProgram: Boolean = false,
    val isRefreshingProgramFromSchemas: Boolean = false,
    val isSavingAssignmentSettings: Boolean = false,
    val assignmentSettings: AssignmentSettingsUiState = AssignmentSettingsUiState(),
    val autoAssignUnresolved: List<AutoAssignUnresolvedSlot> = emptyList(),
    val isClearingAssignments: Boolean = false,
    val clearAssignmentsConfirm: Int? = null,
    val isClearingWeekAssignments: Boolean = false,
    val clearWeekAssignmentsConfirm: Pair<String, Int>? = null,
    val schemaRefreshPreview: SchemaRefreshReport? = null,
    val futureNeedsSchemaRefresh: Boolean = false,
    val partEditorWeekId: String? = null,
    val partEditorParts: List<WeeklyPart> = emptyList(),
    val isSavingPartEditor: Boolean = false,
    val pickerWeekStartDate: LocalDate? = null,
    val pickerWeeklyPartId: WeeklyPartId? = null,
    val pickerSlot: Int? = null,
    val pickerSearchTerm: String = "",
    val pickerSortGlobal: Boolean = true,
    val pickerSuggestions: List<SuggestedProclamatore> = emptyList(),
    val isPickerLoading: Boolean = false,
    val isAssigning: Boolean = false,
    val isRemovingAssignment: Boolean = false,
    val notice: FeedbackBannerModel? = null,
) {
    val hasPrograms: Boolean get() = currentProgram != null || futureProgram != null
    val canDeleteSelectedProgram: Boolean get() = selectedProgramId != null && selectedProgramId == futureProgram?.id?.value
    val isPickerOpen: Boolean get() = pickerWeekStartDate != null && pickerWeeklyPartId != null && pickerSlot != null
    val isPartEditorOpen: Boolean get() = partEditorWeekId != null
    val editablePartTypes: List<PartType> get() = partTypes.filterNot { it.fixed }
}

class ProgramWorkspaceViewModel(
    private val scope: CoroutineScope,
    private val caricaProgrammiAttivi: CaricaProgrammiAttiviUseCase,
    private val creaProssimoProgramma: CreaProssimoProgrammaUseCase,
    private val eliminaProgrammaFuturo: EliminaProgrammaFuturoUseCase,
    private val generaSettimaneProgramma: GeneraSettimaneProgrammaUseCase,
    private val autoAssegnaProgramma: AutoAssegnaProgrammaUseCase,
    private val stampaProgramma: StampaProgrammaUseCase,
    private val aggiornaSchemi: AggiornaSchemiUseCase,
    private val aggiornaProgrammaDaSchemi: AggiornaProgrammaDaSchemiUseCase,
    private val schemaTemplateStore: SchemaTemplateStore,
    private val weekPlanStore: WeekPlanStore,
    private val cercaTipiParte: CercaTipiParteUseCase,
    private val caricaAssegnazioni: CaricaAssegnazioniUseCase,
    private val assegnaPersona: AssegnaPersonaUseCase,
    private val rimuoviAssegnazione: RimuoviAssegnazioneUseCase,
    private val suggerisciProclamatori: SuggerisciProclamatoriUseCase,
    private val caricaImpostazioniAssegnatore: CaricaImpostazioniAssegnatoreUseCase,
    private val salvaImpostazioniAssegnatore: SalvaImpostazioniAssegnatoreUseCase,
    private val svuotaAssegnazioni: SvuotaAssegnazioniProgrammaUseCase,
    private val settings: Settings,
) {
    private val _state = MutableStateFlow(ProgramWorkspaceUiState())
    val state: StateFlow<ProgramWorkspaceUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var pickerSuggestionsJob: Job? = null

    fun onScreenEntered() {
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
        _state.update {
            it.copy(
                selectedProgramId = programId,
                partEditorWeekId = null,
                partEditorParts = emptyList(),
                isSavingPartEditor = false,
                pickerWeekStartDate = null,
                pickerWeeklyPartId = null,
                pickerSlot = null,
                pickerSearchTerm = "",
                pickerSuggestions = emptyList(),
                isPickerLoading = false,
            )
        }
        loadWeeksForSelectedProgram()
    }

    fun openPartEditor(week: WeekPlan) {
        if (!canMutateWeek(week)) return
        _state.update {
            it.copy(
                partEditorWeekId = week.id.value,
                partEditorParts = week.parts.sortedBy { part -> part.sortOrder },
                isSavingPartEditor = false,
            )
        }
    }

    fun dismissPartEditor() {
        _state.update {
            it.copy(
                partEditorWeekId = null,
                partEditorParts = emptyList(),
                isSavingPartEditor = false,
            )
        }
    }

    fun addPartToEditor(partTypeId: PartTypeId) {
        val current = _state.value
        val partType = current.editablePartTypes.firstOrNull { it.id == partTypeId } ?: return
        val parts = current.partEditorParts
        val newPart = WeeklyPart(
            id = WeeklyPartId(UUID.randomUUID().toString()),
            partType = partType,
            sortOrder = parts.size,
        )
        _state.update { it.copy(partEditorParts = parts + newPart) }
    }

    fun movePartInEditor(fromIndex: Int, toIndex: Int) {
        val parts = _state.value.partEditorParts.toMutableList()
        if (fromIndex !in parts.indices || toIndex !in parts.indices || fromIndex == toIndex) return
        val moved = parts.removeAt(fromIndex)
        parts.add(toIndex, moved)
        _state.update {
            it.copy(
                partEditorParts = parts.mapIndexed { index, part -> part.copy(sortOrder = index) },
            )
        }
    }

    fun removePartFromEditor(partId: WeeklyPartId) {
        val parts = _state.value.partEditorParts
        val target = parts.firstOrNull { it.id == partId } ?: return
        if (target.partType.fixed) return
        _state.update {
            it.copy(
                partEditorParts = parts
                    .filter { part -> part.id != partId }
                    .mapIndexed { index, part -> part.copy(sortOrder = index) },
            )
        }
    }

    fun savePartEditor() {
        val current = _state.value
        val weekId = current.partEditorWeekId ?: return
        if (current.isSavingPartEditor) return
        val orderedParts = current.partEditorParts.sortedBy { it.sortOrder }
        if (orderedParts.isEmpty()) {
            _state.update {
                it.copy(notice = FeedbackBannerModel("Aggiungi almeno una parte", FeedbackBannerKind.ERROR))
            }
            return
        }

        scope.launch {
            _state.update { it.copy(isSavingPartEditor = true) }
            runCatching {
                weekPlanStore.replaceAllParts(
                    WeekPlanId(weekId),
                    orderedParts.map { it.partType.id },
                )
            }.onSuccess {
                _state.update {
                    it.copy(
                        partEditorWeekId = null,
                        partEditorParts = emptyList(),
                        isSavingPartEditor = false,
                        notice = FeedbackBannerModel("Parti settimana aggiornate", FeedbackBannerKind.SUCCESS),
                    )
                }
                loadWeeksForSelectedProgram()
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isSavingPartEditor = false,
                        notice = FeedbackBannerModel("Errore salvataggio parti: ${error.message}", FeedbackBannerKind.ERROR),
                    )
                }
            }
        }
    }

    fun openPersonPicker(weekStartDate: LocalDate, weeklyPartId: WeeklyPartId, slot: Int) {
        val week = _state.value.selectedProgramWeeks.find { it.weekStartDate == weekStartDate } ?: return
        if (!canMutateWeek(week)) return

        _state.update {
            it.copy(
                pickerWeekStartDate = weekStartDate,
                pickerWeeklyPartId = weeklyPartId,
                pickerSlot = slot,
                pickerSearchTerm = "",
                pickerSortGlobal = true,
                pickerSuggestions = emptyList(),
                isPickerLoading = true,
            )
        }
        loadSuggestions()
    }

    fun closePersonPicker() {
        _state.update {
            it.copy(
                pickerWeekStartDate = null,
                pickerWeeklyPartId = null,
                pickerSlot = null,
                pickerSearchTerm = "",
                pickerSuggestions = emptyList(),
                isPickerLoading = false,
            )
        }
    }

    fun setPickerSearchTerm(term: String) {
        _state.update { it.copy(pickerSearchTerm = term) }
    }

    fun togglePickerSort() {
        _state.update { it.copy(pickerSortGlobal = !it.pickerSortGlobal) }
    }

    fun confirmAssignment(personId: ProclamatoreId) {
        if (_state.value.isAssigning) return
        val pickerWeekStartDate = _state.value.pickerWeekStartDate ?: return
        val pickerWeeklyPartId = _state.value.pickerWeeklyPartId ?: return
        val pickerSlot = _state.value.pickerSlot ?: return

        _state.update { it.copy(isAssigning = true) }
        scope.launch {
            try {
                assegnaPersona(
                    weekStartDate = pickerWeekStartDate,
                    weeklyPartId = pickerWeeklyPartId,
                    personId = personId,
                    slot = pickerSlot,
                ).fold(
                    ifLeft = { error ->
                        _state.update { state ->
                            state.copy(
                                notice = FeedbackBannerModel(error.toMessage(), FeedbackBannerKind.ERROR),
                            )
                        }
                    },
                    ifRight = {
                        closePersonPicker()
                        loadWeeksForSelectedProgram()
                    },
                )
            } finally {
                _state.update { it.copy(isAssigning = false) }
            }
        }
    }

    fun removeAssignment(assignmentId: AssignmentId) {
        if (_state.value.isRemovingAssignment) return
        scope.launch {
            _state.update { it.copy(isRemovingAssignment = true) }
            try {
                rimuoviAssegnazione(assignmentId).fold(
                    ifLeft = { error ->
                        _state.update {
                            it.copy(
                                notice = FeedbackBannerModel(error.toMessage(), FeedbackBannerKind.ERROR),
                            )
                        }
                    },
                    ifRight = {
                        _state.update {
                            it.copy(
                                notice = FeedbackBannerModel("Assegnazione rimossa", FeedbackBannerKind.SUCCESS),
                            )
                        }
                        loadWeeksForSelectedProgram()
                    },
                )
            } finally {
                _state.update { it.copy(isRemovingAssignment = false) }
            }
        }
    }

    fun reactivateWeek(week: WeekPlan) {
        scope.launch {
            runCatching {
                weekPlanStore.updateWeekStatus(week.id, WeekPlanStatus.ACTIVE)
            }.onSuccess {
                val weekLabel = formatWeekRangeLabel(week.weekStartDate, week.weekStartDate.plusDays(6))
                _state.update {
                    it.copy(notice = FeedbackBannerModel("Settimana $weekLabel riattivata", FeedbackBannerKind.SUCCESS))
                }
                loadWeeksForSelectedProgram()
            }.onFailure { error ->
                _state.update {
                    it.copy(notice = FeedbackBannerModel("Errore riattivazione: ${error.message}", FeedbackBannerKind.ERROR))
                }
            }
        }
    }

    fun refreshSchemasAndProgram() {
        if (_state.value.isRefreshingSchemas || _state.value.isRefreshingProgramFromSchemas) return
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
                                buildSchemaUpdateNotice(result),
                                FeedbackBannerKind.SUCCESS,
                            ),
                        )
                    }
                    if (_state.value.selectedProgramId != null) {
                        refreshProgramFromSchemas()
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
                                        "Programma ${formatMonthYearLabel(program.month, program.year)} creato",
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

    fun deleteSelectedProgram() {
        val selectedProgramId = _state.value.selectedProgramId ?: return
        if (_state.value.isDeletingSelectedProgram) return

        scope.launch {
            _state.update { it.copy(isDeletingSelectedProgram = true) }
            eliminaProgrammaFuturo(ProgramMonthId(selectedProgramId), _state.value.today).fold(
                ifLeft = { error ->
                    _state.update {
                        it.copy(
                            isDeletingSelectedProgram = false,
                            notice = FeedbackBannerModel(error.toMessage(), FeedbackBannerKind.ERROR),
                        )
                    }
                },
                ifRight = {
                    _state.update {
                        it.copy(
                            isDeletingSelectedProgram = false,
                            notice = FeedbackBannerModel("Programma selezionato eliminato", FeedbackBannerKind.SUCCESS),
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

    fun refreshProgramFromSchemas() {
        val programId = _state.value.selectedProgramId ?: return
        if (_state.value.isRefreshingProgramFromSchemas) return
        scope.launch {
            _state.update { it.copy(isRefreshingProgramFromSchemas = true) }
            aggiornaProgrammaDaSchemi(programId, _state.value.today, dryRun = true).fold(
                ifLeft = { error ->
                    _state.update {
                        it.copy(
                            isRefreshingProgramFromSchemas = false,
                            notice = FeedbackBannerModel(error.toMessage(), FeedbackBannerKind.ERROR),
                        )
                    }
                },
                ifRight = { preview ->
                    _state.update {
                        it.copy(isRefreshingProgramFromSchemas = false, schemaRefreshPreview = preview)
                    }
                },
            )
        }
    }

    fun confirmSchemaRefresh() {
        val programId = _state.value.selectedProgramId ?: return
        scope.launch {
            _state.update { it.copy(schemaRefreshPreview = null, isRefreshingProgramFromSchemas = true) }
            aggiornaProgrammaDaSchemi(programId, _state.value.today, dryRun = false).fold(
                ifLeft = { error ->
                    _state.update {
                        it.copy(
                            isRefreshingProgramFromSchemas = false,
                            notice = FeedbackBannerModel(error.toMessage(), FeedbackBannerKind.ERROR),
                        )
                    }
                },
                ifRight = { report ->
                    _state.update {
                        it.copy(
                            isRefreshingProgramFromSchemas = false,
                            notice = FeedbackBannerModel(
                                "Programma aggiornato: ${report.weeksUpdated} settimane, ${report.assignmentsPreserved} preservate, ${report.assignmentsRemoved} rimosse",
                                FeedbackBannerKind.SUCCESS,
                            ),
                        )
                    }
                    loadWeeksForSelectedProgram()
                },
            )
        }
    }

    fun dismissSchemaRefresh() {
        _state.update { it.copy(schemaRefreshPreview = null) }
    }

    fun requestClearAssignments() {
        val programId = _state.value.selectedProgramId ?: return
        if (_state.value.isClearingAssignments) return
        scope.launch {
            _state.update { it.copy(isClearingAssignments = true) }
            runCatching {
                svuotaAssegnazioni.count(programId, _state.value.today)
            }.onSuccess { count ->
                _state.update {
                    it.copy(isClearingAssignments = false, clearAssignmentsConfirm = count)
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isClearingAssignments = false,
                        notice = FeedbackBannerModel("Errore conteggio: ${error.message}", FeedbackBannerKind.ERROR),
                    )
                }
            }
        }
    }

    fun confirmClearAssignments() {
        val programId = _state.value.selectedProgramId ?: return
        scope.launch {
            _state.update { it.copy(clearAssignmentsConfirm = null, isClearingAssignments = true) }
            runCatching {
                svuotaAssegnazioni.execute(programId, _state.value.today)
            }.onSuccess { count ->
                _state.update {
                    it.copy(
                        isClearingAssignments = false,
                        notice = FeedbackBannerModel("$count assegnazioni rimosse", FeedbackBannerKind.SUCCESS),
                    )
                }
                loadWeeksForSelectedProgram()
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isClearingAssignments = false,
                        notice = FeedbackBannerModel("Errore svuotamento: ${error.message}", FeedbackBannerKind.ERROR),
                    )
                }
            }
        }
    }

    fun dismissClearAssignments() {
        _state.update { it.copy(clearAssignmentsConfirm = null) }
    }

    private fun canMutateWeek(week: WeekPlan): Boolean =
        week.weekStartDate >= _state.value.today && week.status == WeekPlanStatus.ACTIVE

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
                val assignmentsByWeek = loadAssignmentsByWeek(weeks)
                val partTypes = runCatching { cercaTipiParte() }.getOrDefault(emptyList())
                val assignmentSettings = caricaImpostazioniAssegnatore()
                val lastSchemaImport = settings.getStringOrNull("last_schema_import_at")
                    ?.let { runCatching { java.time.LocalDateTime.parse(it) }.getOrNull() }
                val futureNeedsRefresh = snapshot.future != null && lastSchemaImport != null &&
                    (snapshot.future.templateAppliedAt == null || snapshot.future.templateAppliedAt < lastSchemaImport)

                val validPartEditorWeek = _state.value.partEditorWeekId?.takeIf { id ->
                    weeks.any { week -> week.id.value == id }
                }
                val validPickerPart = _state.value.pickerWeeklyPartId?.takeIf { partId ->
                    weeks.any { week -> week.parts.any { part -> part.id == partId } }
                }

                ProgramWorkspaceUiState(
                    today = today,
                    isLoading = false,
                    currentProgram = snapshot.current,
                    futureProgram = snapshot.future,
                    selectedProgramId = selectedProgramId,
                    selectedProgramWeeks = weeks,
                    selectedProgramAssignments = assignmentsByWeek,
                    partTypes = partTypes,
                    futureNeedsSchemaRefresh = futureNeedsRefresh,
                    assignmentSettings = AssignmentSettingsUiState(
                        strictCooldown = assignmentSettings.strictCooldown,
                        leadWeight = assignmentSettings.leadWeight.toString(),
                        assistWeight = assignmentSettings.assistWeight.toString(),
                        leadCooldownWeeks = assignmentSettings.leadCooldownWeeks.toString(),
                        assistCooldownWeeks = assignmentSettings.assistCooldownWeeks.toString(),
                    ),
                    autoAssignUnresolved = _state.value.autoAssignUnresolved,
                    partEditorWeekId = validPartEditorWeek,
                    partEditorParts = if (validPartEditorWeek != null) _state.value.partEditorParts else emptyList(),
                    isSavingPartEditor = false,
                    pickerWeekStartDate = if (validPickerPart != null) _state.value.pickerWeekStartDate else null,
                    pickerWeeklyPartId = validPickerPart,
                    pickerSlot = if (validPickerPart != null) _state.value.pickerSlot else null,
                    pickerSearchTerm = _state.value.pickerSearchTerm,
                    pickerSortGlobal = _state.value.pickerSortGlobal,
                    pickerSuggestions = _state.value.pickerSuggestions,
                    isPickerLoading = _state.value.isPickerLoading,
                    isAssigning = _state.value.isAssigning,
                    isRemovingAssignment = _state.value.isRemovingAssignment,
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

    private fun buildSchemaUpdateNotice(result: AggiornaSchemiResult): String {
        val base = "Schemi aggiornati: ${result.partTypesImported} tipi, ${result.weekTemplatesImported} settimane"
        return if (result.eligibilityAnomalies > 0) {
            "$base. Alcune persone potrebbero richiedere una verifica manuale."
        } else {
            base
        }
    }

    private fun loadWeeksForSelectedProgram() {
        scope.launch {
            val selectedProgramId = _state.value.selectedProgramId ?: return@launch
            val weeks = weekPlanStore.listByProgram(selectedProgramId)
            val assignmentsByWeek = loadAssignmentsByWeek(weeks)
            _state.update {
                val validPicker = it.pickerWeeklyPartId?.takeIf { partId -> weeks.any { week -> week.parts.any { p -> p.id == partId } } }
                val validPartEditorWeek = it.partEditorWeekId?.takeIf { id -> weeks.any { week -> week.id.value == id } }
                it.copy(
                    selectedProgramWeeks = weeks,
                    selectedProgramAssignments = assignmentsByWeek,
                    partEditorWeekId = validPartEditorWeek,
                    partEditorParts = if (validPartEditorWeek != null) it.partEditorParts else emptyList(),
                    pickerWeeklyPartId = validPicker,
                    pickerWeekStartDate = if (validPicker != null) it.pickerWeekStartDate else null,
                    pickerSlot = if (validPicker != null) it.pickerSlot else null,
                )
            }
        }
    }

    private suspend fun loadAssignmentsByWeek(weeks: List<WeekPlan>): Map<String, List<AssignmentWithPerson>> = coroutineScope {
        if (weeks.isEmpty()) return@coroutineScope emptyMap()
        val deferredByWeekId = weeks.associate { week ->
            week.id.value to async {
                runCatching { caricaAssegnazioni(week.weekStartDate) }.getOrElse { emptyList() }
            }
        }
        deferredByWeekId.mapValues { (_, deferred) -> deferred.await() }
    }

    private fun loadSuggestions() {
        val pickerWeekStartDate = _state.value.pickerWeekStartDate ?: return
        val pickerWeeklyPartId = _state.value.pickerWeeklyPartId ?: return
        val pickerSlot = _state.value.pickerSlot ?: return

        pickerSuggestionsJob?.cancel()
        pickerSuggestionsJob = scope.launch {
            _state.update { it.copy(isPickerLoading = true) }
            try {
                val week = _state.value.selectedProgramWeeks.find { it.weekStartDate == pickerWeekStartDate }
                val assignedPeople = if (week == null) {
                    emptySet()
                } else {
                    (_state.value.selectedProgramAssignments[week.id.value] ?: emptyList())
                        .map { it.personId }
                        .toSet()
                }
                val suggestions = suggerisciProclamatori(
                    weekStartDate = pickerWeekStartDate,
                    weeklyPartId = pickerWeeklyPartId,
                    slot = pickerSlot,
                    alreadyAssignedIds = assignedPeople,
                )
                _state.update { it.copy(pickerSuggestions = suggestions, isPickerLoading = false) }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        isPickerLoading = false,
                        notice = FeedbackBannerModel(
                            "Errore nel caricamento suggerimenti: ${error.message}",
                            FeedbackBannerKind.ERROR,
                        ),
                    )
                }
            }
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

package org.example.project.ui.weeklyparts

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.example.project.core.application.SharedWeekState
import org.example.project.core.domain.DomainError
import org.example.project.core.domain.toMessage
import org.example.project.feature.assignments.application.AssignmentRepository
import org.example.project.feature.weeklyparts.application.AggiornaDatiRemotiUseCase
import org.example.project.feature.weeklyparts.application.CaricaSettimanaUseCase
import org.example.project.feature.weeklyparts.application.CercaTipiParteUseCase
import org.example.project.feature.weeklyparts.application.CreaSettimanaUseCase
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeeklyPart
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import org.example.project.feature.weeklyparts.application.WeekPlanStore
import org.example.project.feature.weeklyparts.application.RemoteWeekSchema
import org.example.project.ui.components.FeedbackBannerKind
import org.example.project.ui.components.FeedbackBannerModel
import org.example.project.ui.components.WeekTimeIndicator
import org.example.project.ui.components.computeWeekIndicator
import org.example.project.ui.components.sundayOf
import java.time.LocalDate

internal data class WeeklyPartsUiState(
    val currentMonday: LocalDate = SharedWeekState.currentMonday(),
    val weekPlan: WeekPlan? = null,
    val isLoading: Boolean = true,
    val partTypes: List<PartType> = emptyList(),
    val notice: FeedbackBannerModel? = null,
    val isImporting: Boolean = false,
    val weeksNeedingConfirmation: List<RemoteWeekSchema> = emptyList(),
    val removePartCandidate: WeeklyPartId? = null,
    val partTypesLoadFailed: Boolean = false,
    val overwriteAssignmentCounts: Map<String, Int> = emptyMap(),
    val editingParts: List<WeeklyPart>? = null,
    val isDirty: Boolean = false,
    val showDirtyPrompt: Boolean = false,
    val pendingNavigation: (() -> Unit)? = null,
    val isSaving: Boolean = false,
) {
    val weekIndicator: WeekTimeIndicator get() = computeWeekIndicator(currentMonday)

    val sundayDate: LocalDate get() = sundayOf(currentMonday)
}

internal class WeeklyPartsViewModel(
    private val scope: CoroutineScope,
    private val sharedWeekState: SharedWeekState,
    private val caricaSettimana: CaricaSettimanaUseCase,
    private val creaSettimana: CreaSettimanaUseCase,
    private val cercaTipiParte: CercaTipiParteUseCase,
    private val aggiornaDatiRemoti: AggiornaDatiRemotiUseCase,
    private val assignmentRepository: AssignmentRepository,
    private val weekPlanStore: WeekPlanStore,
) {
    private val _state = MutableStateFlow(WeeklyPartsUiState())
    val state: StateFlow<WeeklyPartsUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        scope.launch {
            sharedWeekState.currentMonday.collect { monday ->
                val current = _state.value
                if (current.isDirty && monday != current.currentMonday) {
                    // External navigation while dirty — discard local edits to avoid silent data loss
                    _state.update { it.copy(editingParts = null, isDirty = false, currentMonday = monday) }
                } else {
                    _state.update { it.copy(currentMonday = monday) }
                }
                try {
                    loadWeek()
                } catch (e: Exception) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            notice = FeedbackBannerModel("Errore nel caricamento: ${e.message}", FeedbackBannerKind.ERROR),
                        )
                    }
                }
            }
        }
        loadPartTypes()
    }

    fun onScreenEntered() {
        loadWeek()
        loadPartTypes()
    }

    fun navigateToPreviousWeek() {
        if (_state.value.isDirty) {
            _state.update { it.copy(showDirtyPrompt = true, pendingNavigation = { sharedWeekState.navigateToPreviousWeek() }) }
            return
        }
        sharedWeekState.navigateToPreviousWeek()
    }

    fun navigateToNextWeek() {
        if (_state.value.isDirty) {
            _state.update { it.copy(showDirtyPrompt = true, pendingNavigation = { sharedWeekState.navigateToNextWeek() }) }
            return
        }
        sharedWeekState.navigateToNextWeek()
    }

    fun navigateToCurrentWeek() {
        if (_state.value.isDirty) {
            _state.update { it.copy(showDirtyPrompt = true, pendingNavigation = { sharedWeekState.navigateToCurrentWeek() }) }
            return
        }
        sharedWeekState.navigateToCurrentWeek()
    }

    fun createWeek() {
        scope.launch {
            _state.update { it.copy(isLoading = true) }
            creaSettimana(_state.value.currentMonday).fold(
                ifLeft = { error -> showError(error) },
                ifRight = { weekPlan -> _state.update { it.copy(isLoading = false, weekPlan = weekPlan) } },
            )
        }
    }

    fun addPart(partTypeId: PartTypeId) {
        val weekPlan = _state.value.weekPlan ?: return
        val partType = _state.value.partTypes.find { it.id == partTypeId } ?: return
        val currentParts = _state.value.editingParts ?: weekPlan.parts
        val newPart = WeeklyPart(
            id = WeeklyPartId(java.util.UUID.randomUUID().toString()),
            partType = partType,
            sortOrder = currentParts.size,
        )
        val updated = currentParts + newPart
        _state.update { it.copy(editingParts = updated, isDirty = true) }
    }

    fun requestRemovePart(weeklyPartId: WeeklyPartId) {
        _state.update { it.copy(removePartCandidate = weeklyPartId) }
    }

    fun confirmRemovePart() {
        val partId = _state.value.removePartCandidate ?: return
        val weekPlan = _state.value.weekPlan ?: return
        val currentParts = _state.value.editingParts ?: weekPlan.parts
        val updated = currentParts
            .filter { it.id != partId }
            .mapIndexed { i, p -> p.copy(sortOrder = i) }
        _state.update { it.copy(removePartCandidate = null, editingParts = updated, isDirty = true) }
    }

    fun dismissRemovePart() {
        _state.update { it.copy(removePartCandidate = null) }
    }

    fun saveChanges() {
        performSave { loadWeek() }
    }

    fun saveAndNavigate() {
        val pendingNav = _state.value.pendingNavigation
        performSave { pendingNav?.invoke() }
    }

    private fun performSave(onSuccess: () -> Unit) {
        val weekPlan = _state.value.weekPlan ?: return
        val editingParts = _state.value.editingParts ?: return
        scope.launch {
            _state.update { it.copy(isSaving = true, showDirtyPrompt = false) }
            runCatching {
                weekPlanStore.replaceAllParts(
                    weekPlan.id,
                    editingParts.sortedBy { it.sortOrder }.map { it.partType.id },
                )
            }.onSuccess {
                _state.update {
                    it.copy(editingParts = null, isDirty = false, isSaving = false, pendingNavigation = null)
                }
                onSuccess()
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isSaving = false,
                        notice = FeedbackBannerModel("Errore salvataggio: ${error.message}", FeedbackBannerKind.ERROR),
                    )
                }
            }
        }
    }

    fun discardChanges() {
        val pendingNav = _state.value.pendingNavigation
        _state.update { it.copy(editingParts = null, isDirty = false, showDirtyPrompt = false, pendingNavigation = null) }
        pendingNav?.invoke()
    }

    fun cancelNavigation() {
        _state.update { it.copy(showDirtyPrompt = false, pendingNavigation = null) }
    }

    fun movePart(fromIndex: Int, toIndex: Int) {
        val weekPlan = _state.value.weekPlan ?: return
        val parts = (_state.value.editingParts ?: weekPlan.parts).toMutableList()
        if (fromIndex !in parts.indices || toIndex !in parts.indices) return
        val moved = parts.removeAt(fromIndex)
        parts.add(toIndex, moved)
        val reordered = parts.mapIndexed { i, p -> p.copy(sortOrder = i) }
        _state.update { it.copy(editingParts = reordered, isDirty = true) }
    }

    fun syncRemoteData() {
        scope.launch {
            _state.update { it.copy(isImporting = true, weeksNeedingConfirmation = emptyList()) }
            aggiornaDatiRemoti.fetchAndImport().fold(
                ifLeft = { error ->
                    _state.update { it.copy(isImporting = false) }
                    showError(error)
                },
                ifRight = { result ->
                    if (result.weeksNeedingConfirmation.isNotEmpty()) {
                        val counts = mutableMapOf<String, Int>()
                        for (schema in result.weeksNeedingConfirmation) {
                            val date = LocalDate.parse(schema.weekStartDate)
                            val existing = caricaSettimana(date)
                            if (existing != null) {
                                counts[schema.weekStartDate] = assignmentRepository.countAssignmentsForWeek(existing.id)
                            }
                        }
                        _state.update { it.copy(
                            isImporting = false,
                            weeksNeedingConfirmation = result.weeksNeedingConfirmation,
                            overwriteAssignmentCounts = counts,
                        ) }
                    } else {
                        val message = buildString {
                            append("Aggiornamento completato: ${result.partTypesImported} tipi, ${result.weeksImported} settimane")
                            if (result.unresolvedPartTypeCodes.isNotEmpty()) {
                                append(" | Codici non risolti: ${result.unresolvedPartTypeCodes.joinToString(", ")}")
                            }
                        }
                        val kind = if (result.unresolvedPartTypeCodes.isEmpty()) {
                            FeedbackBannerKind.SUCCESS
                        } else {
                            FeedbackBannerKind.ERROR
                        }
                        _state.update { it.copy(
                            isImporting = false,
                            notice = FeedbackBannerModel(message, kind),
                        ) }
                        loadWeek()
                        loadPartTypes()
                    }
                },
            )
        }
    }

    fun confirmOverwrite() {
        val pending = _state.value.weeksNeedingConfirmation
        scope.launch {
            _state.update { it.copy(isImporting = true, weeksNeedingConfirmation = emptyList(), overwriteAssignmentCounts = emptyMap()) }
            aggiornaDatiRemoti.importSchemas(pending).fold(
                ifLeft = { error ->
                    _state.update { it.copy(isImporting = false) }
                    showError(error)
                },
                ifRight = { count ->
                    _state.update { it.copy(
                        isImporting = false,
                        notice = FeedbackBannerModel(
                            "Sovrascritte $count settimane",
                            FeedbackBannerKind.SUCCESS,
                        ),
                    ) }
                    loadWeek()
                },
            )
        }
    }

    fun dismissConfirmation() {
        _state.update { it.copy(weeksNeedingConfirmation = emptyList(), overwriteAssignmentCounts = emptyMap()) }
        loadWeek()
        loadPartTypes()
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }

    private fun loadWeek() {
        loadJob?.cancel()
        loadJob = scope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val weekPlan = caricaSettimana(_state.value.currentMonday)
                _state.update { it.copy(isLoading = false, weekPlan = weekPlan, editingParts = null, isDirty = false) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        notice = FeedbackBannerModel("Errore nel caricamento: ${e.message}", FeedbackBannerKind.ERROR),
                    )
                }
            }
        }
    }

    private fun loadPartTypes() {
        scope.launch {
            try {
                val types = cercaTipiParte()
                _state.update { it.copy(partTypes = types, partTypesLoadFailed = false) }
            } catch (_: Exception) {
                _state.update { it.copy(partTypesLoadFailed = true) }
            }
        }
    }

    private fun showError(error: DomainError) {
        _state.update { it.copy(
            isLoading = false,
            notice = FeedbackBannerModel(error.toMessage(), FeedbackBannerKind.ERROR),
        ) }
    }
}

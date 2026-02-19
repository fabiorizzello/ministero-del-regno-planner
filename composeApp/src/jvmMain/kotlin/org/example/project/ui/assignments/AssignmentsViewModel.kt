package org.example.project.ui.assignments

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
import org.example.project.feature.assignments.application.AssegnaPersonaUseCase
import org.example.project.feature.assignments.application.CaricaAssegnazioniUseCase
import org.example.project.feature.assignments.application.RimuoviAssegnazioneUseCase
import org.example.project.feature.assignments.application.SuggerisciProclamatoriUseCase
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.assignments.domain.AssignmentWithPerson
import org.example.project.feature.assignments.domain.SuggestedProclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.weeklyparts.application.CaricaSettimanaUseCase
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import org.example.project.ui.components.FeedbackBannerKind
import org.example.project.ui.components.FeedbackBannerModel
import org.example.project.ui.components.WeekTimeIndicator
import org.example.project.ui.components.computeWeekIndicator
import org.example.project.ui.components.sundayOf
import java.time.LocalDate

internal data class AssignmentsUiState(
    val currentMonday: LocalDate = SharedWeekState.currentMonday(),
    val weekPlan: WeekPlan? = null,
    val assignments: List<AssignmentWithPerson> = emptyList(),
    val isLoading: Boolean = true,
    val notice: FeedbackBannerModel? = null,
    // Dialog state
    val pickerWeeklyPartId: WeeklyPartId? = null,
    val pickerSlot: Int? = null,
    val pickerSearchTerm: String = "",
    val pickerSortGlobal: Boolean = true,
    val pickerSuggestions: List<SuggestedProclamatore> = emptyList(),
    val isPickerLoading: Boolean = false,
    val isAssigning: Boolean = false,
    val isRemoving: Boolean = false,
) {
    val isPickerOpen: Boolean get() = pickerWeeklyPartId != null

    val weekIndicator: WeekTimeIndicator get() = computeWeekIndicator(currentMonday)

    val sundayDate: LocalDate get() = sundayOf(currentMonday)

    val assignedSlotCount: Int get() = assignments.size
    val totalSlotCount: Int get() = weekPlan?.parts
        ?.sumOf { it.partType.peopleCount } ?: 0
}

internal class AssignmentsViewModel(
    private val scope: CoroutineScope,
    private val sharedWeekState: SharedWeekState,
    private val caricaSettimana: CaricaSettimanaUseCase,
    private val caricaAssegnazioni: CaricaAssegnazioniUseCase,
    private val assegnaPersona: AssegnaPersonaUseCase,
    private val rimuoviAssegnazione: RimuoviAssegnazioneUseCase,
    private val suggerisciProclamatori: SuggerisciProclamatoriUseCase,
) {
    private val _state = MutableStateFlow(AssignmentsUiState())
    val state: StateFlow<AssignmentsUiState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var suggestionsJob: Job? = null

    init {
        scope.launch {
            sharedWeekState.currentMonday.collect { monday ->
                _state.update { it.copy(currentMonday = monday) }
                loadWeekData()
            }
        }
    }

    fun navigateToPreviousWeek() {
        sharedWeekState.navigateToPreviousWeek()
    }

    fun navigateToNextWeek() {
        sharedWeekState.navigateToNextWeek()
    }

    fun openPersonPicker(weeklyPartId: WeeklyPartId, slot: Int) {
        _state.update {
            it.copy(
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
        val s = _state.value
        val weeklyPartId = s.pickerWeeklyPartId ?: return
        val slot = s.pickerSlot ?: return

        _state.update { it.copy(isAssigning = true) }
        scope.launch {
            try {
                assegnaPersona(
                    weekStartDate = s.currentMonday,
                    weeklyPartId = weeklyPartId,
                    personId = personId,
                    slot = slot,
                ).fold(
                    ifLeft = { error -> showError(error) },
                    ifRight = {
                        closePersonPicker()
                        loadWeekData()
                    },
                )
            } finally {
                _state.update { it.copy(isAssigning = false) }
            }
        }
    }

    fun removeAssignment(assignmentId: AssignmentId) {
        if (_state.value.isRemoving) return
        scope.launch {
            _state.update { it.copy(isRemoving = true) }
            try {
                rimuoviAssegnazione(assignmentId).fold(
                    ifLeft = { error -> showError(error) },
                    ifRight = {
                        _state.update {
                            it.copy(notice = FeedbackBannerModel("Assegnazione rimossa", FeedbackBannerKind.SUCCESS))
                        }
                        loadWeekData()
                    },
                )
            } finally {
                _state.update { it.copy(isRemoving = false) }
            }
        }
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }

    private fun loadWeekData() {
        loadJob?.cancel()
        loadJob = scope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val weekPlan = caricaSettimana(_state.value.currentMonday)
                val assignments = caricaAssegnazioni(_state.value.currentMonday)
                _state.update { it.copy(isLoading = false, weekPlan = weekPlan, assignments = assignments) }
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

    private fun loadSuggestions() {
        val s = _state.value
        val weeklyPartId = s.pickerWeeklyPartId ?: return
        val slot = s.pickerSlot ?: return

        suggestionsJob?.cancel()
        suggestionsJob = scope.launch {
            _state.update { it.copy(isPickerLoading = true) }
            try {
                val suggestions = suggerisciProclamatori(
                    weekStartDate = s.currentMonday,
                    weeklyPartId = weeklyPartId,
                    slot = slot,
                )
                _state.update { it.copy(pickerSuggestions = suggestions, isPickerLoading = false) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isPickerLoading = false,
                        notice = FeedbackBannerModel("Errore nel caricamento suggerimenti: ${e.message}", FeedbackBannerKind.ERROR),
                    )
                }
            }
        }
    }

    private fun showError(error: DomainError) {
        _state.update {
            it.copy(
                isLoading = false,
                notice = FeedbackBannerModel(error.toMessage(), FeedbackBannerKind.ERROR),
            )
        }
    }
}

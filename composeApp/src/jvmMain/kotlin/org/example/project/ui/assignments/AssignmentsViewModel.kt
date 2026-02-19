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
import org.example.project.feature.assignments.application.AssegnaPersonaUseCase
import org.example.project.feature.assignments.application.CaricaAssegnazioniUseCase
import org.example.project.feature.assignments.application.RimuoviAssegnazioneUseCase
import org.example.project.feature.assignments.application.SuggerisciProclamatoriUseCase
import org.example.project.feature.assignments.domain.AssignmentWithPerson
import org.example.project.feature.assignments.domain.SuggestedProclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.weeklyparts.application.CaricaSettimanaUseCase
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import org.example.project.ui.components.FeedbackBannerKind
import org.example.project.ui.components.FeedbackBannerModel
import org.example.project.ui.components.WeekTimeIndicator
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

internal data class AssignmentsUiState(
    val currentMonday: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
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
) {
    val isPickerOpen: Boolean get() = pickerWeeklyPartId != null

    val weekIndicator: WeekTimeIndicator
        get() {
            val thisMonday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            return when {
                currentMonday == thisMonday -> WeekTimeIndicator.CORRENTE
                currentMonday.isAfter(thisMonday) -> WeekTimeIndicator.FUTURA
                else -> WeekTimeIndicator.PASSATA
            }
        }

    val sundayDate: LocalDate get() = currentMonday.plusDays(6)

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
        val s = _state.value
        val weeklyPartId = s.pickerWeeklyPartId ?: return
        val slot = s.pickerSlot ?: return

        scope.launch {
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
        }
    }

    fun removeAssignment(assignmentId: String) {
        scope.launch {
            rimuoviAssegnazione(assignmentId).fold(
                ifLeft = { error -> showError(error) },
                ifRight = { loadWeekData() },
            )
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

        scope.launch {
            _state.update { it.copy(isPickerLoading = true) }
            val suggestions = suggerisciProclamatori(
                weekStartDate = s.currentMonday,
                weeklyPartId = weeklyPartId,
                slot = slot,
            )
            _state.update { it.copy(pickerSuggestions = suggestions, isPickerLoading = false) }
        }
    }

    private fun showError(error: DomainError) {
        val message = when (error) {
            is DomainError.Validation -> error.message
            is DomainError.NotImplemented -> "Non implementato: ${error.area}"
        }
        _state.update {
            it.copy(
                isLoading = false,
                notice = FeedbackBannerModel(message, FeedbackBannerKind.ERROR),
            )
        }
    }
}

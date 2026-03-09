package org.example.project.ui.workspace

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.feature.assignments.application.AssegnaPersonaUseCase
import org.example.project.feature.assignments.application.CaricaAssegnazioniUseCase
import org.example.project.feature.assignments.application.RimuoviAssegnazioneUseCase
import org.example.project.feature.assignments.application.SuggerisciProclamatoriUseCase
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.assignments.domain.SuggestedProclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import org.example.project.ui.components.FeedbackBannerKind
import org.example.project.ui.components.FeedbackBannerModel
import org.example.project.ui.components.executeEitherOperationWithNotice
import java.time.LocalDate

internal data class PersonPickerUiState(
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
    val isPickerOpen: Boolean get() = pickerWeekStartDate != null && pickerWeeklyPartId != null && pickerSlot != null
}

internal class PersonPickerViewModel(
    private val scope: CoroutineScope,
    private val assegnaPersona: AssegnaPersonaUseCase,
    private val rimuoviAssegnazione: RimuoviAssegnazioneUseCase,
    private val suggerisciProclamatori: SuggerisciProclamatoriUseCase,
    private val caricaAssegnazioni: CaricaAssegnazioniUseCase,
) {
    private val _state = MutableStateFlow(PersonPickerUiState())
    val state: StateFlow<PersonPickerUiState> = _state.asStateFlow()

    private var pickerSuggestionsJob: Job? = null

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun openPersonPicker(
        weekStartDate: LocalDate,
        weeklyPartId: WeeklyPartId,
        slot: Int,
    ) {
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

    fun reloadSuggestions() {
        if (_state.value.isPickerOpen) loadSuggestions()
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

    fun confirmAssignment(personId: ProclamatoreId, onSuccess: () -> Unit) {
        if (_state.value.isAssigning) return
        val pickerWeekStartDate = _state.value.pickerWeekStartDate ?: return
        val pickerWeeklyPartId = _state.value.pickerWeeklyPartId ?: return
        val pickerSlot = _state.value.pickerSlot ?: return

        scope.launch {
            _state.executeEitherOperationWithNotice(
                loadingUpdate = { it.copy(isAssigning = true) },
                noticeUpdate = { state, notice -> state.copy(isAssigning = false, notice = notice) },
                successMessage = null,
                operation = {
                    assegnaPersona(
                        weekStartDate = pickerWeekStartDate,
                        weeklyPartId = pickerWeeklyPartId,
                        personId = personId,
                        slot = pickerSlot,
                    )
                },
                onSuccess = {
                    closePersonPicker()
                    onSuccess()
                },
            )
        }
    }

    fun removeAssignment(assignmentId: AssignmentId, onSuccess: () -> Unit) {
        if (_state.value.isRemovingAssignment) return
        scope.launch {
            _state.executeEitherOperationWithNotice(
                loadingUpdate = { it.copy(isRemovingAssignment = true) },
                noticeUpdate = { state, notice -> state.copy(isRemovingAssignment = false, notice = notice) },
                successMessage = null,
                operation = { rimuoviAssegnazione(assignmentId) },
                onSuccess = { onSuccess() },
            )
        }
    }

    private fun loadSuggestions() {
        val pickerWeekStartDate = _state.value.pickerWeekStartDate ?: return
        val pickerWeeklyPartId = _state.value.pickerWeeklyPartId ?: return
        val pickerSlot = _state.value.pickerSlot ?: return

        pickerSuggestionsJob?.cancel()
        pickerSuggestionsJob = scope.launch {
            _state.update { it.copy(isPickerLoading = true) }
            try {
                // Already-assigned IDs are loaded internally by the use case from the repository.
                // No need to pass them from the ViewModel (which could become stale).
                val suggestions = suggerisciProclamatori(
                    weekStartDate = pickerWeekStartDate,
                    weeklyPartId = pickerWeeklyPartId,
                    slot = pickerSlot,
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
}

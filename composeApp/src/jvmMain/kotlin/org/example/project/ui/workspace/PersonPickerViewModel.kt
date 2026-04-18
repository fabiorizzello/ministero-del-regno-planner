package org.example.project.ui.workspace

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.feature.assignments.application.AssegnaPersonaUseCase
import org.example.project.feature.assignments.application.RimuoviAssegnazioneUseCase
import org.example.project.feature.assignments.application.SuggerisciProclamatoriUseCase
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.assignments.domain.SuggestedProclamatore
import org.example.project.feature.output.application.AnnullaConsegnaUseCase
import org.example.project.feature.output.application.VerificaConsegnaPreAssegnazioneUseCase
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import org.example.project.core.domain.toMessage
import org.example.project.ui.components.FeedbackBannerKind
import org.example.project.ui.components.FeedbackBannerModel
import org.example.project.ui.components.errorNotice
import org.example.project.ui.components.executeEitherOperationWithNotice
import java.time.LocalDate

internal data class DeliveryWarningState(
    val previousStudentName: String,
    val pendingPersonId: ProclamatoreId,
)

internal data class PersonPickerUiState(
    val pickerWeekStartDate: LocalDate? = null,
    val pickerWeeklyPartId: WeeklyPartId? = null,
    val pickerSlot: Int? = null,
    val pickerWeekPlanId: WeekPlanId? = null,
    val pickerSearchTerm: String = "",
    val pickerSuggestions: List<SuggestedProclamatore> = emptyList(),
    val isPickerLoading: Boolean = false,
    val isAssigning: Boolean = false,
    val isRemovingAssignment: Boolean = false,
    val notice: FeedbackBannerModel? = null,
    val deliveryWarning: DeliveryWarningState? = null,
) {
    val isPickerOpen: Boolean get() = pickerWeekStartDate != null && pickerWeeklyPartId != null && pickerSlot != null
}

internal class PersonPickerViewModel(
    private val scope: CoroutineScope,
    private val assegnaPersona: AssegnaPersonaUseCase,
    private val rimuoviAssegnazione: RimuoviAssegnazioneUseCase,
    private val suggerisciProclamatori: SuggerisciProclamatoriUseCase,
    private val verificaConsegna: VerificaConsegnaPreAssegnazioneUseCase,
    private val annullaConsegna: AnnullaConsegnaUseCase,
) {
    private val _state = MutableStateFlow(PersonPickerUiState())
    val state: StateFlow<PersonPickerUiState> = _state.asStateFlow()

    private var pickerSuggestionsJob: Job? = null
    private var pickerReloadVersion: Long = 0L

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun openPersonPicker(
        weekStartDate: LocalDate,
        weeklyPartId: WeeklyPartId,
        slot: Int,
        weekPlanId: WeekPlanId,
    ) {
        _state.update {
            it.copy(
                pickerWeekStartDate = weekStartDate,
                pickerWeeklyPartId = weeklyPartId,
                pickerSlot = slot,
                pickerWeekPlanId = weekPlanId,
                pickerSearchTerm = "",
                pickerSuggestions = emptyList(),
                isPickerLoading = true,
            )
        }
        loadSuggestions()
    }

    fun reloadSuggestions(strictCooldownOverride: Boolean? = null) {
        if (_state.value.isPickerOpen) loadSuggestions(strictCooldownOverride)
    }

    fun closePersonPicker() {
        _state.update {
            it.copy(
                pickerWeekStartDate = null,
                pickerWeeklyPartId = null,
                pickerSlot = null,
                pickerWeekPlanId = null,
                pickerSearchTerm = "",
                pickerSuggestions = emptyList(),
                isPickerLoading = false,
                deliveryWarning = null,
            )
        }
    }

    fun setPickerSearchTerm(term: String) {
        _state.update { it.copy(pickerSearchTerm = term) }
    }

    fun confirmAssignment(personId: ProclamatoreId, onSuccess: () -> Unit) {
        if (_state.value.isAssigning) return
        val pickerWeeklyPartId = _state.value.pickerWeeklyPartId ?: return
        val pickerWeekPlanId = _state.value.pickerWeekPlanId ?: return

        scope.launch {
            val previousStudent = try {
                verificaConsegna(pickerWeeklyPartId, pickerWeekPlanId)
            } catch (e: Exception) {
                _state.update {
                    it.copy(notice = errorNotice("Errore verifica consegna: ${e.message}"))
                }
                return@launch
            }
            if (previousStudent != null) {
                _state.update {
                    it.copy(deliveryWarning = DeliveryWarningState(previousStudent, personId))
                }
                return@launch
            }
            doAssign(personId, onSuccess)
        }
    }

    fun confirmAssignmentAfterWarning(onSuccess: () -> Unit) {
        val warning = _state.value.deliveryWarning ?: return
        val pickerWeeklyPartId = _state.value.pickerWeeklyPartId ?: return
        val pickerWeekPlanId = _state.value.pickerWeekPlanId ?: return
        _state.update { it.copy(deliveryWarning = null) }

        scope.launch {
            annullaConsegna(pickerWeeklyPartId, pickerWeekPlanId).fold(
                ifLeft = { error ->
                    _state.update { it.copy(notice = errorNotice(error.toMessage())) }
                },
                ifRight = {
                    doAssign(warning.pendingPersonId, onSuccess)
                },
            )
        }
    }

    fun dismissDeliveryWarning() {
        _state.update { it.copy(deliveryWarning = null) }
    }

    private fun doAssign(personId: ProclamatoreId, onSuccess: () -> Unit) {
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

    private fun loadSuggestions(strictCooldownOverride: Boolean? = null) {
        val pickerWeekStartDate = _state.value.pickerWeekStartDate ?: return
        val pickerWeeklyPartId = _state.value.pickerWeeklyPartId ?: return
        val pickerSlot = _state.value.pickerSlot ?: return
        val requestVersion = ++pickerReloadVersion

        pickerSuggestionsJob?.cancel()
        pickerSuggestionsJob = scope.launch {
            _state.update {
                it.copy(
                    isPickerLoading = true,
                    pickerSuggestions = emptyList(),
                )
            }
            try {
                // Already-assigned IDs are loaded internally by the use case from the repository.
                // No need to pass them from the ViewModel (which could become stale).
                val suggestions = suggerisciProclamatori(
                    weekStartDate = pickerWeekStartDate,
                    weeklyPartId = pickerWeeklyPartId,
                    slot = pickerSlot,
                    strictCooldownOverride = strictCooldownOverride,
                )
                _state.update { current ->
                    if (requestVersion != pickerReloadVersion) {
                        current
                    } else {
                        current.copy(
                            pickerSuggestions = suggestions,
                            isPickerLoading = false,
                        )
                    }
                }
            } catch (error: Exception) {
                _state.update {
                    if (requestVersion != pickerReloadVersion) {
                        it
                    } else {
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
}

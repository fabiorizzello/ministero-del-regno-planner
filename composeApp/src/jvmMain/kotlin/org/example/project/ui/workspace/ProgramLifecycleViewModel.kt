package org.example.project.ui.workspace

import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.feature.programs.application.CaricaProgrammiAttiviUseCase
import org.example.project.feature.programs.application.CreaProssimoProgrammaUseCase
import org.example.project.feature.programs.application.EliminaProgrammaFuturoUseCase
import org.example.project.feature.programs.application.GeneraSettimaneProgrammaUseCase
import org.example.project.feature.programs.domain.ProgramMonth
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.schemas.application.SchemaTemplateStore
import org.example.project.ui.components.FeedbackBannerKind
import org.example.project.ui.components.FeedbackBannerModel
import org.example.project.ui.components.errorNotice
import org.example.project.ui.components.executeAsyncOperation
import org.example.project.ui.components.executeEitherOperationWithNotice
import org.example.project.ui.components.formatMonthYearLabel
import java.time.LocalDate

internal data class ProgramLifecycleUiState(
    val today: LocalDate = LocalDate.now(),
    val isLoading: Boolean = true,
    val currentProgram: ProgramMonth? = null,
    val futureProgram: ProgramMonth? = null,
    val selectedProgramId: String? = null,
    val isCreatingProgram: Boolean = false,
    val isDeletingSelectedProgram: Boolean = false,
    val notice: FeedbackBannerModel? = null,
) {
    val hasPrograms: Boolean get() = currentProgram != null || futureProgram != null
    val canCreateProgram: Boolean get() = futureProgram == null
    val canDeleteSelectedProgram: Boolean get() = selectedProgramId != null && selectedProgramId == futureProgram?.id?.value
}

internal class ProgramLifecycleViewModel(
    private val scope: CoroutineScope,
    private val caricaProgrammiAttivi: CaricaProgrammiAttiviUseCase,
    private val creaProssimoProgramma: CreaProssimoProgrammaUseCase,
    private val eliminaProgrammaFuturo: EliminaProgrammaFuturoUseCase,
    private val generaSettimaneProgramma: GeneraSettimaneProgrammaUseCase,
    private val schemaTemplateStore: SchemaTemplateStore,
) {
    private val _state = MutableStateFlow(ProgramLifecycleUiState())
    val state: StateFlow<ProgramLifecycleUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    fun onScreenEntered() {
        loadProgramsAndWeeks()
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun selectProgram(programId: String) {
        _state.update { it.copy(selectedProgramId = programId) }
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

            _state.executeEitherOperationWithNotice(
                loadingUpdate = { it.copy(isCreatingProgram = true) },
                noticeUpdate = { state, notice -> state.copy(isCreatingProgram = false, notice = notice) },
                successMessage = null,
                operation = { creaProssimoProgramma() },
                onSuccess = { program ->
                    _state.executeEitherOperationWithNotice(
                        loadingUpdate = { it },
                        noticeUpdate = { state, notice -> state.copy(notice = notice) },
                        successMessage = "Programma ${formatMonthYearLabel(program.month, program.year)} creato",
                        operation = { generaSettimaneProgramma(program.id.value) },
                        onSuccess = { loadProgramsAndWeeks() },
                    )
                },
            )
        }
    }

    fun deleteSelectedProgram() {
        val selectedProgramId = _state.value.selectedProgramId ?: return
        if (_state.value.isDeletingSelectedProgram) return

        scope.launch {
            _state.executeEitherOperationWithNotice(
                loadingUpdate = { it.copy(isDeletingSelectedProgram = true) },
                noticeUpdate = { state, notice -> state.copy(isDeletingSelectedProgram = false, notice = notice) },
                successMessage = "Programma selezionato eliminato",
                operation = { eliminaProgrammaFuturo(ProgramMonthId(selectedProgramId), _state.value.today) },
                onSuccess = { loadProgramsAndWeeks() },
            )
        }
    }

    fun loadProgramsAndWeeks() {
        loadJob?.cancel()
        loadJob = scope.launch {
            _state.executeAsyncOperation(
                loadingUpdate = { it.copy(isLoading = true) },
                successUpdate = { state, snapshot ->
                    val selectedProgramId = when {
                        state.selectedProgramId != null -> state.selectedProgramId
                        snapshot.current != null -> snapshot.current.id.value
                        else -> snapshot.future?.id?.value
                    }
                    state.copy(
                        isLoading = false,
                        currentProgram = snapshot.current,
                        futureProgram = snapshot.future,
                        selectedProgramId = selectedProgramId,
                    )
                },
                errorUpdate = { state, error ->
                    state.copy(
                        isLoading = false,
                        notice = errorNotice("Errore caricamento cruscotto programma: ${error.message}"),
                    )
                },
                operation = {
                    val today = _state.value.today
                    caricaProgrammiAttivi(today)
                },
            )
        }
    }
}

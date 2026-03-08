package org.example.project.ui.workspace

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.core.domain.DomainError
import org.example.project.feature.assignments.application.CaricaAssegnazioniUseCase
import org.example.project.feature.assignments.domain.AssignmentWithPerson
import org.example.project.feature.programs.application.CaricaProgrammiAttiviUseCase
import org.example.project.feature.programs.application.CreaProssimoProgrammaUseCase
import org.example.project.feature.programs.application.EliminaProgrammaUseCase
import org.example.project.feature.programs.application.GeneraSettimaneProgrammaUseCase
import org.example.project.feature.programs.application.ProgramSelectionSnapshot
import org.example.project.feature.programs.domain.ProgramMonth
import org.example.project.feature.programs.domain.ProgramMonthAggregate
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.schemas.application.SchemaTemplateStore
import org.example.project.feature.weeklyparts.application.CercaTipiParteUseCase
import org.example.project.feature.weeklyparts.application.WeekPlanQueries
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.ui.components.FeedbackBannerKind
import org.example.project.ui.components.FeedbackBannerModel
import org.example.project.ui.components.errorNotice
import org.example.project.ui.components.executeAsyncOperation
import org.example.project.ui.components.executeEitherOperationWithNotice
import org.example.project.core.domain.toMessage
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class DeleteProgramImpact(
    val year: Int,
    val month: Int,
    val weeksCount: Int,
    val assignmentsCount: Int,
)

internal data class ProgramLifecycleUiState(
    val today: LocalDate = LocalDate.now(),
    val isLoading: Boolean = true,
    val currentProgram: ProgramMonth? = null,
    val futurePrograms: List<ProgramMonth> = emptyList(),
    val creatableTargets: List<YearMonth> = emptyList(),
    val selectedProgramId: ProgramMonthId? = null,
    val selectedProgramWeeks: List<WeekPlan> = emptyList(),
    val selectedProgramAssignments: Map<String, List<AssignmentWithPerson>> = emptyMap(),
    val partTypes: List<PartType> = emptyList(),
    val isCreatingProgram: Boolean = false,
    val isDeletingSelectedProgram: Boolean = false,
    val deleteImpactConfirm: DeleteProgramImpact? = null,
    val notice: FeedbackBannerModel? = null,
) {
    val hasPrograms: Boolean get() = currentProgram != null || futurePrograms.isNotEmpty()
    val canCreateProgram: Boolean get() = creatableTargets.isNotEmpty()
    val selectedProgram: ProgramMonth?
        get() = when (selectedProgramId) {
            currentProgram?.id -> currentProgram
            else -> futurePrograms.firstOrNull { it.id == selectedProgramId }
        }
    val selectedFutureProgram: ProgramMonth?
        get() = futurePrograms.firstOrNull { it.id == selectedProgramId }
    val canDeleteSelectedProgram: Boolean
        get() = selectedProgram != null
}

internal class ProgramLifecycleViewModel(
    private val scope: CoroutineScope,
    private val caricaProgrammiAttivi: CaricaProgrammiAttiviUseCase,
    private val creaProssimoProgramma: CreaProssimoProgrammaUseCase,
    private val eliminaProgramma: EliminaProgrammaUseCase,
    private val generaSettimaneProgramma: GeneraSettimaneProgrammaUseCase,
    private val schemaTemplateStore: SchemaTemplateStore,
    private val weekPlanStore: WeekPlanQueries,
    private val caricaAssegnazioni: CaricaAssegnazioniUseCase,
    private val cercaTipiParte: CercaTipiParteUseCase,
) {
    private val _state = MutableStateFlow(ProgramLifecycleUiState())
    val state: StateFlow<ProgramLifecycleUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var weeksJob: Job? = null

    fun onScreenEntered() {
        loadProgramsAndWeeks()
        loadPartTypes()
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun selectProgram(programId: ProgramMonthId) {
        _state.update { it.copy(selectedProgramId = programId) }
        loadWeeksForSelectedProgram()
    }

    private fun loadPartTypes() {
        scope.launch {
            val partTypes = cercaTipiParte()
            _state.update { it.copy(partTypes = partTypes) }
        }
    }

    fun createProgramForTarget(targetYear: Int, targetMonth: Int) {
        if (_state.value.isCreatingProgram) return
        scope.launch {
            if (schemaTemplateStore.isEmpty()) {
                _state.update {
                    it.copy(
                        notice = errorNotice("Aggiorna schemi prima di creare il programma"),
                    )
                }
                return@launch
            }

            _state.update { it.copy(isCreatingProgram = true, notice = null) }

            creaProssimoProgramma(targetYear, targetMonth, _state.value.today).fold(
                ifLeft = { error ->
                    _state.update {
                        it.copy(
                            isCreatingProgram = false,
                            notice = errorNotice(error.toMessage()),
                        )
                    }
                },
                ifRight = { program ->
                    generaSettimaneProgramma(program.id).fold(
                        ifLeft = { error ->
                            val rollbackError = eliminaProgramma(program.id, _state.value.today)
                                .fold(
                                    ifLeft = { it },
                                    ifRight = { null },
                                )
                            _state.update {
                                it.copy(
                                    isCreatingProgram = false,
                                    notice = buildProgramCreationFailureNotice(
                                        error = error,
                                        rollbackError = rollbackError,
                                    ),
                                )
                            }
                        },
                        ifRight = {
                            _state.update { state -> state.copy(isCreatingProgram = false, notice = null) }
                            loadProgramsAndWeeks()
                        },
                    )
                },
            )
        }
    }

    fun requestDeleteSelectedProgram() {
        if (_state.value.isDeletingSelectedProgram) return
        val selectedProgram = _state.value.selectedProgram ?: return
        _state.update {
            it.copy(
                deleteImpactConfirm = DeleteProgramImpact(
                    year = selectedProgram.year,
                    month = selectedProgram.month,
                    weeksCount = it.selectedProgramWeeks.size,
                    assignmentsCount = it.selectedProgramAssignments.values.sumOf { assignments -> assignments.size },
                ),
            )
        }
    }

    fun dismissDeleteSelectedProgram() {
        _state.update { it.copy(deleteImpactConfirm = null) }
    }

    fun confirmDeleteSelectedProgram() {
        val selectedProgramId = _state.value.selectedProgramId ?: return
        if (_state.value.isDeletingSelectedProgram) return

        scope.launch {
            _state.executeEitherOperationWithNotice(
                loadingUpdate = { it.copy(isDeletingSelectedProgram = true, deleteImpactConfirm = null) },
                noticeUpdate = { state, notice ->
                    state.copy(
                        isDeletingSelectedProgram = false,
                        deleteImpactConfirm = null,
                        notice = notice,
                    )
                },
                successMessage = null,
                operation = { eliminaProgramma(selectedProgramId, _state.value.today) },
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
                    applyProgramSnapshot(state, snapshot)
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
            loadWeeksForSelectedProgram()
        }
    }

    private fun loadWeeksForSelectedProgram() {
        weeksJob?.cancel()
        weeksJob = scope.launch {
            val selectedProgramId = _state.value.selectedProgramId ?: return@launch
            val weeks = runCatching { weekPlanStore.listByProgram(selectedProgramId) }
                .getOrElse { error ->
                    _state.update {
                        it.copy(
                            selectedProgramWeeks = emptyList(),
                            selectedProgramAssignments = emptyMap(),
                            notice = errorNotice("Errore caricamento settimane programma: ${error.message}"),
                        )
                    }
                    return@launch
                }

            runCatching { loadAssignmentsByWeek(weeks) }
                .onSuccess { assignmentsByWeek ->
                    _state.update {
                        it.copy(
                            selectedProgramWeeks = weeks,
                            selectedProgramAssignments = assignmentsByWeek,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            selectedProgramWeeks = weeks,
                            selectedProgramAssignments = emptyMap(),
                            notice = errorNotice("Errore caricamento assegnazioni: ${error.message}"),
                        )
                    }
                }
        }
    }

    private suspend fun loadAssignmentsByWeek(weeks: List<WeekPlan>): Map<String, List<AssignmentWithPerson>> = coroutineScope {
        if (weeks.isEmpty()) return@coroutineScope emptyMap()
        val deferredByWeekId = weeks.associate { week ->
            week.id.value to async {
                caricaAssegnazioni(week.weekStartDate)
            }
        }
        deferredByWeekId.mapValues { (_, deferred) -> deferred.await() }
    }

}

private fun buildProgramCreationFailureNotice(
    error: DomainError,
    rollbackError: DomainError? = null,
): FeedbackBannerModel {
    val baseDetails = when (error) {
        is DomainError.SettimanaSenzaTemplateENessunaParteFissa -> {
            val formattedDate = error.weekStartDate.format(
                DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ITALIAN),
            )
            "Schemi non aggiornati per la settimana del $formattedDate. Aggiorna schemi e riprova."
        }
        is DomainError.CatalogoSchemiIncoerente -> {
            "Catalogo schemi incoerente per la settimana ${error.weekStartDate}. Aggiorna schemi e riprova."
        }
        DomainError.CatalogoTipiNonDisponibile -> {
            "Catalogo parti non disponibile. Aggiorna schemi e riprova."
        }
        else -> error.toMessage()
    }
    val details = rollbackError?.let { rollback ->
        "$baseDetails | Ripristino automatico del mese non riuscito: ${rollback.toMessage()}"
    } ?: baseDetails
    return errorNotice(details)
}

internal fun resolveSelectedProgramId(
    previousSelectedId: ProgramMonthId?,
    currentProgram: ProgramMonth?,
    futurePrograms: List<ProgramMonth>,
): ProgramMonthId? {
    val ids = buildSet {
        currentProgram?.let { add(it.id) }
        futurePrograms.forEach { add(it.id) }
    }
    return when {
        previousSelectedId != null && previousSelectedId in ids -> previousSelectedId
        currentProgram != null -> currentProgram.id
        else -> futurePrograms.firstOrNull()?.id
    }
}

internal fun computeCreatableTargets(
    today: LocalDate,
    currentProgram: ProgramMonth?,
    futurePrograms: List<ProgramMonth>,
): List<YearMonth> {
    val referenceMonth = YearMonth.from(today)
    val window = listOf(referenceMonth, referenceMonth.plusMonths(1), referenceMonth.plusMonths(2))
    val existingByMonth = buildSet {
        currentProgram?.let { add(it.yearMonth) }
        futurePrograms.forEach { add(it.yearMonth) }
    }
    val futureMonths = futurePrograms.map { it.yearMonth }.toSet()
    return window.filter { target ->
        ProgramMonthAggregate.validateCreationTarget(target, today, existingByMonth, futureMonths) == null
    }
}

internal fun applyProgramSnapshot(
    state: ProgramLifecycleUiState,
    snapshot: ProgramSelectionSnapshot,
): ProgramLifecycleUiState {
    val creatableTargets = computeCreatableTargets(
        today = state.today,
        currentProgram = snapshot.current,
        futurePrograms = snapshot.futures,
    )
    val selectedProgramId = resolveSelectedProgramId(
        previousSelectedId = state.selectedProgramId,
        currentProgram = snapshot.current,
        futurePrograms = snapshot.futures,
    )
    val clearWeeks = selectedProgramId == null
    return state.copy(
        isLoading = false,
        currentProgram = snapshot.current,
        futurePrograms = snapshot.futures,
        creatableTargets = creatableTargets,
        selectedProgramId = selectedProgramId,
        selectedProgramWeeks = if (clearWeeks) emptyList() else state.selectedProgramWeeks,
        selectedProgramAssignments = if (clearWeeks) emptyMap() else state.selectedProgramAssignments,
        deleteImpactConfirm = null,
    )
}

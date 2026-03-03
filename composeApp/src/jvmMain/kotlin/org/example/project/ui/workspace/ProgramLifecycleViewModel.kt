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
import org.example.project.feature.assignments.application.CaricaAssegnazioniUseCase
import org.example.project.feature.assignments.domain.AssignmentWithPerson
import org.example.project.feature.programs.application.CaricaProgrammiAttiviUseCase
import org.example.project.feature.programs.application.CreaProssimoProgrammaUseCase
import org.example.project.feature.programs.application.EliminaProgrammaFuturoUseCase
import org.example.project.feature.programs.application.GeneraSettimaneProgrammaUseCase
import org.example.project.feature.programs.domain.ProgramMonth
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.schemas.application.SchemaTemplateStore
import org.example.project.feature.weeklyparts.application.CercaTipiParteUseCase
import org.example.project.feature.weeklyparts.application.WeekPlanStore
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.ui.components.FeedbackBannerKind
import org.example.project.ui.components.FeedbackBannerModel
import org.example.project.ui.components.errorNotice
import org.example.project.ui.components.executeAsyncOperation
import org.example.project.ui.components.executeEitherOperationWithNotice
import java.time.LocalDate
import java.time.YearMonth

private const val MAX_FUTURE_PROGRAMS = 2

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
    val selectedProgramId: String? = null,
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
            currentProgram?.id?.value -> currentProgram
            else -> futurePrograms.firstOrNull { it.id.value == selectedProgramId }
        }
    val selectedFutureProgram: ProgramMonth?
        get() = futurePrograms.firstOrNull { it.id.value == selectedProgramId }
    val canDeleteSelectedProgram: Boolean
        get() = selectedProgram != null
}

internal class ProgramLifecycleViewModel(
    private val scope: CoroutineScope,
    private val caricaProgrammiAttivi: CaricaProgrammiAttiviUseCase,
    private val creaProssimoProgramma: CreaProssimoProgrammaUseCase,
    private val eliminaProgramma: EliminaProgrammaFuturoUseCase,
    private val generaSettimaneProgramma: GeneraSettimaneProgrammaUseCase,
    private val schemaTemplateStore: SchemaTemplateStore,
    private val weekPlanStore: WeekPlanStore,
    private val caricaAssegnazioni: CaricaAssegnazioniUseCase,
    private val cercaTipiParte: CercaTipiParteUseCase,
) {
    private val _state = MutableStateFlow(ProgramLifecycleUiState())
    val state: StateFlow<ProgramLifecycleUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    fun onScreenEntered() {
        loadProgramsAndWeeks()
        loadPartTypes()
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun selectProgram(programId: String) {
        _state.update { it.copy(selectedProgramId = programId) }
        loadWeeksForSelectedProgram()
    }

    private fun loadPartTypes() {
        scope.launch {
            val partTypes = cercaTipiParte()
            _state.update { it.copy(partTypes = partTypes) }
        }
    }

    fun createNextProgram() {
        val target = _state.value.creatableTargets.firstOrNull() ?: return
        createProgramForTarget(target.year, target.monthValue)
    }

    fun createProgramForTarget(targetYear: Int, targetMonth: Int) {
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
                operation = { creaProssimoProgramma(targetYear, targetMonth, _state.value.today) },
                onSuccess = { program ->
                    _state.executeEitherOperationWithNotice(
                        loadingUpdate = { it },
                        noticeUpdate = { state, notice -> state.copy(notice = notice) },
                        successMessage = null,
                        operation = { generaSettimaneProgramma(program.id.value) },
                        onSuccess = { loadProgramsAndWeeks() },
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
                operation = { eliminaProgramma(ProgramMonthId(selectedProgramId), _state.value.today) },
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
                    state.copy(
                        isLoading = false,
                        currentProgram = snapshot.current,
                        futurePrograms = snapshot.futures,
                        creatableTargets = creatableTargets,
                        selectedProgramId = selectedProgramId,
                        deleteImpactConfirm = null,
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
            loadWeeksForSelectedProgram()
        }
    }

    private fun loadWeeksForSelectedProgram() {
        scope.launch {
            val selectedProgramId = _state.value.selectedProgramId ?: return@launch
            val weeks = weekPlanStore.listByProgram(selectedProgramId)
            val assignmentsByWeek = loadAssignmentsByWeek(weeks)
            _state.update {
                it.copy(
                    selectedProgramWeeks = weeks,
                    selectedProgramAssignments = assignmentsByWeek,
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

}

internal fun resolveSelectedProgramId(
    previousSelectedId: String?,
    currentProgram: ProgramMonth?,
    futurePrograms: List<ProgramMonth>,
): String? {
    val ids = buildSet {
        currentProgram?.let { add(it.id.value) }
        futurePrograms.forEach { add(it.id.value) }
    }
    return when {
        previousSelectedId != null && previousSelectedId in ids -> previousSelectedId
        currentProgram != null -> currentProgram.id.value
        else -> futurePrograms.firstOrNull()?.id?.value
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
    val hasCurrent = currentProgram != null
    val futureMonths = futurePrograms.map { it.yearMonth }.toSet()

    return window.filter { target ->
        if (target in existingByMonth) return@filter false

        val isCurrentTarget = target == referenceMonth
        val projectedFutureCount = futureMonths.size + if (isCurrentTarget) 0 else 1
        if (!isCurrentTarget && projectedFutureCount > MAX_FUTURE_PROGRAMS) return@filter false

        val projected = existingByMonth + target
        val plusOne = referenceMonth.plusMonths(1)
        val plusTwo = referenceMonth.plusMonths(2)
        if (plusTwo in projected && plusOne !in projected) return@filter false

        if (!hasCurrent && futureMonths.isEmpty() && target != plusOne) return@filter false
        true
    }
}

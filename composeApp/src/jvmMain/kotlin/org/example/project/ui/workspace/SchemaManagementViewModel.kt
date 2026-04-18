package org.example.project.ui.workspace

import arrow.core.Either
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.core.domain.DomainError
import org.example.project.core.domain.toMessage
import org.example.project.feature.programs.application.AggiornaProgrammaDaSchemiUseCase
import org.example.project.feature.programs.application.CaricaProgrammiAttiviUseCase
import org.example.project.feature.programs.application.SchemaRefreshMode
import org.example.project.feature.programs.application.SchemaRefreshPreview
import org.example.project.feature.programs.application.SchemaRefreshReport
import org.example.project.feature.programs.domain.ProgramMonth
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.schemas.application.AggiornaSchemiResult
import org.example.project.feature.schemas.application.AggiornaSchemiUseCase
import org.example.project.feature.schemas.application.SchemaTemplateStore
import org.example.project.ui.components.FeedbackBannerKind
import org.example.project.ui.components.FeedbackBannerModel
import org.example.project.ui.components.executeEitherOperation
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

internal fun isSchemaRefreshNeeded(lastSchemaImport: LocalDateTime?, selectedFutureProgram: ProgramMonth?): Boolean {
    if (selectedFutureProgram == null || lastSchemaImport == null) return false
    val appliedOrCreatedAt = selectedFutureProgram.templateAppliedAt ?: selectedFutureProgram.createdAt
    return appliedOrCreatedAt < lastSchemaImport
}

internal data class SchemaManagementUiState(
    val today: LocalDate = LocalDate.now(),
    val isRefreshingSchemas: Boolean = false,
    val isRefreshingProgramFromSchemas: Boolean = false,
    val impactedProgramIds: Set<ProgramMonthId> = emptySet(),
    val notice: FeedbackBannerModel? = null,
    val pendingRefreshPreview: SchemaRefreshPreview? = null,
    val pendingRefreshProgramId: ProgramMonthId? = null,
)

internal fun schemaRefreshReferenceDate(today: LocalDate): LocalDate =
    today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

internal class SchemaManagementViewModel(
    private val scope: CoroutineScope,
    private val aggiornaSchemi: AggiornaSchemiUseCase,
    private val aggiornaProgrammaDaSchemi: AggiornaProgrammaDaSchemiUseCase,
    private val caricaProgrammiAttivi: CaricaProgrammiAttiviUseCase,
    private val schemaTemplateStore: SchemaTemplateStore,
) {
    private val _state = MutableStateFlow(SchemaManagementUiState())
    val state: StateFlow<SchemaManagementUiState> = _state.asStateFlow()

    private var pendingOnComplete: (() -> Unit)? = null

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun refreshSchemasAndProgram(selectedProgramId: ProgramMonthId?, onProgramRefreshComplete: () -> Unit = {}) {
        if (_state.value.isRefreshingSchemas || _state.value.isRefreshingProgramFromSchemas) return
        scope.launch {
            val before = captureSchemaFingerprint()
            _state.update { it.copy(isRefreshingSchemas = true) }
            when (val updateResult = aggiornaSchemi()) {
                is Either.Left -> {
                    val error = updateResult.value
                    _state.update {
                        it.copy(
                            isRefreshingSchemas = false,
                            notice = FeedbackBannerModel(error.toMessage(), FeedbackBannerKind.ERROR),
                        )
                    }
                    onProgramRefreshComplete()
                }
                is Either.Right -> {
                    val result = updateResult.value
                    val after = captureSchemaFingerprint()
                    val allPrograms = when (val programsResult = loadCurrentAndFuturePrograms()) {
                        is Either.Left -> {
                            _state.update {
                                it.copy(
                                    isRefreshingSchemas = false,
                                    notice = FeedbackBannerModel(
                                        "Errore caricamento programmi: ${programsResult.value.toMessage()}",
                                        FeedbackBannerKind.ERROR,
                                    ),
                                )
                            }
                            onProgramRefreshComplete()
                            return@launch
                        }
                        is Either.Right -> programsResult.value
                    }
                    val validProgramIds = allPrograms.map { program -> program.id }.toSet()
                    val impactedProgramIds = calculateImpactedProgramIds(
                        allPrograms = allPrograms,
                        before = before,
                        after = after,
                    ).intersect(validProgramIds)
                    _state.update { state ->
                        state.copy(
                            isRefreshingSchemas = false,
                            impactedProgramIds = impactedProgramIds,
                            notice = FeedbackBannerModel(
                                buildSchemaUpdateNotice(result),
                                FeedbackBannerKind.SUCCESS,
                            ),
                        )
                    }
                    if (selectedProgramId != null) {
                        requestProgramRefreshPreview(selectedProgramId, onProgramRefreshComplete)
                    } else {
                        onProgramRefreshComplete()
                    }
                }
            }
        }
    }

    private suspend fun requestProgramRefreshPreview(
        programId: ProgramMonthId,
        onComplete: () -> Unit,
    ) {
        val referenceDate = schemaRefreshReferenceDate(_state.value.today)
        when (val fullPreview = aggiornaProgrammaDaSchemi(
            programId = programId,
            referenceDate = referenceDate,
            dryRun = true,
            mode = SchemaRefreshMode.ALL,
        )) {
            is Either.Left -> {
                _state.update {
                    it.copy(notice = FeedbackBannerModel(fullPreview.value.toMessage(), FeedbackBannerKind.ERROR))
                }
                onComplete()
            }
            is Either.Right -> {
                when (val onlyUnassignedPreview = aggiornaProgrammaDaSchemi(
                    programId = programId,
                    referenceDate = referenceDate,
                    dryRun = true,
                    mode = SchemaRefreshMode.ONLY_UNASSIGNED,
                )) {
                    is Either.Left -> {
                        _state.update {
                            it.copy(
                                notice = FeedbackBannerModel(
                                    onlyUnassignedPreview.value.toMessage(),
                                    FeedbackBannerKind.ERROR,
                                ),
                            )
                        }
                        onComplete()
                    }
                    is Either.Right -> {
                        val preview = SchemaRefreshPreview(
                            allChanges = fullPreview.value,
                            onlyUnassignedChanges = onlyUnassignedPreview.value,
                        )
                        if (preview.allChanges.weeksUpdated == 0 && preview.onlyUnassignedChanges.weeksUpdated == 0) {
                            onComplete()
                        } else {
                            pendingOnComplete = onComplete
                            _state.update {
                                it.copy(
                                    pendingRefreshPreview = preview,
                                    pendingRefreshProgramId = programId,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun confirmProgramRefreshAll() {
        val programId = _state.value.pendingRefreshProgramId ?: return
        val onComplete = pendingOnComplete ?: {}
        pendingOnComplete = null
        _state.update { it.copy(pendingRefreshPreview = null, pendingRefreshProgramId = null) }
        scope.launch { applyProgramRefresh(programId, SchemaRefreshMode.ALL, onComplete) }
    }

    fun confirmProgramRefreshOnlyUnassigned() {
        val programId = _state.value.pendingRefreshProgramId ?: return
        val onComplete = pendingOnComplete ?: {}
        pendingOnComplete = null
        _state.update { it.copy(pendingRefreshPreview = null, pendingRefreshProgramId = null) }
        scope.launch { applyProgramRefresh(programId, SchemaRefreshMode.ONLY_UNASSIGNED, onComplete) }
    }

    fun dismissProgramRefreshPreview() {
        val onComplete = pendingOnComplete ?: {}
        pendingOnComplete = null
        _state.update { it.copy(pendingRefreshPreview = null, pendingRefreshProgramId = null) }
        onComplete()
    }

    private suspend fun applyProgramRefresh(
        programId: ProgramMonthId,
        mode: SchemaRefreshMode,
        onComplete: () -> Unit,
    ) {
        val referenceDate = schemaRefreshReferenceDate(_state.value.today)
        _state.executeEitherOperation(
            loadingUpdate = { it.copy(isRefreshingProgramFromSchemas = true) },
            successUpdate = { state, report ->
                state.copy(
                    isRefreshingProgramFromSchemas = false,
                    notice = FeedbackBannerModel(
                        buildProgramRefreshNotice(report, mode),
                        FeedbackBannerKind.SUCCESS,
                    ),
                    impactedProgramIds = state.impactedProgramIds - programId,
                )
            },
            errorUpdate = { state, error ->
                state.copy(
                    isRefreshingProgramFromSchemas = false,
                    notice = FeedbackBannerModel(error.toMessage(), FeedbackBannerKind.ERROR),
                )
            },
            operation = {
                aggiornaProgrammaDaSchemi(
                    programId = programId,
                    referenceDate = referenceDate,
                    dryRun = false,
                    mode = mode,
                )
            },
        )
        onComplete()
    }

    private fun buildProgramRefreshNotice(report: SchemaRefreshReport, mode: SchemaRefreshMode): String {
        val prefix = when (mode) {
            SchemaRefreshMode.ALL -> "Programma aggiornato"
            SchemaRefreshMode.ONLY_UNASSIGNED -> "Programma aggiornato solo sulle parti non assegnate"
        }
        return "$prefix: ${report.weeksUpdated} settimane, ${report.assignmentsPreserved} preservate, ${report.assignmentsRemoved} rimosse"
    }

    private fun buildSchemaUpdateNotice(result: AggiornaSchemiResult): String {
        val base = "Schemi aggiornati: ${result.partTypesImported} tipi, ${result.weekTemplatesImported} settimane"
        return if (result.eligibilityAnomalies > 0) {
            "$base. Alcuni studenti potrebbero richiedere una verifica manuale."
        } else {
            base
        }
    }

    private suspend fun captureSchemaFingerprint(): Map<LocalDate, List<String>> {
        return schemaTemplateStore.listAll()
            .associate { template ->
                template.weekStartDate to template.partTypeIds.map { partTypeId -> partTypeId.value }
            }
    }

    private suspend fun loadCurrentAndFuturePrograms(): Either<DomainError, List<ProgramMonth>> {
        return caricaProgrammiAttivi(_state.value.today).map { snapshot ->
            buildList {
                snapshot.current?.let { add(it) }
                addAll(snapshot.futures)
            }
        }
    }

}

internal fun calculateImpactedProgramIds(
    allPrograms: List<ProgramMonth>,
    before: Map<LocalDate, List<String>>,
    after: Map<LocalDate, List<String>>,
): Set<ProgramMonthId> {
    val candidateWeeks = (before.keys + after.keys).filter { date -> before[date] != after[date] }.toSet()
    if (candidateWeeks.isEmpty()) return emptySet()

    return allPrograms
        .filter { program ->
            var weekStart = program.startDate
            while (!weekStart.isAfter(program.endDate)) {
                if (weekStart in candidateWeeks) return@filter true
                weekStart = weekStart.plusWeeks(1)
            }
            false
        }
        .map { program -> program.id }
        .toSet()
}

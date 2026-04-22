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
import org.example.project.feature.programs.application.AggiornaProgrammaDaSchemiOperation
import org.example.project.feature.programs.application.CaricaProgrammiAttiviOperation
import org.example.project.feature.programs.application.SchemaRefreshMode
import org.example.project.feature.programs.application.SchemaRefreshPreview
import org.example.project.feature.programs.application.SchemaRefreshReport
import org.example.project.feature.programs.application.WeekRefreshDetail
import org.example.project.feature.programs.application.hasEffectiveChanges
import org.example.project.feature.programs.domain.ProgramMonth
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.schemas.application.AggiornaSchemiOperation
import org.example.project.feature.schemas.application.AggiornaSchemiResult
import org.example.project.feature.schemas.application.SkippedPart
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
    val pendingUnknownParts: List<SkippedPart> = emptyList(),
    val pendingDownloadedIssues: List<String> = emptyList(),
    val showRefreshResultDialog: Boolean = false,
)

internal fun schemaRefreshReferenceDate(today: LocalDate): LocalDate =
    today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

internal class SchemaManagementViewModel(
    private val scope: CoroutineScope,
    private val aggiornaSchemi: AggiornaSchemiOperation,
    private val aggiornaProgrammaDaSchemi: AggiornaProgrammaDaSchemiOperation,
    private val caricaProgrammiAttivi: CaricaProgrammiAttiviOperation,
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
                    val impactedProgramIds = when (val impacted = calculateImpactedProgramIds(allPrograms)) {
                        is Either.Left -> {
                            _state.update {
                                it.copy(
                                    isRefreshingSchemas = false,
                                    notice = FeedbackBannerModel(
                                        "Errore analisi impatto programmi: ${impacted.value.toMessage()}",
                                        FeedbackBannerKind.ERROR,
                                    ),
                                )
                            }
                            onProgramRefreshComplete()
                            return@launch
                        }
                        is Either.Right -> impacted.value
                    }
                    _state.update { state ->
                        state.copy(
                            isRefreshingSchemas = false,
                            impactedProgramIds = impactedProgramIds,
                            notice = FeedbackBannerModel(
                                buildSchemaUpdateNotice(result),
                                FeedbackBannerKind.SUCCESS,
                            ),
                            pendingUnknownParts = result.skippedUnknownParts,
                            pendingDownloadedIssues = result.downloadedIssues,
                        )
                    }
                    if (selectedProgramId != null) {
                        requestProgramRefreshPreview(selectedProgramId, onProgramRefreshComplete)
                    } else if (result.skippedUnknownParts.isNotEmpty() || result.downloadedIssues.isNotEmpty()) {
                        pendingOnComplete = onProgramRefreshComplete
                        _state.update { it.copy(showRefreshResultDialog = true) }
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
                        val hasReport = _state.value.pendingUnknownParts.isNotEmpty() ||
                            _state.value.pendingDownloadedIssues.isNotEmpty()
                        if (!preview.hasEffectiveChanges() && !hasReport) {
                            onComplete()
                            return
                        }
                        pendingOnComplete = onComplete
                        _state.update {
                            it.copy(
                                pendingRefreshPreview = if (preview.hasEffectiveChanges()) preview else null,
                                pendingRefreshProgramId = programId,
                                showRefreshResultDialog = true,
                            )
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
        _state.update {
            it.copy(
                pendingRefreshPreview = null,
                pendingRefreshProgramId = null,
                pendingUnknownParts = emptyList(),
                pendingDownloadedIssues = emptyList(),
                showRefreshResultDialog = false,
            )
        }
        scope.launch { applyProgramRefresh(programId, SchemaRefreshMode.ALL, onComplete) }
    }

    fun confirmProgramRefreshOnlyUnassigned() {
        val programId = _state.value.pendingRefreshProgramId ?: return
        val onComplete = pendingOnComplete ?: {}
        pendingOnComplete = null
        _state.update {
            it.copy(
                pendingRefreshPreview = null,
                pendingRefreshProgramId = null,
                pendingUnknownParts = emptyList(),
                pendingDownloadedIssues = emptyList(),
                showRefreshResultDialog = false,
            )
        }
        scope.launch { applyProgramRefresh(programId, SchemaRefreshMode.ONLY_UNASSIGNED, onComplete) }
    }

    fun dismissRefreshResultDialog() {
        val onComplete = pendingOnComplete ?: {}
        pendingOnComplete = null
        _state.update {
            it.copy(
                pendingRefreshPreview = null,
                pendingRefreshProgramId = null,
                pendingUnknownParts = emptyList(),
                pendingDownloadedIssues = emptyList(),
                showRefreshResultDialog = false,
            )
        }
        onComplete()
    }

    fun dismissProgramRefreshPreview() {
        val onComplete = pendingOnComplete ?: {}
        pendingOnComplete = null
        _state.update {
            it.copy(
                pendingRefreshPreview = null,
                pendingRefreshProgramId = null,
                pendingUnknownParts = emptyList(),
                pendingDownloadedIssues = emptyList(),
                showRefreshResultDialog = false,
            )
        }
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
        val weeksChanged = report.changedWeeksCount()
        val partsAdded = report.partsAddedCount()
        val partsRemoved = report.partsRemovedCount()
        if (weeksChanged == 0) {
            return when (mode) {
                SchemaRefreshMode.ALL -> "Programma gia' allineato agli schemi. Nessuna settimana modificata."
                SchemaRefreshMode.ONLY_UNASSIGNED ->
                    "Programma gia' allineato sulle parti non assegnate. Nessuna settimana modificata."
            }
        }
        val prefix = when (mode) {
            SchemaRefreshMode.ALL -> "Programma aggiornato"
            SchemaRefreshMode.ONLY_UNASSIGNED -> "Programma aggiornato solo sulle parti non assegnate"
        }
        return buildString {
            append(prefix)
            append(": ")
            append("$weeksChanged settimane modificate")
            append(", $partsAdded parti aggiunte")
            append(", $partsRemoved parti rimosse")
            append(", ${report.assignmentsPreserved} assegnazioni preservate")
            append(", ${report.assignmentsRemoved} rimosse")
        }
    }

    private fun buildSchemaUpdateNotice(result: AggiornaSchemiResult): String =
        when {
            result.weekTemplatesChanged == 0 && result.weekTemplatesImported == 0 ->
                "Catalogo schemi invariato. Nessuna settimana nuova e nessuna modifica."
            result.weekTemplatesChanged == 0 ->
                "Catalogo schemi gia' allineato: ${result.weekTemplatesImported} settimane verificate, nulla cambiato."
            else ->
                "Catalogo schemi aggiornato: ${result.weekTemplatesChanged} settimane cambiate, " +
                    "${result.weekTemplatesUnchanged} gia' allineate."
        }

    private suspend fun loadCurrentAndFuturePrograms(): Either<DomainError, List<ProgramMonth>> {
        return caricaProgrammiAttivi(_state.value.today).map { snapshot ->
            buildList {
                snapshot.current?.let { add(it) }
                addAll(snapshot.futures)
            }
        }
    }

    private suspend fun calculateImpactedProgramIds(
        allPrograms: List<ProgramMonth>,
    ): Either<DomainError, Set<ProgramMonthId>> {
        val referenceDate = schemaRefreshReferenceDate(_state.value.today)
        val impactedProgramIds = linkedSetOf<ProgramMonthId>()

        for (program in allPrograms) {
            when (val preview = aggiornaProgrammaDaSchemi(
                programId = program.id,
                referenceDate = referenceDate,
                dryRun = true,
                mode = SchemaRefreshMode.ALL,
            )) {
                is Either.Left -> return Either.Left(preview.value)
                is Either.Right -> if (preview.value.hasEffectiveChanges()) {
                    impactedProgramIds += program.id
                }
            }
        }

        return Either.Right(impactedProgramIds)
    }
}

private fun SchemaRefreshReport.changedWeeksCount(): Int =
    weekDetails.count { it.hasEffectiveChanges() }

private fun SchemaRefreshReport.partsAddedCount(): Int =
    weekDetails.sumOf(WeekRefreshDetail::partsAdded)

private fun SchemaRefreshReport.partsRemovedCount(): Int =
    weekDetails.sumOf(WeekRefreshDetail::partsRemoved)

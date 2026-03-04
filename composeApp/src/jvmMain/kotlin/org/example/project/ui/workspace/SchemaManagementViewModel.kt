package org.example.project.ui.workspace

import arrow.core.Either
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.core.domain.toMessage
import org.example.project.feature.programs.application.AggiornaProgrammaDaSchemiUseCase
import org.example.project.feature.programs.application.CaricaProgrammiAttiviUseCase
import org.example.project.feature.programs.domain.ProgramMonth
import org.example.project.feature.schemas.application.AggiornaSchemiResult
import org.example.project.feature.schemas.application.AggiornaSchemiUseCase
import org.example.project.feature.schemas.application.SchemaTemplateStore
import org.example.project.ui.components.FeedbackBannerKind
import org.example.project.ui.components.FeedbackBannerModel
import org.example.project.ui.components.executeEitherOperation
import java.time.LocalDate
import java.time.LocalDateTime

internal fun isSchemaRefreshNeeded(lastSchemaImport: LocalDateTime?, selectedFutureProgram: ProgramMonth?): Boolean {
    if (selectedFutureProgram == null || lastSchemaImport == null) return false
    val appliedOrCreatedAt = selectedFutureProgram.templateAppliedAt ?: selectedFutureProgram.createdAt
    return appliedOrCreatedAt < lastSchemaImport
}

internal data class SchemaManagementUiState(
    val today: LocalDate = LocalDate.now(),
    val isRefreshingSchemas: Boolean = false,
    val isRefreshingProgramFromSchemas: Boolean = false,
    val impactedProgramIds: Set<String> = emptySet(),
    val notice: FeedbackBannerModel? = null,
)

internal class SchemaManagementViewModel(
    private val scope: CoroutineScope,
    private val aggiornaSchemi: AggiornaSchemiUseCase,
    private val aggiornaProgrammaDaSchemi: AggiornaProgrammaDaSchemiUseCase,
    private val caricaProgrammiAttivi: CaricaProgrammiAttiviUseCase,
    private val schemaTemplateStore: SchemaTemplateStore,
) {
    private val _state = MutableStateFlow(SchemaManagementUiState())
    val state: StateFlow<SchemaManagementUiState> = _state.asStateFlow()

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun refreshSchemasAndProgram(selectedProgramId: String?, onProgramRefreshComplete: () -> Unit = {}) {
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
                    val allPrograms = runCatching { loadCurrentAndFuturePrograms() }
                        .getOrElse { error ->
                            _state.update {
                                it.copy(
                                    isRefreshingSchemas = false,
                                    notice = FeedbackBannerModel(
                                        "Errore caricamento programmi: ${error.message}",
                                        FeedbackBannerKind.ERROR,
                                    ),
                                )
                            }
                            onProgramRefreshComplete()
                            return@launch
                        }
                    val validProgramIds = allPrograms.map { program -> program.id.value }.toSet()
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
                        applyProgramRefresh(selectedProgramId, onProgramRefreshComplete)
                    } else {
                        onProgramRefreshComplete()
                    }
                }
            }
        }
    }

    private suspend fun applyProgramRefresh(programId: String, onComplete: () -> Unit) {
        _state.executeEitherOperation(
            loadingUpdate = { it.copy(isRefreshingProgramFromSchemas = true) },
            successUpdate = { state, report ->
                state.copy(
                    isRefreshingProgramFromSchemas = false,
                    notice = FeedbackBannerModel(
                        "Programma aggiornato: ${report.weeksUpdated} settimane, ${report.assignmentsPreserved} preservate, ${report.assignmentsRemoved} rimosse",
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
            operation = { aggiornaProgrammaDaSchemi(programId, _state.value.today, dryRun = false) },
        )
        onComplete()
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

    private suspend fun loadCurrentAndFuturePrograms(): List<ProgramMonth> {
        val snapshot = caricaProgrammiAttivi(_state.value.today)
        return buildList {
            snapshot.current?.let { add(it) }
            addAll(snapshot.futures)
        }
    }

}

internal fun calculateImpactedProgramIds(
    allPrograms: List<ProgramMonth>,
    before: Map<LocalDate, List<String>>,
    after: Map<LocalDate, List<String>>,
): Set<String> {
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
        .map { program -> program.id.value }
        .toSet()
}

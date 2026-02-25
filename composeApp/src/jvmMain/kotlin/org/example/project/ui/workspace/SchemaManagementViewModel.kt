package org.example.project.ui.workspace

import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.core.domain.toMessage
import org.example.project.feature.programs.application.AggiornaProgrammaDaSchemiUseCase
import org.example.project.feature.programs.application.SchemaRefreshReport
import org.example.project.feature.programs.domain.ProgramMonth
import org.example.project.feature.schemas.application.AggiornaSchemiResult
import org.example.project.feature.schemas.application.AggiornaSchemiUseCase
import org.example.project.feature.schemas.application.SchemaTemplateStore
import org.example.project.ui.components.FeedbackBannerKind
import org.example.project.ui.components.FeedbackBannerModel
import org.example.project.ui.components.executeEitherOperation
import java.time.LocalDate
import java.time.LocalDateTime

internal fun isSchemaRefreshNeeded(lastSchemaImport: LocalDateTime?, futureProgram: ProgramMonth?): Boolean {
    if (futureProgram == null || lastSchemaImport == null) return false
    val appliedOrCreatedAt = futureProgram.templateAppliedAt ?: futureProgram.createdAt
    return appliedOrCreatedAt < lastSchemaImport
}

internal data class SchemaManagementUiState(
    val today: LocalDate = LocalDate.now(),
    val selectedProgramId: String? = null,
    val futureProgram: ProgramMonth? = null,
    val isRefreshingSchemas: Boolean = false,
    val isRefreshingProgramFromSchemas: Boolean = false,
    val schemaRefreshPreview: SchemaRefreshReport? = null,
    val futureNeedsSchemaRefresh: Boolean = false,
    val notice: FeedbackBannerModel? = null,
)

internal class SchemaManagementViewModel(
    private val scope: CoroutineScope,
    private val aggiornaSchemi: AggiornaSchemiUseCase,
    private val aggiornaProgrammaDaSchemi: AggiornaProgrammaDaSchemiUseCase,
    private val schemaTemplateStore: SchemaTemplateStore,
    private val settings: Settings,
) {
    private val _state = MutableStateFlow(SchemaManagementUiState())
    val state: StateFlow<SchemaManagementUiState> = _state.asStateFlow()

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun updateSelection(selectedProgramId: String?, futureProgram: ProgramMonth?) {
        _state.update {
            it.copy(
                selectedProgramId = selectedProgramId,
                futureProgram = futureProgram,
                futureNeedsSchemaRefresh = checkSchemaRefreshNeeded(futureProgram)
            )
        }
    }

    fun refreshSchemasAndProgram(onProgramRefreshComplete: () -> Unit = {}) {
        if (_state.value.isRefreshingSchemas || _state.value.isRefreshingProgramFromSchemas) return
        scope.launch {
            _state.executeEitherOperation(
                loadingUpdate = { it.copy(isRefreshingSchemas = true) },
                successUpdate = { state, result ->
                    state.copy(
                        isRefreshingSchemas = false,
                        notice = FeedbackBannerModel(
                            buildSchemaUpdateNotice(result),
                            FeedbackBannerKind.SUCCESS,
                        ),
                        futureNeedsSchemaRefresh = checkSchemaRefreshNeeded(state.futureProgram)
                    )
                },
                errorUpdate = { state, error ->
                    state.copy(
                        isRefreshingSchemas = false,
                        notice = FeedbackBannerModel(error.toMessage(), FeedbackBannerKind.ERROR),
                    )
                },
                operation = { aggiornaSchemi() },
            )
            if (_state.value.selectedProgramId != null) {
                refreshProgramFromSchemas(onProgramRefreshComplete)
            }
        }
    }

    fun refreshProgramFromSchemas(onComplete: () -> Unit = {}) {
        val programId = _state.value.selectedProgramId ?: return
        if (_state.value.isRefreshingProgramFromSchemas) return
        scope.launch {
            _state.executeEitherOperation(
                loadingUpdate = { it.copy(isRefreshingProgramFromSchemas = true) },
                successUpdate = { state, preview ->
                    state.copy(isRefreshingProgramFromSchemas = false, schemaRefreshPreview = preview)
                },
                errorUpdate = { state, error ->
                    state.copy(
                        isRefreshingProgramFromSchemas = false,
                        notice = FeedbackBannerModel(error.toMessage(), FeedbackBannerKind.ERROR),
                    )
                },
                operation = { aggiornaProgrammaDaSchemi(programId, _state.value.today, dryRun = true) },
            )
        }
    }

    fun confirmSchemaRefresh(onComplete: () -> Unit = {}) {
        val programId = _state.value.selectedProgramId ?: return
        scope.launch {
            _state.update { it.copy(schemaRefreshPreview = null) }
            _state.executeEitherOperation(
                loadingUpdate = { it.copy(isRefreshingProgramFromSchemas = true) },
                successUpdate = { state, report ->
                    state.copy(
                        isRefreshingProgramFromSchemas = false,
                        notice = FeedbackBannerModel(
                            "Programma aggiornato: ${report.weeksUpdated} settimane, ${report.assignmentsPreserved} preservate, ${report.assignmentsRemoved} rimosse",
                            FeedbackBannerKind.SUCCESS,
                        ),
                        futureNeedsSchemaRefresh = false
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
    }

    fun dismissSchemaRefresh() {
        _state.update { it.copy(schemaRefreshPreview = null) }
    }

    private fun buildSchemaUpdateNotice(result: AggiornaSchemiResult): String {
        val base = "Schemi aggiornati: ${result.partTypesImported} tipi, ${result.weekTemplatesImported} settimane"
        return if (result.eligibilityAnomalies > 0) {
            "$base. Alcune persone potrebbero richiedere una verifica manuale."
        } else {
            base
        }
    }

    private fun checkSchemaRefreshNeeded(futureProgram: ProgramMonth?): Boolean {
        val lastSchemaImport = settings.getStringOrNull("last_schema_import_at")
            ?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
        return isSchemaRefreshNeeded(lastSchemaImport, futureProgram)
    }
}

package org.example.project.ui.workspace

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.example.project.feature.weeklyparts.application.CercaTipiParteUseCase
import org.example.project.feature.weeklyparts.application.WeekPlanStore
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeekPlanStatus
import org.example.project.feature.weeklyparts.domain.WeeklyPart
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import org.example.project.ui.components.FeedbackBannerKind
import org.example.project.ui.components.FeedbackBannerModel
import org.example.project.ui.components.executeAsyncOperationWithNotice
import org.example.project.ui.components.formatWeekRangeLabel
import java.time.LocalDate
import java.util.UUID

internal data class PartEditorUiState(
    val today: LocalDate = LocalDate.now(),
    val partEditorWeekId: String? = null,
    val partEditorParts: List<WeeklyPart> = emptyList(),
    val isSavingPartEditor: Boolean = false,
    val partTypes: List<PartType> = emptyList(),
    val notice: FeedbackBannerModel? = null,
) {
    val editablePartTypes: List<PartType> get() = partTypes.filterNot { it.fixed }
    val isPartEditorOpen: Boolean get() = partEditorWeekId != null
}

internal class PartEditorViewModel(
    private val scope: CoroutineScope,
    private val weekPlanStore: WeekPlanStore,
    private val cercaTipiParte: CercaTipiParteUseCase,
) {
    private val _state = MutableStateFlow(PartEditorUiState())
    val state: StateFlow<PartEditorUiState> = _state.asStateFlow()

    init {
        loadPartTypes()
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun openPartEditor(week: WeekPlan) {
        if (!canMutateWeek(week)) return
        _state.update {
            it.copy(
                partEditorWeekId = week.id.value,
                partEditorParts = week.parts.sortedBy { part -> part.sortOrder },
                isSavingPartEditor = false,
            )
        }
    }

    fun dismissPartEditor() {
        _state.update {
            it.copy(
                partEditorWeekId = null,
                partEditorParts = emptyList(),
                isSavingPartEditor = false,
            )
        }
    }

    fun addPartToEditor(partTypeId: PartTypeId) {
        val current = _state.value
        val partType = current.editablePartTypes.firstOrNull { it.id == partTypeId } ?: return
        val parts = current.partEditorParts
        val newPart = WeeklyPart(
            id = WeeklyPartId(UUID.randomUUID().toString()),
            partType = partType,
            sortOrder = parts.size,
        )
        _state.update { it.copy(partEditorParts = parts + newPart) }
    }

    fun movePartInEditor(fromIndex: Int, toIndex: Int) {
        val parts = _state.value.partEditorParts.toMutableList()
        if (fromIndex !in parts.indices || toIndex !in parts.indices || fromIndex == toIndex) return
        val moved = parts.removeAt(fromIndex)
        parts.add(toIndex, moved)
        _state.update {
            it.copy(
                partEditorParts = parts.mapIndexed { index, part -> part.copy(sortOrder = index) },
            )
        }
    }

    fun removePartFromEditor(partId: WeeklyPartId) {
        val parts = _state.value.partEditorParts
        val target = parts.firstOrNull { it.id == partId } ?: return
        if (target.partType.fixed) return
        _state.update {
            it.copy(
                partEditorParts = parts
                    .filter { part -> part.id != partId }
                    .mapIndexed { index, part -> part.copy(sortOrder = index) },
            )
        }
    }

    fun savePartEditor(onSuccess: () -> Unit) {
        val current = _state.value
        val weekId = current.partEditorWeekId ?: return
        if (current.isSavingPartEditor) return
        val orderedParts = current.partEditorParts.sortedBy { it.sortOrder }
        if (orderedParts.isEmpty()) {
            _state.update {
                it.copy(notice = FeedbackBannerModel("Aggiungi almeno una parte", FeedbackBannerKind.ERROR))
            }
            return
        }

        scope.launch {
            _state.executeAsyncOperationWithNotice(
                loadingUpdate = { it.copy(isSavingPartEditor = true) },
                noticeUpdate = { state, notice ->
                    state.copy(
                        partEditorWeekId = null,
                        partEditorParts = emptyList(),
                        isSavingPartEditor = false,
                        notice = notice,
                    )
                },
                successMessage = "Parti settimana aggiornate",
                errorMessagePrefix = "Errore salvataggio parti",
                operation = {
                    weekPlanStore.replaceAllParts(
                        WeekPlanId(weekId),
                        orderedParts.map { it.partType.id },
                    )
                },
                onSuccess = { onSuccess() },
            )
        }
    }

    fun reactivateWeek(week: WeekPlan, onSuccess: () -> Unit) {
        scope.launch {
            val weekLabel = formatWeekRangeLabel(week.weekStartDate, week.weekStartDate.plusDays(6))
            _state.executeAsyncOperationWithNotice(
                loadingUpdate = { it },
                noticeUpdate = { state, notice -> state.copy(notice = notice) },
                successMessage = "Settimana $weekLabel riattivata",
                errorMessagePrefix = "Errore riattivazione",
                operation = { weekPlanStore.updateWeekStatus(week.id, WeekPlanStatus.ACTIVE) },
                onSuccess = { onSuccess() },
            )
        }
    }

    private fun canMutateWeek(week: WeekPlan): Boolean =
        week.weekStartDate >= _state.value.today && week.status == WeekPlanStatus.ACTIVE

    private fun loadPartTypes() {
        scope.launch {
            try {
                val types = cercaTipiParte()
                _state.update { it.copy(partTypes = types) }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        notice = FeedbackBannerModel(
                            "Errore nel caricamento tipi parte: ${error.message}",
                            FeedbackBannerKind.ERROR,
                        ),
                    )
                }
            }
        }
    }
}

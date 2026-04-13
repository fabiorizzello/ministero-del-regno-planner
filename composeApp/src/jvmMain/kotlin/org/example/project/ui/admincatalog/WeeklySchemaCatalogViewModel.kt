package org.example.project.ui.admincatalog

import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.feature.schemas.application.SchemaTemplateStore
import org.example.project.feature.weeklyparts.application.PartTypeStore
import org.example.project.ui.components.errorNotice

internal class WeeklySchemaCatalogViewModel(
    private val scope: CoroutineScope,
    private val schemaTemplateStore: SchemaTemplateStore,
    private val partTypeStore: PartTypeStore,
) {
    private val detailByWeek = mutableMapOf<LocalDate, WeeklySchemaDetail>()
    private val _uiState = MutableStateFlow(WeeklySchemaCatalogUiState())
    val uiState: StateFlow<WeeklySchemaCatalogUiState> = _uiState.asStateFlow()

    fun onScreenEntered() {
        reload()
    }

    fun reload() {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, notice = null) }
            runCatching {
                val templates = schemaTemplateStore.listAll()
                val partTypes = partTypeStore.allWithStatus()
                val weeks = buildWeeklySchemaCatalogItems(templates)
                val details = buildWeeklySchemaCatalogDetailMap(templates, partTypes)
                weeks to details
            }.fold(
                onSuccess = { (weeks, details) ->
                    detailByWeek.clear()
                    detailByWeek.putAll(details)
                    val selectedWeek = resolveSelectedWeekStartDate(
                        previousSelectedDate = _uiState.value.selectedWeekStartDate,
                        items = weeks,
                    )
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            weeks = weeks,
                            selectedWeekStartDate = selectedWeek,
                            selectedDetail = selectedWeek?.let(details::get),
                            notice = null,
                        )
                    }
                },
                onFailure = { error ->
                    detailByWeek.clear()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            weeks = emptyList(),
                            selectedWeekStartDate = null,
                            selectedDetail = null,
                            notice = errorNotice("Impossibile caricare gli schemi settimanali: ${error.message}"),
                        )
                    }
                },
            )
        }
    }

    fun selectWeek(weekStartDate: LocalDate) {
        _uiState.update {
            it.copy(
                selectedWeekStartDate = weekStartDate,
                selectedDetail = detailByWeek[weekStartDate],
            )
        }
    }

    fun dismissNotice() {
        _uiState.update { it.copy(notice = null) }
    }
}

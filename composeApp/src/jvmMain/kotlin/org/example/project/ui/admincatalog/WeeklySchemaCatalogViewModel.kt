package org.example.project.ui.admincatalog

import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.feature.schemas.application.CaricaCatalogoSchemiSettimanaliUseCase
import org.example.project.ui.components.errorNotice
import org.example.project.ui.components.executeAsyncOperation

internal class WeeklySchemaCatalogViewModel(
    private val scope: CoroutineScope,
    private val caricaCatalogoSchemiSettimanali: CaricaCatalogoSchemiSettimanaliUseCase,
) {
    private val _uiState = MutableStateFlow(WeeklySchemaCatalogUiState())
    val uiState: StateFlow<WeeklySchemaCatalogUiState> = _uiState.asStateFlow()

    fun onScreenEntered() {
        reload()
    }

    fun reload() {
        scope.launch {
            _uiState.executeAsyncOperation(
                loadingUpdate = { it.copy(isLoading = true, notice = null) },
                successUpdate = { state, catalogo ->
                    val weeks = buildWeeklySchemaCatalogItems(catalogo.templates)
                    val details = buildWeeklySchemaCatalogDetailMap(catalogo.templates, catalogo.partTypes)
                    val selectedWeek = resolveSelectedWeekStartDate(
                        previousSelectedDate = state.selectedWeekStartDate,
                        items = weeks,
                    )
                    state.copy(
                        isLoading = false,
                        weeks = weeks,
                        selectedWeekStartDate = selectedWeek,
                        selectedDetail = selectedWeek?.let(details::get),
                        cachedDetails = details,
                        notice = null,
                    )
                },
                errorUpdate = { state, error ->
                    state.copy(
                        isLoading = false,
                        weeks = emptyList(),
                        selectedWeekStartDate = null,
                        selectedDetail = null,
                        cachedDetails = emptyMap(),
                        notice = errorNotice("Impossibile caricare gli schemi settimanali: ${error.message}"),
                    )
                },
                operation = { caricaCatalogoSchemiSettimanali() },
            )
        }
    }

    fun selectWeek(weekStartDate: LocalDate) {
        _uiState.update { state ->
            state.copy(
                selectedWeekStartDate = weekStartDate,
                selectedDetail = state.cachedDetails[weekStartDate],
            )
        }
    }

    fun dismissNotice() {
        _uiState.update { it.copy(notice = null) }
    }
}

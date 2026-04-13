package org.example.project.ui.admincatalog

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.feature.weeklyparts.application.CaricaCatalogoTipiParteUseCase
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.ui.components.errorNotice
import org.example.project.ui.components.executeAsyncOperation

internal class PartTypeCatalogViewModel(
    private val scope: CoroutineScope,
    private val caricaCatalogoTipiParte: CaricaCatalogoTipiParteUseCase,
) {
    private val _uiState = MutableStateFlow(PartTypeCatalogUiState())
    val uiState: StateFlow<PartTypeCatalogUiState> = _uiState.asStateFlow()

    fun onScreenEntered() {
        reload()
    }

    fun reload() {
        scope.launch {
            _uiState.executeAsyncOperation(
                loadingUpdate = { it.copy(isLoading = true, notice = null) },
                successUpdate = { state, loaded ->
                    val sorted = loaded
                        .map { it.toPartTypeCatalogItem() }
                        .sortedWith(compareBy<PartTypeCatalogItem> { !it.active }.thenBy { it.code })
                    val selectedId = resolveSelectedPartTypeId(state.selectedId, sorted)
                    state.copy(
                        isLoading = false,
                        items = sorted,
                        selectedId = selectedId,
                        selectedDetail = sorted.firstOrNull { item -> item.id == selectedId }?.toDetail(),
                        notice = null,
                    )
                },
                errorUpdate = { state, error ->
                    state.copy(
                        isLoading = false,
                        items = emptyList(),
                        selectedId = null,
                        selectedDetail = null,
                        notice = errorNotice("Impossibile caricare il catalogo tipi parte: ${error.message}"),
                    )
                },
                operation = { caricaCatalogoTipiParte() },
            )
        }
    }

    fun selectItem(id: PartTypeId) {
        _uiState.update { state ->
            state.copy(
                selectedId = id,
                selectedDetail = state.items.firstOrNull { item -> item.id == id }?.toDetail(),
            )
        }
    }

    fun dismissNotice() {
        _uiState.update { it.copy(notice = null) }
    }
}

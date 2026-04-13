package org.example.project.ui.admincatalog

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.feature.weeklyparts.application.CaricaCatalogoTipiParteUseCase
import org.example.project.feature.weeklyparts.application.CaricaRevisioniTipoParteUseCase
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.ui.components.errorNotice
import org.example.project.ui.components.executeAsyncOperation

internal class PartTypeCatalogViewModel(
    private val scope: CoroutineScope,
    private val caricaCatalogoTipiParte: CaricaCatalogoTipiParteUseCase,
    private val caricaRevisioniTipoParte: CaricaRevisioniTipoParteUseCase,
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
                        revisionsForSelected = emptyList(),
                        cachedRevisions = emptyMap(),
                        isLoadingRevisions = false,
                        notice = null,
                    )
                },
                errorUpdate = { state, error ->
                    state.copy(
                        isLoading = false,
                        items = emptyList(),
                        selectedId = null,
                        selectedDetail = null,
                        revisionsForSelected = emptyList(),
                        cachedRevisions = emptyMap(),
                        isLoadingRevisions = false,
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
                revisionsForSelected = state.cachedRevisions[id].orEmpty(),
                isLoadingRevisions = false,
            )
        }
        val current = _uiState.value
        if (current.viewMode == PartTypeDetailViewMode.Cronologia && current.cachedRevisions[id] == null) {
            loadRevisionsFor(id)
        }
    }

    fun setViewMode(mode: PartTypeDetailViewMode) {
        val current = _uiState.value
        if (current.viewMode == mode) return
        _uiState.update { it.copy(viewMode = mode) }
        if (mode == PartTypeDetailViewMode.Cronologia) {
            val id = current.selectedId ?: return
            if (current.cachedRevisions[id] == null) {
                loadRevisionsFor(id)
            } else {
                _uiState.update { it.copy(revisionsForSelected = it.cachedRevisions[id].orEmpty()) }
            }
        }
    }

    private fun loadRevisionsFor(id: PartTypeId) {
        scope.launch {
            _uiState.executeAsyncOperation(
                loadingUpdate = { it.copy(isLoadingRevisions = true) },
                successUpdate = { state, rows ->
                    if (state.selectedId != id) return@executeAsyncOperation state
                    val items = rows.map { it.toListItem() }
                    state.copy(
                        isLoadingRevisions = false,
                        revisionsForSelected = items,
                        cachedRevisions = state.cachedRevisions + (id to items),
                    )
                },
                errorUpdate = { state, error ->
                    state.copy(
                        isLoadingRevisions = false,
                        revisionsForSelected = emptyList(),
                        notice = errorNotice("Impossibile caricare la cronologia: ${error.message}"),
                    )
                },
                operation = { caricaRevisioniTipoParte(id) },
            )
        }
    }

    fun dismissNotice() {
        _uiState.update { it.copy(notice = null) }
    }
}

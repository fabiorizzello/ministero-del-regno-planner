package org.example.project.ui.admincatalog

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.feature.weeklyparts.application.PartTypeStore
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.ui.components.errorNotice

internal class PartTypeCatalogViewModel(
    private val scope: CoroutineScope,
    private val partTypeStore: PartTypeStore,
) {
    private val _uiState = MutableStateFlow(PartTypeCatalogUiState())
    val uiState: StateFlow<PartTypeCatalogUiState> = _uiState.asStateFlow()

    fun onScreenEntered() {
        reload()
    }

    fun reload() {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, notice = null) }
            runCatching {
                partTypeStore.allWithStatus()
                    .map { it.toPartTypeCatalogItem() }
                    .sortedWith(compareBy<PartTypeCatalogItem> { !it.active }.thenBy { it.code })
            }.fold(
                onSuccess = { items ->
                    val selectedId = resolveSelectedPartTypeId(_uiState.value.selectedId, items)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            items = items,
                            selectedId = selectedId,
                            selectedDetail = items.firstOrNull { item -> item.id == selectedId }?.toDetail(),
                            notice = null,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            items = emptyList(),
                            selectedId = null,
                            selectedDetail = null,
                            notice = errorNotice("Impossibile caricare il catalogo tipi parte: ${error.message}"),
                        )
                    }
                },
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

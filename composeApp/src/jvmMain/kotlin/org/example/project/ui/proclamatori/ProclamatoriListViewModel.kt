package org.example.project.ui.proclamatori

import arrow.core.Either
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.example.project.core.domain.DomainError
import org.example.project.feature.people.application.CercaProclamatoriUseCase
import org.example.project.feature.people.application.EliminaProclamatoreUseCase
import org.example.project.feature.people.application.ImpostaStatoProclamatoreUseCase
import org.example.project.feature.people.application.ImportaProclamatoriDaJsonUseCase
import org.example.project.feature.assignments.application.ContaAssegnazioniPersonaUseCase
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.ui.components.FeedbackBannerModel

internal data class ProclamatoriListUiState(
    val searchTerm: String = "",
    val allItems: List<Proclamatore> = emptyList(),
    val isLoading: Boolean = true,
    val notice: FeedbackBannerModel? = null,
    val sort: ProclamatoriSort = ProclamatoriSort(),
    val pageIndex: Int = 0,
    val pageSize: Int = 10,
    val selectedIds: Set<ProclamatoreId> = emptySet(),
    val deleteCandidate: Proclamatore? = null,
    val deleteAssignmentCount: Int = 0,
    val showBatchDeleteConfirm: Boolean = false,
    val isImporting: Boolean = false,
)

internal class ProclamatoriListViewModel(
    private val scope: CoroutineScope,
    private val cerca: CercaProclamatoriUseCase,
    private val impostaStato: ImpostaStatoProclamatoreUseCase,
    private val elimina: EliminaProclamatoreUseCase,
    private val importaDaJson: ImportaProclamatoriDaJsonUseCase,
    private val contaAssegnazioni: ContaAssegnazioniPersonaUseCase,
) {
    private val _uiState = MutableStateFlow(ProclamatoriListUiState())
    val uiState: StateFlow<ProclamatoriListUiState> = _uiState.asStateFlow()

    init {
        refreshList(resetPage = true)
    }

    fun refreshList(resetPage: Boolean = false) {
        scope.launch { refreshListInternal(resetPage) }
    }

    fun setSearchTerm(value: String) {
        _uiState.update { it.copy(searchTerm = value) }
        refreshList(resetPage = true)
    }

    fun resetSearch() {
        if (_uiState.value.searchTerm.isBlank()) return
        setSearchTerm("")
    }

    fun dismissNotice() {
        _uiState.update { it.copy(notice = null) }
    }

    fun setNotice(notice: FeedbackBannerModel) {
        _uiState.update { it.copy(notice = notice) }
    }

    fun setSort(nextSort: ProclamatoriSort) {
        _uiState.update { it.copy(sort = nextSort) }
    }

    fun goToPreviousPage() {
        _uiState.update { it.copy(pageIndex = (it.pageIndex - 1).coerceAtLeast(0)) }
    }

    fun goToNextPage() {
        _uiState.update { state ->
            val totalPages = if (state.allItems.isEmpty()) 1 else ((state.allItems.size - 1) / state.pageSize) + 1
            state.copy(pageIndex = (state.pageIndex + 1).coerceAtMost(totalPages - 1))
        }
    }

    fun toggleSelectPage(pageIds: List<ProclamatoreId>, checked: Boolean) {
        _uiState.update { state ->
            state.copy(
                selectedIds = if (checked) {
                    state.selectedIds + pageIds
                } else {
                    state.selectedIds - pageIds.toSet()
                },
            )
        }
    }

    fun setRowSelected(id: ProclamatoreId, checked: Boolean) {
        _uiState.update { state ->
            state.copy(
                selectedIds = if (checked) {
                    state.selectedIds + id
                } else {
                    state.selectedIds - id
                },
            )
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet()) }
    }

    fun requestDeleteCandidate(candidate: Proclamatore) {
        scope.launch {
            val count = try {
                contaAssegnazioni(candidate.id)
            } catch (_: Exception) {
                -1
            }
            _uiState.update { it.copy(deleteCandidate = candidate, deleteAssignmentCount = count) }
        }
    }

    fun dismissDeleteCandidate() {
        _uiState.update { it.copy(deleteCandidate = null) }
    }

    fun confirmDeleteCandidate() {
        val candidate = _uiState.value.deleteCandidate ?: return
        scope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = elimina(candidate.id)
            result.fold(
                ifLeft = { err ->
                    _uiState.update {
                        it.copy(
                            notice = errorNotice(
                                (err as? DomainError.Validation)?.message ?: "Rimozione non completata",
                            ),
                        )
                    }
                },
                ifRight = {
                    _uiState.update {
                        it.copy(
                            notice = successNotice(
                                details = "Rimosso ${personDetails(candidate.nome, candidate.cognome)}",
                            ),
                        )
                    }
                    refreshListInternal()
                },
            )
            _uiState.update { it.copy(deleteCandidate = null, isLoading = false) }
        }
    }

    fun requestBatchDeleteConfirm() {
        if (_uiState.value.selectedIds.isEmpty()) return
        _uiState.update { it.copy(showBatchDeleteConfirm = true) }
    }

    fun dismissBatchDeleteConfirm() {
        _uiState.update { it.copy(showBatchDeleteConfirm = false) }
    }

    fun confirmBatchDelete() {
        scope.launch {
            val resultNotice = executeOnSelected(
                action = { id -> elimina(id) },
                completedLabel = "Proclamatori rimossi",
                noneCompletedLabel = "Nessun proclamatore rimosso",
            )
            _uiState.update { it.copy(showBatchDeleteConfirm = false, notice = resultNotice) }
        }
    }

    fun activateSelected() {
        scope.launch {
            val notice = executeOnSelected(
                action = { id -> impostaStato(id, true) },
                completedLabel = "Proclamatori attivati",
                noneCompletedLabel = "Nessun proclamatore attivato",
            )
            _uiState.update { it.copy(notice = notice) }
        }
    }

    fun deactivateSelected() {
        scope.launch {
            val notice = executeOnSelected(
                action = { id -> impostaStato(id, false) },
                completedLabel = "Proclamatori disattivati",
                noneCompletedLabel = "Nessun proclamatore disattivato",
            )
            _uiState.update { it.copy(notice = notice) }
        }
    }

    fun toggleActive(id: ProclamatoreId, next: Boolean) {
        scope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = impostaStato(id, next)
            result.fold(
                ifLeft = { err ->
                    _uiState.update {
                        it.copy(
                            notice = errorNotice(
                                (err as? DomainError.Validation)?.message ?: "Operazione non completata",
                            ),
                        )
                    }
                },
                ifRight = {
                    _uiState.update {
                        it.copy(
                            notice = successNotice(
                                if (next) "Proclamatore attivato" else "Proclamatore disattivato",
                            ),
                        )
                    }
                },
            )
            refreshListInternal()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun importFromJsonFile(selectedFile: File) {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, isImporting = true) }
            val jsonContent = withContext(Dispatchers.IO) {
                runCatching { selectedFile.readText(Charsets.UTF_8) }.getOrNull()
            }
            if (jsonContent == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isImporting = false,
                        notice = errorNotice("Impossibile leggere il file selezionato"),
                    )
                }
                return@launch
            }

            val result = withContext(Dispatchers.IO) { importaDaJson(jsonContent) }
            result.fold(
                ifLeft = { err ->
                    _uiState.update {
                        it.copy(
                            notice = errorNotice(
                                (err as? DomainError.Validation)?.message ?: "Import non completato",
                            ),
                        )
                    }
                },
                ifRight = { imported ->
                    _uiState.update {
                        it.copy(
                            notice = successNotice("Importati ${imported.importati} proclamatori da ${selectedFile.name}"),
                        )
                    }
                    refreshListInternal(resetPage = true)
                },
            )
            _uiState.update { it.copy(isLoading = false, isImporting = false) }
        }
    }

    private suspend fun executeOnSelected(
        action: suspend (ProclamatoreId) -> Either<DomainError, Unit>,
        completedLabel: String,
        noneCompletedLabel: String,
    ): FeedbackBannerModel {
        _uiState.update { it.copy(isLoading = true) }
        val selected = _uiState.value.selectedIds
        val result = runMultiAction(selected, action)
        refreshListInternal()
        _uiState.update {
            it.copy(
                selectedIds = result.failedIds,
                isLoading = false,
            )
        }
        return noticeForMultiAction(
            result = result,
            completedLabel = completedLabel,
            noneCompletedLabel = noneCompletedLabel,
        )
    }

    private suspend fun refreshListInternal(resetPage: Boolean = false) {
        _uiState.update { it.copy(isLoading = true) }
        val state = _uiState.value
        val allItems = cerca(state.searchTerm)
        val validIds = allItems.map { it.id }.toSet()
        val selected = state.selectedIds.filterTo(mutableSetOf()) { it in validIds }
        var nextPageIndex = if (resetPage) 0 else state.pageIndex
        val maxPage = (allItems.size - 1).coerceAtLeast(0) / state.pageSize
        if (nextPageIndex > maxPage) nextPageIndex = maxPage

        _uiState.update {
            it.copy(
                allItems = allItems,
                selectedIds = selected,
                pageIndex = nextPageIndex,
                isLoading = false,
            )
        }
    }
}

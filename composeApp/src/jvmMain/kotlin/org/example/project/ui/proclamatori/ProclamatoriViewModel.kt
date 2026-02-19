package org.example.project.ui.proclamatori

import arrow.core.Either
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.example.project.core.domain.DomainError
import org.example.project.feature.people.application.AggiornaProclamatoreUseCase
import org.example.project.feature.people.application.CaricaProclamatoreUseCase
import org.example.project.feature.people.application.CercaProclamatoriUseCase
import org.example.project.feature.people.application.CreaProclamatoreUseCase
import org.example.project.feature.people.application.EliminaProclamatoreUseCase
import org.example.project.feature.people.application.ImpostaStatoProclamatoreUseCase
import org.example.project.feature.people.application.ImportaProclamatoriDaJsonUseCase
import org.example.project.feature.people.application.VerificaDuplicatoProclamatoreUseCase
import org.example.project.feature.assignments.application.AssignmentStore
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.domain.Sesso
import org.example.project.ui.components.FeedbackBannerModel

internal data class ProclamatoriUiState(
    val searchTerm: String = "",
    val allItems: List<Proclamatore> = emptyList(),
    val isLoading: Boolean = true,
    val notice: FeedbackBannerModel? = null,
    val formError: String? = null,
    val duplicateError: String? = null,
    val isCheckingDuplicate: Boolean = false,
    val sort: ProclamatoriSort = ProclamatoriSort(),
    val pageIndex: Int = 0,
    val pageSize: Int = 10,
    val initialNome: String = "",
    val initialCognome: String = "",
    val initialSesso: Sesso = Sesso.M,
    val nome: String = "",
    val cognome: String = "",
    val sesso: Sesso = Sesso.M,
    val showFieldErrors: Boolean = false,
    val selectedIds: Set<ProclamatoreId> = emptySet(),
    val deleteCandidate: Proclamatore? = null,
    val deleteAssignmentCount: Int = 0,
    val showBatchDeleteConfirm: Boolean = false,
    val isImporting: Boolean = false,
)

internal class ProclamatoriViewModel(
    private val scope: CoroutineScope,
    private val cerca: CercaProclamatoriUseCase,
    private val carica: CaricaProclamatoreUseCase,
    private val crea: CreaProclamatoreUseCase,
    private val aggiorna: AggiornaProclamatoreUseCase,
    private val impostaStato: ImpostaStatoProclamatoreUseCase,
    private val elimina: EliminaProclamatoreUseCase,
    private val importaDaJson: ImportaProclamatoriDaJsonUseCase,
    private val verificaDuplicato: VerificaDuplicatoProclamatoreUseCase,
    private val assignmentStore: AssignmentStore,
) {
    private val _uiState = MutableStateFlow(ProclamatoriUiState())
    val uiState: StateFlow<ProclamatoriUiState> = _uiState.asStateFlow()

    private var duplicateCheckJob: Job? = null

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

    fun setNome(value: String) {
        _uiState.update { it.copy(nome = value) }
    }

    fun setCognome(value: String) {
        _uiState.update { it.copy(cognome = value) }
    }

    fun setSesso(value: Sesso) {
        _uiState.update { it.copy(sesso = value) }
    }

    fun prepareForManualNavigation() {
        dismissNotice()
        clearSelection()
        clearForm()
    }

    fun loadForEdit(id: ProclamatoreId, onSuccess: () -> Unit) {
        scope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val loaded = carica(id)
            if (loaded == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        notice = errorNotice("Proclamatore non trovato"),
                    )
                }
                return@launch
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    notice = null,
                    initialNome = loaded.nome,
                    initialCognome = loaded.cognome,
                    initialSesso = loaded.sesso,
                    nome = loaded.nome,
                    cognome = loaded.cognome,
                    sesso = loaded.sesso,
                    formError = null,
                    duplicateError = null,
                    isCheckingDuplicate = false,
                    showFieldErrors = false,
                )
            }
            onSuccess()
        }
    }

    fun submitForm(
        route: ProclamatoriRoute,
        currentEditId: ProclamatoreId?,
        onSuccessNavigateToList: () -> Unit,
    ) {
        val snapshot = _uiState.value
        val nomeTrim = snapshot.nome.trim()
        val cognomeTrim = snapshot.cognome.trim()
        val requiredFieldsValid = nomeTrim.isNotBlank() && cognomeTrim.isNotBlank()
        val hasFormChanges = when (route) {
            ProclamatoriRoute.Nuovo -> requiredFieldsValid || snapshot.sesso != Sesso.M
            is ProclamatoriRoute.Modifica -> {
                nomeTrim != snapshot.initialNome.trim() ||
                    cognomeTrim != snapshot.initialCognome.trim() ||
                    snapshot.sesso != snapshot.initialSesso
            }
            ProclamatoriRoute.Elenco -> false
        }

        val canSubmit = route != ProclamatoriRoute.Elenco &&
            requiredFieldsValid &&
            hasFormChanges &&
            snapshot.duplicateError == null &&
            !snapshot.isCheckingDuplicate &&
            !snapshot.isLoading

        if (!canSubmit) {
            _uiState.update { it.copy(showFieldErrors = true) }
            return
        }

        scope.launch {
            _uiState.update { it.copy(showFieldErrors = true, formError = null, isLoading = true) }
            val state = _uiState.value
            val result = if (route == ProclamatoriRoute.Nuovo) {
                crea(
                    CreaProclamatoreUseCase.Command(
                        nome = state.nome,
                        cognome = state.cognome,
                        sesso = state.sesso,
                    ),
                )
            } else {
                aggiorna(
                    AggiornaProclamatoreUseCase.Command(
                        id = requireNotNull(currentEditId),
                        nome = state.nome,
                        cognome = state.cognome,
                        sesso = state.sesso,
                    ),
                )
            }

            result.fold(
                ifLeft = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            formError = (err as? DomainError.Validation)?.message ?: "Operazione non completata",
                        )
                    }
                },
                ifRight = {
                    val operation = if (route == ProclamatoriRoute.Nuovo) {
                        "Proclamatore aggiunto"
                    } else {
                        "Proclamatore aggiornato"
                    }
                    val details = personDetails(state.nome, state.cognome)
                    _uiState.update {
                        it.copy(
                            notice = successNotice("$operation: $details"),
                            initialNome = "",
                            initialCognome = "",
                            initialSesso = Sesso.M,
                            nome = "",
                            cognome = "",
                            sesso = Sesso.M,
                            formError = null,
                            duplicateError = null,
                            isCheckingDuplicate = false,
                            showFieldErrors = false,
                        )
                    }
                    onSuccessNavigateToList()
                    refreshListInternal()
                },
            )
        }
    }

    fun scheduleDuplicateCheck(isFormRoute: Boolean, currentEditId: ProclamatoreId?) {
        duplicateCheckJob?.cancel()
        val state = _uiState.value
        val nomeTrim = state.nome.trim()
        val cognomeTrim = state.cognome.trim()

        if (!isFormRoute || nomeTrim.isBlank() || cognomeTrim.isBlank()) {
            _uiState.update { it.copy(duplicateError = null, isCheckingDuplicate = false) }
            return
        }

        duplicateCheckJob = scope.launch {
            _uiState.update { it.copy(isCheckingDuplicate = true) }
            delay(250)
            val exists = verificaDuplicato(nomeTrim, cognomeTrim, currentEditId)
            _uiState.update {
                it.copy(
                    duplicateError = if (exists) {
                        "Esiste gia' un proclamatore con questo nome e cognome"
                    } else {
                        null
                    },
                    isCheckingDuplicate = false,
                )
            }
        }
    }

    fun requestDeleteCandidate(candidate: Proclamatore) {
        scope.launch {
            val count = try {
                assignmentStore.countAssignmentsForPerson(candidate.id)
            } catch (_: Exception) {
                0
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

    private fun clearForm() {
        _uiState.update {
            it.copy(
                initialNome = "",
                initialCognome = "",
                initialSesso = Sesso.M,
                nome = "",
                cognome = "",
                sesso = Sesso.M,
                formError = null,
                duplicateError = null,
                isCheckingDuplicate = false,
                showFieldErrors = false,
            )
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

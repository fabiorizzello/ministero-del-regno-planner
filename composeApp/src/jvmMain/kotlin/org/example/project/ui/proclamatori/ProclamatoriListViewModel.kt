package org.example.project.ui.proclamatori

import arrow.core.Either
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.example.project.core.domain.DomainError
import org.example.project.feature.people.application.CaricaIdoneitaProclamatoreUseCase
import org.example.project.feature.people.application.CercaProclamatoriUseCase
import org.example.project.feature.people.application.EliminaProclamatoreUseCase
import org.example.project.feature.assignments.application.ContaAssegnazioniPersonaUseCase
import org.example.project.feature.schemas.application.ArchivaAnomalieSchemaUseCase
import org.example.project.feature.schemas.application.SchemaUpdateAnomalyStore
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.weeklyparts.application.PartTypeStore
import org.example.project.ui.components.FeedbackBannerModel
import org.example.project.ui.components.errorNotice
import org.example.project.ui.components.executeEitherOperationWithNotice
import org.example.project.ui.search.FuzzySearchCandidate
import org.example.project.ui.search.rankPeopleByQuery

internal data class SchemaUpdateAnomalyUi(
    val id: String,
    val personLabel: String,
    val partTypeLabel: String,
    val reason: String,
    val schemaVersion: String?,
    val createdAt: String,
)

internal data class ProclamatoreCapabilitySummaryUi(
    val leadLabels: List<String> = emptyList(),
) {
    val leadCount: Int get() = leadLabels.size
}

internal data class ProclamatoriListUiState(
    val searchTerm: String = "",
    val allItems: List<Proclamatore> = emptyList(),
    val isLoading: Boolean = true,
    val notice: FeedbackBannerModel? = null,
    val sort: ProclamatoriSort = ProclamatoriSort(),
    val pageIndex: Int = 0,
    val pageSize: Int = 20,
    val selectedIds: Set<ProclamatoreId> = emptySet(),
    val deleteCandidate: Proclamatore? = null,
    val deleteAssignmentCount: Int = 0,
    val showBatchDeleteConfirm: Boolean = false,
    val batchDeleteAssignmentCount: Int = 0,
    val isBatchInProgress: Boolean = false,
    val schemaUpdateAnomalies: List<SchemaUpdateAnomalyUi> = emptyList(),
    val isDismissingSchemaAnomalies: Boolean = false,
    val capabilitySummaryById: Map<ProclamatoreId, ProclamatoreCapabilitySummaryUi> = emptyMap(),
    val scrollResetToken: Int = 0,
) {
    val sortedItems: List<Proclamatore> get() = allItems.applySort(sort)
}

private data class ProclamatoriNavigationTarget(
    val id: ProclamatoreId,
    val pageIndex: Int,
)

internal class ProclamatoriListViewModel(
    private val scope: CoroutineScope,
    private val cerca: CercaProclamatoriUseCase,
    private val caricaIdoneita: CaricaIdoneitaProclamatoreUseCase,
    private val elimina: EliminaProclamatoreUseCase,
    private val contaAssegnazioni: ContaAssegnazioniPersonaUseCase,
    private val archivaAnomalieSchema: ArchivaAnomalieSchemaUseCase,
    private val schemaUpdateAnomalyStore: SchemaUpdateAnomalyStore,
    private val partTypeStore: PartTypeStore,
) {
    private val _uiState = MutableStateFlow(ProclamatoriListUiState())
    val uiState: StateFlow<ProclamatoriListUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null

    fun onScreenEntered() {
        scope.launch {
            refreshListInternal(resetPage = true)
            refreshSchemaUpdateAnomalies()
        }
    }

    fun refreshList(resetPage: Boolean = false) {
        scope.launch { refreshListInternal(resetPage) }
    }

    fun setSearchTerm(value: String) {
        _uiState.update { it.copy(searchTerm = value) }
        searchJob?.cancel()
        searchJob = scope.launch {
            delay(250)
            refreshListInternal(resetPage = true, showLoading = false)
        }
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
        scope.launch { refreshCurrentPageCapabilitySummaries() }
    }

    fun goToPreviousPage() {
        _uiState.update {
            it.copy(
                pageIndex = (it.pageIndex - 1).coerceAtLeast(0),
                scrollResetToken = it.scrollResetToken + 1,
            )
        }
        scope.launch { refreshCurrentPageCapabilitySummaries() }
    }

    fun goToNextPage() {
        _uiState.update { state ->
            val totalPages = if (state.allItems.isEmpty()) 1 else ((state.allItems.size - 1) / state.pageSize) + 1
            state.copy(
                pageIndex = (state.pageIndex + 1).coerceAtMost(totalPages - 1),
                scrollResetToken = state.scrollResetToken + 1,
            )
        }
        scope.launch { refreshCurrentPageCapabilitySummaries() }
    }

    fun hasNextItem(currentId: ProclamatoreId): Boolean {
        return resolveNextTarget(_uiState.value, currentId) != null
    }

    fun openNextItem(currentId: ProclamatoreId, onResolved: (ProclamatoreId?) -> Unit) {
        scope.launch {
            val target = resolveNextTarget(_uiState.value, currentId)
            revealNavigationTarget(target)
            onResolved(target?.id)
        }
    }

    fun refreshAndOpenNextItem(currentId: ProclamatoreId, onResolved: (ProclamatoreId?) -> Unit) {
        scope.launch {
            refreshListInternal(showLoading = false)
            val target = resolveNextTarget(_uiState.value, currentId)
            revealNavigationTarget(target)
            onResolved(target?.id)
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
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(notice = errorNotice("Errore nel conteggio assegnazioni: ${e.message}"))
                }
                return@launch
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
            _uiState.executeEitherOperationWithNotice(
                loadingUpdate = { it.copy(isLoading = true) },
                noticeUpdate = { state, notice -> state.copy(isLoading = false, deleteCandidate = null, notice = notice) },
                successMessage = "Rimosso ${personDetails(candidate.nome, candidate.cognome)}",
                operation = { elimina(candidate.id) },
                onSuccess = { refreshListInternal() },
            )
        }
    }

    fun requestBatchDeleteConfirm() {
        val ids = _uiState.value.selectedIds
        if (ids.isEmpty()) return
        scope.launch {
            val total = try {
                ids.sumOf { id -> contaAssegnazioni(id) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(notice = errorNotice("Errore nel conteggio assegnazioni: ${e.message}"))
                }
                return@launch
            }
            _uiState.update { it.copy(showBatchDeleteConfirm = true, batchDeleteAssignmentCount = total) }
        }
    }

    fun dismissBatchDeleteConfirm() {
        _uiState.update { it.copy(showBatchDeleteConfirm = false) }
    }

    fun confirmBatchDelete() {
        if (_uiState.value.isBatchInProgress) return
        scope.launch {
            val resultNotice = executeOnSelected(
                action = { id -> elimina(id) },
                completedLabel = "Studenti rimossi",
                noneCompletedLabel = "Nessun studente rimosso",
            )
            _uiState.update { it.copy(showBatchDeleteConfirm = false, notice = resultNotice) }
        }
    }

    fun dismissSchemaUpdateAnomalies() {
        if (_uiState.value.isDismissingSchemaAnomalies) return
        scope.launch {
            _uiState.executeEitherOperationWithNotice(
                loadingUpdate = { it.copy(isDismissingSchemaAnomalies = true) },
                noticeUpdate = { state, notice -> state.copy(isDismissingSchemaAnomalies = false, notice = notice) },
                successMessage = "Pannello anomalie archiviato",
                operation = { archivaAnomalieSchema() },
                onSuccess = { _uiState.update { it.copy(schemaUpdateAnomalies = emptyList()) } },
            )
        }
    }

    private suspend fun executeOnSelected(
        action: suspend (ProclamatoreId) -> Either<DomainError, Unit>,
        completedLabel: String,
        noneCompletedLabel: String,
    ): FeedbackBannerModel {
        _uiState.update { it.copy(isLoading = true, isBatchInProgress = true) }
        val selected = _uiState.value.selectedIds
        val result = runMultiAction(selected, action)
        refreshListInternal()
        _uiState.update {
            it.copy(
                selectedIds = result.failedIds,
                isLoading = false,
                isBatchInProgress = false,
            )
        }
        return noticeForMultiAction(
            result = result,
            completedLabel = completedLabel,
            noneCompletedLabel = noneCompletedLabel,
        )
    }

    private suspend fun refreshListInternal(resetPage: Boolean = false, showLoading: Boolean = true) {
        if (showLoading) _uiState.update { it.copy(isLoading = true) }
        val state = _uiState.value
        val sourceItems = cerca(null)
        val allItems = filterPeopleForSearch(sourceItems, state.searchTerm)
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
                scrollResetToken = if (resetPage) it.scrollResetToken + 1 else it.scrollResetToken,
            )
        }
        refreshCurrentPageCapabilitySummaries()
    }

    private suspend fun refreshSchemaUpdateAnomalies() {
        val anomalies = schemaUpdateAnomalyStore.listOpen()
        if (anomalies.isEmpty()) {
            _uiState.update { it.copy(schemaUpdateAnomalies = emptyList()) }
            return
        }
        val state = _uiState.value
        val peopleById = if (state.searchTerm.isBlank()) {
            state.allItems.associateBy { it.id }
        } else {
            cerca(null).associateBy { it.id }
        }
        val partTypesById = partTypeStore.allWithStatus().associateBy { it.partType.id }
        val mapped = anomalies.map { anomaly ->
            val person = peopleById[anomaly.personId]
            val personLabel = if (person == null) {
                "Proclamatore non trovato"
            } else {
                "${person.nome} ${person.cognome}"
            }
            val partTypeLabel = partTypesById[anomaly.partTypeId]?.partType?.label ?: "Parte non trovata"
            SchemaUpdateAnomalyUi(
                id = anomaly.id,
                personLabel = personLabel,
                partTypeLabel = partTypeLabel,
                reason = anomaly.reason,
                schemaVersion = anomaly.schemaVersion,
                createdAt = anomaly.createdAt,
            )
        }
        _uiState.update { it.copy(schemaUpdateAnomalies = mapped) }
    }

    private suspend fun refreshCurrentPageCapabilitySummaries() {
        val state = _uiState.value
        val pageItems = state.sortedItems
            .drop(state.pageIndex * state.pageSize)
            .take(state.pageSize)
        if (pageItems.isEmpty()) {
            _uiState.update { it.copy(capabilitySummaryById = emptyMap()) }
            return
        }

        val orderedPartTypes = partTypeStore.allWithStatus()
            .map { it.partType }
            .sortedBy { it.sortOrder }
        val partTypeLabelsById = orderedPartTypes.associate { it.id to it.label }
        val partTypeOrderById = orderedPartTypes.mapIndexed { index, partType ->
            partType.id to index
        }.toMap()

        val summaries = pageItems.associate { person ->
            val leadLabels = caricaIdoneita(person.id)
                .asSequence()
                .filter { it.canLead }
                .map { it.partTypeId }
                .distinct()
                .sortedBy { partTypeOrderById[it] ?: Int.MAX_VALUE }
                .mapNotNull { partTypeLabelsById[it] }
                .toList()

            person.id to ProclamatoreCapabilitySummaryUi(leadLabels = leadLabels)
        }

        _uiState.update { it.copy(capabilitySummaryById = summaries) }
    }

    private fun resolveNextTarget(
        state: ProclamatoriListUiState,
        currentId: ProclamatoreId,
    ): ProclamatoriNavigationTarget? {
        val currentIndex = state.sortedItems.indexOfFirst { it.id == currentId }
        if (currentIndex < 0) return null
        val nextIndex = currentIndex + 1
        val nextItem = state.sortedItems.getOrNull(nextIndex) ?: return null
        return ProclamatoriNavigationTarget(
            id = nextItem.id,
            pageIndex = nextIndex / state.pageSize,
        )
    }

    private suspend fun revealNavigationTarget(target: ProclamatoriNavigationTarget?) {
        if (target == null) return
        val currentState = _uiState.value
        if (currentState.pageIndex != target.pageIndex) {
            _uiState.update {
                it.copy(
                    pageIndex = target.pageIndex,
                    scrollResetToken = it.scrollResetToken + 1,
                )
            }
        }
        refreshCurrentPageCapabilitySummaries()
    }
}

internal fun filterPeopleForSearch(
    items: List<Proclamatore>,
    searchTerm: String,
): List<Proclamatore> {
    if (searchTerm.isBlank()) return items
    val peopleById = items.associateBy { it.id }
    return rankPeopleByQuery(
        query = searchTerm,
        candidates = items.map { person ->
            FuzzySearchCandidate(
                value = person.id,
                firstName = person.nome,
                lastName = person.cognome,
            )
        },
    ).mapNotNull { peopleById[it] }
}

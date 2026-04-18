package org.example.project.ui.proclamatori

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.core.config.UiPreferencesStore
import org.example.project.ui.components.errorNotice
import org.example.project.ui.components.FeedbackBannerKind
import org.example.project.ui.components.workspace.WorkspaceStateKind
import org.example.project.ui.components.workspace.WorkspaceStatePane
import org.example.project.ui.theme.spacing
import org.koin.core.context.GlobalContext

sealed interface ProclamatoriRoute {
    data object Elenco : ProclamatoriRoute
    data object Nuovo : ProclamatoriRoute
    data class Modifica(val id: ProclamatoreId) : ProclamatoriRoute
}

@Composable
fun ProclamatoriScreen() {
    val listVm = remember { GlobalContext.get().get<ProclamatoriListViewModel>() }
    val formVm = remember { GlobalContext.get().get<ProclamatoreFormViewModel>() }
    val uiPreferencesStore = remember { GlobalContext.get().get<UiPreferencesStore>() }
    val listState by listVm.uiState.collectAsState()
    val formState by formVm.uiState.collectAsState()

    LaunchedEffect(Unit) { listVm.onScreenEntered() }

    val tableListState = rememberLazyListState()
    val searchFocusRequester = remember { FocusRequester() }
    val rootFocusRequester = remember { FocusRequester() }
    var viewMode by remember(uiPreferencesStore) {
        mutableStateOf(
            ProclamatoriListViewMode.entries.firstOrNull {
                it.name == uiPreferencesStore.loadStudentsViewMode(ProclamatoriListViewMode.TABLE.name)
            } ?: ProclamatoriListViewMode.TABLE,
        )
    }

    LaunchedEffect(viewMode) {
        uiPreferencesStore.saveStudentsViewMode(viewMode.name)
    }

    LaunchedEffect(listState.scrollResetToken, viewMode) {
        if (viewMode == ProclamatoriListViewMode.TABLE) {
            tableListState.scrollToItem(0)
        }
    }

    listState.deleteCandidate?.let { candidate ->
        ConfirmDeleteDialogComponent(
            title = "Rimuovi studente",
            isLoading = listState.isLoading,
            onConfirm = { listVm.confirmDeleteCandidate() },
            onDismiss = { listVm.dismissDeleteCandidate() },
        ) {
            Column {
                Text("Confermi rimozione di ${candidate.nome} ${candidate.cognome}?")
                val count = listState.deleteAssignmentCount
                if (count != 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = when {
                            count < 0 -> "Attenzione: conteggio assegnazioni non disponibile."
                            count == 1 -> "Attenzione: 1 assegnazione verra' cancellata."
                            else -> "Attenzione: $count assegnazioni verranno cancellate."
                        },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Questa azione e' irreversibile.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    if (listState.showBatchDeleteConfirm) {
        ConfirmDeleteDialogComponent(
            title = "Rimuovi studenti selezionati",
            isLoading = listState.isLoading,
            onConfirm = { listVm.confirmBatchDelete() },
            onDismiss = { listVm.dismissBatchDeleteConfirm() },
        ) {
            Column {
                Text("Confermi rimozione di ${listState.selectedIds.size} studenti selezionati?")
                val batchCount = listState.batchDeleteAssignmentCount
                if (batchCount > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (batchCount == 1) "Attenzione: 1 assegnazione verra' cancellata."
                               else "Attenzione: $batchCount assegnazioni verranno cancellate.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Questa azione e' irreversibile.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    var route by remember { mutableStateOf<ProclamatoriRoute>(ProclamatoriRoute.Elenco) }
    var showUnsavedNextConfirm by remember { mutableStateOf(false) }
    val currentEditId = (route as? ProclamatoriRoute.Modifica)?.id
    val isFormRoute = route != ProclamatoriRoute.Elenco

    val canSubmitForm = formState.canSubmitForm(route)
    val hasUnsavedChanges = formState.hasUnsavedChanges(route)
    val canGoToNext = currentEditId?.let(listVm::hasNextItem) == true

    fun goToListManual() {
        listVm.dismissNotice()
        listVm.clearSelection()
        showUnsavedNextConfirm = false
        formVm.clearForm()
        route = ProclamatoriRoute.Elenco
    }

    fun goToNuovo() {
        listVm.dismissNotice()
        listVm.clearSelection()
        showUnsavedNextConfirm = false
        route = ProclamatoriRoute.Nuovo
        formVm.prepareForNew()
    }

    fun submitAndNavigate() {
        formVm.submitForm(
            route = route,
            currentEditId = currentEditId,
            onSuccess = { notice ->
                listVm.setNotice(notice)
                listVm.refreshList()
                showUnsavedNextConfirm = false
                route = ProclamatoriRoute.Elenco
            },
        )
    }

    fun openEdit(id: ProclamatoreId) {
        formVm.loadForEdit(
            id = id,
            onNotFound = { listVm.setNotice(errorNotice("Studente non trovato")) },
            onSuccess = {
                showUnsavedNextConfirm = false
                route = ProclamatoriRoute.Modifica(id)
            },
        )
    }

    fun openNextStudent() {
        val editId = currentEditId ?: return
        listVm.openNextItem(editId) { nextId ->
            if (nextId != null) {
                openEdit(nextId)
            }
        }
    }

    fun saveAndOpenNextStudent() {
        val editId = currentEditId ?: return
        formVm.submitForm(
            route = route,
            currentEditId = currentEditId,
            onSuccess = { notice ->
                listVm.setNotice(notice)
                listVm.refreshAndOpenNextItem(editId) { nextId ->
                    if (nextId != null) {
                        openEdit(nextId)
                    } else {
                        showUnsavedNextConfirm = false
                        route = ProclamatoriRoute.Elenco
                    }
                }
            },
        )
    }

    fun onNextRequested() {
        if (hasUnsavedChanges) {
            showUnsavedNextConfirm = true
        } else {
            openNextStudent()
        }
    }

    LaunchedEffect(route, formState.nome, formState.cognome, currentEditId) {
        formVm.scheduleDuplicateCheck(isFormRoute = isFormRoute, currentEditId = currentEditId)
    }

    LaunchedEffect(route) {
        rootFocusRequester.requestFocus()
        if (route == ProclamatoriRoute.Elenco) {
            searchFocusRequester.requestFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    event.isCtrlPressed && event.key == Key.F && route == ProclamatoriRoute.Elenco -> {
                        searchFocusRequester.requestFocus()
                        true
                    }
                    event.isCtrlPressed && event.key == Key.N && route == ProclamatoriRoute.Elenco -> {
                        goToNuovo()
                        true
                    }
                    event.key == Key.Escape && isFormRoute -> {
                        showUnsavedNextConfirm = false
                        goToListManual()
                        true
                    }
                    event.key == Key.Enter && isFormRoute && canSubmitForm -> {
                        submitAndNavigate()
                        true
                    }
                    else -> false
                }
            }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg),
    ) {
            val showLoadingState = listState.isLoading && listState.allItems.isEmpty()
            val showErrorState = !listState.isLoading &&
                listState.allItems.isEmpty() &&
                listState.notice?.kind == FeedbackBannerKind.ERROR
            when {
                showLoadingState -> WorkspaceStatePane(
                    kind = WorkspaceStateKind.Loading,
                    message = "Caricamento studenti in corso...",
                )
                showErrorState -> WorkspaceStatePane(
                    kind = WorkspaceStateKind.Error,
                    message = "Impossibile caricare l'elenco studenti.",
                )
            }

            val elencoEvents = remember(listVm, formVm) {
                ProclamatoriElencoEvents(
                    onSearchTermChange = { value -> listVm.setSearchTerm(value) },
                    onResetSearch = { listVm.resetSearch() },
                    onDismissNotice = { listVm.dismissNotice() },
                    onSortChange = { nextSort -> listVm.setSort(nextSort) },
                    onToggleSelectPage = { pageIds, checked -> listVm.toggleSelectPage(pageIds, checked) },
                    onToggleRowSelected = { id, checked -> listVm.setRowSelected(id, checked) },
                    onRequestDeleteSelected = { listVm.requestBatchDeleteConfirm() },
                    onClearSelection = { listVm.clearSelection() },
                    onGoNuovo = { goToNuovo() },
                    onDismissSchemaAnomalies = { listVm.dismissSchemaUpdateAnomalies() },
                    onEdit = { id -> openEdit(id) },
                    onDelete = { candidate -> listVm.requestDeleteCandidate(candidate) },
                    onPreviousPage = { listVm.goToPreviousPage() },
                    onNextPage = { listVm.goToNextPage() },
                )
            }

            ProclamatoriElencoContent(
                state = listState,
                viewMode = viewMode,
                onViewModeChange = { viewMode = it },
                searchFocusRequester = searchFocusRequester,
                tableListState = tableListState,
                events = elencoEvents,
            )
    }

    if (isFormRoute) {
        if (showUnsavedNextConfirm) {
            ConfirmUnsavedNextDialogComponent(
                isLoading = formState.isLoading,
                onSaveAndContinue = { saveAndOpenNextStudent() },
                onDiscardAndContinue = {
                    showUnsavedNextConfirm = false
                    openNextStudent()
                },
                onDismiss = { showUnsavedNextConfirm = false },
            )
        }
        ProclamatoriFormDialogComponent(
            route = route,
            nome = formState.nome,
            onNomeChange = { formVm.setNome(it) },
            cognome = formState.cognome,
            onCognomeChange = { formVm.setCognome(it) },
            sesso = formState.sesso,
            onSessoChange = { formVm.setSesso(it) },
            sospeso = formState.sospeso,
            onSospesoChange = { formVm.setSospeso(it) },
            puoAssistere = formState.puoAssistere,
            onPuoAssistereChange = { formVm.setPuoAssistere(it) },
            leadEligibilityOptions = formState.leadEligibilityOptions,
            lastAssistantAssignmentDate = formState.lastAssistantAssignmentDate,
            onLeadEligibilityChange = { partTypeId, checked ->
                formVm.setLeadEligibility(partTypeId, checked)
            },
            onSetAllEligibilityChange = { checked -> formVm.setAllEligibility(checked) },
            nomeTrim = formState.nome.trim(),
            cognomeTrim = formState.cognome.trim(),
            showFieldErrors = formState.showFieldErrors,
            duplicateError = formState.duplicateError,
            isCheckingDuplicate = formState.isCheckingDuplicate,
            canSubmitForm = canSubmitForm,
            canGoToNext = canGoToNext,
            isLoading = formState.isLoading,
            formError = formState.formError,
            onSubmit = { submitAndNavigate() },
            onNext = if (currentEditId != null) ({ onNextRequested() }) else null,
            onCancel = { goToListManual() },
            onDismiss = { goToListManual() },
            onDelete = currentEditId?.let { editId ->
                {
                    val candidate = listState.allItems.find { it.id == editId }
                    if (candidate != null) {
                        goToListManual()
                        listVm.requestDeleteCandidate(candidate)
                    }
                }
            },
        )
    }
}

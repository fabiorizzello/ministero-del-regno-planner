package org.example.project.ui.proclamatori

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
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
import org.example.project.ui.components.errorNotice
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
    val listState by listVm.uiState.collectAsState()
    val formState by formVm.uiState.collectAsState()

    LaunchedEffect(Unit) { listVm.onScreenEntered() }

    val tableListState = rememberLazyListState()
    val searchFocusRequester = remember { FocusRequester() }
    val rootFocusRequester = remember { FocusRequester() }

    listState.deleteCandidate?.let { candidate ->
        ConfirmDeleteDialog(
            title = "Rimuovi proclamatore",
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
            }
        }
    }

    if (listState.showBatchDeleteConfirm) {
        ConfirmDeleteDialog(
            title = "Rimuovi proclamatori selezionati",
            isLoading = listState.isLoading,
            onConfirm = { listVm.confirmBatchDelete() },
            onDismiss = { listVm.dismissBatchDeleteConfirm() },
        ) {
            Text("Confermi rimozione di ${listState.selectedIds.size} proclamatori selezionati?")
        }
    }

    var route by remember { mutableStateOf<ProclamatoriRoute>(ProclamatoriRoute.Elenco) }
    val currentEditId = (route as? ProclamatoriRoute.Modifica)?.id
    val isFormRoute = route != ProclamatoriRoute.Elenco

    val canSubmitForm = formState.canSubmitForm(route)

    fun goToListManual() {
        listVm.dismissNotice()
        listVm.clearSelection()
        formVm.clearForm()
        route = ProclamatoriRoute.Elenco
    }

    fun goToNuovo() {
        listVm.dismissNotice()
        listVm.clearSelection()
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
                route = ProclamatoriRoute.Elenco
            },
        )
    }

    LaunchedEffect(route, formState.nome, formState.cognome, currentEditId) {
        formVm.scheduleDuplicateCheck(isFormRoute = isFormRoute, currentEditId = currentEditId)
    }

    LaunchedEffect(route) {
        rootFocusRequester.requestFocus()
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
                        goToListManual()
                        true
                    }
                    event.key == Key.Enter && isFormRoute && canSubmitForm -> {
                        submitAndNavigate()
                        true
                    }
                    else -> false
                }
            },
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg),
    ) {
        val elencoEvents = remember(listVm, formVm) {
            ProclamatoriElencoEvents(
                onSearchTermChange = { value -> listVm.setSearchTerm(value) },
                onResetSearch = { listVm.resetSearch() },
                onDismissNotice = { listVm.dismissNotice() },
                onSortChange = { nextSort -> listVm.setSort(nextSort) },
                onToggleSelectPage = { pageIds, checked -> listVm.toggleSelectPage(pageIds, checked) },
                onToggleRowSelected = { id, checked -> listVm.setRowSelected(id, checked) },
                onActivateSelected = { listVm.activateSelected() },
                onDeactivateSelected = { listVm.deactivateSelected() },
                onRequestDeleteSelected = { listVm.requestBatchDeleteConfirm() },
                onClearSelection = { listVm.clearSelection() },
                onGoNuovo = { goToNuovo() },
                onImportJson = { listVm.startImportFromJson() },
                onDismissSchemaAnomalies = { listVm.dismissSchemaUpdateAnomalies() },
                onEdit = { id ->
                    formVm.loadForEdit(
                        id = id,
                        onNotFound = { listVm.setNotice(errorNotice("Proclamatore non trovato")) },
                        onSuccess = { route = ProclamatoriRoute.Modifica(id) },
                    )
                },
                onToggleActive = { id, next -> listVm.toggleActive(id, next) },
                onDelete = { candidate -> listVm.requestDeleteCandidate(candidate) },
                onPreviousPage = { listVm.goToPreviousPage() },
                onNextPage = { listVm.goToNextPage() },
            )
        }

        ProclamatoriElencoContent(
            state = listState,
            searchFocusRequester = searchFocusRequester,
            tableListState = tableListState,
            canImportInitialJson = !listState.isLoading && !listState.isImporting && listState.allItems.isEmpty(),
            events = elencoEvents,
        )
    }

    if (isFormRoute) {
        ProclamatoriFormDialog(
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
            onLeadEligibilityChange = { partTypeId, checked ->
                formVm.setLeadEligibility(partTypeId, checked)
            },
            assignmentHistory = formState.assignmentHistory,
            isHistoryExpanded = formState.isHistoryExpanded,
            onToggleHistoryExpanded = { formVm.toggleHistoryExpanded() },
            nomeTrim = formState.nome.trim(),
            cognomeTrim = formState.cognome.trim(),
            showFieldErrors = formState.showFieldErrors,
            duplicateError = formState.duplicateError,
            isCheckingDuplicate = formState.isCheckingDuplicate,
            canSubmitForm = canSubmitForm,
            isLoading = formState.isLoading,
            formError = formState.formError,
            onSubmit = { submitAndNavigate() },
            onCancel = { goToListManual() },
            onDismiss = { goToListManual() },
        )
    }
}

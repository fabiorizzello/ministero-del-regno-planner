package org.example.project.ui.proclamatori

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.ui.theme.spacing
import org.koin.core.context.GlobalContext

sealed interface ProclamatoriRoute {
    data object Elenco : ProclamatoriRoute
    data object Nuovo : ProclamatoriRoute
    data class Modifica(val id: ProclamatoreId) : ProclamatoriRoute
}

private sealed interface ProclamatoriFlowScreen : Screen

private data object ProclamatoriElencoScreen : ProclamatoriFlowScreen {
    @Composable
    override fun Content() {}
}

private data object ProclamatoriNuovoScreen : ProclamatoriFlowScreen {
    @Composable
    override fun Content() {}
}

private data class ProclamatoriModificaScreen(
    val id: ProclamatoreId,
) : ProclamatoriFlowScreen {
    @Composable
    override fun Content() {}
}

@Composable
fun ProclamatoriScreen() {
    val listVm = remember { GlobalContext.get().get<ProclamatoriListViewModel>() }
    val formVm = remember { GlobalContext.get().get<ProclamatoreFormViewModel>() }
    val listState by listVm.uiState.collectAsState()
    val formState by formVm.uiState.collectAsState()

    val tableListState = rememberLazyListState()
    val searchFocusRequester = remember { FocusRequester() }
    val rootFocusRequester = remember { FocusRequester() }

    listState.deleteCandidate?.let { candidate ->
        ProclamatoreDeleteDialog(
            candidate = candidate,
            assignmentCount = listState.deleteAssignmentCount,
            isLoading = listState.isLoading,
            onConfirm = { listVm.confirmDeleteCandidate() },
            onDismiss = { listVm.dismissDeleteCandidate() },
        )
    }

    if (listState.showBatchDeleteConfirm) {
        ProclamatoriDeleteDialog(
            selectedCount = listState.selectedIds.size,
            isLoading = listState.isLoading,
            onConfirm = { listVm.confirmBatchDelete() },
            onDismiss = { listVm.dismissBatchDeleteConfirm() },
        )
    }

    Navigator(ProclamatoriElencoScreen) { navigator ->
        val currentScreen = navigator.lastItem
        val route = when (currentScreen) {
            ProclamatoriElencoScreen -> ProclamatoriRoute.Elenco
            ProclamatoriNuovoScreen -> ProclamatoriRoute.Nuovo
            is ProclamatoriModificaScreen -> ProclamatoriRoute.Modifica(currentScreen.id)
            else -> ProclamatoriRoute.Elenco
        }
        val currentEditId = (currentScreen as? ProclamatoriModificaScreen)?.id
        val isFormRoute = route != ProclamatoriRoute.Elenco

        val canSubmitForm = formState.canSubmitForm(route)

        fun goToListManual() {
            listVm.dismissNotice()
            listVm.clearSelection()
            formVm.clearForm()
            navigator.replaceAll(ProclamatoriElencoScreen)
        }

        fun goToNuovo() {
            listVm.dismissNotice()
            listVm.clearSelection()
            formVm.clearForm()
            navigator.push(ProclamatoriNuovoScreen)
        }

        fun submitAndNavigate() {
            formVm.submitForm(
                route = route,
                currentEditId = currentEditId,
                onSuccess = { notice ->
                    listVm.setNotice(notice)
                    listVm.refreshList()
                    navigator.replaceAll(ProclamatoriElencoScreen)
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
            val currentModificaLabel = if (route is ProclamatoriRoute.Modifica) {
                listOf(formState.nome.trim(), formState.cognome.trim())
                    .filter { it.isNotEmpty() }
                    .joinToString(" ")
                    .ifBlank { "Modifica" }
            } else {
                null
            }

            Breadcrumbs(
                route = route,
                currentModificaLabel = currentModificaLabel,
                onGoList = { goToListManual() },
            )

            when (route) {
                ProclamatoriRoute.Elenco -> {
                    ProclamatoriElencoContent(
                        searchTerm = listState.searchTerm,
                        onSearchTermChange = { value -> listVm.setSearchTerm(value) },
                        onResetSearch = { listVm.resetSearch() },
                        searchFocusRequester = searchFocusRequester,
                        allItems = listState.allItems,
                        isLoading = listState.isLoading,
                        notice = listState.notice,
                        onDismissNotice = { listVm.dismissNotice() },
                        selectedIds = listState.selectedIds,
                        sort = listState.sort,
                        pageIndex = listState.pageIndex,
                        pageSize = listState.pageSize,
                        tableListState = tableListState,
                        onSortChange = { nextSort -> listVm.setSort(nextSort) },
                        onToggleSelectPage = { pageIds, checked -> listVm.toggleSelectPage(pageIds, checked) },
                        onToggleRowSelected = { id, checked -> listVm.setRowSelected(id, checked) },
                        onActivateSelected = { listVm.activateSelected() },
                        onDeactivateSelected = { listVm.deactivateSelected() },
                        onRequestDeleteSelected = { listVm.requestBatchDeleteConfirm() },
                        onClearSelection = { listVm.clearSelection() },
                        onGoNuovo = { goToNuovo() },
                        canImportInitialJson = !listState.isLoading && !listState.isImporting && listState.allItems.isEmpty(),
                        onImportJson = {
                            val selectedFile = selectJsonFileForImport() ?: return@ProclamatoriElencoContent
                            listVm.importFromJsonFile(selectedFile)
                        },
                        onEdit = { id ->
                            formVm.loadForEdit(
                                id = id,
                                onNotFound = { listVm.setNotice(errorNotice("Proclamatore non trovato")) },
                                onSuccess = { navigator.push(ProclamatoriModificaScreen(id)) },
                            )
                        },
                        onToggleActive = { id, next -> listVm.toggleActive(id, next) },
                        onDelete = { candidate -> listVm.requestDeleteCandidate(candidate) },
                        onPreviousPage = { listVm.goToPreviousPage() },
                        onNextPage = { listVm.goToNextPage() },
                    )
                }

                ProclamatoriRoute.Nuovo,
                is ProclamatoriRoute.Modifica,
                -> {
                    ProclamatoriFormContent(
                        route = route,
                        nome = formState.nome,
                        onNomeChange = { formVm.setNome(it) },
                        cognome = formState.cognome,
                        onCognomeChange = { formVm.setCognome(it) },
                        sesso = formState.sesso,
                        onSessoChange = { formVm.setSesso(it) },
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
                    )
                }
            }
        }
    }
}

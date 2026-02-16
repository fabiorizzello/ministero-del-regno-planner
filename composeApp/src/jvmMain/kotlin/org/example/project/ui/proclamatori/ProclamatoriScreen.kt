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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import org.example.project.feature.people.application.AggiornaProclamatoreUseCase
import org.example.project.feature.people.application.CaricaProclamatoreUseCase
import org.example.project.feature.people.application.CercaProclamatoriUseCase
import org.example.project.feature.people.application.CreaProclamatoreUseCase
import org.example.project.feature.people.application.EliminaProclamatoreUseCase
import org.example.project.feature.people.application.ImpostaStatoProclamatoreUseCase
import org.example.project.feature.people.application.ImportaProclamatoriDaJsonUseCase
import org.example.project.feature.people.application.VerificaDuplicatoProclamatoreUseCase
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.domain.Sesso
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
    val koin = remember { GlobalContext.get() }
    val vmScope = rememberCoroutineScope()
    val viewModel = remember {
        ProclamatoriViewModel(
            scope = vmScope,
            cerca = koin.get<CercaProclamatoriUseCase>(),
            carica = koin.get<CaricaProclamatoreUseCase>(),
            crea = koin.get<CreaProclamatoreUseCase>(),
            aggiorna = koin.get<AggiornaProclamatoreUseCase>(),
            impostaStato = koin.get<ImpostaStatoProclamatoreUseCase>(),
            elimina = koin.get<EliminaProclamatoreUseCase>(),
            importaDaJson = koin.get<ImportaProclamatoriDaJsonUseCase>(),
            verificaDuplicato = koin.get<VerificaDuplicatoProclamatoreUseCase>(),
        )
    }
    val state by viewModel.uiState.collectAsState()

    val tableListState = rememberLazyListState()
    val searchFocusRequester = remember { FocusRequester() }
    val rootFocusRequester = remember { FocusRequester() }

    state.deleteCandidate?.let { candidate ->
        ProclamatoreDeleteDialog(
            candidate = candidate,
            isLoading = state.isLoading,
            onConfirm = { viewModel.confirmDeleteCandidate() },
            onDismiss = { viewModel.dismissDeleteCandidate() },
        )
    }

    if (state.showBatchDeleteConfirm) {
        ProclamatoriDeleteDialog(
            selectedCount = state.selectedIds.size,
            isLoading = state.isLoading,
            onConfirm = { viewModel.confirmBatchDelete() },
            onDismiss = { viewModel.dismissBatchDeleteConfirm() },
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

        val nomeTrim = state.nome.trim()
        val cognomeTrim = state.cognome.trim()
        val requiredFieldsValid = nomeTrim.isNotBlank() && cognomeTrim.isNotBlank()
        val hasFormChanges = when (route) {
            ProclamatoriRoute.Nuovo -> requiredFieldsValid || state.sesso != Sesso.M
            is ProclamatoriRoute.Modifica -> {
                nomeTrim != state.initialNome.trim() ||
                    cognomeTrim != state.initialCognome.trim() ||
                    state.sesso != state.initialSesso
            }
            ProclamatoriRoute.Elenco -> false
        }
        val canSubmitForm = isFormRoute &&
            requiredFieldsValid &&
            hasFormChanges &&
            state.duplicateError == null &&
            !state.isCheckingDuplicate &&
            !state.isLoading

        fun goToListManual() {
            viewModel.prepareForManualNavigation()
            navigator.replaceAll(ProclamatoriElencoScreen)
        }

        fun goToNuovo() {
            viewModel.prepareForManualNavigation()
            navigator.push(ProclamatoriNuovoScreen)
        }

        LaunchedEffect(route, state.nome, state.cognome, currentEditId) {
            viewModel.scheduleDuplicateCheck(isFormRoute = isFormRoute, currentEditId = currentEditId)
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
                            viewModel.submitForm(
                                route = route,
                                currentEditId = currentEditId,
                                onSuccessNavigateToList = { navigator.replaceAll(ProclamatoriElencoScreen) },
                            )
                            true
                        }
                        else -> false
                    }
                },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val currentModificaLabel = if (route is ProclamatoriRoute.Modifica) {
                listOf(state.nome.trim(), state.cognome.trim())
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
                        searchTerm = state.searchTerm,
                        onSearchTermChange = { value -> viewModel.setSearchTerm(value) },
                        onResetSearch = { viewModel.resetSearch() },
                        searchFocusRequester = searchFocusRequester,
                        allItems = state.allItems,
                        isLoading = state.isLoading,
                        notice = state.notice,
                        onDismissNotice = { viewModel.dismissNotice() },
                        selectedIds = state.selectedIds,
                        sort = state.sort,
                        pageIndex = state.pageIndex,
                        pageSize = state.pageSize,
                        tableListState = tableListState,
                        onSortChange = { nextSort -> viewModel.setSort(nextSort) },
                        onToggleSelectPage = { pageIds, checked -> viewModel.toggleSelectPage(pageIds, checked) },
                        onToggleRowSelected = { id, checked -> viewModel.setRowSelected(id, checked) },
                        onActivateSelected = { viewModel.activateSelected() },
                        onDeactivateSelected = { viewModel.deactivateSelected() },
                        onRequestDeleteSelected = { viewModel.requestBatchDeleteConfirm() },
                        onClearSelection = { viewModel.clearSelection() },
                        onGoNuovo = { goToNuovo() },
                        canImportInitialJson = !state.isLoading && state.allItems.isEmpty(),
                        onImportJson = {
                            val selectedFile = selectJsonFileForImport() ?: return@ProclamatoriElencoContent
                            viewModel.importFromJsonFile(selectedFile)
                        },
                        onEdit = { id ->
                            viewModel.loadForEdit(id) {
                                navigator.push(ProclamatoriModificaScreen(id))
                            }
                        },
                        onToggleActive = { id, next -> viewModel.toggleActive(id, next) },
                        onDelete = { candidate -> viewModel.requestDeleteCandidate(candidate) },
                        onPreviousPage = { viewModel.goToPreviousPage() },
                        onNextPage = { viewModel.goToNextPage() },
                    )
                }

                ProclamatoriRoute.Nuovo,
                is ProclamatoriRoute.Modifica,
                -> {
                    ProclamatoriFormContent(
                        route = route,
                        nome = state.nome,
                        onNomeChange = { viewModel.setNome(it) },
                        cognome = state.cognome,
                        onCognomeChange = { viewModel.setCognome(it) },
                        sesso = state.sesso,
                        onSessoChange = { viewModel.setSesso(it) },
                        nomeTrim = nomeTrim,
                        cognomeTrim = cognomeTrim,
                        showFieldErrors = state.showFieldErrors,
                        duplicateError = state.duplicateError,
                        isCheckingDuplicate = state.isCheckingDuplicate,
                        canSubmitForm = canSubmitForm,
                        isLoading = state.isLoading,
                        notice = state.notice,
                        onDismissNotice = { viewModel.dismissNotice() },
                        formError = state.formError,
                        onSubmit = {
                            viewModel.submitForm(
                                route = route,
                                currentEditId = currentEditId,
                                onSuccessNavigateToList = { navigator.replaceAll(ProclamatoriElencoScreen) },
                            )
                        },
                        onCancel = { goToListManual() },
                    )
                }
            }
        }
    }
}

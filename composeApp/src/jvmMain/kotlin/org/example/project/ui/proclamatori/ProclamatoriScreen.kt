package org.example.project.ui.proclamatori

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import arrow.core.Either
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.example.project.core.domain.DomainError
import org.example.project.feature.people.application.AggiornaProclamatoreUseCase
import org.example.project.feature.people.application.CaricaProclamatoreUseCase
import org.example.project.feature.people.application.CercaProclamatoriUseCase
import org.example.project.feature.people.application.CreaProclamatoreUseCase
import org.example.project.feature.people.application.EliminaProclamatoreUseCase
import org.example.project.feature.people.application.ImpostaStatoProclamatoreUseCase
import org.example.project.feature.people.application.ImportaProclamatoriDaJsonUseCase
import org.example.project.feature.people.application.VerificaDuplicatoProclamatoreUseCase
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.domain.Sesso
import org.example.project.ui.components.FeedbackBannerModel
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
    val cerca = remember { koin.get<CercaProclamatoriUseCase>() }
    val carica = remember { koin.get<CaricaProclamatoreUseCase>() }
    val crea = remember { koin.get<CreaProclamatoreUseCase>() }
    val aggiorna = remember { koin.get<AggiornaProclamatoreUseCase>() }
    val impostaStato = remember { koin.get<ImpostaStatoProclamatoreUseCase>() }
    val elimina = remember { koin.get<EliminaProclamatoreUseCase>() }
    val importaDaJson = remember { koin.get<ImportaProclamatoriDaJsonUseCase>() }
    val verificaDuplicato = remember { koin.get<VerificaDuplicatoProclamatoreUseCase>() }
    val scope = rememberCoroutineScope()
    val searchFocusRequester = remember { FocusRequester() }
    val rootFocusRequester = remember { FocusRequester() }

    var searchTerm by remember { mutableStateOf("") }
    var allItems by remember { mutableStateOf<List<Proclamatore>>(emptyList()) }
    val tableListState = rememberLazyListState()
    var isLoading by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<FeedbackBannerModel?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }
    var duplicateError by remember { mutableStateOf<String?>(null) }
    var isCheckingDuplicate by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(ProclamatoriSort()) }
    var pageIndex by remember { mutableIntStateOf(0) }
    val pageSize = 10

    var initialNome by remember { mutableStateOf("") }
    var initialCognome by remember { mutableStateOf("") }
    var initialSesso by remember { mutableStateOf(Sesso.M) }
    var nome by remember { mutableStateOf("") }
    var cognome by remember { mutableStateOf("") }
    var sesso by remember { mutableStateOf(Sesso.M) }
    var showFieldErrors by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<ProclamatoreId>()) }

    var deleteCandidate by remember { mutableStateOf<Proclamatore?>(null) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    suspend fun refreshList(resetPage: Boolean = false) {
        isLoading = true
        allItems = cerca(searchTerm)
        val validIds = allItems.map { it.id }.toSet()
        selectedIds = selectedIds.filterTo(mutableSetOf()) { it in validIds }
        if (resetPage) {
            pageIndex = 0
        }
        val maxPage = (allItems.size - 1).coerceAtLeast(0) / pageSize
        if (pageIndex > maxPage) pageIndex = maxPage
        isLoading = false
    }

    fun clearForm() {
        initialNome = ""
        initialCognome = ""
        initialSesso = Sesso.M
        nome = ""
        cognome = ""
        sesso = Sesso.M
        formError = null
        duplicateError = null
        isCheckingDuplicate = false
        showFieldErrors = false
    }

    suspend fun executeOnSelected(
        action: suspend (ProclamatoreId) -> Either<DomainError, Unit>,
        completedLabel: String,
        noneCompletedLabel: String,
    ): FeedbackBannerModel {
        isLoading = true
        val result = runMultiAction(selectedIds, action)
        refreshList()
        selectedIds = result.failedIds
        isLoading = false
        return noticeForMultiAction(
            result = result,
            completedLabel = completedLabel,
            noneCompletedLabel = noneCompletedLabel,
        )
    }

    suspend fun openEdit(id: ProclamatoreId): Boolean {
        isLoading = true
        val loaded = carica(id)
        if (loaded == null) {
            notice = errorNotice("Proclamatore non trovato")
            isLoading = false
            return false
        } else {
            notice = null
            initialNome = loaded.nome
            initialCognome = loaded.cognome
            initialSesso = loaded.sesso
            nome = loaded.nome
            cognome = loaded.cognome
            sesso = loaded.sesso
            isLoading = false
            return true
        }
    }

    LaunchedEffect(Unit) {
        refreshList(resetPage = true)
    }

    deleteCandidate?.let { candidate ->
        ProclamatoreDeleteDialog(
            candidate = candidate,
            isLoading = isLoading,
            onConfirm = {
                scope.launch {
                    isLoading = true
                    val result = elimina(candidate.id)
                    result.fold(
                        ifLeft = { err ->
                            notice = errorNotice(
                                (err as? DomainError.Validation)?.message ?: "Rimozione non completata",
                            )
                        },
                        ifRight = {
                            notice = successNotice(
                                details = "Rimosso ${personDetails(candidate.nome, candidate.cognome)}",
                            )
                            refreshList()
                        },
                    )
                    deleteCandidate = null
                    isLoading = false
                }
            },
            onDismiss = { deleteCandidate = null },
        )
    }

    if (showBatchDeleteConfirm) {
        ProclamatoriDeleteDialog(
            selectedCount = selectedIds.size,
            isLoading = isLoading,
            onConfirm = {
                scope.launch {
                    notice = executeOnSelected(
                        action = { id -> elimina(id) },
                        completedLabel = "Proclamatori rimossi",
                        noneCompletedLabel = "Nessun proclamatore rimosso",
                    )
                    showBatchDeleteConfirm = false
                }
            },
            onDismiss = { showBatchDeleteConfirm = false },
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
        val nomeTrim = nome.trim()
        val cognomeTrim = cognome.trim()
        val requiredFieldsValid = nomeTrim.isNotBlank() && cognomeTrim.isNotBlank()
        val hasFormChanges = when (route) {
            ProclamatoriRoute.Nuovo -> requiredFieldsValid || sesso != Sesso.M
            is ProclamatoriRoute.Modifica -> {
                nomeTrim != initialNome.trim() ||
                    cognomeTrim != initialCognome.trim() ||
                    sesso != initialSesso
            }
            ProclamatoriRoute.Elenco -> false
        }
        val canSubmitForm = isFormRoute &&
            requiredFieldsValid &&
            hasFormChanges &&
            duplicateError == null &&
            !isCheckingDuplicate &&
            !isLoading

        fun setRowSelected(id: ProclamatoreId, checked: Boolean) {
            selectedIds = if (checked) {
                selectedIds + id
            } else {
                selectedIds - id
            }
        }

        fun goToList() {
            notice = null
            selectedIds = emptySet()
            navigator.replaceAll(ProclamatoriElencoScreen)
            clearForm()
            formError = null
        }

        fun goToNuovo() {
            notice = null
            selectedIds = emptySet()
            clearForm()
            formError = null
            navigator.push(ProclamatoriNuovoScreen)
        }

        fun importaJsonIniziale() {
            val selectedFile = selectJsonFileForImport() ?: return
            scope.launch {
                isLoading = true
                val jsonContent = withContext(Dispatchers.IO) {
                    runCatching { selectedFile.readText(Charsets.UTF_8) }.getOrNull()
                }
                if (jsonContent == null) {
                    notice = errorNotice("Impossibile leggere il file selezionato")
                    isLoading = false
                    return@launch
                }

                val result = withContext(Dispatchers.IO) { importaDaJson(jsonContent) }
                result.fold(
                    ifLeft = { err ->
                        notice = errorNotice(
                            (err as? DomainError.Validation)?.message ?: "Import non completato",
                        )
                    },
                    ifRight = { imported ->
                        notice = successNotice("Importati ${imported.importati} proclamatori da ${selectedFile.name}")
                        refreshList(resetPage = true)
                    },
                )
                isLoading = false
            }
        }

        fun submitForm() {
            if (!canSubmitForm) return
            scope.launch {
                showFieldErrors = true
                formError = null
                isLoading = true
                val result = if (route == ProclamatoriRoute.Nuovo) {
                    crea(
                        CreaProclamatoreUseCase.Command(
                            nome = nome,
                            cognome = cognome,
                            sesso = sesso,
                        ),
                    )
                } else {
                    aggiorna(
                        AggiornaProclamatoreUseCase.Command(
                            id = requireNotNull(currentEditId),
                            nome = nome,
                            cognome = cognome,
                            sesso = sesso,
                        ),
                    )
                }
                result.fold(
                    ifLeft = { err ->
                        formError = (err as? DomainError.Validation)?.message ?: "Operazione non completata"
                    },
                    ifRight = {
                        val operation = if (route == ProclamatoriRoute.Nuovo) {
                            "Proclamatore aggiunto"
                        } else {
                            "Proclamatore aggiornato"
                        }
                        notice = successNotice("$operation: ${personDetails(nome, cognome)}")
                        navigator.replaceAll(ProclamatoriElencoScreen)
                        clearForm()
                        refreshList()
                    },
                )
                isLoading = false
            }
        }

        LaunchedEffect(route, nome, cognome, currentEditId) {
            if (!isFormRoute) {
                duplicateError = null
                isCheckingDuplicate = false
                return@LaunchedEffect
            }
            if (nomeTrim.isBlank() || cognomeTrim.isBlank()) {
                duplicateError = null
                isCheckingDuplicate = false
                return@LaunchedEffect
            }
            isCheckingDuplicate = true
            delay(250)
            val exists = verificaDuplicato(nomeTrim, cognomeTrim, currentEditId)
            duplicateError = if (exists) {
                "Esiste gia' un proclamatore con questo nome e cognome"
            } else {
                null
            }
            isCheckingDuplicate = false
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
                            goToList()
                            true
                        }
                        event.key == Key.Enter && isFormRoute && canSubmitForm -> {
                            submitForm()
                            true
                        }
                        else -> false
                    }
                },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        val currentModificaLabel = if (route is ProclamatoriRoute.Modifica) {
            listOf(nome.trim(), cognome.trim())
                .filter { it.isNotEmpty() }
                .joinToString(" ")
                .ifBlank { "Modifica" }
        } else {
            null
        }

        Breadcrumbs(
            route = route,
            currentModificaLabel = currentModificaLabel,
            onGoList = {
                goToList()
            },
        )

        when (route) {
            ProclamatoriRoute.Elenco -> {
                ProclamatoriElencoContent(
                    searchTerm = searchTerm,
                    onSearchTermChange = { value ->
                        searchTerm = value
                        scope.launch { refreshList(resetPage = true) }
                    },
                    onResetSearch = {
                        searchTerm = ""
                        scope.launch { refreshList(resetPage = true) }
                    },
                    searchFocusRequester = searchFocusRequester,
                    allItems = allItems,
                    isLoading = isLoading,
                    notice = notice,
                    onDismissNotice = { notice = null },
                    selectedIds = selectedIds,
                    sort = sort,
                    pageIndex = pageIndex,
                    pageSize = pageSize,
                    tableListState = tableListState,
                    onSortChange = { nextSort -> sort = nextSort },
                    onToggleSelectPage = { pageIds, checked ->
                        selectedIds = if (checked) {
                            selectedIds + pageIds
                        } else {
                            selectedIds - pageIds.toSet()
                        }
                    },
                    onToggleRowSelected = { id, checked -> setRowSelected(id, checked) },
                    onActivateSelected = {
                        scope.launch {
                            notice = executeOnSelected(
                                action = { id -> impostaStato(id, true) },
                                completedLabel = "Proclamatori attivati",
                                noneCompletedLabel = "Nessun proclamatore attivato",
                            )
                        }
                    },
                    onDeactivateSelected = {
                        scope.launch {
                            notice = executeOnSelected(
                                action = { id -> impostaStato(id, false) },
                                completedLabel = "Proclamatori disattivati",
                                noneCompletedLabel = "Nessun proclamatore disattivato",
                            )
                        }
                    },
                    onRequestDeleteSelected = { showBatchDeleteConfirm = true },
                    onClearSelection = { selectedIds = emptySet() },
                    onGoNuovo = { goToNuovo() },
                    onImportJson = { importaJsonIniziale() },
                    onEdit = { id ->
                        scope.launch {
                            if (openEdit(id)) {
                                navigator.push(ProclamatoriModificaScreen(id))
                            }
                        }
                    },
                    onToggleActive = { id, next ->
                        scope.launch {
                            isLoading = true
                            impostaStato(id, next)
                            refreshList()
                            isLoading = false
                        }
                    },
                    onDelete = { candidate -> deleteCandidate = candidate },
                    onPreviousPage = { pageIndex = (pageIndex - 1).coerceAtLeast(0) },
                    onNextPage = {
                        val totalPages = if (allItems.isEmpty()) 1 else ((allItems.size - 1) / pageSize) + 1
                        pageIndex = (pageIndex + 1).coerceAtMost(totalPages - 1)
                    },
                )
            }
            ProclamatoriRoute.Nuovo,
            is ProclamatoriRoute.Modifica,
            -> {
                ProclamatoriFormContent(
                    route = route,
                    nome = nome,
                    onNomeChange = { value -> nome = value },
                    cognome = cognome,
                    onCognomeChange = { value -> cognome = value },
                    sesso = sesso,
                    onSessoChange = { nextSesso -> sesso = nextSesso },
                    nomeTrim = nomeTrim,
                    cognomeTrim = cognomeTrim,
                    showFieldErrors = showFieldErrors,
                    duplicateError = duplicateError,
                    isCheckingDuplicate = isCheckingDuplicate,
                    canSubmitForm = canSubmitForm,
                    isLoading = isLoading,
                    notice = notice,
                    onDismissNotice = { notice = null },
                    formError = formError,
                    onSubmit = {
                        showFieldErrors = true
                        submitForm()
                    },
                    onCancel = { goToList() },
                )
            }
        }
    }
}

}

package org.example.project.ui.proclamatori

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.focusable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.example.project.core.domain.DomainError
import org.example.project.feature.people.application.AggiornaProclamatoreUseCase
import org.example.project.feature.people.application.CaricaProclamatoreUseCase
import org.example.project.feature.people.application.CercaProclamatoriUseCase
import org.example.project.feature.people.application.CreaProclamatoreUseCase
import org.example.project.feature.people.application.EliminaProclamatoreUseCase
import org.example.project.feature.people.application.ImpostaStatoProclamatoreUseCase
import org.example.project.feature.people.application.VerificaDuplicatoProclamatoreUseCase
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.domain.Sesso
import org.example.project.ui.components.FeedbackBanner
import org.example.project.ui.components.FeedbackBannerKind
import org.example.project.ui.components.FeedbackBannerModel
import org.example.project.ui.components.StandardTableEmptyRow
import org.example.project.ui.components.StandardTableHeader
import org.example.project.ui.components.StandardTableViewport
import org.example.project.ui.components.TableColumnSpec
import org.example.project.ui.components.handCursorOnHover
import org.example.project.ui.components.standardTableCell
import org.koin.core.context.GlobalContext

private sealed interface ProclamatoriRoute {
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

private val proclamatoriTableColumns = listOf(
    TableColumnSpec("", 0.6f),
    TableColumnSpec("Nome", 2f),
    TableColumnSpec("Cognome", 2f),
    TableColumnSpec("Sesso", 1f),
    TableColumnSpec("Attivo", 1f),
    TableColumnSpec("Azioni", 3f),
)

private const val proclamatoriTableTotalWeight = 9.6f
private val tableScrollbarPadding = 12.dp
private val successTimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

private enum class ProclamatoriSortField { NOME, COGNOME, SESSO, ATTIVO }
private enum class SortDirection { ASC, DESC }
private data class ProclamatoriSort(
    val field: ProclamatoriSortField = ProclamatoriSortField.COGNOME,
    val direction: SortDirection = SortDirection.ASC,
)

private fun personDetails(nome: String, cognome: String): String {
    val fullName = listOf(nome.trim(), cognome.trim())
        .filter { it.isNotEmpty() }
        .joinToString(" ")
        .ifBlank { "-" }
    return "Proclamatore: $fullName"
}

private fun detailsWithTimestamp(details: String? = null): String {
    val timestamp = LocalDateTime.now().format(successTimestampFormatter)
    return if (details.isNullOrBlank()) {
        "Ora: $timestamp"
    } else {
        "$details | Ora: $timestamp"
    }
}

private fun successNotice(details: String): FeedbackBannerModel {
    return FeedbackBannerModel(
        message = "Operazione completata",
        kind = FeedbackBannerKind.SUCCESS,
        details = detailsWithTimestamp(details),
    )
}

private fun errorNotice(details: String): FeedbackBannerModel {
    return FeedbackBannerModel(
        message = "Operazione non completata",
        kind = FeedbackBannerKind.ERROR,
        details = detailsWithTimestamp(details),
    )
}

private fun partialNotice(details: String): FeedbackBannerModel {
    return FeedbackBannerModel(
        message = "Operazione parziale",
        kind = FeedbackBannerKind.ERROR,
        details = detailsWithTimestamp(details),
    )
}

private fun toggleSort(current: ProclamatoriSort, field: ProclamatoriSortField): ProclamatoriSort {
    return if (current.field == field) {
        current.copy(direction = if (current.direction == SortDirection.ASC) SortDirection.DESC else SortDirection.ASC)
    } else {
        ProclamatoriSort(field = field, direction = SortDirection.ASC)
    }
}

private fun List<Proclamatore>.applySort(sort: ProclamatoriSort): List<Proclamatore> {
    val comparator = when (sort.field) {
        ProclamatoriSortField.NOME -> compareBy<Proclamatore> { it.nome.lowercase() }
            .thenBy { it.cognome.lowercase() }
        ProclamatoriSortField.COGNOME -> compareBy<Proclamatore> { it.cognome.lowercase() }
            .thenBy { it.nome.lowercase() }
        ProclamatoriSortField.SESSO -> compareBy<Proclamatore> { it.sesso.name }
            .thenBy { it.cognome.lowercase() }
            .thenBy { it.nome.lowercase() }
        ProclamatoriSortField.ATTIVO -> compareBy<Proclamatore> { if (it.attivo) 0 else 1 }
            .thenBy { it.cognome.lowercase() }
            .thenBy { it.nome.lowercase() }
    }
    val sorted = this.sortedWith(comparator)
    return if (sort.direction == SortDirection.ASC) sorted else sorted.reversed()
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

    if (deleteCandidate != null) {
        DisableSelection {
            AlertDialog(
                onDismissRequest = { deleteCandidate = null },
                title = { Text("Rimuovi proclamatore") },
                text = {
                    Text(
                        "Confermi rimozione di " +
                            "${deleteCandidate!!.nome} ${deleteCandidate!!.cognome}?",
                    )
                },
                confirmButton = {
                    TextButton(
                        modifier = Modifier.handCursorOnHover(enabled = !isLoading),
                        onClick = {
                            val candidate = deleteCandidate ?: return@TextButton
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
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) { Text("Rimuovi") }
                },
                dismissButton = {
                    TextButton(
                        modifier = Modifier.handCursorOnHover(),
                        onClick = { deleteCandidate = null },
                    ) { Text("Annulla") }
                },
            )
        }
    }

    if (showBatchDeleteConfirm) {
        DisableSelection {
            AlertDialog(
                onDismissRequest = { showBatchDeleteConfirm = false },
                title = { Text("Rimuovi proclamatori selezionati") },
                text = {
                    Text("Confermi rimozione di ${selectedIds.size} proclamatori selezionati?")
                },
                confirmButton = {
                    TextButton(
                        modifier = Modifier.handCursorOnHover(enabled = !isLoading),
                        onClick = {
                            scope.launch {
                                val idsToRemove = selectedIds.toList()
                                var removedCount = 0
                                var failedCount = 0
                                val failedIds = mutableSetOf<ProclamatoreId>()
                                isLoading = true
                                idsToRemove.forEach { id ->
                                    elimina(id).fold(
                                        ifLeft = {
                                            failedCount++
                                            failedIds += id
                                        },
                                        ifRight = {
                                            removedCount++
                                        },
                                    )
                                }
                                refreshList()
                                selectedIds = failedIds
                                showBatchDeleteConfirm = false
                                isLoading = false

                                notice = when {
                                    removedCount > 0 && failedCount == 0 -> {
                                        successNotice("Proclamatori rimossi: $removedCount")
                                    }
                                    removedCount == 0 -> {
                                        errorNotice("Nessun proclamatore rimosso")
                                    }
                                    else -> {
                                        partialNotice("Proclamatori rimossi: $removedCount | Errori: $failedCount")
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) { Text("Rimuovi") }
                },
                dismissButton = {
                    TextButton(
                        modifier = Modifier.handCursorOnHover(),
                        onClick = { showBatchDeleteConfirm = false },
                    ) { Text("Annulla") }
                },
            )
        }
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Proclamatori", style = MaterialTheme.typography.headlineMedium)
                        Button(
                            modifier = Modifier.handCursorOnHover(enabled = !isLoading),
                            onClick = { goToNuovo() },
                            enabled = !isLoading,
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text("Aggiungi")
                        }
                    }

                    OutlinedTextField(
                        value = searchTerm,
                        onValueChange = { value ->
                            searchTerm = value
                            scope.launch { refreshList(resetPage = true) }
                        },
                        modifier = Modifier
                            .width(320.dp)
                            .focusRequester(searchFocusRequester),
                        label = { Text("Ricerca") },
                        singleLine = true,
                        trailingIcon = {
                            if (searchTerm.isNotBlank()) {
                                IconButton(
                                    modifier = Modifier.handCursorOnHover(),
                                    onClick = {
                                        searchTerm = ""
                                        scope.launch { refreshList(resetPage = true) }
                                    },
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Reset ricerca")
                                }
                            }
                        },
                    )
                }

                FeedbackBanner(
                    model = notice,
                    onDismissRequest = { notice = null },
                )

                val totalPages = if (allItems.isEmpty()) 1 else ((allItems.size - 1) / pageSize) + 1
                val sortedItems = allItems.applySort(sort)
                val pageItems = sortedItems
                    .drop(pageIndex * pageSize)
                    .take(pageSize)
                val pageItemIds = pageItems.map { it.id }
                val hasBatchSelection = selectedIds.isNotEmpty()
                val batchActionsEnabled = hasBatchSelection && !isLoading
                val allPageSelected = pageItemIds.isNotEmpty() && pageItemIds.all { it in selectedIds }
                val tableLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)

                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = allPageSelected,
                                onCheckedChange = { checked ->
                                    selectedIds = if (checked) {
                                        selectedIds + pageItemIds
                                    } else {
                                        selectedIds - pageItemIds.toSet()
                                    }
                                },
                                enabled = !isLoading && pageItemIds.isNotEmpty(),
                            )
                            Text("Selezionati: ${selectedIds.size}")
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Button(
                                modifier = Modifier.handCursorOnHover(enabled = batchActionsEnabled),
                                onClick = {
                                    scope.launch {
                                        val ids = selectedIds.toList()
                                        var updatedCount = 0
                                        var failedCount = 0
                                        val failedIds = mutableSetOf<ProclamatoreId>()
                                        isLoading = true
                                        ids.forEach { id ->
                                            impostaStato(id, true).fold(
                                                ifLeft = {
                                                    failedCount++
                                                    failedIds += id
                                                },
                                                ifRight = {
                                                    updatedCount++
                                                },
                                            )
                                        }
                                        refreshList()
                                        selectedIds = failedIds
                                        isLoading = false
                                        notice = when {
                                            updatedCount > 0 && failedCount == 0 -> {
                                                successNotice("Proclamatori attivati: $updatedCount")
                                            }
                                            updatedCount == 0 -> {
                                                errorNotice("Nessun proclamatore attivato")
                                            }
                                            else -> {
                                                partialNotice("Proclamatori attivati: $updatedCount | Errori: $failedCount")
                                            }
                                        }
                                    }
                                },
                                enabled = batchActionsEnabled,
                            ) {
                                Text("Attiva")
                            }
                            Button(
                                modifier = Modifier.handCursorOnHover(enabled = batchActionsEnabled),
                                onClick = {
                                    scope.launch {
                                        val ids = selectedIds.toList()
                                        var updatedCount = 0
                                        var failedCount = 0
                                        val failedIds = mutableSetOf<ProclamatoreId>()
                                        isLoading = true
                                        ids.forEach { id ->
                                            impostaStato(id, false).fold(
                                                ifLeft = {
                                                    failedCount++
                                                    failedIds += id
                                                },
                                                ifRight = {
                                                    updatedCount++
                                                },
                                            )
                                        }
                                        refreshList()
                                        selectedIds = failedIds
                                        isLoading = false
                                        notice = when {
                                            updatedCount > 0 && failedCount == 0 -> {
                                                successNotice("Proclamatori disattivati: $updatedCount")
                                            }
                                            updatedCount == 0 -> {
                                                errorNotice("Nessun proclamatore disattivato")
                                            }
                                            else -> {
                                                partialNotice("Proclamatori disattivati: $updatedCount | Errori: $failedCount")
                                            }
                                        }
                                    }
                                },
                                enabled = batchActionsEnabled,
                            ) {
                                Text("Disattiva")
                            }
                            Button(
                                modifier = Modifier.handCursorOnHover(enabled = batchActionsEnabled),
                                onClick = { showBatchDeleteConfirm = true },
                                enabled = batchActionsEnabled,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                ),
                            ) {
                                Text("Rimuovi")
                            }
                            TextButton(
                                modifier = Modifier.handCursorOnHover(enabled = hasBatchSelection),
                                onClick = { selectedIds = emptySet() },
                                enabled = hasBatchSelection && !isLoading,
                            ) {
                                Text("Annulla selezione")
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StandardTableHeader(
                            columns = proclamatoriTableColumns,
                            modifier = Modifier.padding(end = tableScrollbarPadding),
                            lineColor = tableLineColor,
                            onColumnClick = { index ->
                                val sortField = when (index) {
                                    1 -> ProclamatoriSortField.NOME
                                    2 -> ProclamatoriSortField.COGNOME
                                    3 -> ProclamatoriSortField.SESSO
                                    4 -> ProclamatoriSortField.ATTIVO
                                    else -> null
                                }
                                if (sortField != null) {
                                    sort = toggleSort(sort, sortField)
                                }
                            },
                            sortIndicatorText = { index ->
                                val field = when (index) {
                                    1 -> ProclamatoriSortField.NOME
                                    2 -> ProclamatoriSortField.COGNOME
                                    3 -> ProclamatoriSortField.SESSO
                                    4 -> ProclamatoriSortField.ATTIVO
                                    else -> null
                                }
                                if (field == sort.field) {
                                    if (sort.direction == SortDirection.ASC) "▲" else "▼"
                                } else {
                                    null
                                }
                            },
                        )
                        StandardTableViewport(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = true),
                            lineColor = tableLineColor,
                        ) {
                            LazyColumn(
                                state = tableListState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(end = tableScrollbarPadding),
                            ) {
                                if (pageItems.isEmpty()) {
                                    item {
                                        StandardTableEmptyRow(
                                            message = "Nessun proclamatore",
                                            totalWeight = proclamatoriTableTotalWeight,
                                            lineColor = tableLineColor,
                                        )
                                    }
                                } else {
                                    items(pageItems, key = { it.id.value }) { item ->
                                        TableDataRow(
                                            proclamatore = item,
                                            loading = isLoading,
                                            selected = item.id in selectedIds,
                                            batchMode = hasBatchSelection,
                                            lineColor = tableLineColor,
                                            onToggleSelected = { checked ->
                                                selectedIds = if (checked) {
                                                    selectedIds + item.id
                                                } else {
                                                    selectedIds - item.id
                                                }
                                            },
                                            onEdit = {
                                                scope.launch {
                                                    if (openEdit(item.id)) {
                                                        navigator.push(ProclamatoriModificaScreen(item.id))
                                                    }
                                                }
                                            },
                                            onToggleActive = { next ->
                                                scope.launch {
                                                    isLoading = true
                                                    impostaStato(item.id, next)
                                                    refreshList()
                                                    isLoading = false
                                                }
                                            },
                                            onDelete = { deleteCandidate = item },
                                        )
                                    }
                                }
                            }

                            VerticalScrollbar(
                                adapter = rememberScrollbarAdapter(tableListState),
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight(),
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Totale: ${allItems.size}")
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Button(
                                    modifier = Modifier.handCursorOnHover(enabled = !isLoading && pageIndex > 0),
                                    onClick = { pageIndex = (pageIndex - 1).coerceAtLeast(0) },
                                    enabled = !isLoading && pageIndex > 0,
                                ) { Text("Prec") }
                                Text("Pagina ${pageIndex + 1} / $totalPages")
                                Button(
                                    modifier = Modifier.handCursorOnHover(enabled = !isLoading && pageIndex < totalPages - 1),
                                    onClick = { pageIndex = (pageIndex + 1).coerceAtMost(totalPages - 1) },
                                    enabled = !isLoading && pageIndex < totalPages - 1,
                                ) { Text("Succ") }
                            }
                        }
                    }
                }
            }

            ProclamatoriRoute.Nuovo,
            is ProclamatoriRoute.Modifica,
            -> {
                Text(
                    if (route == ProclamatoriRoute.Nuovo) "Nuovo proclamatore" else "Modifica proclamatore",
                    style = MaterialTheme.typography.headlineMedium,
                )

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = nome,
                            onValueChange = { nome = it },
                            label = { Text("Nome") },
                            isError = (showFieldErrors && nomeTrim.isBlank()) || duplicateError != null,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            supportingText = {
                                if (showFieldErrors && nomeTrim.isBlank()) {
                                    Text("Campo obbligatorio", color = MaterialTheme.colorScheme.error)
                                }
                            },
                        )
                        OutlinedTextField(
                            value = cognome,
                            onValueChange = { cognome = it },
                            label = { Text("Cognome") },
                            isError = (showFieldErrors && cognomeTrim.isBlank()) || duplicateError != null,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            supportingText = {
                                if (showFieldErrors && cognomeTrim.isBlank()) {
                                    Text("Campo obbligatorio", color = MaterialTheme.colorScheme.error)
                                } else if (duplicateError != null) {
                                    Text(duplicateError!!, color = MaterialTheme.colorScheme.error)
                                } else if (isCheckingDuplicate) {
                                    Text("Verifica duplicato in corso...")
                                }
                            },
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = sesso == Sesso.M, onClick = { sesso = Sesso.M })
                                Text("Uomo")
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = sesso == Sesso.F, onClick = { sesso = Sesso.F })
                                Text("Donna")
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Button(
                                modifier = Modifier.handCursorOnHover(enabled = canSubmitForm),
                                onClick = {
                                    showFieldErrors = true
                                    submitForm()
                                },
                                enabled = canSubmitForm,
                            ) {
                                Text(if (route == ProclamatoriRoute.Nuovo) "Salva" else "Aggiorna")
                            }
                            TextButton(
                                modifier = Modifier.handCursorOnHover(enabled = !isLoading),
                                onClick = { goToList() },
                                enabled = !isLoading,
                            ) {
                                Text("Annulla")
                            }
                        }
                        FeedbackBanner(
                            model = notice,
                            onDismissRequest = { notice = null },
                        )
                        if (formError != null) {
                            SelectionContainer {
                                Text(
                                    formError!!,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
private fun Breadcrumbs(
    route: ProclamatoriRoute,
    currentModificaLabel: String?,
    onGoList: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(
            modifier = Modifier.handCursorOnHover(),
            onClick = onGoList,
        ) { Text("Proclamatori") }
        Text("/")
        when (route) {
            ProclamatoriRoute.Elenco -> SelectionContainer { Text("Elenco") }
            ProclamatoriRoute.Nuovo -> SelectionContainer { Text("Nuovo") }
            is ProclamatoriRoute.Modifica -> SelectionContainer { Text(currentModificaLabel ?: "Modifica") }
        }
    }
}

@Composable
private fun TableDataRow(
    proclamatore: Proclamatore,
    loading: Boolean,
    selected: Boolean,
    batchMode: Boolean,
    lineColor: Color,
    onToggleSelected: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val singleActionsEnabled = !loading && !batchMode
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.weight(0.6f).fillMaxHeight().standardTableCell(lineColor),
            contentAlignment = Alignment.Center,
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = onToggleSelected,
                enabled = !loading,
            )
        }
        Box(modifier = Modifier.weight(2f).fillMaxHeight().standardTableCell(lineColor), contentAlignment = Alignment.CenterStart) {
            Text(proclamatore.nome)
        }
        Box(modifier = Modifier.weight(2f).fillMaxHeight().standardTableCell(lineColor), contentAlignment = Alignment.CenterStart) {
            Text(proclamatore.cognome)
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight().standardTableCell(lineColor), contentAlignment = Alignment.CenterStart) {
            Text(proclamatore.sesso.name)
        }
        Row(
            modifier = Modifier.weight(1f).fillMaxHeight().standardTableCell(lineColor),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(if (proclamatore.attivo) "Si" else "No")
            Switch(
                checked = proclamatore.attivo,
                onCheckedChange = onToggleActive,
                enabled = singleActionsEnabled,
            )
        }
        Row(
            modifier = Modifier.weight(3f).fillMaxHeight().standardTableCell(lineColor),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Button(
                modifier = Modifier.handCursorOnHover(enabled = singleActionsEnabled),
                onClick = onEdit,
                enabled = singleActionsEnabled,
            ) {
                Icon(Icons.Filled.Edit, contentDescription = null)
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Modifica")
            }
            Button(
                modifier = Modifier.handCursorOnHover(enabled = singleActionsEnabled),
                onClick = onDelete,
                enabled = singleActionsEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Rimuovi")
            }
        }
    }
}

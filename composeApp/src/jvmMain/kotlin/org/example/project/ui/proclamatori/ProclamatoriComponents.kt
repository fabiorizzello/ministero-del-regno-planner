package org.example.project.ui.proclamatori

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.domain.Sesso
import org.example.project.ui.components.FeedbackBanner
import org.example.project.ui.components.FeedbackBannerModel
import org.example.project.ui.components.handCursorOnHover

@Composable
internal fun Breadcrumbs(
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
internal fun ProclamatoreDeleteDialog(
    candidate: Proclamatore,
    isLoading: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    DisableSelection {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Rimuovi proclamatore") },
            text = {
                Text("Confermi rimozione di ${candidate.nome} ${candidate.cognome}?")
            },
            confirmButton = {
                TextButton(
                    modifier = Modifier.handCursorOnHover(enabled = !isLoading),
                    onClick = onConfirm,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Rimuovi") }
            },
            dismissButton = {
                TextButton(
                    modifier = Modifier.handCursorOnHover(),
                    onClick = onDismiss,
                ) { Text("Annulla") }
            },
        )
    }
}

@Composable
internal fun ProclamatoriDeleteDialog(
    selectedCount: Int,
    isLoading: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    DisableSelection {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Rimuovi proclamatori selezionati") },
            text = {
                Text("Confermi rimozione di $selectedCount proclamatori selezionati?")
            },
            confirmButton = {
                TextButton(
                    modifier = Modifier.handCursorOnHover(enabled = !isLoading),
                    onClick = onConfirm,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Rimuovi") }
            },
            dismissButton = {
                TextButton(
                    modifier = Modifier.handCursorOnHover(),
                    onClick = onDismiss,
                ) { Text("Annulla") }
            },
        )
    }
}

@Composable
internal fun ColumnScope.ProclamatoriElencoContent(
    searchTerm: String,
    onSearchTermChange: (String) -> Unit,
    onResetSearch: () -> Unit,
    searchFocusRequester: FocusRequester,
    allItems: List<Proclamatore>,
    isLoading: Boolean,
    notice: FeedbackBannerModel?,
    onDismissNotice: () -> Unit,
    selectedIds: Set<ProclamatoreId>,
    sort: ProclamatoriSort,
    pageIndex: Int,
    pageSize: Int,
    tableListState: LazyListState,
    onSortChange: (ProclamatoriSort) -> Unit,
    onToggleSelectPage: (List<ProclamatoreId>, Boolean) -> Unit,
    onToggleRowSelected: (ProclamatoreId, Boolean) -> Unit,
    onActivateSelected: () -> Unit,
    onDeactivateSelected: () -> Unit,
    onRequestDeleteSelected: () -> Unit,
    onClearSelection: () -> Unit,
    onGoNuovo: () -> Unit,
    canImportInitialJson: Boolean,
    onImportJson: () -> Unit,
    onEdit: (ProclamatoreId) -> Unit,
    onToggleActive: (ProclamatoreId, Boolean) -> Unit,
    onDelete: (Proclamatore) -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
) {
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
                onClick = onGoNuovo,
                enabled = !isLoading,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Aggiungi")
            }
            if (canImportInitialJson) {
                Button(
                    modifier = Modifier.handCursorOnHover(enabled = !isLoading),
                    onClick = onImportJson,
                    enabled = !isLoading,
                ) {
                    Text("Importa JSON iniziale")
                }
            }
        }

        OutlinedTextField(
            value = searchTerm,
            onValueChange = onSearchTermChange,
            modifier = Modifier
                .width(320.dp)
                .focusRequester(searchFocusRequester),
            label = { Text("Ricerca") },
            singleLine = true,
            trailingIcon = {
                if (searchTerm.isNotBlank()) {
                    IconButton(
                        modifier = Modifier.handCursorOnHover(),
                        onClick = onResetSearch,
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Reset ricerca")
                    }
                }
            },
        )
    }

    FeedbackBanner(
        model = notice,
        onDismissRequest = onDismissNotice,
    )

    val totalPages = if (allItems.isEmpty()) 1 else ((allItems.size - 1) / pageSize) + 1
    val sortedItems = allItems.applySort(sort)
    val pageItems = sortedItems
        .drop(pageIndex * pageSize)
        .take(pageSize)
    val pageItemIds = pageItems.map { it.id }
    val hasSelection = selectedIds.isNotEmpty()
    val batchActionsEnabled = !isLoading
    val allPageSelected = pageItemIds.isNotEmpty() && pageItemIds.all { it in selectedIds }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
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
                    onCheckedChange = { checked -> onToggleSelectPage(pageItemIds, checked) },
                    enabled = !isLoading && pageItemIds.isNotEmpty(),
                )
                Text("Selezionati: ${selectedIds.size}")
            }
            if (hasSelection) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        modifier = Modifier.handCursorOnHover(enabled = batchActionsEnabled),
                        onClick = onActivateSelected,
                        enabled = batchActionsEnabled,
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Attiva")
                    }
                    Button(
                        modifier = Modifier.handCursorOnHover(enabled = batchActionsEnabled),
                        onClick = onDeactivateSelected,
                        enabled = batchActionsEnabled,
                    ) {
                        Icon(Icons.Filled.Block, contentDescription = null)
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Disattiva")
                    }
                    Button(
                        modifier = Modifier.handCursorOnHover(enabled = batchActionsEnabled),
                        onClick = onRequestDeleteSelected,
                        enabled = batchActionsEnabled,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Rimuovi")
                    }
                    TextButton(
                        modifier = Modifier.handCursorOnHover(enabled = !isLoading),
                        onClick = onClearSelection,
                        enabled = !isLoading,
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = null)
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Annulla selezione")
                    }
                }
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().weight(1f, fill = true),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(end = 12.dp)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(0.6f))
                Text(
                    text = "Nome" + (sortIndicatorForColumn(1, sort)?.let { " $it" } ?: ""),
                    modifier = Modifier
                        .weight(2f)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { sortFieldForColumn(1)?.let { field -> onSortChange(toggleSort(sort, field)) } },
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = "Cognome" + (sortIndicatorForColumn(2, sort)?.let { " $it" } ?: ""),
                    modifier = Modifier
                        .weight(2f)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { sortFieldForColumn(2)?.let { field -> onSortChange(toggleSort(sort, field)) } },
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = "Sesso" + (sortIndicatorForColumn(3, sort)?.let { " $it" } ?: ""),
                    modifier = Modifier
                        .weight(1f)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { sortFieldForColumn(3)?.let { field -> onSortChange(toggleSort(sort, field)) } },
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = "Attivo" + (sortIndicatorForColumn(4, sort)?.let { " $it" } ?: ""),
                    modifier = Modifier
                        .weight(1f)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { sortFieldForColumn(4)?.let { field -> onSortChange(toggleSort(sort, field)) } },
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = "Azioni",
                    modifier = Modifier.weight(3f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            Box(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = true),
            ) {
                LazyColumn(
                    state = tableListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = 12.dp),
                ) {
                    if (pageItems.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("Nessun proclamatore", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    } else {
                        items(pageItems, key = { it.id.value }) { item ->
                            val index = pageItems.indexOf(item)
                            val zebraColor = if (index % 2 == 0) Color.Transparent
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

                            TableDataRow(
                                proclamatore = item,
                                loading = isLoading,
                                selected = item.id in selectedIds,
                                batchMode = hasSelection,
                                backgroundColor = zebraColor,
                                onToggleSelected = { checked -> onToggleRowSelected(item.id, checked) },
                                onEdit = { onEdit(item.id) },
                                onToggleActive = { next -> onToggleActive(item.id, next) },
                                onDelete = { onDelete(item) },
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
                        onClick = onPreviousPage,
                        enabled = !isLoading && pageIndex > 0,
                    ) { Text("Prec") }
                    Text("Pagina ${pageIndex + 1} / $totalPages")
                    Button(
                        modifier = Modifier.handCursorOnHover(enabled = !isLoading && pageIndex < totalPages - 1),
                        onClick = onNextPage,
                        enabled = !isLoading && pageIndex < totalPages - 1,
                    ) { Text("Succ") }
                }
            }
        }
    }
}

@Composable
internal fun ProclamatoriFormContent(
    route: ProclamatoriRoute,
    nome: String,
    onNomeChange: (String) -> Unit,
    cognome: String,
    onCognomeChange: (String) -> Unit,
    sesso: Sesso,
    onSessoChange: (Sesso) -> Unit,
    nomeTrim: String,
    cognomeTrim: String,
    showFieldErrors: Boolean,
    duplicateError: String?,
    isCheckingDuplicate: Boolean,
    canSubmitForm: Boolean,
    isLoading: Boolean,
    notice: FeedbackBannerModel?,
    onDismissNotice: () -> Unit,
    formError: String?,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    val isNew = route == ProclamatoriRoute.Nuovo
    Text(
        if (isNew) "Nuovo proclamatore" else "Modifica proclamatore",
        style = MaterialTheme.typography.headlineMedium,
    )

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = nome,
                onValueChange = onNomeChange,
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
                onValueChange = onCognomeChange,
                label = { Text("Cognome") },
                isError = (showFieldErrors && cognomeTrim.isBlank()) || duplicateError != null,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = {
                    if (showFieldErrors && cognomeTrim.isBlank()) {
                        Text("Campo obbligatorio", color = MaterialTheme.colorScheme.error)
                    } else if (duplicateError != null) {
                        Text(duplicateError, color = MaterialTheme.colorScheme.error)
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
                    RadioButton(selected = sesso == Sesso.M, onClick = { onSessoChange(Sesso.M) })
                    Text("Uomo")
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = sesso == Sesso.F, onClick = { onSessoChange(Sesso.F) })
                    Text("Donna")
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    modifier = Modifier.handCursorOnHover(enabled = canSubmitForm),
                    onClick = onSubmit,
                    enabled = canSubmitForm,
                ) {
                    Text(if (isNew) "Salva" else "Aggiorna")
                }
                TextButton(
                    modifier = Modifier.handCursorOnHover(enabled = !isLoading),
                    onClick = onCancel,
                    enabled = !isLoading,
                ) { Text("Annulla") }
            }

            FeedbackBanner(
                model = notice,
                onDismissRequest = onDismissNotice,
            )
            if (formError != null) {
                SelectionContainer {
                    Text(
                        formError,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
internal fun TableDataRow(
    proclamatore: Proclamatore,
    loading: Boolean,
    selected: Boolean,
    batchMode: Boolean,
    backgroundColor: Color,
    onToggleSelected: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val singleActionsEnabled = !loading && !batchMode
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(backgroundColor),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = onToggleSelected,
                enabled = !loading,
            )
        }
        Box(
            modifier = Modifier
                .weight(2f)
                .fillMaxHeight()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(proclamatore.nome)
        }
        Box(
            modifier = Modifier
                .weight(2f)
                .fillMaxHeight()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(proclamatore.cognome)
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(proclamatore.sesso.name)
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 6.dp, vertical = 4.dp),
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
            modifier = Modifier
                .weight(3f)
                .fillMaxHeight()
                .padding(horizontal = 6.dp, vertical = 4.dp),
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

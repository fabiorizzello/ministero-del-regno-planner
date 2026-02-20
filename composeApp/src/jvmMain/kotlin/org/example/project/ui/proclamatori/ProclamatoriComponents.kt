package org.example.project.ui.proclamatori

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.ui.theme.spacing
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.domain.Sesso
import org.example.project.ui.components.FeedbackBanner
import org.example.project.ui.components.FeedbackBannerModel
import org.example.project.ui.components.handCursorOnHover

internal data class ProclamatoriElencoEvents(
    val onSearchTermChange: (String) -> Unit,
    val onResetSearch: () -> Unit,
    val onDismissNotice: () -> Unit,
    val onSortChange: (ProclamatoriSort) -> Unit,
    val onToggleSelectPage: (List<ProclamatoreId>, Boolean) -> Unit,
    val onToggleRowSelected: (ProclamatoreId, Boolean) -> Unit,
    val onActivateSelected: () -> Unit,
    val onDeactivateSelected: () -> Unit,
    val onRequestDeleteSelected: () -> Unit,
    val onClearSelection: () -> Unit,
    val onGoNuovo: () -> Unit,
    val onImportJson: () -> Unit,
    val onEdit: (ProclamatoreId) -> Unit,
    val onToggleActive: (ProclamatoreId, Boolean) -> Unit,
    val onDelete: (Proclamatore) -> Unit,
    val onPreviousPage: () -> Unit,
    val onNextPage: () -> Unit,
)

private const val NAME_MAX_LENGTH = 100

@Composable
internal fun Breadcrumbs(
    route: ProclamatoriRoute,
    currentModificaLabel: String?,
    onGoList: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalAlignment = Alignment.CenterVertically) {
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
internal fun ConfirmDeleteDialog(
    title: String,
    isLoading: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    DisableSelection {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = { content() },
            confirmButton = {
                TextButton(
                    modifier = Modifier.handCursorOnHover(enabled = !isLoading),
                    onClick = onConfirm,
                    enabled = !isLoading,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Rimuovi") }
            },
            dismissButton = {
                TextButton(
                    modifier = Modifier.handCursorOnHover(enabled = !isLoading),
                    onClick = onDismiss,
                    enabled = !isLoading,
                ) { Text("Annulla") }
            },
        )
    }
}

@Composable
internal fun ColumnScope.ProclamatoriElencoContent(
    state: ProclamatoriListUiState,
    searchFocusRequester: FocusRequester,
    tableListState: LazyListState,
    canImportInitialJson: Boolean,
    events: ProclamatoriElencoEvents,
) {
    val searchTerm = state.searchTerm
    val allItems = state.allItems
    val sortedItems = state.sortedItems
    val isLoading = state.isLoading
    val notice = state.notice
    val selectedIds = state.selectedIds
    val sort = state.sort
    val pageIndex = state.pageIndex
    val pageSize = state.pageSize
    val spacing = MaterialTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Proclamatori", style = MaterialTheme.typography.headlineMedium)
            Button(
                modifier = Modifier.handCursorOnHover(enabled = !isLoading),
                onClick = events.onGoNuovo,
                enabled = !isLoading,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Aggiungi proclamatore")
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Aggiungi")
            }
            if (canImportInitialJson) {
                Button(
                    modifier = Modifier.handCursorOnHover(enabled = !isLoading),
                    onClick = events.onImportJson,
                    enabled = !isLoading,
                ) {
                    Text("Importa JSON iniziale")
                }
            }
        }

        OutlinedTextField(
            value = searchTerm,
            onValueChange = events.onSearchTermChange,
            modifier = Modifier
                .width(320.dp)
                .focusRequester(searchFocusRequester),
            label = { Text("Ricerca") },
            singleLine = true,
            trailingIcon = {
                if (searchTerm.isNotBlank()) {
                    IconButton(
                        modifier = Modifier.handCursorOnHover(),
                        onClick = events.onResetSearch,
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Reset ricerca")
                    }
                }
            },
        )
    }

    FeedbackBanner(
        model = notice,
        onDismissRequest = events.onDismissNotice,
    )

    val totalPages = if (allItems.isEmpty()) 1 else ((allItems.size - 1) / pageSize) + 1
    val pageItems = sortedItems
        .drop(pageIndex * pageSize)
        .take(pageSize)
    val pageItemIds = pageItems.map { it.id }
    val hasSelection = selectedIds.isNotEmpty()
    val batchActionsEnabled = !isLoading
    val allPageSelected = pageItemIds.isNotEmpty() && pageItemIds.all { it in selectedIds }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(spacing.cardRadius),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = allPageSelected,
                    onCheckedChange = { checked -> events.onToggleSelectPage(pageItemIds, checked) },
                    enabled = !isLoading && pageItemIds.isNotEmpty(),
                )
                Text("Selezionati: ${selectedIds.size}", style = MaterialTheme.typography.bodySmall)
            }
            if (hasSelection) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        modifier = Modifier.handCursorOnHover(enabled = batchActionsEnabled).height(30.dp),
                        onClick = events.onActivateSelected,
                        enabled = batchActionsEnabled,
                        contentPadding = PaddingValues(horizontal = spacing.lg, vertical = 0.dp),
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = "Attiva selezionati", modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(spacing.xs))
                        Text("Attiva", style = MaterialTheme.typography.labelSmall)
                    }
                    Button(
                        modifier = Modifier.handCursorOnHover(enabled = batchActionsEnabled).height(30.dp),
                        onClick = events.onDeactivateSelected,
                        enabled = batchActionsEnabled,
                        contentPadding = PaddingValues(horizontal = spacing.lg, vertical = 0.dp),
                    ) {
                        Icon(Icons.Filled.Block, contentDescription = "Disattiva selezionati", modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(spacing.xs))
                        Text("Disattiva", style = MaterialTheme.typography.labelSmall)
                    }
                    Button(
                        modifier = Modifier.handCursorOnHover(enabled = batchActionsEnabled).height(30.dp),
                        onClick = events.onRequestDeleteSelected,
                        enabled = batchActionsEnabled,
                        contentPadding = PaddingValues(horizontal = spacing.lg, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "Rimuovi selezionati", modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(spacing.xs))
                        Text("Rimuovi", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(
                        modifier = Modifier.handCursorOnHover(enabled = !isLoading).height(30.dp),
                        onClick = events.onClearSelection,
                        enabled = !isLoading,
                        contentPadding = PaddingValues(horizontal = spacing.lg, vertical = 0.dp),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Annulla selezione", modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(spacing.xs))
                        Text("Annulla", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().weight(1f, fill = true),
        shape = RoundedCornerShape(spacing.cardRadius),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(end = spacing.lg)
                    .padding(horizontal = spacing.sm, vertical = spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(0.6f))
                Text(
                    text = "Nome" + (sortIndicatorForColumn(1, sort)?.let { " $it" } ?: ""),
                    modifier = Modifier
                        .weight(2f)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { sortFieldForColumn(1)?.let { field -> events.onSortChange(toggleSort(sort, field)) } },
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = "Cognome" + (sortIndicatorForColumn(2, sort)?.let { " $it" } ?: ""),
                    modifier = Modifier
                        .weight(2f)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { sortFieldForColumn(2)?.let { field -> events.onSortChange(toggleSort(sort, field)) } },
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = "Sesso" + (sortIndicatorForColumn(3, sort)?.let { " $it" } ?: ""),
                    modifier = Modifier
                        .weight(1f)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { sortFieldForColumn(3)?.let { field -> events.onSortChange(toggleSort(sort, field)) } },
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = "Attivo" + (sortIndicatorForColumn(4, sort)?.let { " $it" } ?: ""),
                    modifier = Modifier
                        .weight(1f)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { sortFieldForColumn(4)?.let { field -> events.onSortChange(toggleSort(sort, field)) } },
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
                        .padding(end = spacing.lg),
                ) {
                    if (pageItems.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(spacing.xl),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("Nessun proclamatore", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    } else {
                        itemsIndexed(pageItems, key = { _, item -> item.id.value }) { index, item ->
                            val zebraColor = if (index % 2 == 0) Color.Transparent
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

                            TableDataRow(
                                proclamatore = item,
                                loading = isLoading,
                                selected = item.id in selectedIds,
                                batchMode = hasSelection,
                                backgroundColor = zebraColor,
                                onToggleSelected = { checked -> events.onToggleRowSelected(item.id, checked) },
                                onEdit = { events.onEdit(item.id) },
                                onToggleActive = { next -> events.onToggleActive(item.id, next) },
                                onDelete = { events.onDelete(item) },
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
                Text("Totale: ${allItems.size}", style = MaterialTheme.typography.bodySmall)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        modifier = Modifier.handCursorOnHover(enabled = !isLoading && pageIndex > 0).height(28.dp),
                        onClick = events.onPreviousPage,
                        enabled = !isLoading && pageIndex > 0,
                        contentPadding = PaddingValues(horizontal = spacing.lg, vertical = 0.dp),
                    ) { Text("Prec", style = MaterialTheme.typography.labelSmall) }
                    Text("Pagina ${pageIndex + 1} / $totalPages", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(
                        modifier = Modifier.handCursorOnHover(enabled = !isLoading && pageIndex < totalPages - 1).height(28.dp),
                        onClick = events.onNextPage,
                        enabled = !isLoading && pageIndex < totalPages - 1,
                        contentPadding = PaddingValues(horizontal = spacing.lg, vertical = 0.dp),
                    ) { Text("Succ", style = MaterialTheme.typography.labelSmall) }
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
    formError: String?,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    val isNew = route == ProclamatoriRoute.Nuovo
    Text(
        if (isNew) "Nuovo proclamatore" else "Modifica proclamatore",
        style = MaterialTheme.typography.headlineMedium,
    )

    val spacing = MaterialTheme.spacing
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            OutlinedTextField(
                value = nome,
                onValueChange = { if (it.length <= NAME_MAX_LENGTH) onNomeChange(it) },
                label = { Text("Nome") },
                isError = (showFieldErrors && nomeTrim.isBlank()) || duplicateError != null,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = {
                    if (showFieldErrors && nomeTrim.isBlank()) {
                        Text("Campo obbligatorio", color = MaterialTheme.colorScheme.error)
                    } else {
                        Text("${nome.length}/$NAME_MAX_LENGTH")
                    }
                },
            )
            OutlinedTextField(
                value = cognome,
                onValueChange = { if (it.length <= NAME_MAX_LENGTH) onCognomeChange(it) },
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
                    } else {
                        Text("${cognome.length}/$NAME_MAX_LENGTH")
                    }
                },
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = sesso == Sesso.M, onClick = { onSessoChange(Sesso.M) })
                    Text("Uomo")
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = sesso == Sesso.F, onClick = { onSessoChange(Sesso.F) })
                    Text("Donna")
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
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
    val spacing = MaterialTheme.spacing
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
                .padding(horizontal = spacing.sm, vertical = spacing.xs),
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
                .padding(horizontal = spacing.sm, vertical = spacing.xs),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(proclamatore.nome, style = MaterialTheme.typography.bodySmall)
        }
        Box(
            modifier = Modifier
                .weight(2f)
                .fillMaxHeight()
                .padding(horizontal = spacing.sm, vertical = spacing.xs),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(proclamatore.cognome, style = MaterialTheme.typography.bodySmall)
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = spacing.sm, vertical = spacing.xs),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(proclamatore.sesso.name, style = MaterialTheme.typography.bodySmall)
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = spacing.sm, vertical = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Text(if (proclamatore.attivo) "Si" else "No", style = MaterialTheme.typography.bodySmall)
            Switch(
                checked = proclamatore.attivo,
                onCheckedChange = onToggleActive,
                enabled = singleActionsEnabled,
                modifier = Modifier.scale(0.7f),
            )
        }
        Row(
            modifier = Modifier
                .weight(3f)
                .fillMaxHeight()
                .padding(horizontal = spacing.sm, vertical = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            OutlinedButton(
                modifier = Modifier.handCursorOnHover(enabled = singleActionsEnabled).height(28.dp),
                onClick = onEdit,
                enabled = singleActionsEnabled,
                contentPadding = PaddingValues(horizontal = spacing.lg, vertical = 0.dp),
            ) {
                Icon(Icons.Filled.Edit, contentDescription = "Modifica proclamatore", modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(spacing.xs))
                Text("Modifica", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
                modifier = Modifier.handCursorOnHover(enabled = singleActionsEnabled).height(28.dp),
                onClick = onDelete,
                enabled = singleActionsEnabled,
                contentPadding = PaddingValues(horizontal = spacing.lg, vertical = 0.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Rimuovi proclamatore", modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(spacing.xs))
                Text("Rimuovi", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/**
 * Table components for the Proclamatori list screen.
 *
 * Contains the table UI with data rows, pagination controls, batch actions,
 * sort headers, and schema anomaly display.
 */
package org.example.project.ui.proclamatori

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
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
import org.example.project.ui.components.FeedbackBanner
import org.example.project.ui.components.handCursorOnHover

/**
 * Main table content for the Proclamatori list screen.
 *
 * Displays a searchable, sortable, paginated table of proclamatori with:
 * - Search bar and action buttons
 * - Schema update anomaly alerts (if any)
 * - Batch selection and actions
 * - Individual row actions (edit, delete)
 * - Pagination controls
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ColumnScope.ProclamatoriElencoContentTable(
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
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                    positioning = TooltipAnchorPosition.Above,
                ),
                tooltip = { PlainTooltip { Text("Ctrl+N per aggiungere") } },
                state = rememberTooltipState(),
            ) {
                Button(
                    modifier = Modifier.handCursorOnHover(enabled = !isLoading),
                    onClick = events.onGoNuovo,
                    enabled = !isLoading,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Aggiungi proclamatore")
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Aggiungi")
                }
            }
            if (canImportInitialJson) {
                Button(
                    modifier = Modifier.handCursorOnHover(enabled = !isLoading),
                    onClick = events.onImportJson,
                    enabled = !isLoading,
                ) {
                    Icon(Icons.Filled.FileOpen, contentDescription = "Importa file JSON iniziale")
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Importa JSON iniziale")
                }
            }
        }

        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                positioning = TooltipAnchorPosition.Above,
            ),
            tooltip = { PlainTooltip { Text("Ctrl+F per cercare") } },
            state = rememberTooltipState(),
        ) {
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
    }

    FeedbackBanner(
        model = notice,
        onDismissRequest = events.onDismissNotice,
    )

    if (state.schemaUpdateAnomalies.isNotEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(spacing.cardRadius),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Anomalie idoneita da aggiornamento schemi",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    OutlinedButton(
                        onClick = events.onDismissSchemaAnomalies,
                        enabled = !state.isDismissingSchemaAnomalies,
                        modifier = Modifier.handCursorOnHover(enabled = !state.isDismissingSchemaAnomalies),
                    ) {
                        Text(if (state.isDismissingSchemaAnomalies) "Archiviazione..." else "Archivia pannello")
                    }
                }
                state.schemaUpdateAnomalies.forEach { anomaly ->
                    val versionText = anomaly.schemaVersion?.let { " | schema $it" }.orEmpty()
                    Text(
                        "• ${anomaly.personLabel} | ${anomaly.partTypeLabel}$versionText | ${anomaly.reason} | ${anomaly.createdAt}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }

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
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.74f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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

                            ProclamatoriDataRow(
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
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Pagina precedente", modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(spacing.xs))
                        Text("Prec", style = MaterialTheme.typography.labelSmall)
                    }
                    Text("Pagina ${pageIndex + 1} / $totalPages", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(
                        modifier = Modifier.handCursorOnHover(enabled = !isLoading && pageIndex < totalPages - 1).height(28.dp),
                        onClick = events.onNextPage,
                        enabled = !isLoading && pageIndex < totalPages - 1,
                        contentPadding = PaddingValues(horizontal = spacing.lg, vertical = 0.dp),
                    ) {
                        Text("Succ", style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.width(spacing.xs))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Pagina successiva", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProclamatoriDataRow(
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
                modifier = Modifier.handCursorOnHover(enabled = singleActionsEnabled).height(32.dp),
                onClick = onEdit,
                enabled = singleActionsEnabled,
                contentPadding = PaddingValues(horizontal = spacing.lg, vertical = 0.dp),
            ) {
                Icon(Icons.Filled.Edit, contentDescription = "Modifica proclamatore", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(spacing.xs))
                Text("Modifica", style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(
                modifier = Modifier.handCursorOnHover(enabled = singleActionsEnabled).height(32.dp),
                onClick = onDelete,
                enabled = singleActionsEnabled,
                contentPadding = PaddingValues(horizontal = spacing.lg, vertical = 0.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Rimuovi proclamatore", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(spacing.xs))
                Text("Rimuovi", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

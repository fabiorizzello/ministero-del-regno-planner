/**
 * Table components for the Proclamatori list screen.
 *
 * Contains the table UI with data rows, batch actions,
 * sort headers, and schema anomaly display.
 */
package org.example.project.ui.proclamatori

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.domain.Sesso
import org.example.project.ui.components.FeedbackBanner
import org.example.project.ui.components.handCursorOnHover
import org.example.project.ui.theme.spacing
import org.example.project.ui.theme.workspaceSketch

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

    // ── Header row ──────────────────────────────────────────────────────────
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Proclamatori", style = MaterialTheme.typography.headlineMedium)
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            ) {
                Text(
                    "${allItems.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (canImportInitialJson) {
                Button(
                    modifier = Modifier.handCursorOnHover(enabled = !isLoading),
                    onClick = events.onImportJson,
                    enabled = !isLoading,
                ) {
                    Icon(Icons.Filled.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Importa JSON")
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
                        .widthIn(min = 200.dp, max = 280.dp)
                        .focusRequester(searchFocusRequester),
                    label = { Text("Cerca") },
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
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Nuovo")
                }
            }
        }
    }

    FeedbackBanner(
        model = notice,
        onDismissRequest = events.onDismissNotice,
    )

    // ── Schema anomalies ─────────────────────────────────────────────────────
    if (state.schemaUpdateAnomalies.isNotEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(spacing.cardRadius),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Anomalie idoneità da aggiornamento schemi",
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
    val pageItems = sortedItems.drop(pageIndex * pageSize).take(pageSize)
    val pageItemIds = pageItems.map { it.id }
    val hasSelection = selectedIds.isNotEmpty()
    val allPageSelected = pageItemIds.isNotEmpty() && pageItemIds.all { it in selectedIds }

    // ── Animated bulk bar ────────────────────────────────────────────────────
    AnimatedVisibility(
        visible = hasSelection,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically(),
    ) {
        ProclamatoriiBulkBar(
            count = selectedIds.size,
            enabled = !isLoading,
            onActivate = events.onActivateSelected,
            onDeactivate = events.onDeactivateSelected,
            onDelete = events.onRequestDeleteSelected,
            onClear = events.onClearSelection,
        )
    }

    // ── Table card ───────────────────────────────────────────────────────────
    Card(
        modifier = Modifier.fillMaxWidth().weight(1f, fill = true),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = allPageSelected,
                    onCheckedChange = { checked -> events.onToggleSelectPage(pageItemIds, checked) },
                    modifier = Modifier.size(20.dp),
                    enabled = !isLoading && pageItemIds.isNotEmpty(),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Nome" + (sortIndicatorForColumn(1, sort)?.let { " $it" } ?: ""),
                    modifier = Modifier
                        .weight(2.5f)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { sortFieldForColumn(1)?.let { field -> events.onSortChange(toggleSort(sort, field)) } },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Stato" + (sortIndicatorForColumn(4, sort)?.let { " $it" } ?: ""),
                    modifier = Modifier
                        .weight(1.2f)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { sortFieldForColumn(4)?.let { field -> events.onSortChange(toggleSort(sort, field)) } },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(56.dp))
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f, fill = true)) {
                LazyColumn(
                    state = tableListState,
                    modifier = Modifier.fillMaxSize().padding(end = spacing.lg),
                ) {
                    if (pageItems.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(40.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "Nessun proclamatore",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        itemsIndexed(pageItems, key = { _, item -> item.id.value }) { _, item ->
                            ProclamatoriDataRow(
                                proclamatore = item,
                                loading = isLoading,
                                selected = item.id in selectedIds,
                                batchMode = hasSelection,
                                onToggleSelected = { checked -> events.onToggleRowSelected(item.id, checked) },
                                onEdit = { events.onEdit(item.id) },
                                onDelete = { events.onDelete(item) },
                            )
                        }
                    }
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(tableListState),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                )
            }

            // Footer: count + pagination
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (sortedItems.size == allItems.size) "${allItems.size} proclamatori"
                    else "${sortedItems.size} di ${allItems.size} proclamatori",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (totalPages > 1) {
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
                        Text("${pageIndex + 1} / $totalPages", style = MaterialTheme.typography.bodySmall)
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
}

// ─── Bulk bar ─────────────────────────────────────────────────────────────────

@Composable
private fun ProclamatoriiBulkBar(
    count: Int,
    enabled: Boolean,
    onActivate: () -> Unit,
    onDeactivate: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
) {
    val sketch = MaterialTheme.workspaceSketch
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, sketch.accent.copy(alpha = 0.35f)),
        colors = CardDefaults.cardColors(containerColor = sketch.accentSoft),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$count selezionat${if (count == 1) "o" else "i"}",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = sketch.accent,
            )
            Spacer(Modifier.width(4.dp))
            OutlinedButton(
                onClick = onActivate,
                enabled = enabled,
                modifier = Modifier.height(30.dp).handCursorOnHover(enabled = enabled),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text("Attiva", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
                onClick = onDeactivate,
                enabled = enabled,
                modifier = Modifier.height(30.dp).handCursorOnHover(enabled = enabled),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            ) {
                Icon(Icons.Filled.Block, contentDescription = null, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text("Sospendi", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
                onClick = onDelete,
                enabled = enabled,
                modifier = Modifier.height(30.dp).handCursorOnHover(enabled = enabled),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = sketch.bad),
                border = BorderStroke(1.dp, sketch.bad.copy(alpha = 0.5f)),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text("Rimuovi", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Deseleziona", modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ─── Data row ─────────────────────────────────────────────────────────────────

@Composable
internal fun ProclamatoriDataRow(
    proclamatore: Proclamatore,
    loading: Boolean,
    selected: Boolean,
    batchMode: Boolean,
    onToggleSelected: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val sketch = MaterialTheme.workspaceSketch
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    val rowBg = when {
        selected -> sketch.accentSoft
        hovered  -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        else     -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interactionSource)
            .background(rowBg)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = onToggleSelected,
            modifier = Modifier.size(20.dp),
            enabled = !loading,
        )
        Spacer(Modifier.width(12.dp))

        // Avatar + nome + genere
        Row(
            modifier = Modifier.weight(2.5f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ProclamatoreAvatar(proclamatore.nome, proclamatore.cognome, proclamatore.sesso, proclamatore.attivo)
            Column {
                Text(
                    "${proclamatore.nome} ${proclamatore.cognome}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                )
                Text(
                    if (proclamatore.sesso == Sesso.M) "Uomo" else "Donna",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Stato badge
        Box(modifier = Modifier.weight(1.2f)) {
            ProclamatoreStatusBadge(attivo = proclamatore.attivo, sospeso = proclamatore.sospeso)
        }

        // Hover-reveal icon actions
        Row(
            modifier = Modifier.width(56.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            AnimatedVisibility(visible = hovered && !batchMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(28.dp).handCursorOnHover(enabled = !loading),
                        enabled = !loading,
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = "Modifica", modifier = Modifier.size(15.dp))
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp).handCursorOnHover(enabled = !loading),
                        enabled = !loading,
                        colors = IconButtonDefaults.iconButtonColors(contentColor = sketch.bad),
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "Rimuovi", modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
    }
}

// ─── Shared visual atoms ──────────────────────────────────────────────────────

@Composable
internal fun ProclamatoreAvatar(nome: String, cognome: String, sesso: Sesso, attivo: Boolean) {
    val sketch = MaterialTheme.workspaceSketch
    val bgColor = if (sesso == Sesso.M) sketch.accentSoft else Color(0xFF2A1040)
    val fgColor = if (sesso == Sesso.M) sketch.accent else Color(0xFFC084FC)
    val initials = "${nome.firstOrNull() ?: ""}${cognome.firstOrNull() ?: ""}".uppercase()

    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(bgColor)
            .then(if (!attivo) Modifier.background(Color.Black.copy(alpha = 0.35f)) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = if (attivo) fgColor else fgColor.copy(alpha = 0.5f),
        )
    }
}

@Composable
internal fun ProclamatoreStatusBadge(attivo: Boolean, sospeso: Boolean) {
    val sketch = MaterialTheme.workspaceSketch
    val (bgColor, dotColor, label) = when {
        sospeso -> Triple(sketch.warn.copy(alpha = 0.12f), sketch.warn, "Sospeso")
        attivo -> Triple(sketch.ok.copy(alpha = 0.12f), sketch.ok, "Attivo")
        else -> Triple(sketch.bad.copy(alpha = 0.12f), sketch.bad, "Inattivo")
    }

    Row(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(20.dp))
            .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(dotColor))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = dotColor,
        )
    }
}

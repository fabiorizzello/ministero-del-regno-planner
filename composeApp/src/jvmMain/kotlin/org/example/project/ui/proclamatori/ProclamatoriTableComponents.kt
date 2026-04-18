/**
 * List components for the Proclamatori screen.
 *
 * Contains the shared header, schema anomaly panel,
 * table view, compact cards view, and shared visual atoms.
 */
package org.example.project.ui.proclamatori

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableRows
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
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.domain.Sesso
import org.example.project.ui.components.FeedbackBanner
import org.example.project.ui.components.handCursorOnHover
import org.example.project.ui.theme.spacing
import org.example.project.ui.theme.workspaceSketch

internal data class ProclamatoriElencoEvents(
    val onSearchTermChange: (String) -> Unit,
    val onResetSearch: () -> Unit,
    val onDismissNotice: () -> Unit,
    val onSortChange: (ProclamatoriSort) -> Unit,
    val onToggleSelectPage: (List<ProclamatoreId>, Boolean) -> Unit,
    val onToggleRowSelected: (ProclamatoreId, Boolean) -> Unit,
    val onRequestDeleteSelected: () -> Unit,
    val onClearSelection: () -> Unit,
    val onGoNuovo: () -> Unit,
    val onDismissSchemaAnomalies: () -> Unit,
    val onEdit: (ProclamatoreId) -> Unit,
    val onDelete: (Proclamatore) -> Unit,
    val onPreviousPage: () -> Unit,
    val onNextPage: () -> Unit,
)

internal enum class ProclamatoriListViewMode { TABLE, CARDS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ColumnScope.ProclamatoriElencoContent(
    state: ProclamatoriListUiState,
    viewMode: ProclamatoriListViewMode,
    onViewModeChange: (ProclamatoriListViewMode) -> Unit,
    searchFocusRequester: FocusRequester,
    tableListState: LazyListState,
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
    val cardsScrollState = rememberScrollState()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Studenti", style = MaterialTheme.typography.headlineMedium)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ViewModeSwitch(
                viewMode = viewMode,
                onViewModeChange = onViewModeChange,
            )
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                    positioning = TooltipAnchorPosition.Above,
                ),
                tooltip = { PlainTooltip { Text("Ctrl+F per cercare") } },
                state = rememberTooltipState(),
            ) {
                CompactSearchInput(
                    value = searchTerm,
                    onValueChange = events.onSearchTermChange,
                    onReset = events.onResetSearch,
                    modifier = Modifier.width(220.dp),
                    focusRequester = searchFocusRequester,
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
                    shape = RoundedCornerShape(8.dp),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        hoveredElevation = 0.dp,
                    ),
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

    if (state.schemaUpdateAnomalies.isNotEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(spacing.cardRadius),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
            ),
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
                        "Anomalie idoneita da aggiornamento schemi",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    OutlinedButton(
                        onClick = events.onDismissSchemaAnomalies,
                        enabled = !state.isDismissingSchemaAnomalies,
                        modifier = Modifier.handCursorOnHover(enabled = !state.isDismissingSchemaAnomalies),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 0.dp,
                            hoveredElevation = 0.dp,
                        ),
                    ) {
                        Text(if (state.isDismissingSchemaAnomalies) "Archiviazione..." else "Archivia pannello")
                    }
                }
                state.schemaUpdateAnomalies.forEach { anomaly ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            anomaly.personLabel,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1.5f),
                        )
                        Text(
                            anomaly.partTypeLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1.5f),
                        )
                        Text(
                            anomaly.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.workspaceSketch.warn,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(2f),
                        )
                        anomaly.schemaVersion?.let {
                            Text(
                                "v$it",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            formatAnomalyDate(anomaly.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    val totalPages = if (allItems.isEmpty()) 1 else ((allItems.size - 1) / pageSize) + 1
    val pageItems = sortedItems.drop(pageIndex * pageSize).take(pageSize)
    val pageItemIds = pageItems.map { it.id }
    val hasSelection = selectedIds.isNotEmpty()
    val allPageSelected = pageItemIds.isNotEmpty() && pageItemIds.all { it in selectedIds }

    AnimatedVisibility(
        visible = hasSelection,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically(),
    ) {
        ProclamatoriiBulkBar(
            count = selectedIds.size,
            enabled = !isLoading,
            onDelete = events.onRequestDeleteSelected,
            onClear = events.onClearSelection,
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth().weight(1f, fill = true),
        shape = RoundedCornerShape(spacing.cardRadius),
        border = BorderStroke(1.dp, MaterialTheme.workspaceSketch.cardBorder),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.workspaceSketch.cardSurface),
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            if (viewMode == ProclamatoriListViewMode.TABLE) {
                TableContent(
                    pageItems = pageItems,
                    selectedIds = selectedIds,
                    hasSelection = hasSelection,
                    allPageSelected = allPageSelected,
                    pageItemIds = pageItemIds,
                    sort = sort,
                    isLoading = isLoading,
                    tableListState = tableListState,
                    hasActiveSearch = state.searchTerm.isNotBlank(),
                    capabilitySummaryById = state.capabilitySummaryById,
                    events = events,
                )
            } else {
                CardsContent(
                    pageItems = pageItems,
                    selectedIds = selectedIds,
                    hasSelection = hasSelection,
                    isLoading = isLoading,
                    cardsScrollState = cardsScrollState,
                    scrollResetToken = state.scrollResetToken,
                    hasActiveSearch = state.searchTerm.isNotBlank(),
                    capabilitySummaryById = state.capabilitySummaryById,
                    events = events,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (sortedItems.size == allItems.size) {
                        val totalLabel = if (allItems.size == 1) "1 studente" else "${allItems.size} studenti"
                        "$totalLabel · $pageSize per pagina"
                    } else {
                        "${sortedItems.size} di ${allItems.size} studenti · $pageSize per pagina"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (totalPages > 1) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            modifier = Modifier
                                .handCursorOnHover(enabled = !isLoading && pageIndex > 0)
                                .height(28.dp),
                            onClick = events.onPreviousPage,
                            enabled = !isLoading && pageIndex > 0,
                            contentPadding = PaddingValues(horizontal = spacing.lg, vertical = 0.dp),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 0.dp,
                                pressedElevation = 0.dp,
                                hoveredElevation = 0.dp,
                            ),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Pagina precedente",
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(spacing.xs))
                            Text("Prec", style = MaterialTheme.typography.labelSmall)
                        }
                        Text("${pageIndex + 1} / $totalPages", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(
                            modifier = Modifier
                                .handCursorOnHover(enabled = !isLoading && pageIndex < totalPages - 1)
                                .height(28.dp),
                            onClick = events.onNextPage,
                            enabled = !isLoading && pageIndex < totalPages - 1,
                            contentPadding = PaddingValues(horizontal = spacing.lg, vertical = 0.dp),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 0.dp,
                                pressedElevation = 0.dp,
                                hoveredElevation = 0.dp,
                            ),
                        ) {
                            Text("Succ", style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.width(spacing.xs))
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Pagina successiva",
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewModeSwitch(
    viewMode: ProclamatoriListViewMode,
    onViewModeChange: (ProclamatoriListViewMode) -> Unit,
) {
    val sketch = MaterialTheme.workspaceSketch
    Row(
        modifier = Modifier
            .background(sketch.cardSurfaceMuted, RoundedCornerShape(10.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProclamatoriViewModeButton(
            icon = Icons.Filled.TableRows,
            contentDescription = "Vista tabellare",
            selected = viewMode == ProclamatoriListViewMode.TABLE,
            onClick = { onViewModeChange(ProclamatoriListViewMode.TABLE) },
        )
        ProclamatoriViewModeButton(
            icon = Icons.Filled.GridView,
            contentDescription = "Vista griglia",
            selected = viewMode == ProclamatoriListViewMode.CARDS,
            onClick = { onViewModeChange(ProclamatoriListViewMode.CARDS) },
        )
    }
}

@Composable
private fun ProclamatoriViewModeButton(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val sketch = MaterialTheme.workspaceSketch
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .handCursorOnHover()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) sketch.cardSurface else Color.Transparent,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) sketch.selectionBorder.copy(alpha = 0.35f) else Color.Transparent,
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp).size(18.dp),
            tint = if (selected) sketch.accent else sketch.inkMuted,
        )
    }
}

@Composable
private fun ColumnScope.TableContent(
    pageItems: List<Proclamatore>,
    selectedIds: Set<ProclamatoreId>,
    hasSelection: Boolean,
    allPageSelected: Boolean,
    pageItemIds: List<ProclamatoreId>,
    sort: ProclamatoriSort,
    isLoading: Boolean,
    tableListState: LazyListState,
    hasActiveSearch: Boolean,
    capabilitySummaryById: Map<ProclamatoreId, ProclamatoreCapabilitySummaryUi>,
    events: ProclamatoriElencoEvents,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.workspaceSketch.tableHeaderSurface)
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
        SortableColumnHeader(
            label = "Nome",
            field = ProclamatoriSortField.NOME,
            sort = sort,
            modifier = Modifier.weight(2.2f),
            onSortChange = events.onSortChange,
        )
        Text(
            text = "Idoneita'",
            modifier = Modifier.weight(2f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.workspaceSketch.inkMuted,
        )
        SortableColumnHeader(
            label = "Stato",
            field = ProclamatoriSortField.SOSPESO,
            sort = sort,
            modifier = Modifier.weight(1.2f),
            onSortChange = events.onSortChange,
        )
        Spacer(Modifier.width(56.dp))
    }

    Box(modifier = Modifier.fillMaxWidth().weight(1f, fill = true)) {
        LazyColumn(
            state = tableListState,
            modifier = Modifier.fillMaxSize().padding(end = MaterialTheme.spacing.lg),
        ) {
            if (pageItems.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            studentiEmptyStateLabel(hasActiveSearch = hasActiveSearch),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                itemsIndexed(pageItems, key = { _, item -> item.id.value }) { _, item ->
                    ProclamatoriDataRow(
                        proclamatore = item,
                        summary = capabilitySummaryById[item.id],
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.CardsContent(
    pageItems: List<Proclamatore>,
    selectedIds: Set<ProclamatoreId>,
    hasSelection: Boolean,
    isLoading: Boolean,
    cardsScrollState: ScrollState,
    scrollResetToken: Int,
    hasActiveSearch: Boolean,
    capabilitySummaryById: Map<ProclamatoreId, ProclamatoreCapabilitySummaryUi>,
    events: ProclamatoriElencoEvents,
) {
    LaunchedEffect(scrollResetToken) {
        cardsScrollState.scrollTo(0)
    }
    Box(modifier = Modifier.fillMaxWidth().weight(1f, fill = true)) {
        if (pageItems.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    studentiEmptyStateLabel(hasActiveSearch = hasActiveSearch),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = MaterialTheme.spacing.lg)
                    .verticalScroll(cardsScrollState),
            ) {
                val gap = 12.dp
                val horizontalPadding = 32.dp
                val minCardWidth = 280.dp
                val availableWidth = (maxWidth - horizontalPadding).coerceAtLeast(minCardWidth)
                val columns = (((availableWidth + gap) / (minCardWidth + gap)).toInt())
                    .coerceIn(1, 3)

                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(gap),
                ) {
                    pageItems.chunked(columns).forEach { rowItems ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(gap),
                        ) {
                            rowItems.forEach { item ->
                                ProclamatoriCompactCard(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    proclamatore = item,
                                    summary = capabilitySummaryById[item.id],
                                    loading = isLoading,
                                    selected = item.id in selectedIds,
                                    batchMode = hasSelection,
                                    onToggleSelected = { checked -> events.onToggleRowSelected(item.id, checked) },
                                    onEdit = { events.onEdit(item.id) },
                                    onDelete = { events.onDelete(item) },
                                )
                            }
                            repeat(columns - rowItems.size) {
                                Spacer(Modifier.weight(1f, fill = true))
                            }
                        }
                    }
                }
            }
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(cardsScrollState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}

@Composable
private fun SortableColumnHeader(
    label: String,
    field: ProclamatoriSortField,
    sort: ProclamatoriSort,
    modifier: Modifier = Modifier,
    onSortChange: (ProclamatoriSort) -> Unit,
) {
    val sketch = MaterialTheme.workspaceSketch
    val isActive = sort.field == field
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Box(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        Row(
            modifier = Modifier
                .pointerHoverIcon(PointerIcon.Hand)
                .hoverable(interactionSource)
                .clickable(indication = null, interactionSource = interactionSource) {
                    onSortChange(toggleSort(sort, field))
                }
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    isActive -> sketch.accent
                    hovered -> sketch.ink
                    else -> sketch.inkMuted
                },
            )
            if (isActive) {
                Icon(
                    imageVector = if (sort.direction == SortDirection.ASC) {
                        Icons.Filled.ArrowUpward
                    } else {
                        Icons.Filled.ArrowDownward
                    },
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = sketch.accent,
                )
            }
        }
    }
}

@Composable
private fun ProclamatoriiBulkBar(
    count: Int,
    enabled: Boolean,
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
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
                onClick = onDelete,
                enabled = enabled,
                modifier = Modifier.height(30.dp).handCursorOnHover(enabled = enabled),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = sketch.bad),
                border = BorderStroke(1.dp, sketch.bad.copy(alpha = 0.5f)),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    hoveredElevation = 0.dp,
                ),
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

@Composable
internal fun ProclamatoriDataRow(
    proclamatore: Proclamatore,
    summary: ProclamatoreCapabilitySummaryUi?,
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
    val focused by interactionSource.collectIsFocusedAsState()

    val rowBg = when {
        selected -> sketch.selectionSurface
        focused || hovered -> sketch.hoverSurface
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interactionSource)
            .focusable(interactionSource = interactionSource)
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

        Row(
            modifier = Modifier.weight(2.2f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ProclamatoreAvatar(proclamatore.nome, proclamatore.cognome, proclamatore.sesso)
            Text(
                proclamatore.fullName,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            )
        }

        Text(
            text = formatCapabilitySummaryText(
                canAssist = proclamatore.puoAssistere,
                leadLabels = summary?.leadLabels.orEmpty(),
            ),
            modifier = Modifier.weight(2f),
            style = MaterialTheme.typography.bodySmall,
            color = sketch.inkSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Box(modifier = Modifier.weight(1.2f)) {
            ProclamatoreStatusBadge(sospeso = proclamatore.sospeso)
        }

        Row(
            modifier = Modifier.width(56.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            AnimatedVisibility(visible = (hovered || focused) && !batchMode) {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProclamatoriCompactCard(
    modifier: Modifier = Modifier,
    proclamatore: Proclamatore,
    summary: ProclamatoreCapabilitySummaryUi?,
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
    val focused by interactionSource.collectIsFocusedAsState()

    val borderColor = when {
        selected -> sketch.selectionBorder.copy(alpha = 0.45f)
        hovered || focused -> sketch.cardBorderStrong
        else -> sketch.cardBorder
    }
    val containerColor = when {
        selected -> sketch.selectionSurfaceMuted
        hovered || focused -> sketch.hoverSurface
        else -> sketch.cardSurface
    }

    Card(
        modifier = modifier
            .hoverable(interactionSource)
            .focusable(interactionSource = interactionSource),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                ProclamatoreAvatar(proclamatore.nome, proclamatore.cognome, proclamatore.sesso)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = proclamatore.fullName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (proclamatore.sospeso) {
                        ProclamatoreStatusBadge(sospeso = true)
                    }
                }
                Checkbox(
                    checked = selected,
                    onCheckedChange = onToggleSelected,
                    modifier = Modifier.size(20.dp),
                    enabled = !loading,
                )
            }

            CapabilitySummaryChips(
                canAssist = proclamatore.puoAssistere,
                leadLabels = summary?.leadLabels.orEmpty(),
            )

            Spacer(Modifier.weight(1f, fill = true))

            if (!batchMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = studentCardActionBarMinHeight()),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(30.dp).handCursorOnHover(enabled = !loading),
                        enabled = !loading,
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = "Modifica", modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(30.dp).handCursorOnHover(enabled = !loading),
                        enabled = !loading,
                        colors = IconButtonDefaults.iconButtonColors(contentColor = sketch.bad),
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "Rimuovi", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

internal fun studentiEmptyStateLabel(hasActiveSearch: Boolean): String =
    if (hasActiveSearch) {
        "Nessun risultato per questa ricerca"
    } else {
        "Nessuno studente disponibile"
    }

internal fun studentCardActionBarMinHeight() = 36.dp

@Composable
internal fun ProclamatoreAvatar(nome: String, cognome: String, sesso: Sesso) {
    val sketch = MaterialTheme.workspaceSketch
    val bgColor = if (sesso == Sesso.M) sketch.accentSoft else sketch.avatarFemminaBg
    val fgColor = if (sesso == Sesso.M) sketch.accent else sketch.avatarFemminaFg
    val initials = "${nome.firstOrNull() ?: ""}${cognome.firstOrNull() ?: ""}".uppercase()

    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = fgColor,
        )
    }
}

@Composable
internal fun ProclamatoreStatusBadge(sospeso: Boolean) {
    val sketch = MaterialTheme.workspaceSketch
    val (bgColor, dotColor, label) = if (sospeso) {
        Triple(sketch.warn.copy(alpha = 0.12f), sketch.warn, "Sospeso")
    } else {
        Triple(sketch.ok.copy(alpha = 0.12f), sketch.ok, "Attivo")
    }

    Row(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(20.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(dotColor))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = dotColor,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CapabilitySummaryChips(
    canAssist: Boolean,
    leadLabels: List<String>,
) {
    val sketch = MaterialTheme.workspaceSketch
    if (!canAssist && leadLabels.isEmpty()) {
        Text(
            text = "Nessuna idoneita'",
            style = MaterialTheme.typography.labelSmall,
            color = sketch.inkMuted,
        )
        return
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (canAssist) {
            CapabilityChip(
                label = "Assistente",
                backgroundColor = sketch.accentSoft,
                contentColor = sketch.accent,
            )
        }

        leadLabels.forEach { label ->
            CapabilityChip(
                label = label,
                backgroundColor = sketch.cardSurfaceMuted,
                contentColor = sketch.inkSoft,
            )
        }
    }
}

@Composable
private fun CapabilityChip(
    label: String,
    backgroundColor: Color,
    contentColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = backgroundColor,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatCapabilitySummaryText(
    canAssist: Boolean,
    leadLabels: List<String>,
): String {
    val items = buildList {
        if (canAssist) add("Assistente")
        addAll(leadLabels)
    }
    return items.joinToString(" · ").ifBlank { "Nessuna idoneita'" }
}

@Composable
private fun CompactSearchInput(
    value: String,
    onValueChange: (String) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester,
) {
    val sketch = MaterialTheme.workspaceSketch
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Surface(
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(8.dp),
        color = sketch.cardSurface,
        border = BorderStroke(
            1.dp,
            if (isFocused) sketch.selectionBorder.copy(alpha = 0.6f) else sketch.cardBorder,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = sketch.inkMuted,
            )
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        "Cerca",
                        style = MaterialTheme.typography.bodySmall,
                        color = sketch.inkMuted,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = sketch.ink),
                    cursorBrush = SolidColor(sketch.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    interactionSource = interactionSource,
                )
            }
            if (value.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(sketch.inkMuted.copy(alpha = 0.12f))
                        .handCursorOnHover()
                        .clickable(onClick = onReset),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Reset ricerca",
                        modifier = Modifier.size(9.dp),
                        tint = sketch.inkMuted,
                    )
                }
            }
        }
    }
}

private fun formatAnomalyDate(createdAt: String): String {
    return try {
        val dt = java.time.LocalDateTime.parse(createdAt)
        dt.format(org.example.project.ui.components.shortDateFormatter)
    } catch (_: Exception) {
        createdAt.substringBefore('T')
    }
}

package org.example.project.ui.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.assignments.domain.AssignmentWithPerson
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanStatus
import org.example.project.feature.weeklyparts.domain.WeeklyPart
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import org.example.project.ui.assignments.PartAssignmentCard
import org.example.project.ui.assignments.PersonPickerDialog
import org.example.project.ui.components.DISPLAY_NUMBER_OFFSET
import org.example.project.ui.components.formatMonthYearLabel
import org.example.project.ui.components.formatWeekRangeLabel
import org.example.project.ui.components.handCursorOnHover
import org.example.project.ui.theme.spacing
import org.example.project.ui.theme.workspaceSketch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

// Components extracted from ProgramWorkspaceScreen.kt
// This file contains composables for the Program Workspace UI

@Composable
internal fun ProgramWeekStickyHeader(
    week: WeekPlan,
    isCurrent: Boolean,
    isPast: Boolean,
) {
    val spacing = MaterialTheme.spacing
    val sketch = MaterialTheme.workspaceSketch
    val isSkipped = week.status == WeekPlanStatus.SKIPPED
    val containerColor = when {
        isCurrent -> sketch.accent.copy(alpha = 0.14f)
        isSkipped -> sketch.warn.copy(alpha = 0.14f)
        else -> sketch.surface.copy(alpha = 0.95f)
    }
    val borderColor = when {
        isCurrent -> sketch.accent.copy(alpha = 0.6f)
        isSkipped -> sketch.warn.copy(alpha = 0.6f)
        isPast -> sketch.lineSoft.copy(alpha = 0.7f)
        else -> sketch.lineSoft
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .background(sketch.panelMid)
            .padding(vertical = spacing.xxs),
        shape = RoundedCornerShape(spacing.cardRadius),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Settimana ${formatWeekRangeLabel(week.weekStartDate, week.weekStartDate.plusDays(6))}",
                style = MaterialTheme.typography.titleSmall,
                color = sketch.ink,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isCurrent) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = sketch.accent.copy(alpha = 0.2f),
                    ) {
                        Text(
                            "Corrente",
                            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xxs),
                            style = MaterialTheme.typography.labelSmall,
                            color = sketch.accent,
                        )
                    }
                }
                if (isSkipped) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = sketch.warn.copy(alpha = 0.2f),
                    ) {
                        Text(
                            "Saltata",
                            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xxs),
                            style = MaterialTheme.typography.labelSmall,
                            color = sketch.warn,
                        )
                    }
                }
                if (isPast && !isCurrent) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = sketch.surfaceMuted,
                    ) {
                        Text(
                            "Passata",
                            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xxs),
                            style = MaterialTheme.typography.labelSmall,
                            color = sketch.inkMuted,
                        )
                    }
                }
                Text(
                    "Parti: ${week.parts.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = sketch.inkMuted,
                )
            }
        }
    }
}

@Composable
internal fun ProgramWeekCard(
    week: WeekPlan,
    isCurrent: Boolean,
    today: java.time.LocalDate,
    showClearWeekAssignments: Boolean,
    assignments: List<AssignmentWithPerson>,
    assignedSlots: Int,
    totalSlots: Int,
    onReactivate: () -> Unit,
    onSkipWeek: () -> Unit,
    onOpenPartEditor: () -> Unit,
    onRequestClearWeekAssignments: () -> Unit,
    onAssignSlot: (WeeklyPartId, Int) -> Unit,
    onRemoveAssignment: (AssignmentId) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val sketch = MaterialTheme.workspaceSketch
    val isSkipped = week.status == WeekPlanStatus.SKIPPED
    val isPast = !isCurrent && week.weekStartDate.plusDays(6) < today
    val canMutate = !isSkipped && !isPast
    val assignmentsByPart = remember(assignments) { assignments.groupBy { it.weeklyPartId } }
    val partRows = remember(week.parts) { week.parts.chunked(2) }
    val missingSlots = (totalSlots - assignedSlots).coerceAtLeast(0)
    val weekLabel = formatWeekRangeLabel(week.weekStartDate, week.weekStartDate.plusDays(6))

    val borderColor = when {
        isCurrent -> sketch.accent.copy(alpha = 0.7f)
        isSkipped -> sketch.inkMuted.copy(alpha = 0.6f)
        missingSlots == 0 && totalSlots > 0 -> sketch.ok.copy(alpha = 0.62f)
        missingSlots == totalSlots && totalSlots > 0 -> sketch.bad.copy(alpha = 0.6f)
        else -> sketch.warn.copy(alpha = 0.6f)
    }
    val headerTint = when {
        isCurrent -> sketch.accent.copy(alpha = 0.12f)
        isSkipped -> sketch.inkMuted.copy(alpha = 0.12f)
        missingSlots == 0 && totalSlots > 0 -> sketch.ok.copy(alpha = 0.12f)
        missingSlots == totalSlots && totalSlots > 0 -> sketch.bad.copy(alpha = 0.11f)
        else -> sketch.warn.copy(alpha = 0.11f)
    }
    val doneColor = when {
        missingSlots == 0 && totalSlots > 0 -> sketch.ok
        isSkipped -> sketch.inkMuted
        else -> sketch.accent
    }
    val pendingColor = when {
        missingSlots == 0 -> sketch.lineSoft
        missingSlots == totalSlots -> sketch.bad
        else -> sketch.warn
    }
    val statusTextColor = when {
        missingSlots == 0 && totalSlots > 0 -> sketch.ok
        missingSlots == totalSlots && totalSlots > 0 -> sketch.bad
        else -> sketch.warn
    }
    val actionScroll = rememberScrollState()
    val showActions = (!isPast && !isSkipped) || (isSkipped && !isPast) || (canMutate && showClearWeekAssignments)

    Card(
        shape = RoundedCornerShape(spacing.cardRadius),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = sketch.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerTint)
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Settimana $weekLabel",
                            style = MaterialTheme.typography.titleMedium,
                            color = sketch.ink,
                        )
                        when {
                            isCurrent -> WeekMetaBadge(label = "Corrente", tone = DesktopInlineActionTone.Primary)
                            isSkipped -> WeekMetaBadge(label = "Saltata", tone = DesktopInlineActionTone.Warn)
                            isPast -> WeekMetaBadge(label = "Passata", tone = DesktopInlineActionTone.Neutral)
                        }
                        WeekMetaBadge(label = "Parti: ${week.parts.size}", tone = DesktopInlineActionTone.Neutral)
                    }
                }
                Text(
                    text = "$assignedSlots/$totalSlots slot assegnati",
                    style = MaterialTheme.typography.labelMedium,
                    color = sketch.inkSoft,
                )
                Text(
                    text = if (missingSlots == 0 && totalSlots > 0) "Completa" else "$missingSlots slot vuoti",
                    style = MaterialTheme.typography.labelSmall,
                    color = statusTextColor,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(sketch.lineSoft.copy(alpha = 0.7f)),
                ) {
                    if (totalSlots > 0 && assignedSlots > 0) {
                        Box(
                            modifier = Modifier
                                .weight(assignedSlots.toFloat())
                                .fillMaxHeight()
                                .background(doneColor),
                        )
                    }
                    if (totalSlots > 0 && missingSlots > 0) {
                        Box(
                            modifier = Modifier
                                .weight(missingSlots.toFloat())
                                .fillMaxHeight()
                                .background(pendingColor),
                        )
                    }
                }
            }

            if (showActions) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(actionScroll)
                        .padding(horizontal = spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isSkipped && !isPast) {
                        DesktopInlineAction(
                            label = "Riattiva",
                            onClick = onReactivate,
                            tone = DesktopInlineActionTone.Positive,
                        )
                    } else if (!isPast) {
                        DesktopInlineAction(
                            label = "Modifica parti",
                            onClick = onOpenPartEditor,
                            tone = DesktopInlineActionTone.Primary,
                        )
                        DesktopInlineAction(
                            label = "Salta settimana",
                            onClick = onSkipWeek,
                            tone = DesktopInlineActionTone.Warn,
                        )
                    }
                    if (canMutate && showClearWeekAssignments) {
                        DesktopInlineAction(
                            label = "Rimuovi assegnazioni",
                            icon = Icons.Filled.ClearAll,
                            onClick = onRequestClearWeekAssignments,
                            tone = DesktopInlineActionTone.Danger,
                        )
                    }
                }
            }

            if (week.parts.isEmpty()) {
                Text(
                    "Nessuna parte configurata",
                    modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
                    style = MaterialTheme.typography.bodyMedium,
                    color = sketch.inkMuted,
                )
            } else {
                Column(
                    modifier = Modifier.padding(start = spacing.md, end = spacing.md, bottom = spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    partRows.forEach { rowParts ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                            verticalAlignment = Alignment.Top,
                        ) {
                            rowParts.forEach { part ->
                                PartAssignmentCard(
                                    part = part,
                                    assignments = assignmentsByPart[part.id] ?: emptyList(),
                                    displayNumber = part.sortOrder + DISPLAY_NUMBER_OFFSET,
                                    readOnly = !canMutate,
                                    showSexRuleChip = false,
                                    onAssignSlot = { slot -> onAssignSlot(part.id, slot) },
                                    onRemoveAssignment = onRemoveAssignment,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                )
                            }
                            if (rowParts.size == 1) {
                                Spacer(Modifier.weight(1f).fillMaxHeight())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekMetaBadge(
    label: String,
    tone: DesktopInlineActionTone,
) {
    val sketch = MaterialTheme.workspaceSketch
    val (container, border, content) = when (tone) {
        DesktopInlineActionTone.Neutral -> Triple(
            sketch.surface,
            sketch.lineSoft,
            sketch.inkMuted,
        )
        DesktopInlineActionTone.Primary -> Triple(
            sketch.accent.copy(alpha = 0.12f),
            sketch.accent.copy(alpha = 0.45f),
            sketch.accent,
        )
        DesktopInlineActionTone.Positive -> Triple(
            sketch.ok.copy(alpha = 0.13f),
            sketch.ok.copy(alpha = 0.45f),
            sketch.ok,
        )
        DesktopInlineActionTone.Warn -> Triple(
            sketch.warn.copy(alpha = 0.14f),
            sketch.warn.copy(alpha = 0.45f),
            sketch.warn,
        )
        DesktopInlineActionTone.Danger -> Triple(
            sketch.bad.copy(alpha = 0.12f),
            sketch.bad.copy(alpha = 0.55f),
            sketch.bad,
        )
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = container,
        border = BorderStroke(1.dp, border),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = content,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PartEditorDialog(
    weekLabel: String,
    parts: List<WeeklyPart>,
    availablePartTypes: List<PartType>,
    isSaving: Boolean,
    onAddPart: (PartType) -> Unit,
    onMovePart: (Int, Int) -> Unit,
    onRemovePart: (WeeklyPart) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val sketch = MaterialTheme.workspaceSketch
    var menuExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
    ) { from, to ->
        onMovePart(from.index, to.index)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(spacing.cardRadius),
            tonalElevation = 0.dp,
            border = BorderStroke(1.dp, sketch.lineSoft),
            color = sketch.surface,
            modifier = Modifier.width(780.dp).heightIn(max = 720.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxHeight().padding(spacing.xl),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Text("Modifica parti", style = MaterialTheme.typography.titleLarge)
                Text("Settimana $weekLabel", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Trascina le righe per riordinare le parti",
                    style = MaterialTheme.typography.bodySmall,
                    color = sketch.inkMuted,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    DesktopInlineAction(
                        label = "Aggiungi parte",
                        icon = Icons.Filled.Add,
                        onClick = { menuExpanded = true },
                        enabled = !isSaving,
                        tone = DesktopInlineActionTone.Primary,
                    )
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        availablePartTypes.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.label) },
                                onClick = {
                                    menuExpanded = false
                                    onAddPart(type)
                                },
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(sketch.surfaceMuted.copy(alpha = 0.92f), RoundedCornerShape(10.dp))
                        .padding(end = 10.dp),
                ) {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(spacing.xs),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(spacing.sm),
                    ) {
                        items(parts.size, key = { index -> parts[index].id.value }) { index ->
                            val part = parts[index]
                            ReorderableItem(
                                state = reorderableState,
                                key = part.id.value,
                            ) { isDragging ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isDragging) {
                                        sketch.accent.copy(alpha = 0.18f)
                                    } else {
                                        sketch.surface
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        if (isDragging) {
                                            sketch.accent.copy(alpha = 0.8f)
                                        } else {
                                            sketch.lineSoft
                                        },
                                    ),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = spacing.md, vertical = spacing.sm),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.DragIndicator,
                                            contentDescription = "Trascina per riordinare",
                                            tint = sketch.accent,
                                            modifier = Modifier
                                                .size(20.dp)
                                                .handCursorOnHover(enabled = !isSaving)
                                                .draggableHandle(enabled = !isSaving),
                                        )
                                        Text("${index + DISPLAY_NUMBER_OFFSET}", style = MaterialTheme.typography.labelLarge)
                                        Text(
                                            part.partType.label,
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        Text(
                                            "${part.partType.peopleCount} persone",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = sketch.inkMuted,
                                        )
                                        if (!part.partType.fixed) {
                                            IconButton(
                                                onClick = { onRemovePart(part) },
                                                enabled = !isSaving,
                                                modifier = Modifier.handCursorOnHover(enabled = !isSaving),
                                            ) {
                                                Icon(Icons.Filled.Close, contentDescription = "Rimuovi parte")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(listState),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight(),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    DesktopInlineAction(
                        label = "Annulla",
                        onClick = onDismiss,
                        enabled = !isSaving,
                        tone = DesktopInlineActionTone.Neutral,
                    )
                    Spacer(Modifier.width(spacing.sm))
                    DesktopInlineAction(
                        label = if (isSaving) "Salvataggio..." else "Salva",
                        onClick = onSave,
                        enabled = !isSaving,
                        tone = DesktopInlineActionTone.Positive,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ProgramHeader(
    currentProgram: org.example.project.feature.programs.domain.ProgramMonth?,
    futureProgram: org.example.project.feature.programs.domain.ProgramMonth?,
    selectedProgramId: String?,
    hasPrograms: Boolean,
    canCreateProgram: Boolean,
    isCreatingProgram: Boolean,
    isRefreshingSchemas: Boolean,
    futureNeedsSchemaRefresh: Boolean,
    onSelectProgram: (String) -> Unit,
    onCreateNextProgram: () -> Unit,
    onRefreshSchemas: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val current = currentProgram
    val future = futureProgram

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(spacing.cardRadius),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Programmi attivi",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                DesktopInlineAction(
                    label = if (isRefreshingSchemas) "Aggiornamento..." else "Aggiorna schemi",
                    icon = Icons.Filled.Refresh,
                    onClick = onRefreshSchemas,
                    enabled = !isRefreshingSchemas,
                    tone = DesktopInlineActionTone.Neutral,
                )
            }
            if (!hasPrograms) {
                Text(
                    "Nessun programma disponibile. Crea il primo programma.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                current?.let {
                    val isSelected = selectedProgramId == it.id.value
                    DesktopInlineAction(
                        label = formatMonthYearLabel(it.month, it.year).replaceFirstChar { c -> c.uppercase() },
                        onClick = { if (!isSelected) onSelectProgram(it.id.value) },
                        tone = if (isSelected) DesktopInlineActionTone.Primary else DesktopInlineActionTone.Neutral,
                    )
                }
                future?.let {
                    val isSelected = selectedProgramId == it.id.value
                    DesktopInlineAction(
                        label = formatMonthYearLabel(it.month, it.year).replaceFirstChar { c -> c.uppercase() },
                        onClick = { if (!isSelected) onSelectProgram(it.id.value) },
                        tone = if (isSelected) DesktopInlineActionTone.Warn else DesktopInlineActionTone.Neutral,
                    )
                    if (futureNeedsSchemaRefresh) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                        ) {
                            Text(
                                "Template aggiornato, verificare",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }
                if (canCreateProgram) {
                    DesktopInlineAction(
                        label = if (isCreatingProgram) "Creazione..." else "Crea prossimo mese",
                        icon = Icons.Filled.Add,
                        onClick = onCreateNextProgram,
                        enabled = !isCreatingProgram,
                        tone = DesktopInlineActionTone.Primary,
                    )
                }
            }
        }
    }
}

private enum class DesktopInlineActionTone {
    Neutral,
    Primary,
    Positive,
    Warn,
    Danger,
}

@Composable
private fun DesktopInlineAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    tone: DesktopInlineActionTone = DesktopInlineActionTone.Neutral,
) {
    val spacing = MaterialTheme.spacing
    val sketch = MaterialTheme.workspaceSketch
    val (container, border, content) = when (tone) {
        DesktopInlineActionTone.Neutral -> Triple(
            sketch.surfaceMuted,
            sketch.lineSoft,
            sketch.inkSoft,
        )
        DesktopInlineActionTone.Primary -> Triple(
            sketch.accent.copy(alpha = 0.2f),
            sketch.accent.copy(alpha = 0.7f),
            sketch.accent,
        )
        DesktopInlineActionTone.Positive -> Triple(
            sketch.ok.copy(alpha = 0.2f),
            sketch.ok.copy(alpha = 0.7f),
            sketch.ok,
        )
        DesktopInlineActionTone.Warn -> Triple(
            sketch.warn.copy(alpha = 0.2f),
            sketch.warn.copy(alpha = 0.7f),
            sketch.warn,
        )
        DesktopInlineActionTone.Danger -> Triple(
            sketch.surfaceMuted,
            sketch.bad.copy(alpha = 0.75f),
            sketch.bad,
        )
    }
    val alpha = if (enabled) 1f else 0.46f
    Surface(
        modifier = modifier
            .handCursorOnHover(enabled = enabled)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = container.copy(alpha = alpha),
        border = BorderStroke(1.dp, border.copy(alpha = alpha)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = content.copy(alpha = alpha),
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = content.copy(alpha = alpha),
            )
        }
    }
}

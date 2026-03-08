package org.example.project.ui.workspace

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.example.project.feature.assignments.application.AutoAssignUnresolvedSlot
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.WeeklyPart
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import org.example.project.ui.components.DISPLAY_NUMBER_OFFSET
import org.example.project.ui.components.formatWeekRangeLabel
import org.example.project.ui.components.handCursorOnHover
import org.example.project.ui.theme.spacing
import org.example.project.ui.theme.workspaceSketch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun PartEditorDialog(
    weekLabel: String,
    parts: List<WeeklyPart>,
    availablePartTypes: List<PartType>,
    assignmentCountsByPart: Map<WeeklyPartId, Int>,
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
    var pendingRemovePart by remember { mutableStateOf<WeeklyPart?>(null) }
    val listState = rememberLazyListState()

    pendingRemovePart?.let { part ->
        val count = assignmentCountsByPart[part.id] ?: 0
        Dialog(onDismissRequest = { pendingRemovePart = null }) {
            Surface(
                shape = RoundedCornerShape(spacing.cardRadius),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                border = BorderStroke(1.dp, sketch.lineSoft),
                color = sketch.surface,
                modifier = Modifier.width(440.dp),
            ) {
                Column(
                    modifier = Modifier.padding(spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    Text("Rimuovi parte", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Vuoi rimuovere «${part.partType.label}»?" +
                            if (count > 0) " Questa parte ha $count ${if (count == 1) "assegnazione" else "assegnazioni"} che verranno eliminate." else "",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DesktopInlineAction(
                            label = "Annulla",
                            onClick = { pendingRemovePart = null },
                            tone = DesktopInlineActionTone.Neutral,
                            modifier = Modifier.width(120.dp).height(40.dp),
                        )
                        Spacer(Modifier.width(spacing.sm))
                        DesktopInlineAction(
                            label = "Rimuovi",
                            onClick = {
                                pendingRemovePart = null
                                onRemovePart(part)
                            },
                            tone = if (count > 0) DesktopInlineActionTone.Danger else DesktopInlineActionTone.Neutral,
                            modifier = Modifier.width(120.dp).height(40.dp),
                        )
                    }
                }
            }
        }
    }
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
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, sketch.lineSoft),
            color = sketch.surface,
            modifier = Modifier.width(780.dp).heightIn(max = 720.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxHeight().padding(spacing.xl),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                // ── Header: title + close button ─────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Modifica parti", style = MaterialTheme.typography.titleLarge)
                        Text("Settimana $weekLabel", style = MaterialTheme.typography.bodyMedium)
                    }
                    IconButton(
                        onClick = onDismiss,
                        enabled = !isSaving,
                        modifier = Modifier.size(32.dp).handCursorOnHover(enabled = !isSaving),
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Chiudi",
                            modifier = Modifier.size(18.dp),
                            tint = sketch.inkMuted,
                        )
                    }
                }
                Text(
                    "Trascina le righe per riordinare le parti",
                    style = MaterialTheme.typography.bodySmall,
                    color = sketch.inkMuted,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DesktopInlineAction(
                        label = "Aggiungi parte",
                        icon = Icons.Filled.Add,
                        onClick = { menuExpanded = true },
                        enabled = !isSaving,
                        tone = DesktopInlineActionTone.Primary,
                        modifier = Modifier.width(184.dp).height(40.dp),
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
                                            if (part.partType.peopleCount == 1) "1 studente" else "${part.partType.peopleCount} studenti",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = sketch.inkMuted,
                                        )
                                        val assignCount = assignmentCountsByPart[part.id] ?: 0
                                        if (assignCount > 0) {
                                            val tooltipText = if (assignCount == 1) {
                                                "1 assegnazione presente — verrà eliminata se rimuovi questa parte"
                                            } else {
                                                "$assignCount assegnazioni presenti — verranno eliminate se rimuovi questa parte"
                                            }
                                            TooltipBox(
                                                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                                    positioning = TooltipAnchorPosition.Above,
                                                ),
                                                tooltip = { PlainTooltip { Text(tooltipText) } },
                                                state = rememberTooltipState(),
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(999.dp),
                                                    color = sketch.warn.copy(alpha = 0.15f),
                                                    border = BorderStroke(1.dp, sketch.warn.copy(alpha = 0.5f)),
                                                ) {
                                                    Text(
                                                        "$assignCount assegn.",
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                        color = sketch.warn,
                                                    )
                                                }
                                            }
                                        }
                                        if (!part.partType.fixed) {
                                            IconButton(
                                                onClick = { pendingRemovePart = part },
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
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DesktopInlineAction(
                        label = "Annulla",
                        onClick = onDismiss,
                        enabled = !isSaving,
                        tone = DesktopInlineActionTone.Neutral,
                        modifier = Modifier.width(132.dp).height(40.dp),
                    )
                    Spacer(Modifier.width(spacing.sm))
                    DesktopInlineAction(
                        label = if (isSaving) "Salvataggio..." else "Salva",
                        onClick = onSave,
                        enabled = !isSaving,
                        tone = DesktopInlineActionTone.Primary,
                        modifier = Modifier.width(132.dp).height(40.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun ProgramMisalignedBadge() {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Text(
            "Template aggiornato",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
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
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val (container, border, content) = when (tone) {
        DesktopInlineActionTone.Neutral -> Triple(
            sketch.surfaceMuted,
            sketch.lineSoft,
            sketch.inkSoft,
        )
        DesktopInlineActionTone.Primary -> Triple(
            sketch.accent,
            sketch.accent.copy(alpha = 0.92f),
            Color.White,
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
    val alpha = if (enabled) 1f else 0.72f
    val containerColor = when {
        enabled && tone == DesktopInlineActionTone.Primary && focused -> sketch.accent.copy(alpha = 0.96f)
        enabled && tone == DesktopInlineActionTone.Primary && hovered -> sketch.accent.copy(alpha = 0.86f)
        enabled && focused -> sketch.accentSoft.copy(alpha = 0.86f)
        enabled && hovered -> sketch.surface
        else -> container.copy(alpha = alpha)
    }
    Surface(
        modifier = modifier
            .heightIn(min = 34.dp)
            .handCursorOnHover(enabled = enabled)
            .hoverable(interactionSource)
            .focusable(enabled = enabled, interactionSource = interactionSource)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        border = BorderStroke(
            1.dp,
            if (focused && enabled && tone != DesktopInlineActionTone.Danger) {
                sketch.accent.copy(alpha = 0.72f)
            } else {
                border.copy(alpha = alpha)
            },
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.sm, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs, Alignment.CenterHorizontally),
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun WeekSidebarItem(
    label: String,
    status: WeekSidebarStatus,
    fraction: Float,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val sketch = MaterialTheme.workspaceSketch
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()

    val dotColor = when (status) {
        WeekSidebarStatus.CURRENT -> sketch.accent
        WeekSidebarStatus.COMPLETE -> sketch.ok
        WeekSidebarStatus.PARTIAL -> sketch.warn
        WeekSidebarStatus.PAST -> sketch.inkMuted
        WeekSidebarStatus.SKIPPED, WeekSidebarStatus.EMPTY -> sketch.lineSoft
    }
    val fillColor = when (status) {
        WeekSidebarStatus.CURRENT -> sketch.accent
        WeekSidebarStatus.COMPLETE -> sketch.ok
        WeekSidebarStatus.PARTIAL -> sketch.warn
        WeekSidebarStatus.PAST -> sketch.inkMuted
        else -> Color.Transparent
    }
    val bgColor = when {
        selected -> sketch.accentSoft
        focused -> sketch.accentSoft.copy(alpha = 0.8f)
        hovered -> sketch.lineSoft.copy(alpha = 0.28f)
        else -> Color.Transparent
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interactionSource)
            .handCursorOnHover()
            .focusable(interactionSource = interactionSource)
            .clip(RoundedCornerShape(9.dp))
            .background(bgColor)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(dotColor))
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = if (selected) sketch.accent else sketch.ink,
                maxLines = 1,
            )
            when (status) {
                WeekSidebarStatus.CURRENT -> WeekSidebarTag("CORRENTE", sketch.accent)
                WeekSidebarStatus.SKIPPED -> WeekSidebarTag("SALTATA", sketch.inkMuted)
                else -> {}
            }
        }
        if (status != WeekSidebarStatus.SKIPPED) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .padding(bottom = 6.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(sketch.lineSoft.copy(alpha = 0.5f)),
            ) {
                if (fraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction.coerceAtMost(1f))
                            .background(fillColor, RoundedCornerShape(999.dp)),
                    )
                }
            }
        }
    }
}

@Composable
internal fun SidebarLegendRow(label: String, color: Color) {
    val sketch = MaterialTheme.workspaceSketch
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = sketch.inkMuted,
        )
    }
}

@Composable
private fun WeekSidebarTag(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 10.sp,
                letterSpacing = 0.3.sp,
            ),
            color = color,
        )
    }
}

@Composable
internal fun WeekDetailHeader(
    weekLabel: String,
    monthLabel: String,
    isCurrent: Boolean,
    isSkipped: Boolean,
    canMutate: Boolean,
    onOpenPartEditor: () -> Unit,
    onSkipWeek: () -> Unit,
    onReactivate: () -> Unit,
) {
    val sketch = MaterialTheme.workspaceSketch
    Column(modifier = Modifier.background(sketch.panelLeft)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        weekLabel,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            letterSpacing = (-0.3).sp,
                        ),
                        color = sketch.ink,
                    )
                    when {
                        isCurrent -> WeekDetailBadge("CORRENTE", sketch.accent)
                        isSkipped -> WeekDetailBadge("SALTATA", sketch.inkMuted)
                    }
                }
                Text(monthLabel, style = MaterialTheme.typography.bodySmall, color = sketch.inkMuted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    isSkipped -> WeekHdrButton(
                        label = "Riattiva",
                        icon = Icons.Filled.PlayCircle,
                        fg = sketch.ok,
                        border = sketch.ok.copy(alpha = 0.45f),
                        onClick = onReactivate,
                    )
                    canMutate -> {
                        WeekHdrButton(
                            label = "Modifica parti",
                            icon = Icons.Filled.Edit,
                            fg = sketch.inkSoft,
                            border = sketch.lineSoft,
                            onClick = onOpenPartEditor,
                        )
                        WeekHdrButton(
                            label = "Salta settimana",
                            icon = Icons.Filled.Block,
                            fg = sketch.inkSoft,
                            border = sketch.lineSoft,
                            onClick = onSkipWeek,
                        )
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(sketch.lineSoft))
    }
}

@Composable
private fun WeekDetailBadge(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp,
                letterSpacing = 0.2.sp,
            ),
            color = color,
        )
    }
}

@Composable
private fun WeekHdrButton(
    label: String,
    icon: ImageVector,
    fg: Color,
    border: Color,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val bgColor = when {
        focused -> fg.copy(alpha = 0.14f)
        hovered -> fg.copy(alpha = 0.08f)
        else -> Color.Transparent
    }
    val borderColor = when {
        focused -> fg.copy(alpha = 0.8f)
        hovered -> border.copy(alpha = 0.85f)
        else -> border
    }
    Surface(
        modifier = Modifier
            .handCursorOnHover()
            .hoverable(interactionSource)
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(13.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun WeekCoverageStrip(assigned: Int, total: Int, fraction: Float) {
    val sketch = MaterialTheme.workspaceSketch
    val fillColor = when {
        fraction >= 1f -> sketch.ok
        fraction > 0f -> sketch.accent
        else -> Color.Transparent
    }
    val pctColor = when {
        fraction >= 1f -> sketch.ok
        fraction > 0f -> sketch.accent
        else -> sketch.inkMuted
    }
    Column(modifier = Modifier.background(sketch.panelLeft)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$assigned / $total slot",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = sketch.inkSoft,
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(sketch.lineStrong),
            ) {
                if (fraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction.coerceAtMost(1f))
                            .background(fillColor, RoundedCornerShape(999.dp)),
                    )
                }
            }
            Text(
                "${(fraction * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = pctColor,
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(sketch.lineSoft.copy(alpha = 0.5f)))
    }
}

@Composable
internal fun SidebarFooterButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val sketch = MaterialTheme.workspaceSketch
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val alpha = if (enabled) 1f else 0.46f
    val bgColor = when {
        focused -> sketch.accentSoft.copy(alpha = 0.8f)
        hovered -> sketch.surfaceMuted
        else -> sketch.surface
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .handCursorOnHover(enabled)
            .hoverable(interactionSource)
            .focusable(enabled = enabled, interactionSource = interactionSource)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = BorderStroke(1.dp, sketch.lineSoft.copy(alpha = alpha)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = sketch.inkSoft.copy(alpha = alpha), modifier = Modifier.size(12.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = sketch.inkSoft.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun ProgramRightPanelButton(
    label: String,
    icon: ImageVector,
    isPrimary: Boolean,
    enabled: Boolean,
    iconColor: Color? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sketch = MaterialTheme.workspaceSketch
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val alpha = if (enabled) 1f else 0.72f
    val baseContainer = if (isPrimary) sketch.accent else sketch.surface
    val container = when {
        enabled && isPrimary && focused -> sketch.accent.copy(alpha = 0.88f)
        enabled && isPrimary && hovered -> sketch.accent.copy(alpha = 0.82f)
        enabled && !isPrimary && focused -> sketch.accentSoft.copy(alpha = 0.95f)
        enabled && !isPrimary && hovered -> sketch.accentSoft.copy(alpha = 0.65f)
        else -> baseContainer.copy(alpha = alpha)
    }
    val border = when {
        enabled && focused -> sketch.accent.copy(alpha = 0.8f)
        enabled && hovered -> sketch.accent.copy(alpha = 0.55f)
        isPrimary -> sketch.accent.copy(alpha = alpha)
        else -> sketch.lineSoft.copy(alpha = alpha)
    }
    val contentColor = if (isPrimary) Color.White.copy(alpha = alpha) else sketch.inkSoft.copy(alpha = alpha)
    val iconTint = (iconColor ?: contentColor).copy(alpha = alpha)
    Surface(
        modifier = modifier
            .handCursorOnHover(enabled)
            .hoverable(interactionSource)
            .focusable(enabled = enabled, interactionSource = interactionSource)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(8.dp),
        color = container,
        border = BorderStroke(1.dp, border),
    ) {
        Row(
            modifier = Modifier
                .height(34.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(13.dp),
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                modifier = Modifier.weight(1f),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun ProgramCoverageCard(
    programLabel: String,
    assigned: Int,
    total: Int,
    completeWeeks: Int,
    partialWeeks: Int,
    emptyWeeks: Int,
) {
    val sketch = MaterialTheme.workspaceSketch
    val fraction = if (total > 0) assigned.toFloat() / total else 0f
    val fillColor = if (fraction >= 1f) sketch.ok else sketch.accent

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "COPERTURA",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 10.sp,
                letterSpacing = 0.7.sp,
            ),
            color = sketch.inkMuted,
        )
        Surface(
            shape = RoundedCornerShape(9.dp),
            color = sketch.surfaceMuted,
            border = BorderStroke(1.dp, sketch.lineSoft),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "$assigned",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-1.5).sp,
                        ),
                        color = sketch.ink,
                    )
                    Text(
                        "/ $total",
                        style = MaterialTheme.typography.bodyMedium,
                        color = sketch.inkMuted,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
                Text(
                    "slot assegnati · $programLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = sketch.inkMuted,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(sketch.lineStrong),
                ) {
                    if (fraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction.coerceAtMost(1f))
                                .background(fillColor, RoundedCornerShape(999.dp)),
                        )
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (completeWeeks > 0) CovPill("$completeWeeks complete", sketch.ok)
                    if (partialWeeks > 0) CovPill("$partialWeeks parziali", sketch.warn)
                    if (emptyWeeks > 0) CovPill("$emptyWeeks vuote", sketch.inkMuted)
                }
            }
        }
    }
}

@Composable
private fun CovPill(label: String, color: Color) {
    val sketch = MaterialTheme.workspaceSketch
    val containerColor = when (color) {
        sketch.ok -> MaterialTheme.colorScheme.secondaryContainer
        sketch.warn -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (color) {
        sketch.ok -> MaterialTheme.colorScheme.onSecondaryContainer
        sketch.warn -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
        border = BorderStroke(1.dp, color.copy(alpha = 0.55f)),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = contentColor,
        )
    }
}

@Composable
internal fun RightPanelCollapsibleSection(
    title: String,
    open: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val sketch = MaterialTheme.workspaceSketch
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .handCursorOnHover()
                .focusable(interactionSource = interactionSource)
                .clickable(onClick = onToggle)
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp,
                    letterSpacing = 0.7.sp,
                ),
                color = if (focused) sketch.inkSoft else sketch.inkMuted,
            )
            Icon(
                imageVector = if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = if (focused) sketch.inkSoft else sketch.inkMuted,
                modifier = Modifier.size(14.dp),
            )
        }
        AnimatedVisibility(visible = open) {
            content()
        }
    }
}

@Composable
internal fun ProgramIssuesPanel(issues: List<AutoAssignUnresolvedSlot>) {
    val sketch = MaterialTheme.workspaceSketch
    var open by remember { mutableStateOf(true) }
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = sketch.warn.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, sketch.warn.copy(alpha = 0.4f)),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .handCursorOnHover()
                    .clickable { open = !open }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = sketch.warn,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    "PROBLEMI",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp,
                    ),
                    color = sketch.warn,
                )
                Box(
                    modifier = Modifier.size(17.dp).clip(CircleShape).background(sketch.warn),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${issues.size}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp,
                        ),
                        color = Color.White,
                    )
                }
                Icon(
                    if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = sketch.warn,
                    modifier = Modifier.size(12.dp),
                )
            }
            AnimatedVisibility(visible = open) {
                Column {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(sketch.warn.copy(alpha = 0.4f)))
                    issues.take(6).forEach { issue ->
                        val weekLabel = formatWeekRangeLabel(issue.weekStartDate, issue.weekStartDate.plusDays(6))
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                "${issue.partLabel.uppercase()} · Slot ${issue.slot} · $weekLabel",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 10.5.sp,
                                    letterSpacing = 0.4.sp,
                                ),
                                color = sketch.warn,
                            )
                            Text(
                                issue.reason,
                                style = MaterialTheme.typography.labelSmall,
                                color = sketch.inkSoft,
                            )
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(sketch.warn.copy(alpha = 0.15f)))
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProgramDangerButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val sketch = MaterialTheme.workspaceSketch
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val alpha = if (enabled) 1f else 0.72f
    val container = when {
        enabled && focused -> sketch.bad.copy(alpha = 0.12f)
        enabled && hovered -> sketch.bad.copy(alpha = 0.08f)
        else -> sketch.surface
    }
    val border = when {
        enabled && focused -> sketch.bad.copy(alpha = 0.7f)
        enabled && hovered -> sketch.bad.copy(alpha = 0.58f)
        else -> sketch.bad.copy(alpha = 0.45f * alpha)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .handCursorOnHover(enabled)
            .hoverable(interactionSource)
            .focusable(enabled = enabled, interactionSource = interactionSource)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(8.dp),
        color = container,
        border = BorderStroke(1.dp, border),
    ) {
        Row(
            modifier = Modifier
                .height(34.dp)
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = sketch.bad.copy(alpha = alpha),
                modifier = Modifier.size(12.dp),
            )
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = sketch.bad.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

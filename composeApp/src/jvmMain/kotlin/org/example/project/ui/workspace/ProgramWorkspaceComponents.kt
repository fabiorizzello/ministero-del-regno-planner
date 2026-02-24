package org.example.project.ui.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val isSkipped = week.status == WeekPlanStatus.SKIPPED
    val containerColor = when {
        isCurrent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f)
        isSkipped -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.94f)
        isPast -> MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    }
    val borderColor = when {
        isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
        isSkipped -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.75f)
        isPast -> MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.9f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f))
            .padding(vertical = spacing.xs),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Settimana ${formatWeekRangeLabel(week.weekStartDate, week.weekStartDate.plusDays(6))}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isCurrent) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    ) {
                        Text(
                            "Corrente",
                            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xxs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (isSkipped) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                    ) {
                        Text(
                            "Saltata",
                            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xxs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
                if (isPast) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ) {
                        Text(
                            "Passata",
                            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xxs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    "Parti: ${week.parts.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    assignments: List<AssignmentWithPerson>,
    onReactivate: () -> Unit,
    onOpenPartEditor: () -> Unit,
    onRequestClearWeekAssignments: () -> Unit,
    onAssignSlot: (WeeklyPartId, Int) -> Unit,
    onRemoveAssignment: (AssignmentId) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val isSkipped = week.status == WeekPlanStatus.SKIPPED
    val isPast = week.weekStartDate < today
    val canMutate = !isSkipped && !isPast
    val assignmentsByPart = remember(assignments) { assignments.groupBy { it.weeklyPartId } }
    val partRows = remember(week.parts) { week.parts.chunked(2) }

    val borderColor = when {
        isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
        isSkipped -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.72f)
        isPast -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }
    val containerColor = when {
        isCurrent -> MaterialTheme.colorScheme.surface
        isSkipped -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.28f)
        isPast -> MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
        else -> MaterialTheme.colorScheme.surface
    }
    val accentColor = when {
        isCurrent -> MaterialTheme.colorScheme.primary
        isSkipped -> MaterialTheme.colorScheme.secondary
        isPast -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.tertiary
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCurrent) 4.dp else 3.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(containerColor),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(accentColor.copy(alpha = 0.76f)),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isSkipped && !isPast) {
                    OutlinedButton(
                        onClick = onReactivate,
                        modifier = Modifier.handCursorOnHover(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.secondary,
                        ),
                    ) { Text("Riattiva") }
                } else if (!isPast) {
                    OutlinedButton(
                        onClick = onOpenPartEditor,
                        modifier = Modifier.handCursorOnHover(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) { Text("Modifica parti") }
                }
                if (canMutate) {
                    OutlinedButton(
                        onClick = onRequestClearWeekAssignments,
                        modifier = Modifier.handCursorOnHover(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        ),
                    ) {
                        Icon(Icons.Filled.ClearAll, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(spacing.xs))
                        Text("Rimuovi assegnazioni")
                    }
                }
            }

            if (week.parts.isEmpty()) {
                Text(
                    "Nessuna parte configurata",
                    modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    partRows.forEach { rowParts ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (rowParts.size > 1) Modifier.height(IntrinsicSize.Min) else Modifier),
                            horizontalArrangement = Arrangement.spacedBy(spacing.md),
                            verticalAlignment = Alignment.Top,
                        ) {
                            rowParts.forEach { part ->
                                val partModifier = if (rowParts.size > 1) {
                                    Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                } else {
                                    Modifier.fillMaxWidth()
                                }
                                PartAssignmentCard(
                                    part = part,
                                    assignments = assignmentsByPart[part.id] ?: emptyList(),
                                    displayNumber = part.sortOrder + DISPLAY_NUMBER_OFFSET,
                                    readOnly = !canMutate,
                                    showSexRuleChip = false,
                                    onAssignSlot = { slot -> onAssignSlot(part.id, slot) },
                                    onRemoveAssignment = onRemoveAssignment,
                                    modifier = partModifier,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

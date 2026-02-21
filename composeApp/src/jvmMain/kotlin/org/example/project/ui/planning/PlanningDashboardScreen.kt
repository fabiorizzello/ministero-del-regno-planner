package org.example.project.ui.planning

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.example.project.feature.planning.application.PlanningWeekStatus
import org.example.project.feature.planning.domain.WeekPlanningStatus
import org.example.project.ui.AppSection
import org.example.project.ui.LocalSectionNavigator
import org.example.project.ui.components.FeedbackBanner
import org.example.project.ui.components.FeedbackBannerKind
import org.example.project.ui.components.FeedbackBannerModel
import org.example.project.ui.components.dateFormatter
import org.example.project.ui.components.handCursorOnHover
import org.example.project.ui.components.sundayOf
import org.example.project.ui.theme.SemanticColors
import org.example.project.ui.theme.spacing
import org.koin.core.context.GlobalContext

private val COMPACT_GRID_BREAKPOINT = 900.dp
private val WIDE_GRID_BREAKPOINT = 1360.dp
private val WEEK_CARD_MIN_HEIGHT = 228.dp

@Composable
fun PlanningDashboardScreen() {
    val viewModel = GlobalContext.get().get<PlanningDashboardViewModel>()
    val state by viewModel.state.collectAsState()
    val navigateToSection = LocalSectionNavigator.current
    val spacing = MaterialTheme.spacing
    val isAlertDialogOpen = remember { mutableStateOf(false) }
    val missingWeekKeys = remember(state.alerts) { state.alerts.flatMap { it.weekKeys }.distinct() }
    val missingWeekLabels = remember(missingWeekKeys) {
        missingWeekKeys.map { key ->
            val monday = runCatching { java.time.LocalDate.parse(key) }.getOrNull()
            monday?.format(dateFormatter) ?: key
        }
    }
    if (isAlertDialogOpen.value) {
        AlertDialog(
            onDismissRequest = { isAlertDialogOpen.value = false },
            title = { Text("Settimane da pianificare") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Text("Mancano programmi nelle prossime 4 settimane:")
                    missingWeekLabels.forEach { label ->
                        Text("• $label", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { isAlertDialogOpen.value = false }) {
                    Text("Chiudi")
                }
            },
        )
    }

    LaunchedEffect(Unit) { viewModel.onScreenEntered() }

    Column(
        modifier = Modifier.fillMaxSize().padding(spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        FeedbackBanner(
            model = state.notice,
            onDismissRequest = { viewModel.dismissNotice() },
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
        ) {
            val plannedWeeks = state.weeks.count { it.status == WeekPlanningStatus.PIANIFICATA }
            val plannedThroughLabel = state.plannedThrough?.format(dateFormatter) ?: "n/d"
            val missingWeeksCount = missingWeekLabels.size
            Column(
                modifier = Modifier.fillMaxWidth().padding(spacing.lg),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Cruscotto pianificazione", style = MaterialTheme.typography.titleMedium)
                    if (missingWeeksCount > 0) {
                        PlanningAlertPill(
                            missingWeeksCount = missingWeeksCount,
                            onClick = { isAlertDialogOpen.value = true },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    PlanningHorizonOption.entries.forEach { option ->
                        PlanningHorizonChip(
                            selected = state.horizon == option,
                            onClick = { viewModel.setHorizon(option) },
                            label = option.label,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    PlanningSummaryChip(
                        text = "Pianificato fino al $plannedThroughLabel",
                        modifier = Modifier.weight(1f),
                    )
                    PlanningSummaryChip(
                        text = "Settimane pronte $plannedWeeks/${state.weeks.size}",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (state.isLoading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val cardsPerRow = when {
                    maxWidth < COMPACT_GRID_BREAKPOINT -> 1
                    maxWidth < WIDE_GRID_BREAKPOINT -> 2
                    else -> 3
                }
                val weekRows = remember(state.weeks, cardsPerRow) { state.weeks.chunked(cardsPerRow) }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(weekRows, key = { row -> row.joinToString("|") { it.weekStartDate.toString() } }) { weekRow ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(spacing.md),
                            verticalAlignment = Alignment.Top,
                        ) {
                            weekRow.forEach { week ->
                                WeekPlanningCard(
                                    week = week,
                                    onOpenAssignments = {
                                        viewModel.navigateToWeek(week.weekStartDate)
                                        navigateToSection(AppSection.ASSIGNMENTS)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                )
                            }
                            if (weekRow.size < cardsPerRow) {
                                repeat(cardsPerRow - weekRow.size) {
                                    Spacer(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
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
private fun PlanningAlertPill(
    missingWeeksCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val message = if (missingWeeksCount == 1) {
        "1 settimana da pianificare"
    } else {
        "$missingWeeksCount settimane da pianificare"
    }
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .handCursorOnHover(),
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun PlanningHorizonChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor = when {
        isFocused -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier.handCursorOnHover(),
        interactionSource = interactionSource,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        elevation = FilterChipDefaults.filterChipElevation(
            elevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            draggedElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = borderColor,
            selectedBorderColor = borderColor,
            disabledBorderColor = borderColor,
            disabledSelectedBorderColor = borderColor,
            borderWidth = 1.dp,
            selectedBorderWidth = 1.dp,
        ),
    )
}

@Composable
private fun PlanningSummaryChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun WeekPlanningCard(
    week: PlanningWeekStatus,
    onOpenAssignments: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val statusColor = when (week.status) {
        WeekPlanningStatus.DA_ORGANIZZARE -> SemanticColors.grey
        WeekPlanningStatus.PARZIALE -> SemanticColors.amber
        WeekPlanningStatus.PIANIFICATA -> SemanticColors.green
    }
    val statusLabel = when (week.status) {
        WeekPlanningStatus.DA_ORGANIZZARE -> "Da organizzare"
        WeekPlanningStatus.PARZIALE -> "Parziale"
        WeekPlanningStatus.PIANIFICATA -> "Pianificata"
    }
    val completionProgress = if (week.totalSlots == 0) 0f else (week.assignedSlots.toFloat() / week.totalSlots.toFloat())
    val completionPercent = (completionProgress * 100).toInt().coerceIn(0, 100)
    val remainingSlots = (week.totalSlots - week.assignedSlots).coerceAtLeast(0)

    Card(
        modifier = modifier.fillMaxWidth().heightIn(min = WEEK_CARD_MIN_HEIGHT),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        val sunday = sundayOf(week.weekStartDate)
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(statusColor.copy(alpha = 0.72f)),
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(spacing.lg),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(spacing.xs),
                    ) {
                        Text(
                            text = "${week.weekStartDate.format(dateFormatter)} - ${sunday.format(dateFormatter)}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = "Programma settimanale",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(spacing.sm))
                    WeekStatusBadge(
                        statusLabel = statusLabel,
                        statusColor = statusColor,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    WeekMetricTile(
                        title = "Assegnati",
                        value = "${week.assignedSlots}/${week.totalSlots}",
                        modifier = Modifier.weight(1f),
                    )
                    WeekMetricTile(
                        title = "Mancanti",
                        value = remainingSlots.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    WeekMetricTile(
                        title = "Complet.",
                        value = "$completionPercent%",
                        modifier = Modifier.weight(1f),
                    )
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Completamento assegnazioni",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "$completionPercent%",
                            style = MaterialTheme.typography.labelLarge,
                            color = statusColor,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { completionProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = statusColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                    )
                }
                Button(
                    onClick = onOpenAssignments,
                    modifier = Modifier.fillMaxWidth().handCursorOnHover(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text("Apri assegnazioni")
                }
            }
        }
    }
}

@Composable
private fun WeekStatusBadge(
    statusLabel: String,
    statusColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = statusColor.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
            )
        }
    }
}

@Composable
private fun WeekMetricTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

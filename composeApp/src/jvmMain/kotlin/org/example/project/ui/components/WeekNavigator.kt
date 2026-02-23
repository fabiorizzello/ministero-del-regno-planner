package org.example.project.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.example.project.core.application.SharedWeekState
import org.example.project.ui.theme.SemanticColors
import org.example.project.ui.theme.spacing
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Offset added to sortOrder for user-facing part numbers (meeting program convention). */
const val DISPLAY_NUMBER_OFFSET = 3

enum class WeekTimeIndicator { PASSATA, CORRENTE, FUTURA }

enum class WeekCompletionStatus { COMPLETE, PARTIAL, EMPTY, PAST }

private fun computeWeekDistance(monday: LocalDate): Int {
    val thisMonday = SharedWeekState.currentMonday()
    return (ChronoUnit.DAYS.between(thisMonday, monday) / 7).toInt()
}

fun computeWeekIndicator(currentMonday: LocalDate): WeekTimeIndicator {
    return when (computeWeekDistance(currentMonday)) {
        0 -> WeekTimeIndicator.CORRENTE
        in 1..Int.MAX_VALUE -> WeekTimeIndicator.FUTURA
        else -> WeekTimeIndicator.PASSATA
    }
}

fun formatWeekIndicatorLabel(monday: LocalDate): String {
    val d = computeWeekDistance(monday)
    return when {
        d == 0 -> "Corrente ${formatMonthYearLabel(monday.monthValue, monday.year)}"
        d == 1 -> "tra 1 sett"
        d > 1 -> "tra $d sett"
        d == -1 -> "1 sett fa"
        else -> "${-d} sett fa"
    }
}

fun sundayOf(monday: LocalDate): LocalDate = monday.plusDays(6)

@Composable
private fun CompletionDot(status: WeekCompletionStatus) {
    when (status) {
        WeekCompletionStatus.EMPTY -> Box(
            modifier = Modifier
                .size(8.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        )
        else -> Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    when (status) {
                        WeekCompletionStatus.COMPLETE -> SemanticColors.green
                        WeekCompletionStatus.PARTIAL -> SemanticColors.amber
                        WeekCompletionStatus.PAST -> SemanticColors.grey
                        WeekCompletionStatus.EMPTY -> Color.Transparent
                    },
                    CircleShape,
                ),
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun WeekNavigator(
    monday: LocalDate,
    sunday: LocalDate,
    indicator: WeekTimeIndicator,
    enabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    prevWeekStatus: WeekCompletionStatus? = null,
    nextWeekStatus: WeekCompletionStatus? = null,
    onNavigateToCurrentWeek: (() -> Unit)? = null,
) {
    val spacing = MaterialTheme.spacing
    val indicatorColor = when (indicator) {
        WeekTimeIndicator.CORRENTE -> SemanticColors.green
        WeekTimeIndicator.FUTURA -> SemanticColors.blue
        WeekTimeIndicator.PASSATA -> SemanticColors.grey
    }
    val indicatorLabel = formatWeekIndicatorLabel(monday)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                IconButton(onClick = onPrevious, enabled = enabled, modifier = Modifier.handCursorOnHover()) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Settimana precedente")
                }
                if (prevWeekStatus != null) {
                    CompletionDot(prevWeekStatus)
                }
            }
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Settimana ${formatWeekRangeLabel(monday, sunday)}",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(spacing.md))
                AssistChip(
                    onClick = {},
                    label = { Text(indicatorLabel, style = MaterialTheme.typography.labelSmall) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = indicatorColor.copy(alpha = 0.15f),
                        labelColor = indicatorColor,
                    ),
                )
                if (onNavigateToCurrentWeek != null && indicator != WeekTimeIndicator.CORRENTE) {
                    Spacer(Modifier.width(spacing.sm))
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                            positioning = TooltipAnchorPosition.Above,
                        ),
                        tooltip = { PlainTooltip { Text("Vai a oggi") } },
                        state = rememberTooltipState(),
                    ) {
                        IconButton(
                            onClick = onNavigateToCurrentWeek,
                            modifier = Modifier.handCursorOnHover().size(32.dp),
                        ) {
                            Icon(
                                Icons.Filled.Today,
                                contentDescription = "Vai a settimana corrente",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                IconButton(onClick = onNext, enabled = enabled, modifier = Modifier.handCursorOnHover()) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Settimana successiva")
                }
                if (nextWeekStatus != null) {
                    CompletionDot(nextWeekStatus)
                }
            }
        }
    }
}

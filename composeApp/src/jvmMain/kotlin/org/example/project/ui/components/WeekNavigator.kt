package org.example.project.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.example.project.core.application.SharedWeekState
import org.example.project.ui.theme.SemanticColors
import org.example.project.ui.theme.spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Offset added to sortOrder for user-facing part numbers (meeting program convention). */
const val DISPLAY_NUMBER_OFFSET = 3

enum class WeekTimeIndicator { PASSATA, CORRENTE, FUTURA }

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
        d == 0 -> "Corrente"
        d == 1 -> "tra 1 sett"
        d > 1 -> "tra $d sett"
        d == -1 -> "1 sett fa"
        else -> "${-d} sett fa"
    }
}

fun sundayOf(monday: LocalDate): LocalDate = monday.plusDays(6)

internal val dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ITALIAN)

@Composable
fun WeekNavigator(
    monday: LocalDate,
    sunday: LocalDate,
    indicator: WeekTimeIndicator,
    enabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val indicatorColor = when (indicator) {
        WeekTimeIndicator.CORRENTE -> SemanticColors.green
        WeekTimeIndicator.FUTURA -> SemanticColors.blue
        WeekTimeIndicator.PASSATA -> SemanticColors.grey
    }
    val indicatorLabel = formatWeekIndicatorLabel(monday)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious, enabled = enabled, modifier = Modifier.handCursorOnHover()) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Settimana precedente")
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Settimana ${monday.format(dateFormatter)} - ${sunday.format(dateFormatter)}",
                style = MaterialTheme.typography.titleMedium,
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
        }
        IconButton(onClick = onNext, enabled = enabled, modifier = Modifier.handCursorOnHover()) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Settimana successiva")
        }
    }
}

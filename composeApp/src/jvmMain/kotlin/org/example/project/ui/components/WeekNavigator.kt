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
import androidx.compose.ui.graphics.Color
import org.example.project.ui.theme.spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class WeekTimeIndicator { PASSATA, CORRENTE, FUTURA }

fun computeWeekIndicator(currentMonday: LocalDate): WeekTimeIndicator {
    val thisMonday = LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
    return when {
        currentMonday == thisMonday -> WeekTimeIndicator.CORRENTE
        currentMonday.isAfter(thisMonday) -> WeekTimeIndicator.FUTURA
        else -> WeekTimeIndicator.PASSATA
    }
}

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
    val (indicatorLabel, indicatorColor) = when (indicator) {
        WeekTimeIndicator.CORRENTE -> "Corrente" to Color(0xFF4CAF50)
        WeekTimeIndicator.FUTURA -> "Futura" to Color(0xFF2196F3)
        WeekTimeIndicator.PASSATA -> "Passata" to Color(0xFF9E9E9E)
    }
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

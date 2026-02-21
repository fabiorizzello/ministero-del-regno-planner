package org.example.project.ui.planning

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.example.project.feature.planning.application.PlanningWeekStatus
import org.example.project.feature.planning.domain.WeekPlanningStatus
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

@Composable
fun PlanningDashboardScreen() {
    val viewModel = GlobalContext.get().get<PlanningDashboardViewModel>()
    val state by viewModel.state.collectAsState()
    val spacing = MaterialTheme.spacing

    LaunchedEffect(Unit) { viewModel.onScreenEntered() }

    Column(
        modifier = Modifier.fillMaxSize().padding(spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        FeedbackBanner(
            model = state.notice,
            onDismissRequest = { viewModel.dismissNotice() },
        )

        if (state.alerts.isNotEmpty()) {
            val details = state.alerts.firstOrNull()?.weekKeys
                ?.joinToString(" • ") { key ->
                    val monday = runCatching { java.time.LocalDate.parse(key) }.getOrNull()
                    monday?.format(dateFormatter) ?: key
                }
            FeedbackBanner(
                model = FeedbackBannerModel(
                    message = "Attenzione: mancano programmi nelle prossime 4 settimane",
                    kind = FeedbackBannerKind.ERROR,
                    details = details,
                ),
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            val plannedWeeks = state.weeks.count { it.status == WeekPlanningStatus.PIANIFICATA }
            val plannedThroughLabel = state.plannedThrough?.format(dateFormatter) ?: "n/d"
            Column(
                modifier = Modifier.fillMaxWidth().padding(spacing.lg),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Text("Cruscotto pianificazione", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Orizzonte",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    PlanningHorizonOption.entries.forEach { option ->
                        FilterChip(
                            selected = state.horizon == option,
                            onClick = { viewModel.setHorizon(option) },
                            label = { Text(option.label) },
                            modifier = Modifier.handCursorOnHover(),
                        )
                    }
                }
                Text(
                    "Pianificato fino al $plannedThroughLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Settimane complete: $plannedWeeks/${state.weeks.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.isLoading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val cardsPerRow = if (maxWidth < COMPACT_GRID_BREAKPOINT) 1 else 2
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
private fun WeekPlanningCard(
    week: PlanningWeekStatus,
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

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        val sunday = sundayOf(week.weekStartDate)
        Column(
            modifier = Modifier.fillMaxWidth().padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${week.weekStartDate.format(dateFormatter)} - ${sunday.format(dateFormatter)}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusColor),
                    )
                    Spacer(Modifier.width(spacing.sm))
                    Text(
                        text = statusLabel,
                        color = statusColor,
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${week.assignedSlots}/${week.totalSlots} slot assegnati",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

package org.example.project.ui.planning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
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

private const val MIN_HORIZON_WEEKS = 4
private const val MAX_HORIZON_WEEKS = 16

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
                    if (monday != null) {
                        "${monday.format(dateFormatter)}"
                    } else {
                        key
                    }
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
            Column(
                modifier = Modifier.fillMaxWidth().padding(spacing.lg),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Text("Orizzonte pianificazione", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("${state.horizonWeeks} settimane")
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        OutlinedButton(
                            onClick = { viewModel.setHorizonWeeks(state.horizonWeeks - 1) },
                            enabled = state.horizonWeeks > MIN_HORIZON_WEEKS,
                            modifier = Modifier.handCursorOnHover(),
                        ) {
                            Text("-1")
                        }
                        OutlinedButton(
                            onClick = { viewModel.setHorizonWeeks(state.horizonWeeks + 1) },
                            enabled = state.horizonWeeks < MAX_HORIZON_WEEKS,
                            modifier = Modifier.handCursorOnHover(),
                        ) {
                            Text("+1")
                        }
                    }
                }
                Slider(
                    value = state.horizonWeeks.toFloat(),
                    onValueChange = { viewModel.setHorizonWeeks(it.toInt()) },
                    valueRange = MIN_HORIZON_WEEKS.toFloat()..MAX_HORIZON_WEEKS.toFloat(),
                    steps = MAX_HORIZON_WEEKS - MIN_HORIZON_WEEKS - 1,
                )
                val plannedThroughLabel = state.plannedThrough?.format(dateFormatter) ?: "n/d"
                Text(
                    text = "Pianificato fino al $plannedThroughLabel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.isLoading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                items(state.weeks, key = { it.weekStartDate.toString() }) { week ->
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
                    val sunday = sundayOf(week.weekStartDate)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(spacing.lg),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                                Text(
                                    text = "${week.weekStartDate.format(dateFormatter)} - ${sunday.format(dateFormatter)}",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = "${week.assignedSlots}/${week.totalSlots} slot assegnati",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
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
                    }
                }
            }
        }
    }
}

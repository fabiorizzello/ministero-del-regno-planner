package org.example.project.ui.weeklyparts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.example.project.feature.weeklyparts.domain.WeeklyPart
import org.example.project.ui.components.FeedbackBanner
import org.example.project.ui.components.StandardTableHeader
import org.example.project.ui.components.StandardTableViewport
import org.example.project.ui.components.TableColumnSpec
import org.example.project.ui.components.handCursorOnHover
import org.example.project.ui.components.standardTableCell
import org.koin.core.context.GlobalContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ITALIAN)

private val columns = listOf(
    TableColumnSpec("N.", 0.5f),
    TableColumnSpec("Tipo", 3f),
    TableColumnSpec("Persone", 0.8f),
    TableColumnSpec("Regola", 0.8f),
    TableColumnSpec("", 0.5f),
)
@Composable
fun WeeklyPartsScreen() {
    val viewModel = remember { GlobalContext.get().get<WeeklyPartsViewModel>() }
    val state by viewModel.state.collectAsState()

    // Overwrite confirmation dialog
    if (state.weeksNeedingConfirmation.isNotEmpty()) {
        OverwriteConfirmDialog(
            weeks = state.weeksNeedingConfirmation.map { LocalDate.parse(it.weekStartDate) },
            onConfirmAll = { viewModel.confirmOverwrite() },
            onSkip = { viewModel.dismissConfirmation() },
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Top bar: sync button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            OutlinedButton(
                onClick = { viewModel.syncRemoteData() },
                enabled = !state.isImporting,
                modifier = Modifier.handCursorOnHover(),
            ) {
                if (state.isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(16.dp).width(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Aggiorna dati")
            }
        }

        // Feedback banner
        FeedbackBanner(
            model = state.notice,
            onDismissRequest = { viewModel.dismissNotice() },
        )

        // Week navigator
        WeekNavigator(
            monday = state.currentMonday,
            sunday = state.sundayDate,
            indicator = state.weekIndicator,
            enabled = !state.isLoading,
            onPrevious = { viewModel.navigateToPreviousWeek() },
            onNext = { viewModel.navigateToNextWeek() },
        )

        // Content
        if (state.isLoading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.weekPlan == null) {
            // Week doesn't exist
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Settimana non configurata", style = MaterialTheme.typography.bodyLarge)
                    Button(
                        onClick = { viewModel.createWeek() },
                        enabled = !state.isImporting,
                        modifier = Modifier.handCursorOnHover(),
                    ) {
                        Text("Crea settimana")
                    }
                }
            }
        } else {
            // Parts table
            val parts = state.weekPlan!!.parts
            StandardTableHeader(columns = columns)
            StandardTableViewport {
                LazyColumn {
                    itemsIndexed(parts, key = { _, part -> part.id.value }) { index, part ->
                        PartRow(
                            part = part,
                            displayNumber = part.sortOrder + 3,
                            isFirst = index == 0,
                            isLast = index == parts.lastIndex,
                            enabled = !state.isImporting,
                            onRemove = { viewModel.removePart(part.id) },
                            onMoveUp = { if (index > 0) viewModel.movePart(index, index - 1) },
                            onMoveDown = { if (index < parts.lastIndex) viewModel.movePart(index, index + 1) },
                        )
                    }
                }
            }

            // Add part button
            if (state.partTypes.isNotEmpty()) {
                AddPartDropdown(
                    partTypes = state.partTypes,
                    onSelect = { viewModel.addPart(it.id) },
                    enabled = !state.isImporting,
                )
            }
        }
    }
}

@Composable
private fun WeekNavigator(
    monday: LocalDate,
    sunday: LocalDate,
    indicator: WeekTimeIndicator,
    enabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
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
            Spacer(Modifier.width(8.dp))
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

@Composable
private fun PartRow(
    part: WeeklyPart,
    displayNumber: Int,
    isFirst: Boolean,
    isLast: Boolean,
    enabled: Boolean,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val lineColor = MaterialTheme.colorScheme.outline
    Row(modifier = Modifier.fillMaxWidth()) {
        // N.
        Box(Modifier.weight(columns[0].weight).standardTableCell(lineColor)) {
            Text("$displayNumber", style = MaterialTheme.typography.bodyMedium)
        }
        // Tipo
        Box(Modifier.weight(columns[1].weight).standardTableCell(lineColor)) {
            Text(part.partType.label, style = MaterialTheme.typography.bodyMedium)
        }
        // Persone
        Box(Modifier.weight(columns[2].weight).standardTableCell(lineColor)) {
            Text("${part.partType.peopleCount}", style = MaterialTheme.typography.bodyMedium)
        }
        // Regola
        Box(Modifier.weight(columns[3].weight).standardTableCell(lineColor)) {
            Text(part.partType.sexRule.name, style = MaterialTheme.typography.bodyMedium)
        }
        // Actions
        Box(Modifier.weight(columns[4].weight).standardTableCell(lineColor)) {
            if (!part.partType.fixed) {
                Row {
                    if (!isFirst) {
                        IconButton(onClick = onMoveUp, enabled = enabled, modifier = Modifier.handCursorOnHover()) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Sposta su", modifier = Modifier.height(16.dp))
                        }
                    }
                    if (!isLast) {
                        IconButton(onClick = onMoveDown, enabled = enabled, modifier = Modifier.handCursorOnHover()) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Sposta giu'", modifier = Modifier.height(16.dp))
                        }
                    }
                    IconButton(onClick = onRemove, enabled = enabled, modifier = Modifier.handCursorOnHover()) {
                        Icon(Icons.Filled.Close, contentDescription = "Rimuovi parte", modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AddPartDropdown(
    partTypes: List<org.example.project.feature.weeklyparts.domain.PartType>,
    onSelect: (org.example.project.feature.weeklyparts.domain.PartType) -> Unit,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.handCursorOnHover(),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Aggiungi parte")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            partTypes.filter { !it.fixed }.forEach { partType ->
                DropdownMenuItem(
                    text = { Text(partType.label) },
                    onClick = {
                        expanded = false
                        onSelect(partType)
                    },
                )
            }
        }
    }
}

@Composable
private fun OverwriteConfirmDialog(
    weeks: List<LocalDate>,
    onConfirmAll: () -> Unit,
    onSkip: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text("Settimane gia' presenti") },
        text = {
            Column {
                Text("Le seguenti settimane esistono gia'. Vuoi sovrascriverle?")
                Spacer(Modifier.height(8.dp))
                weeks.forEach { date ->
                    Text(
                        text = "- ${date.format(dateFormatter)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirmAll) {
                Text("Sovrascrivi tutte")
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) {
                Text("Ignora")
            }
        },
    )
}

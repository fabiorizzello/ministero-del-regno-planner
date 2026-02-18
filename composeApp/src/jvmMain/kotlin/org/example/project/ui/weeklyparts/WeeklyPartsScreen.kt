package org.example.project.ui.weeklyparts

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.WeeklyPart
import org.example.project.ui.components.FeedbackBanner
import org.example.project.ui.components.handCursorOnHover
import org.example.project.ui.theme.spacing
import org.koin.core.context.GlobalContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ITALIAN)

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

    val spacing = MaterialTheme.spacing
    Column(
        modifier = Modifier.fillMaxSize().padding(spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
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
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(spacing.md))
                }
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(spacing.xs))
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
            EmptyWeekContent(
                isImporting = state.isImporting,
                onCreate = { viewModel.createWeek() },
            )
        } else {
            PartsCard(
                parts = state.weekPlan!!.parts,
                isImporting = state.isImporting,
                partTypes = state.partTypes,
                onMove = { from, to -> viewModel.movePart(from, to) },
                onRemove = { viewModel.removePart(it) },
                onAddPart = { viewModel.addPart(it) },
            )
        }
    }
}

@Composable
private fun EmptyWeekContent(isImporting: Boolean, onCreate: () -> Unit) {
    val spacing = MaterialTheme.spacing
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = spacing.xxl),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text("Settimana non configurata", style = MaterialTheme.typography.bodyLarge)
            Button(
                onClick = onCreate,
                enabled = !isImporting,
                modifier = Modifier.handCursorOnHover(),
            ) {
                Text("Crea settimana")
            }
        }
    }
}

@Composable
private fun PartsCard(
    parts: List<WeeklyPart>,
    isImporting: Boolean,
    partTypes: List<PartType>,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onRemove: (org.example.project.feature.weeklyparts.domain.WeeklyPartId) -> Unit,
    onAddPart: (org.example.project.feature.weeklyparts.domain.PartTypeId) -> Unit,
) {
    val lazyListState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onMove(from.index, to.index)
    }

    val spacing = MaterialTheme.spacing
    Card(
        shape = RoundedCornerShape(spacing.cardRadius),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            // Column headers
            PartsHeader()

            // Parts list
            LazyColumn(state = lazyListState) {
                items(parts, key = { it.id.value }) { part ->
                    val index = parts.indexOf(part)
                    val isFixed = part.partType.fixed
                    val zebraColor = if (index % 2 == 0) {
                        Color.Transparent
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    }

                    if (isFixed) {
                        FixedPartRow(
                            part = part,
                            displayNumber = part.sortOrder + 3,
                            backgroundColor = zebraColor,
                        )
                    } else {
                        ReorderableItem(
                            reorderableLazyListState,
                            key = part.id.value,
                            enabled = !isImporting,
                        ) { isDragging ->
                            val elevation by animateDpAsState(
                                targetValue = if (isDragging) 4.dp else 0.dp,
                            )
                            DraggablePartRow(
                                part = part,
                                displayNumber = part.sortOrder + 3,
                                backgroundColor = zebraColor,
                                elevation = elevation,
                                enabled = !isImporting,
                                onRemove = { onRemove(part.id) },
                                dragModifier = Modifier.draggableHandle(),
                            )
                        }
                    }
                }
            }
        }
    }

    // Add part button (outside card)
    if (partTypes.isNotEmpty()) {
        Spacer(Modifier.height(spacing.md))
        AddPartDropdown(
            partTypes = partTypes,
            onSelect = { onAddPart(it.id) },
            enabled = !isImporting,
        )
    }
}

@Composable
private fun PartsHeader() {
    val spacing = MaterialTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(40.dp)) // drag handle space
        Text("N.", modifier = Modifier.width(36.dp), style = MaterialTheme.typography.labelMedium)
        Text("Tipo", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
        Text("Persone", modifier = Modifier.width(64.dp), style = MaterialTheme.typography.labelMedium)
        Text("Regola", modifier = Modifier.width(56.dp), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(40.dp)) // remove button space
    }
}

@Composable
private fun DraggablePartRow(
    part: WeeklyPart,
    displayNumber: Int,
    backgroundColor: Color,
    elevation: androidx.compose.ui.unit.Dp,
    enabled: Boolean,
    onRemove: () -> Unit,
    dragModifier: Modifier,
) {
    val spacing = MaterialTheme.spacing
    Surface(shadowElevation = elevation) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .padding(horizontal = spacing.lg, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Drag handle
            Icon(
                Icons.Rounded.DragHandle,
                contentDescription = "Trascina per riordinare",
                modifier = dragModifier
                    .handCursorOnHover()
                    .size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Spacer(Modifier.width(spacing.md))

            // Number
            Text(
                "$displayNumber",
                modifier = Modifier.width(36.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            // Type label
            Text(
                part.partType.label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            // People count
            Text(
                "${part.partType.peopleCount}",
                modifier = Modifier.width(64.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            // Sex rule
            Text(
                part.partType.sexRule.name,
                modifier = Modifier.width(56.dp),
                style = MaterialTheme.typography.bodyMedium,
            )

            // Remove button
            IconButton(
                onClick = onRemove,
                enabled = enabled,
                modifier = Modifier.size(32.dp).handCursorOnHover(),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Rimuovi parte",
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun FixedPartRow(
    part: WeeklyPart,
    displayNumber: Int,
    backgroundColor: Color,
) {
    val spacing = MaterialTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Empty space where drag handle would be
        Spacer(Modifier.width(28.dp))

        Text(
            "$displayNumber",
            modifier = Modifier.width(36.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            part.partType.label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "${part.partType.peopleCount}",
            modifier = Modifier.width(64.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            part.partType.sexRule.name,
            modifier = Modifier.width(56.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Empty space where remove button would be
        Spacer(Modifier.width(40.dp))
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

@Composable
private fun AddPartDropdown(
    partTypes: List<org.example.project.feature.weeklyparts.domain.PartType>,
    onSelect: (org.example.project.feature.weeklyparts.domain.PartType) -> Unit,
    enabled: Boolean = true,
) {
    val spacing = MaterialTheme.spacing
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.handCursorOnHover(),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(spacing.xs))
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
                Spacer(Modifier.height(MaterialTheme.spacing.md))
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

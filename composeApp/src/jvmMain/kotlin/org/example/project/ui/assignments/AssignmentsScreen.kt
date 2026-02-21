package org.example.project.ui.assignments

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.example.project.ui.AppSection
import org.example.project.ui.LocalSectionNavigator
import org.example.project.ui.components.FeedbackBanner
import org.example.project.ui.components.DISPLAY_NUMBER_OFFSET
import org.example.project.ui.components.WeekNavigator
import org.example.project.ui.components.WeekTimeIndicator
import org.example.project.ui.components.handCursorOnHover
import org.example.project.ui.theme.SemanticColors
import org.example.project.ui.theme.spacing
import org.koin.core.context.GlobalContext

@Composable
fun AssignmentsScreen() {
    val viewModel = remember { GlobalContext.get().get<AssignmentsViewModel>() }
    val state by viewModel.state.collectAsState()
    val navigateToSection = LocalSectionNavigator.current
    val spacing = MaterialTheme.spacing

    LaunchedEffect(Unit) { viewModel.onScreenEntered() }

    // Person picker dialog
    if (state.isPickerOpen) {
        val part = state.weekPlan?.parts?.find { it.id == state.pickerWeeklyPartId }
        if (part != null) {
            val slotLabel = if (part.partType.peopleCount > 1) {
                if (state.pickerSlot == 1) "Proclamatore" else "Assistente"
            } else null
            PersonPickerDialog(
                partLabel = part.partType.label,
                slotLabel = slotLabel,
                searchTerm = state.pickerSearchTerm,
                sortGlobal = state.pickerSortGlobal,
                suggestions = state.pickerSuggestions,
                isLoading = state.isPickerLoading,
                isAssigning = state.isAssigning,
                onSearchChange = { viewModel.setPickerSearchTerm(it) },
                onToggleSort = { viewModel.togglePickerSort() },
                onAssign = { viewModel.confirmAssignment(it) },
                onDismiss = { viewModel.closePersonPicker() },
            )
        }
    }

    if (state.isOutputDialogOpen) {
        val parts = state.weekPlan?.parts ?: emptyList()
        OutputPartsDialog(
            parts = parts,
            selectedPartIds = state.outputSelectedPartIds,
            onTogglePart = { viewModel.toggleOutputPart(it.id) },
            onSelectAll = { viewModel.selectAllOutputParts() },
            onClearAll = { viewModel.clearOutputPartsSelection() },
            onDismiss = { viewModel.closeOutputDialog() },
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
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
            prevWeekStatus = state.prevWeekStatus,
            nextWeekStatus = state.nextWeekStatus,
            onNavigateToCurrentWeek = { viewModel.navigateToCurrentWeek() },
        )

        // Status bar: navigation + completion count
        if (state.weekPlan != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { navigateToSection(AppSection.WEEKLY_PARTS) },
                    modifier = Modifier.handCursorOnHover(),
                ) {
                    Text("Vai allo schema")
                }
                val progressColor = when {
                    state.totalSlotCount == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
                    state.assignedSlotCount == 0 -> SemanticColors.grey
                    state.assignedSlotCount < state.totalSlotCount -> SemanticColors.amber
                    else -> SemanticColors.green
                }
                Text(
                    "${state.assignedSlotCount}/${state.totalSlotCount} slot assegnati",
                    style = MaterialTheme.typography.bodyMedium,
                    color = progressColor,
                )
            }
        }

        if (state.weekPlan != null) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = { viewModel.openOutputDialog() },
                        modifier = Modifier.handCursorOnHover(),
                    ) {
                        Text("Seleziona parti")
                    }
                    OutlinedButton(
                        onClick = { viewModel.generatePdf() },
                        enabled = !state.isGeneratingPdf,
                        modifier = Modifier.handCursorOnHover(),
                    ) {
                        Text(if (state.isGeneratingPdf) "PDF in corso..." else "Genera PDF")
                    }
                    OutlinedButton(
                        onClick = { viewModel.generateImages() },
                        enabled = !state.isGeneratingImages,
                        modifier = Modifier.handCursorOnHover(),
                    ) {
                        Text(if (state.isGeneratingImages) "Immagini in corso..." else "Genera immagini")
                    }
                }
                if (!state.outputStatus.isNullOrBlank()) {
                    Text(
                        text = state.outputStatus ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Content
        if (state.isLoading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.weekPlan == null) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = spacing.xxl),
                contentAlignment = Alignment.Center,
            ) {
                if (state.weekIndicator == WeekTimeIndicator.PASSATA) {
                    Text("Nessuno schema per questa settimana", style = MaterialTheme.typography.bodyLarge)
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(spacing.md),
                    ) {
                        Text("Settimana non configurata", style = MaterialTheme.typography.bodyLarge)
                        OutlinedButton(
                            onClick = { navigateToSection(AppSection.WEEKLY_PARTS) },
                            modifier = Modifier.handCursorOnHover(),
                        ) {
                            Text("Vai allo schema per crearla")
                        }
                    }
                }
            }
        } else {
            // Parts grid (1|2, 3|4, ...)
            val readOnly = state.weekIndicator == WeekTimeIndicator.PASSATA
            val parts = state.weekPlan?.parts ?: emptyList()
            val partRows = remember(parts) { parts.chunked(2) }
            val assignmentsByPart = remember(state.assignments) {
                state.assignments.groupBy { it.weeklyPartId }
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                items(partRows, key = { row -> row.joinToString("|") { it.id.value } }) { rowParts ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(spacing.lg),
                        verticalAlignment = Alignment.Top,
                    ) {
                        rowParts.forEach { part ->
                            val partAssignments = assignmentsByPart[part.id] ?: emptyList()
                            PartAssignmentCard(
                                part = part,
                                assignments = partAssignments,
                                displayNumber = part.sortOrder + DISPLAY_NUMBER_OFFSET,
                                readOnly = readOnly,
                                onAssignSlot = { slot -> viewModel.openPersonPicker(part.id, slot) },
                                onRemoveAssignment = { viewModel.removeAssignment(it) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )
                        }
                        if (rowParts.size == 1) {
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

package org.example.project.ui.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import org.example.project.ui.components.FeedbackBanner
import org.example.project.ui.components.formatMonthYearLabel
import org.example.project.ui.components.formatWeekRangeLabel
import org.example.project.ui.components.handCursorOnHover
import org.example.project.ui.theme.spacing
import org.koin.core.context.GlobalContext
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProgramWorkspaceScreen() {
    val viewModel = remember { GlobalContext.get().get<ProgramWorkspaceViewModel>() }
    val state by viewModel.state.collectAsState()
    val spacing = MaterialTheme.spacing
    val weekListState = rememberLazyListState()

    LaunchedEffect(Unit) { viewModel.onScreenEntered() }

    if (state.isPickerOpen) {
        val pickedPart = state.selectedProgramWeeks.firstNotNullOfOrNull { week ->
            week.parts.find { it.id == state.pickerWeeklyPartId }
        }
        if (pickedPart != null) {
            val slotLabel = if (pickedPart.partType.peopleCount > 1) {
                if (state.pickerSlot == 1) "Proclamatore" else "Assistente"
            } else {
                null
            }
            PersonPickerDialog(
                partLabel = pickedPart.partType.label,
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

    if (state.isPartEditorOpen) {
        val editingWeek = state.selectedProgramWeeks.firstOrNull { it.id.value == state.partEditorWeekId }
        if (editingWeek != null) {
            PartEditorDialog(
                weekLabel = formatWeekRangeLabel(editingWeek.weekStartDate, editingWeek.weekStartDate.plusDays(6)),
                parts = state.partEditorParts,
                availablePartTypes = state.editablePartTypes,
                isSaving = state.isSavingPartEditor,
                onAddPart = { viewModel.addPartToEditor(it.id) },
                onMovePart = { fromIndex, toIndex -> viewModel.movePartInEditor(fromIndex, toIndex) },
                onRemovePart = { viewModel.removePartFromEditor(it.id) },
                onSave = { viewModel.savePartEditor() },
                onDismiss = { viewModel.dismissPartEditor() },
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        FeedbackBanner(
            model = state.notice,
            onDismissRequest = { viewModel.dismissNotice() },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { viewModel.autoAssignSelectedProgram() },
                enabled = state.selectedProgramId != null && !state.isAutoAssigning,
                modifier = Modifier.handCursorOnHover(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Autoassegna programma", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(spacing.xs))
                Text(if (state.isAutoAssigning) "Autoassegnazione..." else "Autoassegna programma")
            }
            if (state.canDeleteSelectedProgram) {
                OutlinedButton(
                    onClick = { viewModel.deleteSelectedProgram() },
                    enabled = !state.isDeletingSelectedProgram,
                    modifier = Modifier.handCursorOnHover(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.75f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    ),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "Elimina", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(spacing.xs))
                    Text(if (state.isDeletingSelectedProgram) "Eliminazione..." else "Elimina")
                }
            }
            FilledTonalButton(
                onClick = { viewModel.printSelectedProgram() },
                enabled = state.selectedProgramId != null && !state.isPrintingProgram,
                modifier = Modifier.handCursorOnHover(),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ),
            ) {
                Icon(Icons.Filled.Print, contentDescription = "Stampa programma", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(spacing.xs))
                Text(if (state.isPrintingProgram) "Stampa..." else "Stampa programma")
            }
            OutlinedButton(
                onClick = { viewModel.requestClearAssignments() },
                enabled = state.selectedProgramId != null && !state.isClearingAssignments,
                modifier = Modifier.handCursorOnHover(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                ),
            ) {
                Icon(Icons.Filled.ClearAll, contentDescription = "Svuota assegnazioni", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(spacing.xs))
                Text(if (state.isClearingAssignments) "Svuotamento..." else "Svuota assegnazioni")
            }
        }

        ProgramHeader(
            state = state,
            onSelectProgram = { viewModel.selectProgram(it) },
            onCreateNextProgram = { viewModel.createNextProgram() },
            onRefreshSchemas = { viewModel.refreshSchemasAndProgram() },
        )

        if (state.autoAssignUnresolved.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(spacing.md),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    Text("Slot non assegnati", style = MaterialTheme.typography.titleSmall)
                    state.autoAssignUnresolved.forEach { unresolved ->
                        val weekLabel = formatWeekRangeLabel(unresolved.weekStartDate, unresolved.weekStartDate.plusDays(6))
                        Text(
                            "• $weekLabel | ${unresolved.partLabel} slot ${unresolved.slot}: ${unresolved.reason}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        state.clearAssignmentsConfirm?.let { count ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissClearAssignments() },
                title = { Text("Svuota assegnazioni") },
                text = {
                    Text("Verranno rimosse $count assegnazioni dalle settimane correnti e successive. Continuare?")
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.confirmClearAssignments() },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Svuota") }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissClearAssignments() }) { Text("Annulla") }
                },
            )
        }

        state.clearWeekAssignmentsConfirm?.let { (weekId, count) ->
            val week = state.selectedProgramWeeks.firstOrNull { it.id.value == weekId }
            if (week != null) {
                val weekLabel = formatWeekRangeLabel(week.weekStartDate, week.weekStartDate.plusDays(6))
                AlertDialog(
                    onDismissRequest = { viewModel.dismissClearWeekAssignments() },
                    title = { Text("Rimuovi assegnazioni settimana") },
                    text = {
                        Text("Verranno rimosse $count assegnazioni dalla settimana $weekLabel. Continuare?")
                    },
                    confirmButton = {
                        TextButton(
                            onClick = { viewModel.confirmClearWeekAssignments() },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.handCursorOnHover(),
                        ) { Text("Rimuovi") }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { viewModel.dismissClearWeekAssignments() },
                            modifier = Modifier.handCursorOnHover(),
                        ) { Text("Annulla") }
                    },
                )
            }
        }

        state.schemaRefreshPreview?.let { preview ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissSchemaRefresh() },
                title = { Text("Conferma aggiornamento schemi") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Text("Settimane da aggiornare: ${preview.weeksUpdated}")
                        Text("Assegnazioni preservate: ${preview.assignmentsPreserved}")
                        if (preview.assignmentsRemoved > 0) {
                            Text(
                                "Assegnazioni da rimuovere: ${preview.assignmentsRemoved}",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmSchemaRefresh() }) { Text("Aggiorna") }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissSchemaRefresh() }) { Text("Annulla") }
                },
            )
        }

        if (state.isLoading) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
        } else {
            val currentMonday = remember(state.today) {
                state.today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            }
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = weekListState,
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = 12.dp),
                ) {
                    if (state.selectedProgramWeeks.isEmpty()) {
                        item(key = "empty-weeks") {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)),
                            ) {
                                Text(
                                    "Nessuna settimana disponibile nel programma selezionato",
                                    modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        state.selectedProgramWeeks.forEach { week ->
                            val weekKey = week.id.value
                            val weekIsCurrent = week.weekStartDate == currentMonday
                            val weekIsPast = week.weekStartDate < state.today
                            stickyHeader(key = "week-header-$weekKey") {
                                ProgramWeekStickyHeader(
                                    week = week,
                                    isCurrent = weekIsCurrent,
                                    isPast = weekIsPast,
                                )
                            }
                            item(key = "week-card-$weekKey") {
                                ProgramWeekCard(
                                    week = week,
                                    isCurrent = weekIsCurrent,
                                    today = state.today,
                                    assignments = state.selectedProgramAssignments[week.id.value] ?: emptyList(),
                                    onReactivate = { viewModel.reactivateWeek(week) },
                                    onOpenPartEditor = { viewModel.openPartEditor(week) },
                                    onRequestClearWeekAssignments = { viewModel.requestClearWeekAssignments(week.id.value) },
                                    onAssignSlot = { partId, slot ->
                                        viewModel.openPersonPicker(week.weekStartDate, partId, slot)
                                    },
                                    onRemoveAssignment = { viewModel.removeAssignment(it) },
                                )
                            }
                        }
                    }
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(weekListState),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

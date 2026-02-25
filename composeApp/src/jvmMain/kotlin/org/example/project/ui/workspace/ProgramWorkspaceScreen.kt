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
    // New focused ViewModels
    val lifecycleVM = remember { GlobalContext.get().get<ProgramLifecycleViewModel>() }
    val schemaVM = remember { GlobalContext.get().get<SchemaManagementViewModel>() }
    val assignmentVM = remember { GlobalContext.get().get<AssignmentManagementViewModel>() }
    val personPickerVM = remember { GlobalContext.get().get<PersonPickerViewModel>() }
    val partEditorVM = remember { GlobalContext.get().get<PartEditorViewModel>() }

    val lifecycleState by lifecycleVM.state.collectAsState()
    val schemaState by schemaVM.state.collectAsState()
    val assignmentState by assignmentVM.uiState.collectAsState()
    val personPickerState by personPickerVM.state.collectAsState()
    val partEditorState by partEditorVM.state.collectAsState()

    val spacing = MaterialTheme.spacing
    val weekListState = rememberLazyListState()

    LaunchedEffect(Unit) {
        lifecycleVM.onScreenEntered()
        assignmentVM.onScreenEntered()
    }

    // Keep schema VM in sync with lifecycle VM
    LaunchedEffect(lifecycleState.selectedProgramId, lifecycleState.futureProgram) {
        schemaVM.updateSelection(lifecycleState.selectedProgramId, lifecycleState.futureProgram)
    }

    // Reload callback for operations that modify data
    val reloadData = {
        lifecycleVM.loadProgramsAndWeeks()
    }

    if (personPickerState.isPickerOpen) {
        val pickedPart = lifecycleState.selectedProgramWeeks.firstNotNullOfOrNull { week ->
            week.parts.find { it.id == personPickerState.pickerWeeklyPartId }
        }
        if (pickedPart != null) {
            val slotLabel = if (pickedPart.partType.peopleCount > 1) {
                if (personPickerState.pickerSlot == 1) "Proclamatore" else "Assistente"
            } else {
                null
            }
            PersonPickerDialog(
                partLabel = pickedPart.partType.label,
                slotLabel = slotLabel,
                searchTerm = personPickerState.pickerSearchTerm,
                sortGlobal = personPickerState.pickerSortGlobal,
                suggestions = personPickerState.pickerSuggestions,
                isLoading = personPickerState.isPickerLoading,
                isAssigning = personPickerState.isAssigning,
                onSearchChange = { personPickerVM.setPickerSearchTerm(it) },
                onToggleSort = { personPickerVM.togglePickerSort() },
                onAssign = { personPickerVM.confirmAssignment(it, onSuccess = reloadData) },
                onDismiss = { personPickerVM.closePersonPicker() },
            )
        }
    }

    if (partEditorState.isPartEditorOpen) {
        val editingWeek = lifecycleState.selectedProgramWeeks.firstOrNull { it.id.value == partEditorState.partEditorWeekId }
        if (editingWeek != null) {
            PartEditorDialog(
                weekLabel = formatWeekRangeLabel(editingWeek.weekStartDate, editingWeek.weekStartDate.plusDays(6)),
                parts = partEditorState.partEditorParts,
                availablePartTypes = partEditorState.editablePartTypes,
                isSaving = partEditorState.isSavingPartEditor,
                onAddPart = { partEditorVM.addPartToEditor(it.id) },
                onMovePart = { fromIndex, toIndex -> partEditorVM.movePartInEditor(fromIndex, toIndex) },
                onRemovePart = { partEditorVM.removePartFromEditor(it.id) },
                onSave = { partEditorVM.savePartEditor(onSuccess = reloadData) },
                onDismiss = { partEditorVM.dismissPartEditor() },
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        // Show notice from any ViewModel (first non-null wins)
        val currentNotice = lifecycleState.notice ?: schemaState.notice ?: assignmentState.notice ?: personPickerState.notice ?: partEditorState.notice
        FeedbackBanner(
            model = currentNotice,
            onDismissRequest = {
                lifecycleVM.dismissNotice()
                schemaVM.dismissNotice()
                assignmentVM.dismissNotice()
                personPickerVM.dismissNotice()
                partEditorVM.dismissNotice()
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = {
                    lifecycleState.selectedProgramId?.let { programId ->
                        assignmentVM.autoAssignSelectedProgram(programId, lifecycleState.today, onSuccess = reloadData)
                    }
                },
                enabled = lifecycleState.selectedProgramId != null && !assignmentState.isAutoAssigning,
                modifier = Modifier.handCursorOnHover(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(spacing.xs))
                Text(if (assignmentState.isAutoAssigning) "Autoassegnazione..." else "Autoassegna programma")
            }
            if (lifecycleState.canDeleteSelectedProgram) {
                OutlinedButton(
                    onClick = { lifecycleVM.deleteSelectedProgram() },
                    enabled = !lifecycleState.isDeletingSelectedProgram,
                    modifier = Modifier.handCursorOnHover(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.75f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    ),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(spacing.xs))
                    Text(if (lifecycleState.isDeletingSelectedProgram) "Eliminazione..." else "Elimina")
                }
            }
            FilledTonalButton(
                onClick = {
                    lifecycleState.selectedProgramId?.let { programId ->
                        assignmentVM.printSelectedProgram(programId)
                    }
                },
                enabled = lifecycleState.selectedProgramId != null && !assignmentState.isPrintingProgram,
                modifier = Modifier.handCursorOnHover(),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ),
            ) {
                Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(spacing.xs))
                Text(if (assignmentState.isPrintingProgram) "Stampa..." else "Stampa programma")
            }
            OutlinedButton(
                onClick = {
                    lifecycleState.selectedProgramId?.let { programId ->
                        assignmentVM.requestClearAssignments(programId, lifecycleState.today)
                    }
                },
                enabled = lifecycleState.selectedProgramId != null && !assignmentState.isClearingAssignments,
                modifier = Modifier.handCursorOnHover(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                ),
            ) {
                Icon(Icons.Filled.ClearAll, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(spacing.xs))
                Text(if (assignmentState.isClearingAssignments) "Svuotamento..." else "Svuota assegnazioni")
            }
        }

        ProgramHeader(
            currentProgram = lifecycleState.currentProgram,
            futureProgram = lifecycleState.futureProgram,
            selectedProgramId = lifecycleState.selectedProgramId,
            hasPrograms = lifecycleState.hasPrograms,
            canCreateProgram = lifecycleState.canCreateProgram,
            isCreatingProgram = lifecycleState.isCreatingProgram,
            isRefreshingSchemas = schemaState.isRefreshingSchemas,
            futureNeedsSchemaRefresh = schemaState.futureNeedsSchemaRefresh,
            onSelectProgram = { lifecycleVM.selectProgram(it) },
            onCreateNextProgram = { lifecycleVM.createNextProgram() },
            onRefreshSchemas = { schemaVM.refreshSchemasAndProgram(onProgramRefreshComplete = reloadData) },
        )

        if (assignmentState.autoAssignUnresolved.isNotEmpty()) {
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
                    assignmentState.autoAssignUnresolved.forEach { unresolved ->
                        val weekLabel = formatWeekRangeLabel(unresolved.weekStartDate, unresolved.weekStartDate.plusDays(6))
                        Text(
                            "• $weekLabel | ${unresolved.partLabel} slot ${unresolved.slot}: ${unresolved.reason}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        assignmentState.clearAssignmentsConfirm?.let { count ->
            AlertDialog(
                onDismissRequest = { assignmentVM.dismissClearAssignments() },
                title = { Text("Svuota assegnazioni") },
                text = {
                    Text("Verranno rimosse $count assegnazioni dalle settimane correnti e successive. Continuare?")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            lifecycleState.selectedProgramId?.let { programId ->
                                assignmentVM.confirmClearAssignments(programId, lifecycleState.today, onSuccess = reloadData)
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Svuota") }
                },
                dismissButton = {
                    TextButton(onClick = { assignmentVM.dismissClearAssignments() }) { Text("Annulla") }
                },
            )
        }

        assignmentState.clearWeekAssignmentsConfirm?.let { (weekId, count) ->
            val week = lifecycleState.selectedProgramWeeks.firstOrNull { it.id.value == weekId }
            if (week != null) {
                val weekLabel = formatWeekRangeLabel(week.weekStartDate, week.weekStartDate.plusDays(6))
                AlertDialog(
                    onDismissRequest = { assignmentVM.dismissClearWeekAssignments() },
                    title = { Text("Rimuovi assegnazioni settimana") },
                    text = {
                        Text("Verranno rimosse $count assegnazioni dalla settimana $weekLabel. Continuare?")
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                assignmentVM.confirmClearWeekAssignments(week.weekStartDate, count, onSuccess = reloadData)
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.handCursorOnHover(),
                        ) { Text("Rimuovi") }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { assignmentVM.dismissClearWeekAssignments() },
                            modifier = Modifier.handCursorOnHover(),
                        ) { Text("Annulla") }
                    },
                )
            }
        }

        schemaState.schemaRefreshPreview?.let { preview ->
            AlertDialog(
                onDismissRequest = { schemaVM.dismissSchemaRefresh() },
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
                    TextButton(onClick = { schemaVM.confirmSchemaRefresh(onComplete = reloadData) }) { Text("Aggiorna") }
                },
                dismissButton = {
                    TextButton(onClick = { schemaVM.dismissSchemaRefresh() }) { Text("Annulla") }
                },
            )
        }

        if (lifecycleState.isLoading) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
        } else {
            val currentMonday = remember(lifecycleState.today) {
                lifecycleState.today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            }
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = weekListState,
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = 12.dp),
                ) {
                    if (lifecycleState.selectedProgramWeeks.isEmpty()) {
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
                        lifecycleState.selectedProgramWeeks.forEach { week ->
                            val weekKey = week.id.value
                            val weekIsCurrent = week.weekStartDate == currentMonday
                            val weekIsPast = week.weekStartDate < lifecycleState.today
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
                                    today = lifecycleState.today,
                                    assignments = lifecycleState.selectedProgramAssignments[week.id.value] ?: emptyList(),
                                    onReactivate = { partEditorVM.reactivateWeek(week, onSuccess = reloadData) },
                                    onOpenPartEditor = { partEditorVM.openPartEditor(week) },
                                    onRequestClearWeekAssignments = {
                                        assignmentVM.requestClearWeekAssignments(week.id.value, week.weekStartDate)
                                    },
                                    onAssignSlot = { partId, slot ->
                                        personPickerVM.openPersonPicker(
                                            weekStartDate = week.weekStartDate,
                                            weeklyPartId = partId,
                                            slot = slot,
                                            selectedProgramWeeks = lifecycleState.selectedProgramWeeks,
                                            selectedProgramAssignments = lifecycleState.selectedProgramAssignments
                                        )
                                    },
                                    onRemoveAssignment = { personPickerVM.removeAssignment(it, onSuccess = reloadData) },
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

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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import org.example.project.ui.theme.SemanticColors
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
    var pendingAssignmentRemoval by remember { mutableStateOf<AssignmentWithPerson?>(null) }

    LaunchedEffect(Unit) {
        lifecycleVM.onScreenEntered()
        assignmentVM.onScreenEntered()
    }

    // Keep schema VM in sync with lifecycle VM
    LaunchedEffect(
        lifecycleState.selectedProgramId,
        lifecycleState.selectedFutureProgram,
        lifecycleState.futurePrograms,
    ) {
        schemaVM.updateSelection(
            selectedProgramId = lifecycleState.selectedProgramId,
            selectedFutureProgram = lifecycleState.selectedFutureProgram,
            futurePrograms = lifecycleState.futurePrograms,
        )
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

        assignmentState.clearAssignmentsConfirm?.let { count ->
            val fromFutureDate = lifecycleState.today
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .plusWeeks(1)
            AlertDialog(
                onDismissRequest = { assignmentVM.dismissClearAssignments() },
                title = { Text("Svuota assegnazioni") },
                text = {
                    Text("Verranno rimosse $count assegnazioni dalle settimane future. Continuare?")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            lifecycleState.selectedProgramId?.let { programId ->
                                assignmentVM.confirmClearAssignments(programId, fromFutureDate, onSuccess = reloadData)
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

        pendingAssignmentRemoval?.let { assignment ->
            AlertDialog(
                onDismissRequest = { pendingAssignmentRemoval = null },
                title = { Text("Rimuovi assegnazione") },
                text = { Text("Confermi rimozione dell'assegnazione per ${assignment.fullName}?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingAssignmentRemoval = null
                            personPickerVM.removeAssignment(assignment.id, onSuccess = reloadData)
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Rimuovi") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingAssignmentRemoval = null }) { Text("Annulla") }
                },
            )
        }

        lifecycleState.deleteImpactConfirm?.let { impact ->
            AlertDialog(
                onDismissRequest = { lifecycleVM.dismissDeleteSelectedProgram() },
                title = { Text("Elimina mese") },
                text = {
                    Text(buildDeleteProgramImpactMessage(impact))
                },
                confirmButton = {
                    TextButton(
                        onClick = { lifecycleVM.confirmDeleteSelectedProgram() },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Elimina") }
                },
                dismissButton = {
                    TextButton(onClick = { lifecycleVM.dismissDeleteSelectedProgram() }) { Text("Annulla") }
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
            val selectedProgram = remember(
                lifecycleState.selectedProgramId,
                lifecycleState.currentProgram,
                lifecycleState.futurePrograms,
            ) {
                val selectedId = lifecycleState.selectedProgramId
                when (selectedId) {
                    lifecycleState.currentProgram?.id?.value -> lifecycleState.currentProgram
                    else -> {
                        lifecycleState.futurePrograms.firstOrNull { it.id.value == selectedId }
                            ?: lifecycleState.currentProgram
                            ?: lifecycleState.futurePrograms.firstOrNull()
                    }
                }
            }
            val totalSlots = remember(lifecycleState.selectedProgramWeeks) {
                lifecycleState.selectedProgramWeeks.sumOf { week ->
                    week.parts.sumOf { part -> part.partType.peopleCount }
                }
            }
            val totalAssignments = remember(lifecycleState.selectedProgramAssignments) {
                lifecycleState.selectedProgramAssignments.values.sumOf { it.size }
            }
            val assignmentsById = remember(lifecycleState.selectedProgramAssignments) {
                lifecycleState.selectedProgramAssignments.values
                    .flatten()
                    .associateBy { it.id.value }
            }
            val hasFutureWeeks = remember(lifecycleState.selectedProgramWeeks, currentMonday) {
                lifecycleState.selectedProgramWeeks.any { it.weekStartDate > currentMonday }
            }
            val fromFutureDate = remember(currentMonday) { currentMonday.plusWeeks(1) }

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Surface(
                    modifier = Modifier
                        .width(260.dp)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(spacing.cardRadius),
                    border = BorderStroke(1.dp, SemanticColors.blue.copy(alpha = 0.72f)),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(spacing.md),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SemanticColors.blue.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, SemanticColors.blue.copy(alpha = 0.5f)),
                        ) {
                            Text(
                                "Mesi",
                                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.sm, vertical = spacing.xs),
                                style = MaterialTheme.typography.labelLarge,
                                color = SemanticColors.blue,
                            )
                        }
                        lifecycleState.currentProgram?.let { program ->
                            val isSelected = lifecycleState.selectedProgramId == program.id.value
                            OutlinedButton(
                                onClick = { lifecycleVM.selectProgram(program.id.value) },
                                modifier = Modifier.fillMaxWidth().handCursorOnHover(),
                                border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                ),
                            ) {
                                Text(formatMonthYearLabel(program.month, program.year))
                            }
                        }
                        lifecycleState.futurePrograms.forEach { program ->
                            val isSelected = lifecycleState.selectedProgramId == program.id.value
                            OutlinedButton(
                                onClick = { lifecycleVM.selectProgram(program.id.value) },
                                modifier = Modifier.fillMaxWidth().handCursorOnHover(),
                                border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                                ),
                            ) {
                                Text(formatMonthYearLabel(program.month, program.year))
                            }
                            if (program.id.value in schemaState.impactedFutureProgramIds) {
                                Text(
                                    "Template aggiornato, verificare",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        lifecycleState.creatableTargets.forEach { target ->
                            val label = formatMonthYearLabel(target.monthValue, target.year)
                            Button(
                                onClick = { lifecycleVM.createProgramForTarget(target.year, target.monthValue) },
                                enabled = !lifecycleState.isCreatingProgram,
                                modifier = Modifier.fillMaxWidth().handCursorOnHover(enabled = !lifecycleState.isCreatingProgram),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(spacing.xs))
                                Text(if (lifecycleState.isCreatingProgram) "Creazione..." else "Crea $label")
                            }
                        }

                        OutlinedButton(
                            onClick = { schemaVM.refreshSchemasAndProgram(onProgramRefreshComplete = reloadData) },
                            enabled = !schemaState.isRefreshingSchemas && !schemaState.isRefreshingProgramFromSchemas,
                            modifier = Modifier.fillMaxWidth().handCursorOnHover(
                                enabled = !schemaState.isRefreshingSchemas && !schemaState.isRefreshingProgramFromSchemas,
                            ),
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(spacing.xs))
                            Text(
                                if (schemaState.isRefreshingSchemas || schemaState.isRefreshingProgramFromSchemas) {
                                    "Aggiornamento..."
                                } else {
                                    "Aggiorna schemi"
                                },
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(spacing.cardRadius),
                    border = BorderStroke(1.dp, SemanticColors.amber.copy(alpha = 0.72f)),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(spacing.md)) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SemanticColors.amber.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, SemanticColors.amber.copy(alpha = 0.55f)),
                        ) {
                            Text(
                                selectedProgram?.let { "Settimane · ${formatMonthYearLabel(it.month, it.year)}" }
                                    ?: "Settimane",
                                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.sm, vertical = spacing.xs),
                                style = MaterialTheme.typography.titleSmall,
                                color = SemanticColors.amber,
                            )
                        }
                        Spacer(Modifier.height(spacing.sm))
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                state = weekListState,
                                verticalArrangement = Arrangement.spacedBy(spacing.xs),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(end = 12.dp),
                            ) {
                                if (lifecycleState.selectedProgramWeeks.isEmpty()) {
                                    item(key = "empty-weeks") {
                                        Surface(
                                            shape = RoundedCornerShape(spacing.cardRadius),
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
                                        val weekIsPast = week.weekStartDate.plusDays(6) < lifecycleState.today
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
                                                showClearWeekAssignments = week.weekStartDate > currentMonday,
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
                                                onRemoveAssignment = { assignmentId ->
                                                    val assignment = assignmentsById[assignmentId.value]
                                                    if (assignment != null) {
                                                        pendingAssignmentRemoval = assignment
                                                    } else {
                                                        personPickerVM.removeAssignment(assignmentId, onSuccess = reloadData)
                                                    }
                                                },
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

                Surface(
                    modifier = Modifier
                        .width(320.dp)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(spacing.cardRadius),
                    border = BorderStroke(1.dp, SemanticColors.green.copy(alpha = 0.72f)),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(spacing.md),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SemanticColors.green.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, SemanticColors.green.copy(alpha = 0.52f)),
                        ) {
                            Text(
                                "Azioni",
                                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.sm, vertical = spacing.xs),
                                style = MaterialTheme.typography.labelLarge,
                                color = SemanticColors.green,
                            )
                        }
                        Button(
                            onClick = {
                                lifecycleState.selectedProgramId?.let { programId ->
                                    assignmentVM.autoAssignSelectedProgram(programId, fromFutureDate, onSuccess = reloadData)
                                }
                            },
                            enabled = lifecycleState.selectedProgramId != null && !assignmentState.isAutoAssigning,
                            modifier = Modifier.fillMaxWidth().handCursorOnHover(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SemanticColors.green,
                                contentColor = Color.White,
                            ),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(spacing.xs))
                            Text(if (assignmentState.isAutoAssigning) "Autoassegnazione..." else "Autoassegna")
                        }
                        Button(
                            onClick = {
                                lifecycleState.selectedProgramId?.let { programId ->
                                    assignmentVM.printSelectedProgram(programId)
                                }
                            },
                            enabled = lifecycleState.selectedProgramId != null && !assignmentState.isPrintingProgram,
                            modifier = Modifier.fillMaxWidth().handCursorOnHover(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SemanticColors.blue,
                                contentColor = Color.White,
                            ),
                        ) {
                            Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(spacing.xs))
                            Text(if (assignmentState.isPrintingProgram) "Stampa..." else "Stampa")
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, SemanticColors.blue.copy(alpha = 0.42f)),
                            color = SemanticColors.blue.copy(alpha = 0.14f),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(spacing.sm),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text("Copertura", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$totalAssignments/$totalSlots slot assegnati", style = MaterialTheme.typography.bodyMedium)
                                Text("${assignmentState.autoAssignUnresolved.size} slot non assegnati", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        if (lifecycleState.canDeleteSelectedProgram) {
                            OutlinedButton(
                                onClick = { lifecycleVM.requestDeleteSelectedProgram() },
                                enabled = !lifecycleState.isDeletingSelectedProgram,
                                modifier = Modifier.fillMaxWidth().handCursorOnHover(),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.75f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(spacing.xs))
                                Text(if (lifecycleState.isDeletingSelectedProgram) "Eliminazione..." else "Elimina mese")
                            }
                        }
                        if (hasFutureWeeks) {
                            OutlinedButton(
                                onClick = {
                                    lifecycleState.selectedProgramId?.let { programId ->
                                        assignmentVM.requestClearAssignments(programId, fromFutureDate)
                                    }
                                },
                                enabled = lifecycleState.selectedProgramId != null && !assignmentState.isClearingAssignments,
                                modifier = Modifier.fillMaxWidth().handCursorOnHover(),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            ) {
                                Icon(Icons.Filled.ClearAll, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(spacing.xs))
                                Text(if (assignmentState.isClearingAssignments) "Svuotamento..." else "Svuota assegnazioni")
                            }
                        }

                        if (assignmentState.autoAssignUnresolved.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(spacing.sm),
                                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                                ) {
                                    Text("Slot non assegnati", style = MaterialTheme.typography.labelMedium)
                                    assignmentState.autoAssignUnresolved.take(4).forEach { unresolved ->
                                        val weekLabel = formatWeekRangeLabel(unresolved.weekStartDate, unresolved.weekStartDate.plusDays(6))
                                        Text(
                                            "• $weekLabel · ${unresolved.partLabel} (${unresolved.reason})",
                                            style = MaterialTheme.typography.labelSmall,
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
}

internal fun buildDeleteProgramImpactMessage(impact: DeleteProgramImpact): String {
    val monthLabel = formatMonthYearLabel(impact.month, impact.year)
    return "Confermi eliminazione del mese $monthLabel? " +
        "Verranno rimosse ${impact.weeksCount} settimane e ${impact.assignmentsCount} assegnazioni."
}

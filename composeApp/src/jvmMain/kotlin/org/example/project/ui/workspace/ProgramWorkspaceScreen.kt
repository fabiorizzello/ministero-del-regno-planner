package org.example.project.ui.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
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
import org.example.project.ui.components.FeedbackBannerKind
import org.example.project.ui.components.formatMonthYearLabel
import org.example.project.ui.components.formatWeekRangeLabel
import org.example.project.ui.components.handCursorOnHover
import org.example.project.ui.theme.SemanticColors
import org.example.project.ui.theme.spacing
import org.koin.core.context.GlobalContext
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

private data class ProgramActivityFeedEntry(
    val id: Long,
    val kind: FeedbackBannerKind,
    val message: String,
    val details: String?,
    val timestamp: String,
)

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
    val activityFeed = remember { mutableStateListOf<ProgramActivityFeedEntry>() }
    val incomingNotices = listOfNotNull(
        lifecycleState.notice,
        schemaState.notice,
        assignmentState.notice,
        personPickerState.notice,
        partEditorState.notice,
    )
    val noticeSignature = incomingNotices.joinToString(separator = "|") { "${it.kind}:${it.message}:${it.details.orEmpty()}" }

    LaunchedEffect(Unit) {
        lifecycleVM.onScreenEntered()
        assignmentVM.onScreenEntered()
    }

    LaunchedEffect(noticeSignature) {
        if (incomingNotices.isEmpty()) return@LaunchedEffect
        incomingNotices.forEach { notice ->
            activityFeed.add(
                0,
                ProgramActivityFeedEntry(
                    id = System.nanoTime(),
                    kind = notice.kind,
                    message = notice.message,
                    details = notice.details,
                    timestamp = LocalTime.now().withNano(0).toString(),
                ),
            )
        }
        while (activityFeed.size > 50) {
            activityFeed.removeAt(activityFeed.lastIndex)
        }
        lifecycleVM.dismissNotice()
        schemaVM.dismissNotice()
        assignmentVM.dismissNotice()
        personPickerVM.dismissNotice()
        partEditorVM.dismissNotice()
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
            .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
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
                                assignmentVM.confirmClearWeekAssignments(week.weekStartDate, onSuccess = reloadData)
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
                        .width(244.dp)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(spacing.cardRadius),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(spacing.md),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
                        ) {
                            Text(
                                "Mesi",
                                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.sm, vertical = spacing.xs),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        lifecycleState.currentProgram?.let { program ->
                            val isSelected = lifecycleState.selectedProgramId == program.id.value
                            ProgramMonthSelectorButton(
                                label = formatMonthYearLabel(program.month, program.year),
                                selected = isSelected,
                                accent = MaterialTheme.colorScheme.primary,
                                onClick = { lifecycleVM.selectProgram(program.id.value) },
                            )
                        }
                        lifecycleState.futurePrograms.forEach { program ->
                            val isSelected = lifecycleState.selectedProgramId == program.id.value
                            ProgramMonthSelectorButton(
                                label = formatMonthYearLabel(program.month, program.year),
                                selected = isSelected,
                                accent = MaterialTheme.colorScheme.secondary,
                                onClick = { lifecycleVM.selectProgram(program.id.value) },
                            )
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
                            ProgramPanelActionButton(
                                label = if (lifecycleState.isCreatingProgram) "Creazione..." else "Crea $label",
                                icon = Icons.Filled.Add,
                                onClick = { lifecycleVM.createProgramForTarget(target.year, target.monthValue) },
                                enabled = !lifecycleState.isCreatingProgram,
                                tone = ProgramActionTone.Neutral,
                            )
                        }

                        ProgramPanelActionButton(
                            label = if (schemaState.isRefreshingSchemas || schemaState.isRefreshingProgramFromSchemas) {
                                "Aggiornamento..."
                            } else {
                                "Aggiorna schemi"
                            },
                            icon = Icons.Filled.Refresh,
                            onClick = { schemaVM.refreshSchemasAndProgram(onProgramRefreshComplete = reloadData) },
                            enabled = !schemaState.isRefreshingSchemas && !schemaState.isRefreshingProgramFromSchemas,
                            tone = ProgramActionTone.Neutral,
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(spacing.cardRadius),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(spacing.md)) {
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
                        ) {
                            Text(
                                selectedProgram?.let { "Settimane · ${formatMonthYearLabel(it.month, it.year)}" }
                                    ?: "Settimane",
                                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.sm, vertical = spacing.xs),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                        Spacer(Modifier.height(spacing.sm))
                        ProgramMonthTimeline(
                            weeks = lifecycleState.selectedProgramWeeks,
                            assignmentsByWeek = lifecycleState.selectedProgramAssignments,
                            modifier = Modifier.fillMaxWidth(),
                        )
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
                                                onSkipWeek = { partEditorVM.skipWeek(week, onSuccess = reloadData) },
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
                        .width(338.dp)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(spacing.cardRadius),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(spacing.md),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
                        ) {
                            Text(
                                "Azioni e feed",
                                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.sm, vertical = spacing.xs),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        ProgramPanelActionButton(
                            label = if (assignmentState.isAutoAssigning) "Autoassegnazione..." else "Autoassegna",
                            icon = Icons.Filled.PlayArrow,
                            onClick = {
                                lifecycleState.selectedProgramId?.let { programId ->
                                    assignmentVM.autoAssignSelectedProgram(programId, fromFutureDate, onSuccess = reloadData)
                                }
                            },
                            enabled = lifecycleState.selectedProgramId != null && !assignmentState.isAutoAssigning,
                            tone = ProgramActionTone.Positive,
                        )
                        ProgramPanelActionButton(
                            label = if (assignmentState.isPrintingProgram) "Stampa..." else "Stampa",
                            icon = Icons.Filled.Print,
                            onClick = {
                                lifecycleState.selectedProgramId?.let { programId ->
                                    assignmentVM.printSelectedProgram(programId)
                                }
                            },
                            enabled = lifecycleState.selectedProgramId != null && !assignmentState.isPrintingProgram,
                            tone = ProgramActionTone.Primary,
                        )

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
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
                            ProgramPanelActionButton(
                                label = if (lifecycleState.isDeletingSelectedProgram) "Eliminazione..." else "Elimina mese",
                                icon = Icons.Filled.Delete,
                                onClick = { lifecycleVM.requestDeleteSelectedProgram() },
                                enabled = !lifecycleState.isDeletingSelectedProgram,
                                tone = ProgramActionTone.DangerOutline,
                            )
                        }
                        if (hasFutureWeeks) {
                            ProgramPanelActionButton(
                                label = if (assignmentState.isClearingAssignments) "Svuotamento..." else "Svuota assegnazioni",
                                icon = Icons.Filled.ClearAll,
                                onClick = {
                                    lifecycleState.selectedProgramId?.let { programId ->
                                        assignmentVM.requestClearAssignments(programId, fromFutureDate)
                                    }
                                },
                                enabled = lifecycleState.selectedProgramId != null && !assignmentState.isClearingAssignments,
                                tone = ProgramActionTone.DangerOutline,
                            )
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

                        ProgramInlineAssignmentSettings(
                            state = assignmentState.assignmentSettings,
                            isSaving = assignmentState.isSavingAssignmentSettings,
                            onStrictCooldownChange = assignmentVM::setStrictCooldown,
                            onLeadWeightChange = assignmentVM::setLeadWeight,
                            onAssistWeightChange = assignmentVM::setAssistWeight,
                            onLeadCooldownChange = assignmentVM::setLeadCooldownWeeks,
                            onAssistCooldownChange = assignmentVM::setAssistCooldownWeeks,
                            onSave = assignmentVM::saveAssignmentSettings,
                        )

                        ProgramActivityFeedPanel(
                            entries = activityFeed,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
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

private enum class ProgramActionTone {
    Primary,
    Positive,
    Neutral,
    DangerOutline,
}

@Composable
private fun ProgramMonthSelectorButton(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val border = if (selected) accent.copy(alpha = 0.85f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
    val container = if (selected) accent.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
    val content = if (selected) accent else MaterialTheme.colorScheme.onSurface

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .handCursorOnHover(enabled = enabled)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(7.dp),
        color = container,
        border = BorderStroke(1.dp, border),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.titleSmall,
            color = content.copy(alpha = if (enabled) 1f else 0.45f),
        )
    }
}

@Composable
private fun ProgramPanelActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean,
    tone: ProgramActionTone,
) {
    val (container, content, border) = when (tone) {
        ProgramActionTone.Primary -> Triple(
            SemanticColors.blue,
            Color.White,
            SemanticColors.blue.copy(alpha = 0.9f),
        )
        ProgramActionTone.Positive -> Triple(
            SemanticColors.green,
            Color.White,
            SemanticColors.green.copy(alpha = 0.9f),
        )
        ProgramActionTone.Neutral -> Triple(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            MaterialTheme.colorScheme.onSurface,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f),
        )
        ProgramActionTone.DangerOutline -> Triple(
            Color.Transparent,
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.error.copy(alpha = 0.75f),
        )
    }
    val alpha = if (enabled) 1f else 0.45f

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .handCursorOnHover(enabled = enabled)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(7.dp),
        color = container.copy(alpha = alpha),
        border = BorderStroke(1.dp, border.copy(alpha = alpha)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content.copy(alpha = alpha),
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                color = content.copy(alpha = alpha),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun ProgramMonthTimeline(
    weeks: List<WeekPlan>,
    assignmentsByWeek: Map<String, List<AssignmentWithPerson>>,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(5.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = "Timeline mese",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (weeks.isEmpty()) {
                Text(
                    text = "Nessuna settimana disponibile",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    weeks.forEach { week ->
                        val weekAssignments = assignmentsByWeek[week.id.value].orEmpty()
                        val totalSlots = week.parts.sumOf { it.partType.peopleCount }
                        val assignedSlots = weekAssignments.size
                        val progress = if (totalSlots == 0) 0f else assignedSlots / totalSlots.toFloat()
                        val weekEndDate = week.weekStartDate.plusDays(6)
                        val statusLabel = when (week.status) {
                            WeekPlanStatus.SKIPPED -> "Saltata"
                            else -> "$assignedSlots/$totalSlots"
                        }
                        val progressColor = when (week.status) {
                            WeekPlanStatus.SKIPPED -> MaterialTheme.colorScheme.secondary
                            else -> if (progress >= 1f) SemanticColors.green else SemanticColors.amber
                        }
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Column(
                                modifier = Modifier
                                    .width(132.dp)
                                    .padding(horizontal = spacing.sm, vertical = spacing.xs),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = "${week.weekStartDate.dayOfMonth}-${weekEndDate.dayOfMonth}",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                Text(
                                    text = "Parti ${week.parts.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                LinearProgressIndicator(
                                    progress = { progress },
                                    color = progressColor,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth().height(5.dp),
                                )
                                Text(
                                    text = statusLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = progressColor,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgramInlineAssignmentSettings(
    state: AssignmentSettingsUiState,
    isSaving: Boolean,
    onStrictCooldownChange: (Boolean) -> Unit,
    onLeadWeightChange: (String) -> Unit,
    onAssistWeightChange: (String) -> Unit,
    onLeadCooldownChange: (String) -> Unit,
    onAssistCooldownChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    Surface(
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                "Impostazioni assegnatore",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Strict cooldown",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Switch(
                    checked = state.strictCooldown,
                    onCheckedChange = onStrictCooldownChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        checkedBorderColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                        uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                OutlinedTextField(
                    value = state.leadWeight,
                    onValueChange = onLeadWeightChange,
                    label = { Text("Peso conduzione") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = state.assistWeight,
                    onValueChange = onAssistWeightChange,
                    label = { Text("Peso assistenza") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                OutlinedTextField(
                    value = state.leadCooldownWeeks,
                    onValueChange = onLeadCooldownChange,
                    label = { Text("Cooldown conduzione") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = state.assistCooldownWeeks,
                    onValueChange = onAssistCooldownChange,
                    label = { Text("Cooldown assistenza") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedButton(
                onClick = onSave,
                enabled = !isSaving,
                modifier = Modifier
                    .align(Alignment.End)
                    .handCursorOnHover(enabled = !isSaving),
                shape = RoundedCornerShape(6.dp),
            ) {
                Text(if (isSaving) "Salvataggio..." else "Salva")
            }
        }
    }
}

@Composable
private fun ProgramActivityFeedPanel(
    entries: List<ProgramActivityFeedEntry>,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(5.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = "Feed attività",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (entries.isEmpty()) {
                Text(
                    text = "Nessun evento registrato",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    items(entries, key = { it.id }) { item ->
                        val isError = item.kind == FeedbackBannerKind.ERROR
                        val borderColor = if (isError) {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
                        } else {
                            SemanticColors.green.copy(alpha = 0.55f)
                        }
                        val icon = if (isError) Icons.Filled.ErrorOutline else Icons.Filled.TaskAlt
                        val iconTint = if (isError) MaterialTheme.colorScheme.error else SemanticColors.green
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            border = BorderStroke(1.dp, borderColor),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = spacing.sm, vertical = spacing.xs),
                                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = iconTint,
                                    modifier = Modifier.size(16.dp),
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(item.message, style = MaterialTheme.typography.labelMedium)
                                    if (!item.details.isNullOrBlank()) {
                                        Text(
                                            item.details,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Text(
                                    item.timestamp,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

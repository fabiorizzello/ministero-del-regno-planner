package org.example.project.ui.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.assignments.domain.AssignmentWithPerson
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.WeeklyPart
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import org.example.project.ui.assignments.PartAssignmentCard
import org.example.project.ui.assignments.PersonPickerDialog
import org.example.project.ui.components.DISPLAY_NUMBER_OFFSET
import org.example.project.ui.components.FeedbackBannerKind
import org.example.project.ui.components.formatMonthYearLabel
import org.example.project.ui.components.formatWeekRangeLabel
import org.example.project.ui.components.handCursorOnHover
import org.example.project.ui.components.workspace.WorkspacePanel
import org.example.project.ui.components.workspace.WorkspacePanelHeader
import org.example.project.ui.components.workspace.WorkspaceStateKind
import org.example.project.ui.components.workspace.WorkspaceStatePane
import org.example.project.ui.theme.spacing
import org.example.project.ui.theme.workspaceSketch
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

internal enum class WeekSidebarStatus { CURRENT, PAST, COMPLETE, PARTIAL, EMPTY, SKIPPED }

private fun org.example.project.feature.weeklyparts.domain.WeekPlan.sidebarStatus(
    currentMonday: java.time.LocalDate,
    assignedSlots: Int,
    totalSlots: Int,
): WeekSidebarStatus = when {
    status == org.example.project.feature.weeklyparts.domain.WeekPlanStatus.SKIPPED -> WeekSidebarStatus.SKIPPED
    weekStartDate == currentMonday -> WeekSidebarStatus.CURRENT
    weekStartDate < currentMonday -> WeekSidebarStatus.PAST
    assignedSlots == totalSlots && totalSlots > 0 -> WeekSidebarStatus.COMPLETE
    assignedSlots > 0 -> WeekSidebarStatus.PARTIAL
    else -> WeekSidebarStatus.EMPTY
}

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
            .fillMaxSize(),
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
                    DesktopInlineAction(
                        label = "Svuota",
                        onClick = {
                            lifecycleState.selectedProgramId?.let { programId ->
                                assignmentVM.confirmClearAssignments(programId, fromFutureDate, onSuccess = reloadData)
                            }
                        },
                        destructive = true,
                    )
                },
                dismissButton = {
                    DesktopInlineAction(
                        label = "Annulla",
                        onClick = { assignmentVM.dismissClearAssignments() },
                    )
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
                        DesktopInlineAction(
                            label = "Rimuovi",
                            onClick = {
                                assignmentVM.confirmClearWeekAssignments(week.weekStartDate, onSuccess = reloadData)
                            },
                            modifier = Modifier.handCursorOnHover(),
                            destructive = true,
                        )
                    },
                    dismissButton = {
                        DesktopInlineAction(
                            label = "Annulla",
                            onClick = { assignmentVM.dismissClearWeekAssignments() },
                            modifier = Modifier.handCursorOnHover(),
                        )
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
                    DesktopInlineAction(
                        label = "Rimuovi",
                        onClick = {
                            pendingAssignmentRemoval = null
                            personPickerVM.removeAssignment(assignment.id, onSuccess = reloadData)
                        },
                        destructive = true,
                    )
                },
                dismissButton = {
                    DesktopInlineAction(
                        label = "Annulla",
                        onClick = { pendingAssignmentRemoval = null },
                    )
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
                    DesktopInlineAction(
                        label = "Elimina",
                        onClick = { lifecycleVM.confirmDeleteSelectedProgram() },
                        destructive = true,
                    )
                },
                dismissButton = {
                    DesktopInlineAction(
                        label = "Annulla",
                        onClick = { lifecycleVM.dismissDeleteSelectedProgram() },
                    )
                },
            )
        }

        val showLoadingState = lifecycleState.isLoading
        val showErrorState = !showLoadingState &&
            lifecycleState.selectedProgramWeeks.isEmpty() &&
            incomingNotices.any { it.kind == FeedbackBannerKind.ERROR }
        when {
            showLoadingState -> WorkspaceStatePane(
                kind = WorkspaceStateKind.Loading,
                message = "Caricamento programma in corso...",
                modifier = Modifier.fillMaxWidth(),
            )
            showErrorState -> WorkspaceStatePane(
                kind = WorkspaceStateKind.Error,
                message = "Impossibile caricare il programma selezionato.",
                modifier = Modifier.fillMaxWidth(),
            )
            else -> {
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
            val unresolvedPendingSlots = remember(totalSlots, totalAssignments, assignmentState.autoAssignUnresolved) {
                val remaining = (totalSlots - totalAssignments).coerceAtLeast(0)
                assignmentState.autoAssignUnresolved.size.coerceIn(0, remaining)
            }
            val emptySlots = remember(totalSlots, totalAssignments, unresolvedPendingSlots) {
                (totalSlots - totalAssignments).coerceAtLeast(0) - unresolvedPendingSlots
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
            val weekListState = rememberLazyListState()
            val sketch = MaterialTheme.workspaceSketch

            var selectedWeekId by remember { mutableStateOf<String?>(null) }
            val effectiveSelectedWeekId = remember(selectedWeekId, lifecycleState.selectedProgramWeeks, currentMonday) {
                val id = selectedWeekId
                val weeks = lifecycleState.selectedProgramWeeks
                when {
                    id != null && weeks.any { it.id.value == id } -> id
                    else -> weeks.firstOrNull { it.weekStartDate == currentMonday }?.id?.value
                        ?: weeks.firstOrNull()?.id?.value
                }
            }
            val selectedWeek = lifecycleState.selectedProgramWeeks.firstOrNull { it.id.value == effectiveSelectedWeekId }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // ── Left sidebar ──────────────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .width(210.dp)
                        .fillMaxHeight()
                        .background(sketch.panelLeft),
                ) {
                    // Programs (months) section
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            "PROGRAMMI",
                            modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp,
                                letterSpacing = 0.7.sp,
                            ),
                            color = sketch.inkMuted,
                        )
                        lifecycleState.currentProgram?.let { program ->
                            ProgramMonthSelectorButton(
                                label = formatMonthYearLabel(program.month, program.year),
                                selected = lifecycleState.selectedProgramId == program.id.value,
                                accent = sketch.accent,
                                onClick = {
                                    lifecycleVM.selectProgram(program.id.value)
                                    selectedWeekId = null
                                },
                            )
                        }
                        lifecycleState.futurePrograms.forEach { program ->
                            ProgramMonthSelectorButton(
                                label = formatMonthYearLabel(program.month, program.year),
                                selected = lifecycleState.selectedProgramId == program.id.value,
                                accent = sketch.accent,
                                onClick = {
                                    lifecycleVM.selectProgram(program.id.value)
                                    selectedWeekId = null
                                },
                            )
                        }
                        lifecycleState.creatableTargets.forEach { target ->
                            val label = formatMonthYearLabel(target.monthValue, target.year)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .handCursorOnHover()
                                    .clip(RoundedCornerShape(5.dp))
                                    .clickable(enabled = !lifecycleState.isCreatingProgram) {
                                        lifecycleVM.createProgramForTarget(target.year, target.monthValue)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = null,
                                    tint = sketch.accent.copy(alpha = 0.7f),
                                    modifier = Modifier.size(12.dp),
                                )
                                Text(
                                    if (lifecycleState.isCreatingProgram) "Creazione..." else "Crea $label",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = sketch.accent.copy(alpha = 0.7f),
                                )
                            }
                        }
                    }

                    // Divider
                    Box(Modifier.fillMaxWidth().height(1.dp).background(sketch.lineSoft.copy(alpha = 0.6f)))

                    // Week list
                    val weeksForMonth = lifecycleState.selectedProgramWeeks
                    LazyColumn(
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        item {
                            Text(
                                selectedProgram?.let {
                                    formatMonthYearLabel(it.month, it.year).uppercase()
                                } ?: "SETTIMANE",
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 10.sp,
                                    letterSpacing = 0.6.sp,
                                ),
                                color = sketch.inkMuted,
                            )
                        }
                        items(weeksForMonth, key = { it.id.value }) { week ->
                            val weekAssignedSlots = lifecycleState.selectedProgramAssignments[week.id.value]?.size ?: 0
                            val weekTotalSlots = week.parts.sumOf { it.partType.peopleCount }
                            val weekStatus = week.sidebarStatus(currentMonday, weekAssignedSlots, weekTotalSlots)
                            val fraction = if (weekTotalSlots > 0) weekAssignedSlots.toFloat() / weekTotalSlots else 0f
                            WeekSidebarItem(
                                label = formatWeekRangeLabel(week.weekStartDate, week.weekStartDate.plusDays(6)),
                                status = weekStatus,
                                fraction = fraction,
                                selected = effectiveSelectedWeekId == week.id.value,
                                onClick = { selectedWeekId = week.id.value },
                            )
                        }
                    }

                    // Footer
                    Box(Modifier.fillMaxWidth().height(1.dp).background(sketch.lineSoft.copy(alpha = 0.6f)))
                    Column(modifier = Modifier.padding(8.dp)) {
                        SidebarFooterButton(
                            label = if (schemaState.isRefreshingSchemas || schemaState.isRefreshingProgramFromSchemas)
                                "Aggiornamento..." else "Aggiorna schemi",
                            icon = Icons.Filled.Refresh,
                            onClick = { schemaVM.refreshSchemasAndProgram(onProgramRefreshComplete = reloadData) },
                            enabled = !schemaState.isRefreshingSchemas && !schemaState.isRefreshingProgramFromSchemas,
                        )
                    }
                }
                // Sidebar / center divider
                Box(Modifier.fillMaxHeight().width(1.dp).background(sketch.lineSoft))

                WorkspacePanel(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    containerColor = sketch.panelMid,
                    borderColor = sketch.lineSoft,
                    contentPadding = 12.dp,
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = selectedProgram?.let {
                                        "Programma · ${formatMonthYearLabel(it.month, it.year)}"
                                    } ?: "Programma",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = sketch.ink,
                                )
                                Text(
                                    text = "Quadro completo settimane e slot assegnazioni",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = sketch.inkMuted,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ProgramStatusPill(
                                    label = "$totalAssignments assegnate",
                                    tone = ProgramStatusTone.Good,
                                )
                                ProgramStatusPill(
                                    label = "$unresolvedPendingSlots pending",
                                    tone = ProgramStatusTone.Info,
                                )
                                ProgramStatusPill(
                                    label = "$emptySlots vuoti",
                                    tone = ProgramStatusTone.Warn,
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        if (lifecycleState.selectedProgramWeeks.isEmpty()) {
                            WorkspaceStatePane(
                                kind = WorkspaceStateKind.Empty,
                                message = "Nessuna settimana disponibile nel programma selezionato",
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize()) {
                                LazyColumn(
                                    state = weekListState,
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(end = 8.dp),
                                ) {
                                    items(
                                        items = lifecycleState.selectedProgramWeeks,
                                        key = { it.id.value },
                                    ) { week ->
                                        val weekIsCurrent = week.weekStartDate == currentMonday
                                        val weekAssignments = lifecycleState.selectedProgramAssignments[week.id.value] ?: emptyList()
                                        val weekTotalSlots = week.parts.sumOf { it.partType.peopleCount }
                                        val weekAssignedSlots = weekAssignments.size
                                        ProgramWeekCard(
                                            week = week,
                                            isCurrent = weekIsCurrent,
                                            today = lifecycleState.today,
                                            showClearWeekAssignments = week.weekStartDate > currentMonday,
                                            assignments = weekAssignments,
                                            assignedSlots = weekAssignedSlots,
                                            totalSlots = weekTotalSlots,
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
                                                    selectedProgramAssignments = lifecycleState.selectedProgramAssignments,
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

                WorkspacePanel(
                    modifier = Modifier
                        .width(360.dp)
                        .fillMaxHeight(),
                    containerColor = sketch.panelRight,
                    borderColor = sketch.lineSoft,
                    contentPadding = 12.dp,
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        WorkspacePanelHeader(
                            title = "Inspector",
                            color = sketch.inkMuted,
                        )
                        InspectorSectionLabel("Azioni rapide")
                        ProgramQuickAction(
                            label = if (assignmentState.isAutoAssigning) "Autoassegnazione..." else "Autoassegna",
                            icon = Icons.Filled.PlayArrow,
                            onClick = {
                                lifecycleState.selectedProgramId?.let { programId ->
                                    assignmentVM.autoAssignSelectedProgram(programId, fromFutureDate, onSuccess = reloadData)
                                }
                            },
                            enabled = lifecycleState.selectedProgramId != null && !assignmentState.isAutoAssigning,
                            tone = ProgramQuickActionTone.Positive,
                        )
                        ProgramQuickAction(
                            label = if (assignmentState.isPrintingProgram) "Stampa..." else "Stampa",
                            icon = Icons.Filled.Print,
                            onClick = {
                                lifecycleState.selectedProgramId?.let { programId ->
                                    assignmentVM.printSelectedProgram(programId)
                                }
                            },
                            enabled = lifecycleState.selectedProgramId != null && !assignmentState.isPrintingProgram,
                            tone = ProgramQuickActionTone.Accent,
                        )

                        InspectorSectionLabel("Copertura")
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, sketch.lineSoft),
                            color = sketch.surfaceMuted,
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(spacing.sm),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    "$totalAssignments/$totalSlots slot assegnati",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = sketch.ink,
                                )
                                Text(
                                    "Vuoti $emptySlots · Pending $unresolvedPendingSlots",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = sketch.inkMuted,
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(7.dp)
                                        .background(sketch.lineSoft.copy(alpha = 0.7f), RoundedCornerShape(999.dp)),
                                ) {
                                    if (totalSlots > 0 && totalAssignments > 0) {
                                        Box(
                                            modifier = Modifier
                                                .weight(totalAssignments.toFloat())
                                                .fillMaxHeight()
                                                .background(sketch.ok, RoundedCornerShape(999.dp)),
                                        )
                                    }
                                    if (totalSlots > 0 && unresolvedPendingSlots > 0) {
                                        Box(
                                            modifier = Modifier
                                                .weight(unresolvedPendingSlots.toFloat())
                                                .fillMaxHeight()
                                                .background(sketch.accent),
                                        )
                                    }
                                    if (totalSlots > 0 && emptySlots > 0) {
                                        Box(
                                            modifier = Modifier
                                                .weight(emptySlots.toFloat())
                                                .fillMaxHeight()
                                                .background(sketch.warn),
                                        )
                                    }
                                }
                            }
                        }

                        if (lifecycleState.canDeleteSelectedProgram) {
                            ProgramQuickAction(
                                label = if (lifecycleState.isDeletingSelectedProgram) "Eliminazione..." else "Elimina mese",
                                icon = Icons.Filled.Delete,
                                onClick = { lifecycleVM.requestDeleteSelectedProgram() },
                                enabled = !lifecycleState.isDeletingSelectedProgram,
                                tone = ProgramQuickActionTone.Danger,
                            )
                        }
                        if (hasFutureWeeks) {
                            ProgramQuickAction(
                                label = if (assignmentState.isClearingAssignments) "Svuotamento..." else "Svuota assegnazioni",
                                icon = Icons.Filled.ClearAll,
                                onClick = {
                                    lifecycleState.selectedProgramId?.let { programId ->
                                        assignmentVM.requestClearAssignments(programId, fromFutureDate)
                                    }
                                },
                                enabled = lifecycleState.selectedProgramId != null && !assignmentState.isClearingAssignments,
                                tone = ProgramQuickActionTone.Danger,
                            )
                        }

                        if (assignmentState.autoAssignUnresolved.isNotEmpty()) {
                            InspectorSectionLabel("Issue")
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, sketch.bad.copy(alpha = 0.6f)),
                                color = sketch.bad.copy(alpha = 0.12f),
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(spacing.sm),
                                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                                ) {
                                    Text(
                                        "Slot non assegnati",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = sketch.bad,
                                    )
                                    assignmentState.autoAssignUnresolved.take(4).forEach { unresolved ->
                                        val weekLabel = formatWeekRangeLabel(unresolved.weekStartDate, unresolved.weekStartDate.plusDays(6))
                                        Text(
                                            "• $weekLabel · ${unresolved.partLabel} (${unresolved.reason})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = sketch.inkSoft,
                                        )
                                    }
                                }
                            }
                        }

                        InspectorSectionLabel("Impostazioni assegnatore")
                        Box(modifier = Modifier.testTag("program-assignment-settings")) {
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
                        }

                        InspectorSectionLabel("Feed attività")
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
}

internal fun buildDeleteProgramImpactMessage(impact: DeleteProgramImpact): String {
    val monthLabel = formatMonthYearLabel(impact.month, impact.year)
    return "Confermi eliminazione del mese $monthLabel? " +
        "Verranno rimosse ${impact.weeksCount} settimane e ${impact.assignmentsCount} assegnazioni."
}

@Composable
private fun ProgramMonthSelectorButton(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val sketch = MaterialTheme.workspaceSketch
    val border = if (selected) accent.copy(alpha = 0.58f) else sketch.lineSoft
    val container = if (selected) accent.copy(alpha = 0.14f) else sketch.surface
    val content = if (selected) accent else sketch.inkSoft

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .handCursorOnHover(enabled = enabled)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = container,
        border = BorderStroke(1.dp, border),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            style = MaterialTheme.typography.titleSmall,
            color = content.copy(alpha = if (enabled) 1f else 0.45f),
        )
    }
}

private enum class ProgramStatusTone {
    Good,
    Info,
    Warn,
}

@Composable
private fun ProgramStatusPill(
    label: String,
    tone: ProgramStatusTone,
) {
    val sketch = MaterialTheme.workspaceSketch
    val color = when (tone) {
        ProgramStatusTone.Good -> sketch.ok
        ProgramStatusTone.Info -> sketch.accent
        ProgramStatusTone.Warn -> sketch.bad
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.42f)),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

private enum class ProgramQuickActionTone {
    Accent,
    Positive,
    Danger,
}

@Composable
private fun InspectorSectionLabel(
    label: String,
) {
    val sketch = MaterialTheme.workspaceSketch
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = sketch.inkMuted,
    )
}

@Composable
private fun ProgramQuickAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    enabled: Boolean,
    tone: ProgramQuickActionTone,
) {
    val sketch = MaterialTheme.workspaceSketch
    val (container, border, content) = when (tone) {
        ProgramQuickActionTone.Accent -> Triple(
            sketch.accent,
            sketch.accent,
            Color.White,
        )
        ProgramQuickActionTone.Positive -> Triple(
            sketch.ok,
            sketch.ok,
            Color.White,
        )
        ProgramQuickActionTone.Danger -> Triple(
            sketch.surfaceMuted,
            sketch.bad.copy(alpha = 0.82f),
            sketch.bad,
        )
    }
    val alpha = if (enabled) 1f else 0.45f
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .handCursorOnHover(enabled = enabled)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = container.copy(alpha = alpha),
        border = BorderStroke(1.dp, border.copy(alpha = alpha)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content.copy(alpha = alpha),
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = content.copy(alpha = alpha),
            )
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
    val sketch = MaterialTheme.workspaceSketch
    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, sketch.lineSoft),
        color = sketch.surfaceMuted,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Strict cooldown",
                    style = MaterialTheme.typography.bodySmall,
                    color = sketch.inkSoft,
                )
                DesktopToggle(
                    checked = state.strictCooldown,
                    onToggle = onStrictCooldownChange,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                DesktopNumericField(
                    label = "Peso conduzione",
                    value = state.leadWeight,
                    onValueChange = onLeadWeightChange,
                    modifier = Modifier.weight(1f),
                )
                DesktopNumericField(
                    label = "Peso assistenza",
                    value = state.assistWeight,
                    onValueChange = onAssistWeightChange,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                DesktopNumericField(
                    label = "Cooldown conduzione",
                    value = state.leadCooldownWeeks,
                    onValueChange = onLeadCooldownChange,
                    modifier = Modifier.weight(1f),
                )
                DesktopNumericField(
                    label = "Cooldown assistenza",
                    value = state.assistCooldownWeeks,
                    onValueChange = onAssistCooldownChange,
                    modifier = Modifier.weight(1f),
                )
            }
            DesktopInlineAction(
                label = if (isSaving) "Salvataggio..." else "Salva",
                modifier = Modifier
                    .align(Alignment.End)
                    .handCursorOnHover(enabled = !isSaving),
                enabled = !isSaving,
                onClick = onSave,
            )
        }
    }
}

@Composable
private fun DesktopToggle(
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val sketch = MaterialTheme.workspaceSketch
    val trackColor = if (checked) {
        sketch.accent.copy(alpha = 0.72f)
    } else {
        sketch.lineSoft
    }
    Surface(
        modifier = Modifier
            .width(42.dp)
            .height(24.dp)
            .handCursorOnHover()
            .clickable { onToggle(!checked) },
        shape = RoundedCornerShape(999.dp),
        color = trackColor,
        border = BorderStroke(1.dp, trackColor.copy(alpha = 0.9f)),
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(3.dp)) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                    .background(sketch.ink.copy(alpha = 0.9f), RoundedCornerShape(999.dp)),
            )
        }
    }
}

@Composable
private fun DesktopNumericField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sketch = MaterialTheme.workspaceSketch
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = sketch.inkSoft,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = sketch.surfaceMuted,
            border = BorderStroke(1.dp, sketch.lineSoft),
        ) {
            BasicTextField(
                value = value,
                onValueChange = { next ->
                    if (next.isBlank() || next.all { it.isDigit() }) {
                        onValueChange(next)
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = sketch.ink),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun DesktopInlineAction(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val sketch = MaterialTheme.workspaceSketch
    val alpha = if (enabled) 1f else 0.45f
    val borderColor = if (destructive) {
        sketch.bad.copy(alpha = 0.85f * alpha)
    } else {
        sketch.lineSoft.copy(alpha = alpha)
    }
    val textColor = if (destructive) {
        sketch.bad.copy(alpha = alpha)
    } else {
        sketch.inkSoft.copy(alpha = alpha)
    }
    Surface(
        modifier = modifier
            .handCursorOnHover(enabled = enabled)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = sketch.surfaceMuted,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
        )
    }
}

@Composable
private fun ProgramActivityFeedPanel(
    entries: List<ProgramActivityFeedEntry>,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val sketch = MaterialTheme.workspaceSketch
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, sketch.lineSoft),
        color = sketch.surfaceMuted,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            if (entries.isEmpty()) {
                Text(
                    text = "Nessun evento registrato",
                    style = MaterialTheme.typography.bodySmall,
                    color = sketch.inkMuted,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    items(entries, key = { it.id }) { item ->
                        val isError = item.kind == FeedbackBannerKind.ERROR
                        val borderColor = if (isError) {
                            sketch.bad.copy(alpha = 0.55f)
                        } else {
                            sketch.ok.copy(alpha = 0.55f)
                        }
                        val icon = if (isError) Icons.Filled.ErrorOutline else Icons.Filled.TaskAlt
                        val iconTint = if (isError) sketch.bad else sketch.ok
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, borderColor),
                            color = sketch.surfaceMuted,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = spacing.sm, vertical = spacing.xs),
                                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(34.dp)
                                        .background(iconTint, RoundedCornerShape(999.dp)),
                                )
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
                                            color = sketch.inkMuted,
                                        )
                                    }
                                }
                                Text(
                                    item.timestamp,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = sketch.inkMuted,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

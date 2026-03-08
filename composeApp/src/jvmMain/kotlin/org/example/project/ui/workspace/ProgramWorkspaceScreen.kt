package org.example.project.ui.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.assignments.domain.AssignmentWithPerson
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.WeeklyPart
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import org.example.project.feature.weeklyparts.domain.canBeMutated
import org.example.project.ui.assignments.PartAssignmentCard
import org.example.project.ui.assignments.PersonPickerDialog
import org.example.project.ui.components.DISPLAY_NUMBER_OFFSET
import org.example.project.ui.components.FeedbackBanner
import org.example.project.ui.components.FeedbackBannerKind
import org.example.project.ui.components.formatMonthYearLabel
import org.example.project.ui.components.formatWeekRangeLabel
import org.example.project.ui.components.handCursorOnHover
import org.example.project.ui.components.workspace.WorkspaceStateKind
import org.example.project.ui.components.workspace.WorkspaceStatePane
import org.example.project.ui.theme.spacing
import org.example.project.ui.theme.workspaceSketch
import org.koin.core.context.GlobalContext
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

internal enum class WeekSidebarStatus { CURRENT, PAST, COMPLETE, PARTIAL, EMPTY, SKIPPED }

private fun org.example.project.feature.weeklyparts.domain.WeekPlan.sidebarStatus(
    currentMonday: java.time.LocalDate,
    assignedSlots: Int,
    totalSlots: Int,
): WeekSidebarStatus = when {
    status == org.example.project.feature.weeklyparts.domain.WeekPlanStatus.SKIPPED -> WeekSidebarStatus.SKIPPED
    weekStartDate == currentMonday -> WeekSidebarStatus.CURRENT
    weekStartDate < currentMonday -> WeekSidebarStatus.PAST
    assignedSlots >= totalSlots && totalSlots > 0 -> WeekSidebarStatus.COMPLETE
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
    val incomingNotices = listOfNotNull(
        lifecycleState.notice,
        schemaState.notice,
        assignmentState.notice,
        personPickerState.notice,
        partEditorState.notice,
    )
    val activeNotice = incomingNotices.maxByOrNull { notice ->
        when (notice.kind) {
            FeedbackBannerKind.ERROR -> 3
            FeedbackBannerKind.WARNING -> 2
            FeedbackBannerKind.SUCCESS -> 1
        }
    }

    fun dismissAllNotices() {
        lifecycleVM.dismissNotice()
        schemaVM.dismissNotice()
        assignmentVM.dismissNotice()
        personPickerVM.dismissNotice()
        partEditorVM.dismissNotice()
    }

    LaunchedEffect(Unit) {
        lifecycleVM.onScreenEntered()
        assignmentVM.onScreenEntered()
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
                if (personPickerState.pickerSlot == 1) "Studente" else "Assistente"
            } else {
                null
            }
            val pickerWeekLabel = personPickerState.pickerWeekStartDate?.let {
                formatWeekRangeLabel(it, it.plusDays(6))
            } ?: ""
            PersonPickerDialog(
                partLabel = pickedPart.partType.label,
                slotLabel = slotLabel,
                weekLabel = pickerWeekLabel,
                searchTerm = personPickerState.pickerSearchTerm,
                sortGlobal = personPickerState.pickerSortGlobal,
                strictCooldown = assignmentState.assignmentSettings.strictCooldown,
                suggestions = personPickerState.pickerSuggestions,
                isLoading = personPickerState.isPickerLoading,
                isAssigning = personPickerState.isAssigning,
                onSearchChange = { personPickerVM.setPickerSearchTerm(it) },
                onToggleSort = { personPickerVM.togglePickerSort() },
                onStrictCooldownChange = {
                    assignmentVM.setStrictCooldown(it)
                    personPickerVM.reloadSuggestions()
                },
                onAssign = { personPickerVM.confirmAssignment(it, onSuccess = reloadData) },
                onDismiss = { personPickerVM.closePersonPicker() },
            )
        }
    }

    if (partEditorState.isPartEditorOpen) {
        val editingWeek = lifecycleState.selectedProgramWeeks.firstOrNull { it.id.value == partEditorState.partEditorWeekId }
        if (editingWeek != null) {
            val editingWeekAssignments = lifecycleState.selectedProgramAssignments[editingWeek.id.value] ?: emptyList()
            val editingAssignmentCountsByPart = editingWeekAssignments.groupingBy { it.weeklyPartId }.eachCount()
            PartEditorDialog(
                weekLabel = formatWeekRangeLabel(editingWeek.weekStartDate, editingWeek.weekStartDate.plusDays(6)),
                parts = partEditorState.partEditorParts,
                availablePartTypes = partEditorState.editablePartTypes,
                assignmentCountsByPart = editingAssignmentCountsByPart,
                isSaving = partEditorState.isSavingPartEditor,
                onAddPart = { partEditorVM.addPartToEditor(it.id) },
                onMovePart = { fromIndex, toIndex -> partEditorVM.movePartInEditor(fromIndex, toIndex) },
                onRemovePart = { partEditorVM.removePartFromEditor(it.id) },
                onSave = { partEditorVM.savePartEditor(onSuccess = reloadData) },
                onDismiss = { partEditorVM.dismissPartEditor() },
            )
        }
    }

    if (assignmentState.isAssignmentTicketsDialogOpen) {
        AssignmentTicketsDialog(
            monthLabel = lifecycleState.selectedProgram?.let { formatMonthYearLabel(it.month, it.year) } ?: "",
            tickets = assignmentState.assignmentTickets,
            isLoading = assignmentState.isLoadingAssignmentTickets,
            errorMessage = assignmentState.assignmentTicketsError,
            onDismiss = { assignmentVM.closeAssignmentTicketsDialog() },
        )
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
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        Text("Confermi rimozione dell'assegnazione per ${assignment.fullName}?")
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.handCursorOnHover().clickable {
                                assignmentVM.setSkipRemoveConfirm(!assignmentState.skipRemoveConfirm)
                            },
                        ) {
                            Checkbox(
                                checked = assignmentState.skipRemoveConfirm,
                                onCheckedChange = { assignmentVM.setSkipRemoveConfirm(it) },
                            )
                            Text("Non chiedere più", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
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
                    Column {
                        Text(buildDeleteProgramImpactMessage(impact))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Questa azione e' irreversibile.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
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

        FeedbackBanner(
            model = activeNotice,
            onDismissRequest = ::dismissAllNotices,
        )

        when {
            showLoadingState -> WorkspaceStatePane(
                kind = WorkspaceStateKind.Loading,
                message = "Caricamento programma in corso...",
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
                    lifecycleState.currentProgram?.id -> lifecycleState.currentProgram
                    else -> {
                        lifecycleState.futurePrograms.firstOrNull { it.id == selectedId }
                            ?: lifecycleState.currentProgram
                            ?: lifecycleState.futurePrograms.firstOrNull()
                    }
                }
            }
            val totalSlots = lifecycleState.selectedProgramWeeks
                .filter { it.status != org.example.project.feature.weeklyparts.domain.WeekPlanStatus.SKIPPED }
                .sumOf { week -> week.parts.sumOf { part -> part.partType.peopleCount } }
            val totalAssignments = lifecycleState.selectedProgramAssignments.values.sumOf { it.size }
            val assignmentsById = lifecycleState.selectedProgramAssignments.values
                .flatten()
                .associateBy { it.id.value }
            val hasFutureWeeks = lifecycleState.selectedProgramWeeks.any { it.weekStartDate > currentMonday }
            val fromFutureDate = remember(currentMonday) { currentMonday.plusWeeks(1) }
            val sketch = MaterialTheme.workspaceSketch

            var selectedWeekId by remember { mutableStateOf<String?>(null) }
            var pendingSkipWeek by remember { mutableStateOf(false) }
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
                    .weight(1f)
                    .fillMaxWidth(),
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
                                selected = lifecycleState.selectedProgramId == program.id,
                                accent = sketch.accent,
                                onClick = {
                                    lifecycleVM.selectProgram(program.id)
                                    selectedWeekId = null
                                },
                            )
                            if (program.id in schemaState.impactedProgramIds) {
                                ProgramMisalignedBadge()
                            }
                        }
                        lifecycleState.futurePrograms.forEach { program ->
                            ProgramMonthSelectorButton(
                                label = formatMonthYearLabel(program.month, program.year),
                                selected = lifecycleState.selectedProgramId == program.id,
                                accent = sketch.accent,
                                onClick = {
                                    lifecycleVM.selectProgram(program.id)
                                    selectedWeekId = null
                                },
                            )
                            if (program.id in schemaState.impactedProgramIds) {
                                ProgramMisalignedBadge()
                            }
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
                        items(lifecycleState.selectedProgramWeeks, key = { it.id.value }) { week ->
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

                    // Week status legend
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        SidebarLegendRow(label = "Completa", color = sketch.ok)
                        SidebarLegendRow(label = "Parziale", color = sketch.warn)
                        SidebarLegendRow(label = "Vuota", color = sketch.lineSoft)
                    }

                    // Footer
                    Box(Modifier.fillMaxWidth().height(1.dp).background(sketch.lineSoft.copy(alpha = 0.6f)))
                    Column(modifier = Modifier.padding(8.dp)) {
                        SidebarFooterButton(
                            label = if (schemaState.isRefreshingSchemas || schemaState.isRefreshingProgramFromSchemas)
                                "Aggiornamento..." else "Aggiorna schemi",
                            icon = Icons.Filled.Refresh,
                            onClick = {
                                schemaVM.refreshSchemasAndProgram(
                                    selectedProgramId = lifecycleState.selectedProgramId,
                                    onProgramRefreshComplete = reloadData,
                                )
                            },
                            enabled = !schemaState.isRefreshingSchemas && !schemaState.isRefreshingProgramFromSchemas,
                        )
                    }
                }
                // Sidebar / center divider
                Box(Modifier.fillMaxHeight().width(1.dp).background(sketch.lineSoft))

                // ── Center — week detail ──────────────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(sketch.panelMid),
                ) {
                    if (selectedWeek == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "Seleziona una settimana dalla sidebar",
                                style = MaterialTheme.typography.bodySmall,
                                color = sketch.inkMuted,
                            )
                        }
                    } else {
                        val weekAssignments = lifecycleState.selectedProgramAssignments[selectedWeek.id.value] ?: emptyList()
                        val weekTotalSlots = selectedWeek.parts.sumOf { it.partType.peopleCount }
                        val weekAssignedSlots = weekAssignments.size
                        val fraction = if (weekTotalSlots > 0) weekAssignedSlots.toFloat() / weekTotalSlots else 0f
                        val isSkipped = selectedWeek.status == org.example.project.feature.weeklyparts.domain.WeekPlanStatus.SKIPPED
                        val isCurrent = selectedWeek.weekStartDate == currentMonday
                        val canMutate = selectedWeek.canBeMutated(currentMonday)
                        val weekLabel = formatWeekRangeLabel(selectedWeek.weekStartDate, selectedWeek.weekStartDate.plusDays(6))
                        val monthLabel = selectedProgram?.let { formatMonthYearLabel(it.month, it.year) } ?: ""

                        if (pendingSkipWeek) {
                            val skipHasAssignments = weekAssignments.isNotEmpty()
                            AlertDialog(
                                onDismissRequest = { pendingSkipWeek = false },
                                title = { Text("Salta settimana") },
                                text = {
                                    if (skipHasAssignments) {
                                        Text("La settimana $weekLabel ha ${weekAssignments.size} assegnazioni che verranno cancellate. Continuare?")
                                    } else {
                                        Text("La settimana $weekLabel verrà esclusa dal programma. Continuare?")
                                    }
                                },
                                confirmButton = {
                                    DesktopInlineAction(
                                        label = "Salta",
                                        onClick = {
                                            pendingSkipWeek = false
                                            if (skipHasAssignments) {
                                                assignmentVM.confirmClearWeekAssignments(
                                                    weekStartDate = selectedWeek.weekStartDate,
                                                    onSuccess = { partEditorVM.skipWeek(selectedWeek, onSuccess = reloadData) },
                                                )
                                            } else {
                                                partEditorVM.skipWeek(selectedWeek, onSuccess = reloadData)
                                            }
                                        },
                                        destructive = true,
                                    )
                                },
                                dismissButton = {
                                    DesktopInlineAction(
                                        label = "Annulla",
                                        onClick = { pendingSkipWeek = false },
                                    )
                                },
                            )
                        }

                        WeekDetailHeader(
                            weekLabel = weekLabel,
                            monthLabel = monthLabel,
                            isCurrent = isCurrent,
                            isSkipped = isSkipped,
                            canMutate = canMutate,
                            onOpenPartEditor = { partEditorVM.openPartEditor(selectedWeek) },
                            onSkipWeek = { pendingSkipWeek = true },
                            onReactivate = { partEditorVM.reactivateWeek(selectedWeek, onSuccess = reloadData) },
                        )

                        if (!isSkipped && weekTotalSlots > 0) {
                            WeekCoverageStrip(
                                assigned = weekAssignedSlots,
                                total = weekTotalSlots,
                                fraction = fraction,
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                        ) {
                            when {
                                isSkipped -> {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Text(
                                            "Settimana saltata",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = sketch.inkSoft,
                                        )
                                        Text(
                                            "Questa settimana è stata esclusa dal programma.\nClicca 'Riattiva' per ripristinarla.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = sketch.inkMuted,
                                        )
                                    }
                                }
                                selectedWeek.parts.isEmpty() -> {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(
                                            "Nessuna parte configurata per questa settimana",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = sketch.inkMuted,
                                        )
                                    }
                                }
                                else -> {
                                    val assignmentsByPart = weekAssignments.groupBy { it.weeklyPartId }
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        selectedWeek.parts.chunked(2).forEach { rowParts ->
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(IntrinsicSize.Min),
                                            ) {
                                                rowParts.forEach { part ->
                                                    val partAssignments = assignmentsByPart[part.id] ?: emptyList()
                                                    PartAssignmentCard(
                                                        part = part,
                                                        assignments = partAssignments,
                                                        displayNumber = part.sortOrder + org.example.project.ui.components.DISPLAY_NUMBER_OFFSET,
                                                        readOnly = !canMutate,
                                                        showSexRuleChip = false,
                                                        onAssignSlot = { slot ->
                                                            personPickerVM.openPersonPicker(
                                                                weekStartDate = selectedWeek.weekStartDate,
                                                                weeklyPartId = part.id,
                                                                slot = slot,
                                                                selectedProgramWeeks = lifecycleState.selectedProgramWeeks,
                                                                selectedProgramAssignments = lifecycleState.selectedProgramAssignments,
                                                            )
                                                        },
                                                        onRemoveAssignment = { assignmentId ->
                                                            val assignment = assignmentsById[assignmentId.value]
                                                            if (assignment != null && !assignmentState.skipRemoveConfirm) {
                                                                pendingAssignmentRemoval = assignment
                                                            } else {
                                                                personPickerVM.removeAssignment(assignmentId, onSuccess = reloadData)
                                                            }
                                                        },
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .fillMaxHeight(),
                                                    )
                                                }
                                                if (rowParts.size == 1) Spacer(Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // Center / right panel divider
                Box(Modifier.fillMaxHeight().width(1.dp).background(sketch.lineSoft))

                // ── Right panel ────────────────────────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .width(296.dp)
                        .fillMaxHeight()
                        .background(sketch.panelRight),
                ) {
                    // Scrollable content
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Quick actions row
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (lifecycleState.selectedProgramId != null) {
                                ProgramRightPanelButton(
                                    label = if (assignmentState.isAutoAssigning) "..." else "Autoassegna",
                                    icon = Icons.Filled.PlayArrow,
                                    isPrimary = true,
                                    enabled = !assignmentState.isAutoAssigning,
                                    onClick = {
                                        lifecycleState.selectedProgramId?.let { programId ->
                                            assignmentVM.autoAssignSelectedProgram(programId, currentMonday, onSuccess = reloadData)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                ProgramRightPanelButton(
                                    label = if (assignmentState.isLoadingAssignmentTickets) "Generazione biglietti..." else "Biglietti assegnazioni",
                                    icon = Icons.Filled.Image,
                                    isPrimary = false,
                                    enabled = !assignmentState.isLoadingAssignmentTickets,
                                    onClick = {
                                        lifecycleState.selectedProgramId?.let { programId ->
                                            assignmentVM.openAssignmentTickets(programId)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                ProgramRightPanelButton(
                                    label = if (assignmentState.isPrintingProgram) "Generazione PDF programma..." else "Stampa PDF programma",
                                    icon = Icons.Filled.PictureAsPdf,
                                    isPrimary = false,
                                    iconColor = MaterialTheme.workspaceSketch.bad,
                                    enabled = !assignmentState.isPrintingProgram,
                                    onClick = {
                                        lifecycleState.selectedProgramId?.let { programId ->
                                            assignmentVM.printSelectedProgram(programId)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        // Coverage card (month level)
                        if (selectedProgram != null) {
                            var completeWeeks = 0
                            var partialWeeks = 0
                            var emptyWeeks = 0
                            lifecycleState.selectedProgramWeeks.forEach { week ->
                                if (week.status == org.example.project.feature.weeklyparts.domain.WeekPlanStatus.SKIPPED) return@forEach
                                val assigned = lifecycleState.selectedProgramAssignments[week.id.value]?.size ?: 0
                                val total = week.parts.sumOf { it.partType.peopleCount }
                                when {
                                    assigned >= total && total > 0 -> completeWeeks++
                                    assigned > 0 -> partialWeeks++
                                    else -> emptyWeeks++
                                }
                            }
                            ProgramCoverageCard(
                                programLabel = formatMonthYearLabel(selectedProgram.month, selectedProgram.year),
                                assigned = totalAssignments,
                                total = totalSlots,
                                completeWeeks = completeWeeks,
                                partialWeeks = partialWeeks,
                                emptyWeeks = emptyWeeks,
                            )
                        }

                        // Settings (collapsible, closed by default)
                        var settingsOpen by remember { mutableStateOf(false) }
                        RightPanelCollapsibleSection(
                            title = "IMPOSTAZIONI",
                            open = settingsOpen,
                            onToggle = { settingsOpen = !settingsOpen },
                        ) {
                            Box(modifier = Modifier.testTag("program-assignment-settings")) {
                                ProgramInlineAssignmentSettings(
                                    state = assignmentState.assignmentSettings,
                                    skipRemoveConfirm = assignmentState.skipRemoveConfirm,
                                    onSkipRemoveConfirmChange = assignmentVM::setSkipRemoveConfirm,
                                    onLeadWeightChange = assignmentVM::setLeadWeight,
                                    onAssistWeightChange = assignmentVM::setAssistWeight,
                                    onLeadCooldownChange = assignmentVM::setLeadCooldownWeeks,
                                    onAssistCooldownChange = assignmentVM::setAssistCooldownWeeks,
                                    onSave = assignmentVM::saveAssignmentSettings,
                                )
                            }
                        }

                        // Issues panel
                        if (assignmentState.autoAssignUnresolved.isNotEmpty()) {
                            ProgramIssuesPanel(issues = assignmentState.autoAssignUnresolved)
                        }
                    }

                    // Danger zone (pinned to bottom)
                    Box(Modifier.fillMaxWidth().height(1.dp).background(sketch.lineSoft.copy(alpha = 0.5f)))
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            "AZIONI IRREVERSIBILI",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                                fontSize = 9.sp,
                                letterSpacing = 0.6.sp,
                            ),
                            color = sketch.bad.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
                        )
                        if (hasFutureWeeks) {
                            ProgramDangerButton(
                                label = if (assignmentState.isClearingAssignments) "Svuotamento..." else "Svuota assegnazioni future",
                                icon = Icons.Filled.ClearAll,
                                enabled = lifecycleState.selectedProgramId != null && !assignmentState.isClearingAssignments,
                                onClick = {
                                    lifecycleState.selectedProgramId?.let { programId ->
                                        assignmentVM.requestClearAssignments(programId, fromFutureDate)
                                    }
                                },
                            )
                        }
                        if (lifecycleState.canDeleteSelectedProgram) {
                            ProgramDangerButton(
                                label = if (lifecycleState.isDeletingSelectedProgram) "Eliminazione..." else "Elimina mese",
                                icon = Icons.Filled.Delete,
                                enabled = !lifecycleState.isDeletingSelectedProgram,
                                onClick = { lifecycleVM.requestDeleteSelectedProgram() },
                            )
                        }
                    }
                }
            }

            // Status bar
            if (selectedProgram != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .background(sketch.panelLeft)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        formatMonthYearLabel(selectedProgram.month, selectedProgram.year),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp),
                        color = sketch.inkSoft,
                    )
                    Box(Modifier.height(12.dp).width(1.dp).background(sketch.lineStrong.copy(alpha = 0.5f)))
                    if (selectedWeek != null) {
                        Text(
                            "Settimana ${formatWeekRangeLabel(selectedWeek.weekStartDate, selectedWeek.weekStartDate.plusDays(6))}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp),
                            color = sketch.inkSoft,
                        )
                        Box(Modifier.height(12.dp).width(1.dp).background(sketch.lineStrong.copy(alpha = 0.5f)))
                    }
                    Text(
                        "$totalAssignments/$totalSlots slot",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp),
                        color = sketch.inkSoft,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        lifecycleState.today.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ITALIAN)),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp),
                        color = sketch.inkMuted,
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

@Composable
private fun ProgramMonthSelectorButton(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val sketch = MaterialTheme.workspaceSketch
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val border = when {
        selected -> accent.copy(alpha = 0.58f)
        focused -> accent.copy(alpha = 0.65f)
        else -> sketch.lineSoft
    }
    val container = when {
        selected -> accent.copy(alpha = 0.14f)
        focused -> accent.copy(alpha = 0.1f)
        hovered -> sketch.surfaceMuted
        else -> sketch.surface
    }
    val content = if (selected) accent else sketch.inkSoft

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .handCursorOnHover(enabled = enabled)
            .hoverable(interactionSource)
            .focusable(enabled = enabled, interactionSource = interactionSource)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(10.dp),
        color = container,
        border = BorderStroke(1.dp, border),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            style = MaterialTheme.typography.titleSmall,
            color = content.copy(alpha = if (enabled) 1f else 0.45f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProgramInlineAssignmentSettings(
    state: AssignmentSettingsUiState,
    skipRemoveConfirm: Boolean,
    onSkipRemoveConfirmChange: (Boolean) -> Unit,
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
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                DesktopNumericField(
                    label = "Peso conduzione",
                    value = state.leadWeight,
                    onValueChange = onLeadWeightChange,
                    onBlur = onSave,
                    modifier = Modifier.weight(1f),
                )
                DesktopNumericField(
                    label = "Peso assistenza",
                    value = state.assistWeight,
                    onValueChange = onAssistWeightChange,
                    onBlur = onSave,
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
                    onBlur = onSave,
                    modifier = Modifier.weight(1f),
                )
                DesktopNumericField(
                    label = "Cooldown assistenza",
                    value = state.assistCooldownWeeks,
                    onValueChange = onAssistCooldownChange,
                    onBlur = onSave,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Rimuovi senza conferma",
                    style = MaterialTheme.typography.bodySmall,
                    color = sketch.inkSoft,
                )
                DesktopToggle(
                    checked = skipRemoveConfirm,
                    onToggle = onSkipRemoveConfirmChange,
                )
            }
        }
    }
}

@Composable
private fun DesktopToggle(
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val sketch = MaterialTheme.workspaceSketch
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val trackColor = if (checked) {
        sketch.accent.copy(alpha = 0.72f)
    } else {
        sketch.lineStrong
    }
    Surface(
        modifier = Modifier
            .width(42.dp)
            .height(24.dp)
            .handCursorOnHover()
            .hoverable(interactionSource)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) { onToggle(!checked) },
        shape = RoundedCornerShape(999.dp),
        color = if (focused || hovered) trackColor.copy(alpha = 0.92f) else trackColor,
        border = BorderStroke(
            1.dp,
            if (focused) sketch.accent.copy(alpha = 0.8f) else trackColor.copy(alpha = 0.9f),
        ),
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
    onBlur: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sketch = MaterialTheme.workspaceSketch
    var wasFocused by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
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
            border = BorderStroke(
                1.dp,
                if (isFocused) sketch.accent.copy(alpha = 0.75f) else sketch.lineSoft,
            ),
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
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .onFocusChanged { focusState ->
                        if (wasFocused && !focusState.hasFocus && value.isNotBlank()) {
                            onBlur()
                        }
                        isFocused = focusState.hasFocus
                        wasFocused = focusState.hasFocus
                    },
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
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val alpha = if (enabled) 1f else 0.72f
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
    val containerColor = when {
        enabled && destructive && focused -> sketch.bad.copy(alpha = 0.14f)
        enabled && destructive && hovered -> sketch.bad.copy(alpha = 0.08f)
        enabled && focused -> sketch.accentSoft.copy(alpha = 0.8f)
        enabled && hovered -> sketch.surface
        else -> sketch.surfaceMuted
    }
    Surface(
        modifier = modifier
            .handCursorOnHover(enabled = enabled)
            .hoverable(interactionSource)
            .focusable(enabled = enabled, interactionSource = interactionSource)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        border = BorderStroke(
            1.dp,
            if (focused && enabled && !destructive) sketch.accent.copy(alpha = 0.72f) else borderColor,
        ),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

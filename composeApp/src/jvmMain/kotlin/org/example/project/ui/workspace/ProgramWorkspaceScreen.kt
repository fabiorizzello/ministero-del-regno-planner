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
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import org.example.project.ui.LocalPlanningToolbarActions
import org.example.project.ui.PlanningToolbarActions
import org.example.project.ui.theme.spacing
import org.koin.core.context.GlobalContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProgramWorkspaceScreen() {
    val viewModel = remember { GlobalContext.get().get<ProgramWorkspaceViewModel>() }
    val state by viewModel.state.collectAsState()
    val spacing = MaterialTheme.spacing
    val weekListState = rememberLazyListState()
    val setPlanningToolbarActions = LocalPlanningToolbarActions.current

    LaunchedEffect(Unit) { viewModel.onScreenEntered() }
    DisposableEffect(viewModel, setPlanningToolbarActions) {
        setPlanningToolbarActions(
            PlanningToolbarActions(
                onRefreshSchemas = { viewModel.refreshSchemasAndProgram() },
            ),
        )
        onDispose { setPlanningToolbarActions(PlanningToolbarActions()) }
    }

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
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
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
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
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
                Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
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
                Icon(Icons.Filled.ClearAll, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(spacing.xs))
                Text(if (state.isClearingAssignments) "Svuotamento..." else "Svuota assegnazioni")
            }
        }

        ProgramHeader(
            state = state,
            onSelectProgram = { viewModel.selectProgram(it) },
            onCreateNextProgram = { viewModel.createNextProgram() },
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
                                    onRequestClearWeekAssignments = { viewModel.requestClearWeekAssignments(week) },
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

@Composable
private fun ProgramWeekStickyHeader(
    week: WeekPlan,
    isCurrent: Boolean,
    isPast: Boolean,
) {
    val spacing = MaterialTheme.spacing
    val isSkipped = week.status == WeekPlanStatus.SKIPPED
    val containerColor = when {
        isCurrent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f)
        isSkipped -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.94f)
        isPast -> MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    }
    val borderColor = when {
        isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
        isSkipped -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.75f)
        isPast -> MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.9f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f))
            .padding(vertical = spacing.xs),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Settimana ${formatWeekRangeLabel(week.weekStartDate, week.weekStartDate.plusDays(6))}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isCurrent) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    ) {
                        Text(
                            "Corrente",
                            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xxs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (isSkipped) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                    ) {
                        Text(
                            "Saltata",
                            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xxs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
                if (isPast) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ) {
                        Text(
                            "Passata",
                            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xxs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    "Parti: ${week.parts.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProgramWeekCard(
    week: WeekPlan,
    isCurrent: Boolean,
    today: java.time.LocalDate,
    assignments: List<AssignmentWithPerson>,
    onReactivate: () -> Unit,
    onOpenPartEditor: () -> Unit,
    onRequestClearWeekAssignments: () -> Unit,
    onAssignSlot: (WeeklyPartId, Int) -> Unit,
    onRemoveAssignment: (AssignmentId) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val isSkipped = week.status == WeekPlanStatus.SKIPPED
    val isPast = week.weekStartDate < today
    val canMutate = !isSkipped && !isPast
    val assignmentsByPart = remember(assignments) { assignments.groupBy { it.weeklyPartId } }
    val partRows = remember(week.parts) { week.parts.chunked(2) }

    val borderColor = when {
        isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
        isSkipped -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.72f)
        isPast -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }
    val containerColor = when {
        isCurrent -> MaterialTheme.colorScheme.surface
        isSkipped -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.28f)
        isPast -> MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
        else -> MaterialTheme.colorScheme.surface
    }
    val accentColor = when {
        isCurrent -> MaterialTheme.colorScheme.primary
        isSkipped -> MaterialTheme.colorScheme.secondary
        isPast -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.tertiary
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCurrent) 4.dp else 3.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(containerColor),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(accentColor.copy(alpha = 0.76f)),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isSkipped && !isPast) {
                    OutlinedButton(
                        onClick = onReactivate,
                        modifier = Modifier.handCursorOnHover(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.secondary,
                        ),
                    ) { Text("Riattiva") }
                } else if (!isPast) {
                    OutlinedButton(
                        onClick = onOpenPartEditor,
                        modifier = Modifier.handCursorOnHover(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) { Text("Modifica parti") }
                }
                if (canMutate) {
                    OutlinedButton(
                        onClick = onRequestClearWeekAssignments,
                        modifier = Modifier.handCursorOnHover(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        ),
                    ) {
                        Icon(Icons.Filled.ClearAll, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(spacing.xs))
                        Text("Rimuovi assegnazioni")
                    }
                }
            }

            if (week.parts.isEmpty()) {
                Text(
                    "Nessuna parte configurata",
                    modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    partRows.forEach { rowParts ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (rowParts.size > 1) Modifier.height(IntrinsicSize.Min) else Modifier),
                            horizontalArrangement = Arrangement.spacedBy(spacing.md),
                            verticalAlignment = Alignment.Top,
                        ) {
                            rowParts.forEach { part ->
                                val partModifier = if (rowParts.size > 1) {
                                    Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                } else {
                                    Modifier.fillMaxWidth()
                                }
                                PartAssignmentCard(
                                    part = part,
                                    assignments = assignmentsByPart[part.id] ?: emptyList(),
                                    displayNumber = part.sortOrder + DISPLAY_NUMBER_OFFSET,
                                    readOnly = !canMutate,
                                    showSexRuleChip = false,
                                    onAssignSlot = { slot -> onAssignSlot(part.id, slot) },
                                    onRemoveAssignment = onRemoveAssignment,
                                    modifier = partModifier,
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
private fun PartEditorDialog(
    weekLabel: String,
    parts: List<WeeklyPart>,
    availablePartTypes: List<PartType>,
    isSaving: Boolean,
    onAddPart: (PartType) -> Unit,
    onMovePart: (Int, Int) -> Unit,
    onRemovePart: (WeeklyPart) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    var menuExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
    ) { from, to ->
        onMovePart(from.index, to.index)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.width(780.dp),
        ) {
            Column(
                modifier = Modifier.padding(spacing.xl),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Text("Modifica parti", style = MaterialTheme.typography.titleLarge)
                Text("Settimana $weekLabel", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Trascina le righe per riordinare le parti",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    FilledTonalButton(
                        onClick = { menuExpanded = true },
                        enabled = !isSaving,
                        modifier = Modifier.handCursorOnHover(enabled = !isSaving),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(spacing.xs))
                        Text("Aggiungi parte")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        availablePartTypes.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.label) },
                                onClick = {
                                    menuExpanded = false
                                    onAddPart(type)
                                },
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(end = 10.dp),
                ) {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(spacing.xs),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(spacing.sm),
                    ) {
                        items(parts.size, key = { index -> parts[index].id.value }) { index ->
                            val part = parts[index]
                            ReorderableItem(
                                state = reorderableState,
                                key = part.id.value,
                            ) { isDragging ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isDragging) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        if (isDragging) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                                        },
                                    ),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = spacing.md, vertical = spacing.sm),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.DragIndicator,
                                            contentDescription = "Trascina per riordinare",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .size(20.dp)
                                                .handCursorOnHover(enabled = !isSaving)
                                                .draggableHandle(enabled = !isSaving),
                                        )
                                        Text("${index + DISPLAY_NUMBER_OFFSET}", style = MaterialTheme.typography.labelLarge)
                                        Text(
                                            part.partType.label,
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        Text(
                                            "${part.partType.peopleCount} persone",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (!part.partType.fixed) {
                                            IconButton(
                                                onClick = { onRemovePart(part) },
                                                enabled = !isSaving,
                                                modifier = Modifier.handCursorOnHover(enabled = !isSaving),
                                            ) {
                                                Icon(Icons.Filled.Close, contentDescription = "Rimuovi parte")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(listState),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight(),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Annulla") }
                    Spacer(Modifier.width(spacing.sm))
                    Button(
                        onClick = onSave,
                        enabled = !isSaving,
                        modifier = Modifier.handCursorOnHover(enabled = !isSaving),
                    ) {
                        Text(if (isSaving) "Salvataggio..." else "Salva")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgramHeader(
    state: ProgramWorkspaceUiState,
    onSelectProgram: (String) -> Unit,
    onCreateNextProgram: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val current = state.currentProgram
    val future = state.futureProgram

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(
                "Programmi attivi",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!state.hasPrograms) {
                Text("Nessun programma disponibile. Crea il mese successivo.")
                return@Column
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                current?.let {
                    val isSelected = state.selectedProgramId == it.id.value
                    FilledTonalButton(
                        onClick = { onSelectProgram(it.id.value) },
                        enabled = !isSelected,
                        modifier = Modifier.handCursorOnHover(),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            disabledContainerColor = MaterialTheme.colorScheme.primary,
                            disabledContentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text("In corso ${formatMonthYearLabel(it.month, it.year)}")
                    }
                }
                future?.let {
                    val isSelected = state.selectedProgramId == it.id.value
                    FilledTonalButton(
                        onClick = { onSelectProgram(it.id.value) },
                        enabled = !isSelected,
                        modifier = Modifier.handCursorOnHover(),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            disabledContainerColor = MaterialTheme.colorScheme.tertiary,
                            disabledContentColor = MaterialTheme.colorScheme.onTertiary,
                        ),
                    ) {
                        Text("Prossimo ${formatMonthYearLabel(it.month, it.year)}")
                    }
                    if (state.futureNeedsSchemaRefresh) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                        ) {
                            Text(
                                "Template aggiornato, verificare",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }
                FilledTonalButton(
                    onClick = onCreateNextProgram,
                    enabled = !state.isCreatingProgram,
                    modifier = Modifier.handCursorOnHover(enabled = !state.isCreatingProgram),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(spacing.xs))
                    Text(if (state.isCreatingProgram) "Creazione..." else "Crea prossimo mese")
                }
            }
        }
    }
}

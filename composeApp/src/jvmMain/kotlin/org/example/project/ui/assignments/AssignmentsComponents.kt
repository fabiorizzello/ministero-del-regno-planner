package org.example.project.ui.assignments

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.assignments.domain.AssignmentWithPerson
import org.example.project.feature.assignments.domain.SuggestedProclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.weeklyparts.domain.WeeklyPart
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import org.example.project.ui.components.SexRuleChip
import org.example.project.ui.components.handCursorOnHover
import org.example.project.ui.theme.spacing
import org.example.project.ui.theme.workspaceSketch

// Column width constants for suggestion table
private val WEEKS_COLUMN_WIDTH = 120.dp
private val COOLDOWN_COLUMN_WIDTH = 130.dp
private val BUTTON_COLUMN_WIDTH = 132.dp
private val ASSIGNMENT_CHIP_BORDER_WIDTH = 1.dp
private val ASSIGNED_PERSON_CHIP_MAX_WIDTH = 420.dp

@Composable
fun PartAssignmentCard(
    part: WeeklyPart,
    assignments: List<AssignmentWithPerson>,
    displayNumber: Int,
    readOnly: Boolean,
    showSexRuleChip: Boolean = true,
    onAssignSlot: (slot: Int) -> Unit,
    onRemoveAssignment: (AssignmentId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val sketch = MaterialTheme.workspaceSketch
    val requiredSlots = part.partType.peopleCount
    val filledSlots = assignments.size.coerceAtMost(requiredSlots)
    val statusTone = when {
        filledSlots == 0 && requiredSlots > 0 -> 0
        filledSlots < requiredSlots -> 1
        else -> 2
    }
    val borderColor = when (statusTone) {
        0 -> sketch.lineSoft
        1 -> sketch.warn.copy(alpha = 0.6f)
        else -> sketch.ok.copy(alpha = 0.55f)
    }
    val badgeContainerColor = when (statusTone) {
        0 -> MaterialTheme.colorScheme.surfaceVariant
        1 -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val badgeTextColor = when (statusTone) {
        0 -> MaterialTheme.colorScheme.onSurfaceVariant
        1 -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    val containerColor = sketch.surface
    val statusLabel = when (statusTone) {
        0 -> "Vuota"
        1 -> "Parziale"
        else -> "Assegnata"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(spacing.cardRadius),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            // Header row: display number + label + sex rule chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Text(
                    text = "$displayNumber. ${part.partType.label}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = sketch.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showSexRuleChip) {
                        SexRuleChip(part.partType.sexRule)
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = badgeContainerColor,
                        border = BorderStroke(1.dp, borderColor.copy(alpha = 0.6f)),
                    ) {
                        Text(
                            text = statusLabel,
                            modifier = Modifier.padding(horizontal = spacing.xs, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // Content
            if (part.partType.peopleCount == 1) {
                // Single person slot, no role label
                val assignment = assignments.find { it.slot == 1 }
                SlotRow(
                    label = null,
                    assignment = assignment,
                    readOnly = readOnly,
                    onAssign = { onAssignSlot(1) },
                    onRemove = { assignment?.let { onRemoveAssignment(it.id) } },
                )
            } else {
                // Multiple slots with role labels
                for (slot in 1..part.partType.peopleCount) {
                    val label = if (slot == 1) "Studente" else "Assistente"
                    val assignment = assignments.find { it.slot == slot }
                    SlotRow(
                        label = label,
                        assignment = assignment,
                        readOnly = readOnly,
                        onAssign = { onAssignSlot(slot) },
                        onRemove = { assignment?.let { onRemoveAssignment(it.id) } },
                    )
                }
            }
        }
    }
}


@Composable
private fun SlotRow(
    label: String?,
    assignment: AssignmentWithPerson?,
    readOnly: Boolean,
    onAssign: () -> Unit,
    onRemove: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val sketch = MaterialTheme.workspaceSketch

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        if (label != null) {
            Text(
                text = "$label:",
                modifier = Modifier.width(106.dp),
                style = MaterialTheme.typography.labelMedium,
                color = sketch.inkSoft,
                maxLines = 1,
            )
        }

        if (assignment != null) {
            AssignedPersonChip(
                fullName = assignment.fullName,
                readOnly = readOnly,
                onOpenPicker = onAssign,
                onRemove = onRemove,
                modifier = Modifier.weight(1f),
            )
        } else {
            MissingAssignmentChip(
                readOnly = readOnly,
                onAssign = onAssign,
                modifier = Modifier
                    .weight(1f)
                    .handCursorOnHover(),
            )
        }
    }
}

@Composable
private fun AssignedPersonChip(
    fullName: String,
    readOnly: Boolean,
    onOpenPicker: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val sketch = MaterialTheme.workspaceSketch
    val shape = RoundedCornerShape(6.dp)
    val chipInteractionSource = remember { MutableInteractionSource() }
    val isHovered by chipInteractionSource.collectIsHoveredAsState()
    val containerColor = if (readOnly) {
        sketch.surfaceMuted
    } else if (isHovered) {
        sketch.ok.copy(alpha = 0.18f)
    } else {
        sketch.ok.copy(alpha = 0.12f)
    }
    val borderColor = if (readOnly) {
        sketch.lineSoft
    } else if (isHovered) {
        sketch.ok.copy(alpha = 0.6f)
    } else {
        sketch.ok.copy(alpha = 0.5f)
    }

    Row(
        modifier = modifier
            .widthIn(max = ASSIGNED_PERSON_CHIP_MAX_WIDTH)
            .heightIn(min = 30.dp)
            .handCursorOnHover(enabled = !readOnly)
            .hoverable(
                enabled = !readOnly,
                interactionSource = chipInteractionSource,
            )
            .clip(shape)
            .background(containerColor, shape)
            .border(ASSIGNMENT_CHIP_BORDER_WIDTH, borderColor, shape)
            .clickable(
                enabled = !readOnly,
                interactionSource = chipInteractionSource,
                indication = LocalIndication.current,
                onClick = onOpenPicker,
            )
            .padding(start = spacing.md, end = spacing.xs, top = spacing.xs, bottom = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Icon(
            imageVector = Icons.Filled.TaskAlt,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = if (readOnly) sketch.inkMuted else sketch.ok,
        )
        Text(
            text = fullName,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = sketch.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!readOnly) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(24.dp).handCursorOnHover(),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Rimuovi $fullName",
                    modifier = Modifier.size(14.dp),
                    tint = sketch.inkMuted,
                )
            }
        }
    }
}

@Composable
private fun MissingAssignmentChip(
    readOnly: Boolean,
    onAssign: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val sketch = MaterialTheme.workspaceSketch
    val shape = RoundedCornerShape(6.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val containerColor = when {
        readOnly -> sketch.surfaceMuted
        hovered -> sketch.accentSoft
        else -> Color.Transparent
    }
    val borderColor = when {
        readOnly -> sketch.lineSoft
        hovered -> sketch.accent.copy(alpha = 0.7f)
        else -> sketch.lineSoft
    }
    val contentColor = when {
        readOnly -> sketch.inkMuted
        hovered -> sketch.accent
        else -> sketch.inkMuted
    }

    Row(
        modifier = modifier
            .heightIn(min = 30.dp)
            .hoverable(interactionSource)
            .clip(shape)
            .background(containerColor, shape)
            .border(ASSIGNMENT_CHIP_BORDER_WIDTH, borderColor, shape)
            .clickable(enabled = !readOnly, onClick = onAssign)
            .padding(horizontal = spacing.md, vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Icon(
            if (readOnly) Icons.Filled.Person else Icons.Filled.Add,
            contentDescription = "Assegna",
            modifier = Modifier.size(16.dp),
            tint = contentColor,
        )
        Text(
            text = if (readOnly) "—" else "Assegna",
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
        )
    }
}

// --- Person Picker Dialog ---

@Composable
fun PersonPickerDialog(
    partLabel: String,
    slotLabel: String?,
    searchTerm: String,
    sortGlobal: Boolean,
    suggestions: List<SuggestedProclamatore>,
    isLoading: Boolean,
    isAssigning: Boolean,
    onSearchChange: (String) -> Unit,
    onToggleSort: () -> Unit,
    onAssign: (ProclamatoreId) -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val title = buildString {
        append("Assegna \u2014 $partLabel")
        if (slotLabel != null) append(" ($slotLabel)")
    }

    // Client-side filtering and sorting, memoized to avoid recomputation on every recomposition
    val sorted = remember(suggestions, searchTerm, sortGlobal) {
        val filtered = if (searchTerm.isBlank()) {
            suggestions
        } else {
            val lowerTerm = searchTerm.lowercase()
            suggestions.filter { s ->
                s.proclamatore.nome.lowercase().contains(lowerTerm) ||
                    s.proclamatore.cognome.lowercase().contains(lowerTerm)
            }
        }

        // Sorting: null (mai assegnato) first, then descending by weeks (longest ago first)
        if (sortGlobal) {
            filtered.sortedWith(compareByDescending<SuggestedProclamatore, Int?>(nullsLast()) { it.lastGlobalWeeks })
        } else {
            filtered.sortedWith(compareByDescending<SuggestedProclamatore, Int?>(nullsLast()) { it.lastForPartTypeWeeks })
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(spacing.cardRadius),
            tonalElevation = 6.dp,
            color = MaterialTheme.workspaceSketch.surface,
            border = BorderStroke(1.dp, MaterialTheme.workspaceSketch.lineSoft),
            modifier = Modifier.width(900.dp),
        ) {
            Column(
                modifier = Modifier.padding(spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(spacing.xl),
            ) {
                // Title + close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.handCursorOnHover(),
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Chiudi",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Search field
                OutlinedTextField(
                    value = searchTerm,
                    onValueChange = onSearchChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Cerca studente...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                )

                // Sort toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    Text(
                        text = "Ordina per:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        SortModeButton(
                            label = "Globale",
                            selected = sortGlobal,
                            onClick = { if (!sortGlobal) onToggleSort() },
                        )
                        SortModeButton(
                            label = "Per parte",
                            selected = !sortGlobal,
                            onClick = { if (sortGlobal) onToggleSort() },
                        )
                    }
                }

                // Content
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (sorted.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Nessuno studente disponibile per questa parte.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Verifica: idoneità alla parte, cooldown strict nelle Impostazioni.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        )
                    }
                } else {
                    // Header row
                    SuggestionHeaderRow()

                    HorizontalDivider()

                    // Suggestions list with scrollbar
                    Box(modifier = Modifier.heightIn(max = 400.dp)) {
                        val listState = rememberLazyListState()
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            itemsIndexed(sorted, key = { _, it -> it.proclamatore.id.value }) { index, suggestion ->
                                val zebraColor = if (index % 2 == 0) Color.Transparent
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

                                SuggestionRow(
                                    suggestion = suggestion,
                                    backgroundColor = zebraColor,
                                    isAssigning = isAssigning,
                                    onAssign = { onAssign(suggestion.proclamatore.id) },
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }
                        VerticalScrollbar(
                            adapter = rememberScrollbarAdapter(listState),
                            modifier = Modifier.align(Alignment.CenterEnd),
                        )
                    }
                }

            }
        }
    }
}

@Composable
private fun SuggestionHeaderRow() {
    val spacing = MaterialTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.workspaceSketch.surfaceMuted)
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Nome",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = "Ultima (globale)",
            modifier = Modifier.width(WEEKS_COLUMN_WIDTH),
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = "Ultima (parte)",
            modifier = Modifier.width(WEEKS_COLUMN_WIDTH),
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = "Cooldown",
            modifier = Modifier.width(COOLDOWN_COLUMN_WIDTH),
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.width(BUTTON_COLUMN_WIDTH))
    }
}

@Composable
private fun SuggestionRow(
    suggestion: SuggestedProclamatore,
    backgroundColor: Color,
    isAssigning: Boolean,
    onAssign: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val sketch = MaterialTheme.workspaceSketch
    val rowInteractionSource = remember { MutableInteractionSource() }
    val isHovered by rowInteractionSource.collectIsHoveredAsState()

    // Two-step confirm: first click arms the button, second click fires onAssign.
    // Applies only when the person is in cooldown (which implies strictCooldown=false,
    // since the use case filters them out otherwise).
    var awaitingCooldownConfirm by remember { mutableStateOf(false) }

    val rowBackgroundColor = when {
        isHovered -> sketch.accentSoft
        suggestion.inCooldown -> sketch.warn.copy(alpha = 0.07f)
        else -> backgroundColor
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .handCursorOnHover()
            .hoverable(interactionSource = rowInteractionSource)
            .background(rowBackgroundColor)
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${suggestion.proclamatore.nome} ${suggestion.proclamatore.cognome}",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = formatRecency(suggestion.lastGlobalDays, suggestion.lastGlobalWeeks),
            modifier = Modifier.width(WEEKS_COLUMN_WIDTH),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = formatRecency(suggestion.lastForPartTypeDays, suggestion.lastForPartTypeWeeks),
            modifier = Modifier.width(WEEKS_COLUMN_WIDTH),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Cooldown column — warning icon + amber text when overriding cooldown
        Row(
            modifier = Modifier.width(COOLDOWN_COLUMN_WIDTH),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (suggestion.inCooldown) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = sketch.warn,
                )
                Text(
                    text = "${suggestion.cooldownRemainingWeeks} sett.",
                    style = MaterialTheme.typography.bodySmall,
                    color = sketch.warn,
                )
            } else {
                Text(
                    text = "-",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Button — two-step confirm when overriding cooldown
        val buttonColor = if (suggestion.inCooldown) sketch.warn else MaterialTheme.colorScheme.primary
        val buttonLabel = when {
            isAssigning -> "..."
            suggestion.inCooldown && awaitingCooldownConfirm -> "Conferma"
            else -> "Assegna"
        }
        val buttonIcon = if (suggestion.inCooldown && awaitingCooldownConfirm) Icons.Filled.Warning else Icons.Filled.Add
        val buttonAction: () -> Unit = if (suggestion.inCooldown && !awaitingCooldownConfirm) {
            { awaitingCooldownConfirm = true }
        } else {
            onAssign
        }
        Surface(
            modifier = Modifier
                .width(BUTTON_COLUMN_WIDTH)
                .height(36.dp)
                .handCursorOnHover(enabled = !isAssigning)
                .clickable(enabled = !isAssigning, onClick = buttonAction),
            shape = RoundedCornerShape(6.dp),
            color = buttonColor.copy(alpha = if (isAssigning) 0.35f else 0.92f),
            border = BorderStroke(1.dp, buttonColor.copy(alpha = 0.9f)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = spacing.sm),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    buttonIcon,
                    contentDescription = buttonLabel,
                    modifier = Modifier.size(14.dp),
                    tint = Color.White,
                )
                Spacer(Modifier.width(MaterialTheme.spacing.xs))
                Text(
                    text = buttonLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun SortModeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
    }
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.handCursorOnHover().clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
        )
    }
}

private fun formatRecency(days: Int?, weeks: Int?): String = when {
    days == null -> "Mai assegnato"
    days == 0 -> "Oggi"
    days < 14 -> "$days giorni fa"
    else -> "${weeks ?: (days / 7)} settimane fa"
}

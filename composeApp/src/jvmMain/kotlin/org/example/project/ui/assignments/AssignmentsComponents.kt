package org.example.project.ui.assignments

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
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
import org.example.project.ui.components.SexRuleChip
import org.example.project.ui.components.handCursorOnHover
import org.example.project.ui.theme.spacing

// Column width constants for suggestion table
private val WEEKS_COLUMN_WIDTH = 120.dp
private val BUTTON_COLUMN_WIDTH = 132.dp
private val ASSIGNMENT_CHIP_BORDER_WIDTH = 1.dp

@Composable
internal fun PartAssignmentCard(
    part: WeeklyPart,
    assignments: List<AssignmentWithPerson>,
    displayNumber: Int,
    readOnly: Boolean,
    onAssignSlot: (slot: Int) -> Unit,
    onRemoveAssignment: (AssignmentId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(spacing.cardRadius),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            // Header row: display number + label + sex rule chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "$displayNumber. ${part.partType.label}",
                    style = MaterialTheme.typography.titleSmall,
                )
                SexRuleChip(part.partType.sexRule)
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
                    val label = if (slot == 1) "Proclamatore" else "Assistente"
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

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (label != null) {
            Text(
                text = "$label:",
                modifier = Modifier.width(110.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(spacing.md))
        }

        Spacer(Modifier.weight(1f))
        if (assignment != null) {
            AssignedPersonChip(
                fullName = assignment.fullName,
                readOnly = readOnly,
                onOpenPicker = onAssign,
                onRemove = onRemove,
            )
        } else {
            MissingAssignmentChip(
                readOnly = readOnly,
                onAssign = onAssign,
                modifier = Modifier.handCursorOnHover(),
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
    val shape = RoundedCornerShape(999.dp)
    val containerColor = if (readOnly) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    }
    val borderColor = if (readOnly) {
        MaterialTheme.colorScheme.outlineVariant
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    }

    Row(
        modifier = modifier
            .background(containerColor, shape)
            .border(ASSIGNMENT_CHIP_BORDER_WIDTH, borderColor, shape)
            .padding(start = spacing.lg, end = spacing.sm, top = spacing.sm, bottom = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        FilledTonalIconButton(
            onClick = onOpenPicker,
            enabled = !readOnly,
            modifier = Modifier.size(30.dp).handCursorOnHover(),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = if (readOnly) null else "Modifica assegnazione",
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = fullName,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!readOnly) {
            FilledIconButton(
                onClick = onRemove,
                modifier = Modifier.size(30.dp).handCursorOnHover(),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Rimuovi $fullName",
                    modifier = Modifier.size(20.dp),
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
    val shape = RoundedCornerShape(999.dp)

    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(ASSIGNMENT_CHIP_BORDER_WIDTH, MaterialTheme.colorScheme.outline, shape)
            .clickable(enabled = !readOnly, onClick = onAssign)
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Icon(
            Icons.Filled.PersonAdd,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (readOnly) "Non assegnato" else "Assegna",
            style = if (readOnly) {
                MaterialTheme.typography.titleSmall.copy(fontStyle = FontStyle.Italic)
            } else {
                MaterialTheme.typography.titleSmall
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// --- Person Picker Dialog ---

@Composable
internal fun PersonPickerDialog(
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
                        )
                    }
                }

                // Search field
                OutlinedTextField(
                    value = searchTerm,
                    onValueChange = onSearchChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Cerca proclamatore...") },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null)
                    },
                    singleLine = true,
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
                    FilterChip(
                        selected = sortGlobal,
                        onClick = { if (!sortGlobal) onToggleSort() },
                        label = { Text("Globale") },
                        modifier = Modifier.handCursorOnHover(),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                    FilterChip(
                        selected = !sortGlobal,
                        onClick = { if (sortGlobal) onToggleSort() },
                        label = { Text("Per parte") },
                        modifier = Modifier.handCursorOnHover(),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
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
                    Box(
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Nessun proclamatore disponibile per questa parte",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            items(sorted, key = { it.proclamatore.id.value }) { suggestion ->
                                SuggestionRow(
                                    suggestion = suggestion,
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
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
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
        Spacer(Modifier.width(BUTTON_COLUMN_WIDTH))
    }
}

@Composable
private fun SuggestionRow(
    suggestion: SuggestedProclamatore,
    isAssigning: Boolean,
    onAssign: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
            text = formatWeeksAgo(suggestion.lastGlobalWeeks),
            modifier = Modifier.width(WEEKS_COLUMN_WIDTH),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = formatWeeksAgo(suggestion.lastForPartTypeWeeks),
            modifier = Modifier.width(WEEKS_COLUMN_WIDTH),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onAssign,
            enabled = !isAssigning,
            modifier = Modifier
                .width(BUTTON_COLUMN_WIDTH)
                .height(36.dp)
                .handCursorOnHover(),
        ) {
            Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(MaterialTheme.spacing.xs))
            Text(
                text = "Assegna",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun formatWeeksAgo(weeks: Int?): String = when (weeks) {
    null -> "Mai assegnato"
    0 -> "Questa settimana"
    1 -> "1 settimana fa"
    else -> "$weeks settimane fa"
}

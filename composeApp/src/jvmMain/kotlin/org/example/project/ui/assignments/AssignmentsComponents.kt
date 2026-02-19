package org.example.project.ui.assignments

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.assignments.domain.AssignmentWithPerson
import org.example.project.feature.assignments.domain.SuggestedProclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.weeklyparts.domain.SexRule
import org.example.project.feature.weeklyparts.domain.WeeklyPart
import org.example.project.ui.theme.SemanticColors
import org.example.project.ui.components.handCursorOnHover
import org.example.project.ui.theme.spacing

// Column width constants for suggestion table
private val WEEKS_COLUMN_WIDTH = 120.dp
private val BUTTON_COLUMN_WIDTH = 110.dp

@Composable
internal fun PartAssignmentCard(
    part: WeeklyPart,
    assignments: List<AssignmentWithPerson>,
    displayNumber: Int,
    readOnly: Boolean,
    onAssignSlot: (slot: Int) -> Unit,
    onRemoveAssignment: (AssignmentId) -> Unit,
) {
    val spacing = MaterialTheme.spacing

    Card(
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
private fun SexRuleChip(sexRule: SexRule) {
    val (label, chipColor) = when (sexRule) {
        SexRule.UOMO -> "UOMO" to SemanticColors.blue
        SexRule.LIBERO -> "LIBERO" to SemanticColors.grey
    }
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = chipColor.copy(alpha = 0.15f),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = chipColor,
        )
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

        if (assignment != null) {
            Text(
                text = assignment.fullName,
                modifier = if (readOnly) {
                    Modifier.weight(1f)
                } else {
                    Modifier
                        .weight(1f)
                        .handCursorOnHover()
                        .clickable { onAssign() }
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!readOnly) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.handCursorOnHover(),
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Rimuovi ${assignment.fullName}",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        } else {
            if (readOnly) {
                Text(
                    text = "Non assegnato",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = onAssign,
                    modifier = Modifier.handCursorOnHover(),
                ) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(MaterialTheme.spacing.xs))
                    Text("Assegna")
                }
            }
        }
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

    // Client-side filtering by search term
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
    val sorted = if (sortGlobal) {
        filtered.sortedWith(compareByDescending<SuggestedProclamatore, Int?>(nullsLast()) { it.lastGlobalWeeks })
    } else {
        filtered.sortedWith(compareByDescending<SuggestedProclamatore, Int?>(nullsLast()) { it.lastForPartTypeWeeks })
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
            modifier = Modifier.width(BUTTON_COLUMN_WIDTH).handCursorOnHover(),
        ) {
            Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(MaterialTheme.spacing.xs))
            Text("Assegna", style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun formatWeeksAgo(weeks: Int?): String = when (weeks) {
    null -> "Mai assegnato"
    0 -> "Questa settimana"
    1 -> "1 settimana fa"
    else -> "$weeks settimane fa"
}

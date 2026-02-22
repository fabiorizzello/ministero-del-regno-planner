package org.example.project.ui.assignments

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.nio.file.Files
import java.nio.file.Path
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.assignments.domain.AssignmentWithPerson
import org.example.project.feature.assignments.domain.SuggestedProclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.weeklyparts.domain.WeeklyPart
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import org.example.project.ui.components.SexRuleChip
import org.example.project.ui.components.handCursorOnHover
import org.example.project.ui.theme.spacing
import org.jetbrains.skia.Image

// Column width constants for suggestion table
private val WEEKS_COLUMN_WIDTH = 120.dp
private val COOLDOWN_COLUMN_WIDTH = 130.dp
private val BUTTON_COLUMN_WIDTH = 132.dp
private val ASSIGNMENT_CHIP_BORDER_WIDTH = 1.dp
private val ASSIGNED_PERSON_CHIP_MAX_WIDTH = 420.dp

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
        shape = RoundedCornerShape(spacing.cardRadius + 2.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
    val chipInteractionSource = remember { MutableInteractionSource() }
    val isHovered by chipInteractionSource.collectIsHoveredAsState()
    val containerColor = if (readOnly) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    } else if (isHovered) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f)
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    }
    val borderColor = if (readOnly) {
        MaterialTheme.colorScheme.outlineVariant
    } else if (isHovered) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    }

    Row(
        modifier = modifier
            .widthIn(max = ASSIGNED_PERSON_CHIP_MAX_WIDTH)
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
            .padding(start = spacing.lg, end = spacing.xs, top = spacing.sm, bottom = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Text(
            text = fullName,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!readOnly) {
            FilledIconButton(
                onClick = onRemove,
                modifier = Modifier.size(36.dp).handCursorOnHover(),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Rimuovi $fullName",
                    modifier = Modifier.size(22.dp),
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
            .clip(shape)
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
internal fun OutputWizardDialog(
    parts: List<WeeklyPart>,
    selectedPartIds: Set<WeeklyPartId>,
    onTogglePart: (WeeklyPart) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    onGenerate: () -> Unit,
    onExportZip: () -> Unit,
    generatedPdfPath: Path?,
    generatedImagePaths: List<Path>,
    generatedZipPath: Path?,
    isGeneratingOutput: Boolean,
    isExportingZip: Boolean,
    outputStatus: String?,
    onDismiss: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.82f),
            shape = RoundedCornerShape(spacing.cardRadius),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(spacing.xl),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Text("Wizard output assegnazioni", style = MaterialTheme.typography.titleMedium)
                Text(
                    "1) Seleziona parti  2) Genera PDF + immagini  3) Esporta ZIP",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    items(parts, key = { it.id.value }) { part ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(part.partType.label, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = "Slot: ${part.partType.peopleCount}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Checkbox(
                                checked = selectedPartIds.contains(part.id),
                                onCheckedChange = { onTogglePart(part) },
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        OutlinedButton(onClick = onSelectAll, modifier = Modifier.handCursorOnHover()) {
                            Text("Tutte")
                        }
                        OutlinedButton(onClick = onClearAll, modifier = Modifier.handCursorOnHover()) {
                            Text("Nessuna")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        Button(
                            onClick = onGenerate,
                            enabled = selectedPartIds.isNotEmpty() && !isGeneratingOutput,
                            modifier = Modifier.handCursorOnHover(enabled = selectedPartIds.isNotEmpty() && !isGeneratingOutput),
                        ) {
                            Text(if (isGeneratingOutput) "Generazione..." else "Genera output")
                        }
                        OutlinedButton(
                            onClick = onExportZip,
                            enabled = generatedImagePaths.isNotEmpty() && !isExportingZip,
                            modifier = Modifier.handCursorOnHover(enabled = generatedImagePaths.isNotEmpty() && !isExportingZip),
                        ) {
                            Text(if (isExportingZip) "ZIP..." else "Esporta ZIP")
                        }
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.handCursorOnHover()) {
                            Text("Chiudi")
                        }
                    }
                }

                if (!outputStatus.isNullOrBlank()) {
                    Text(
                        outputStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (generatedPdfPath != null) {
                    Text(
                        "PDF: ${generatedPdfPath.fileName}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (generatedZipPath != null) {
                    Text(
                        "ZIP: ${generatedZipPath.fileName}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                if (generatedImagePaths.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Preview immagini", style = MaterialTheme.typography.titleSmall)
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 170.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                        horizontalArrangement = Arrangement.spacedBy(spacing.md),
                        verticalArrangement = Arrangement.spacedBy(spacing.md),
                    ) {
                        items(generatedImagePaths, key = { it.fileName.toString() }) { imagePath ->
                            OutputImagePreviewCard(imagePath)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OutputImagePreviewCard(
    imagePath: Path,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val bitmap = remember(imagePath) { loadImageBitmap(imagePath) }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = imagePath.fileName.toString(),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(
                        "Preview non disponibile",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = imagePath.fileName.toString(),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun loadImageBitmap(path: Path): ImageBitmap? {
    if (!Files.exists(path) || !Files.isRegularFile(path)) return null
    return runCatching {
        val bytes = Files.readAllBytes(path)
        Image.makeFromEncoded(bytes).toComposeImageBitmap()
    }.getOrNull()
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
        val cooldownLabel = if (suggestion.inCooldown) {
            "${suggestion.cooldownRemainingWeeks} sett."
        } else {
            "-"
        }
        Text(
            text = cooldownLabel,
            modifier = Modifier.width(COOLDOWN_COLUMN_WIDTH),
            style = MaterialTheme.typography.bodySmall,
            color = if (suggestion.inCooldown) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
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

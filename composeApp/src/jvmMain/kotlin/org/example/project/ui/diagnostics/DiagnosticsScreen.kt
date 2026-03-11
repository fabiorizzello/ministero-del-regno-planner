package org.example.project.ui.diagnostics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.example.project.ui.components.FeedbackBanner
import org.example.project.ui.components.FeedbackBannerKind
import org.example.project.ui.components.handCursorOnHover
import org.example.project.ui.components.workspace.WorkspaceStateKind
import org.example.project.ui.components.workspace.WorkspaceStatePane
import org.example.project.ui.theme.spacing
import org.example.project.ui.theme.workspaceSketch
import org.koin.core.context.GlobalContext

@Composable
fun DiagnosticsScreen() {
    val viewModel = remember { GlobalContext.get().get<DiagnosticsViewModel>() }
    val state by viewModel.state.collectAsState()
    val spacing = MaterialTheme.spacing
    val sectionCardShape = RoundedCornerShape(spacing.cardRadius + 2.dp)
    val sectionCardBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.78f))

    LaunchedEffect(Unit) { viewModel.onScreenEntered() }

    if (state.showCleanupConfirmDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissCleanupDialog() },
            title = { Text("Conferma pulizia dati storici") },
            text = {
                val preview = state.cleanupPreview
                Text(
                    "Eliminare i dati precedenti a ${formatCutoffDate(state.selectedRetention)}?\n" +
                        "Settimane: ${preview.weekPlans}, Parti: ${preview.weeklyParts}, Assegnazioni: ${preview.assignments}.\n" +
                        "Vengono eliminate solo settimane programma storiche e relativi collegamenti.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmCleanup() },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Elimina")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissCleanupDialog() }) {
                    Text("Annulla")
                }
            },
        )
    }

    val sketch = MaterialTheme.workspaceSketch
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(sketch.windowBackground)
            .verticalScroll(rememberScrollState())
            .padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
            Text("Diagnostica", style = MaterialTheme.typography.headlineMedium)

            FeedbackBanner(
                model = state.notice,
                onDismissRequest = { viewModel.dismissNotice() },
            )

            when {
                state.isLoading -> WorkspaceStatePane(
                    kind = WorkspaceStateKind.Loading,
                    message = "Caricamento diagnostica in corso...",
                )
                state.notice?.kind == FeedbackBannerKind.ERROR -> WorkspaceStatePane(
                    kind = WorkspaceStateKind.Error,
                    message = "Alcune informazioni diagnostiche non sono disponibili.",
                )
            }

            Card(
            modifier = Modifier.fillMaxWidth(),
            shape = sectionCardShape,
            border = sectionCardBorder,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(spacing.xl),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Text("Informazioni applicazione", style = MaterialTheme.typography.titleMedium)
                DiagnosticsInfoRow(label = "Versione", value = state.appVersion)
                DiagnosticsPathRow(label = "DB", path = state.dbPath)
                DiagnosticsPathRow(label = "Log", path = state.logsPath)
                DiagnosticsPathRow(label = "Export", path = state.exportsPath)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    DiagnosticsTooltipWrap("Crea un archivio ZIP con database, log recenti e metadata di supporto") {
                        OutlinedButton(
                            onClick = { viewModel.exportDiagnosticsBundle() },
                            enabled = !state.isExporting && !state.isCleaning,
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .handCursorOnHover(enabled = !state.isExporting && !state.isCleaning),
                            elevation = diagnosticsFlatButtonElevation(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        ) {
                            if (state.isExporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .width(16.dp)
                                        .height(16.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                Text("Esportazione...")
                            } else {
                                Icon(Icons.Filled.FileOpen, contentDescription = "Esporta diagnostica")
                                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                Text("Esporta diagnostica")
                            }
                        }
                    }
                    DiagnosticsOutlinedActionButton(
                        label = "Apri cartella export",
                        icon = Icons.Filled.FolderOpen,
                        onClick = { viewModel.openExportsFolder() },
                        tooltip = "Apre la cartella dove vengono salvati ZIP diagnostici e file esportati",
                        modifier = Modifier.weight(1f),
                    )
                    DiagnosticsOutlinedActionButton(
                        label = "Copia info supporto",
                        icon = Icons.Filled.ContentCopy,
                        onClick = { viewModel.copySupportInfo() },
                        tooltip = "Copia negli appunti versione app, percorsi e dimensioni utili per assistenza",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

            Card(
            modifier = Modifier.fillMaxWidth(),
            shape = sectionCardShape,
            border = sectionCardBorder,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(spacing.xl),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Text("Occupazione disco", style = MaterialTheme.typography.titleMedium)
                DiagnosticsInfoRow(label = "Database", value = formatBytes(state.dbSizeBytes))
                DiagnosticsInfoRow(label = "Log", value = formatBytes(state.logsSizeBytes))
                DiagnosticsInfoRow(label = "Totale", value = formatBytes(state.dbSizeBytes + state.logsSizeBytes))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    DiagnosticsOutlinedActionButton(
                        label = "Aggiorna spazio",
                        icon = Icons.Filled.Refresh,
                        onClick = { viewModel.refreshStorageUsage() },
                        enabled = !state.isLoading,
                        tooltip = "Ricalcola la dimensione corrente di database e log",
                        modifier = Modifier.weight(1f),
                    )
                    DiagnosticsOutlinedActionButton(
                        label = "Apri cartella log",
                        icon = Icons.Filled.FolderOpen,
                        onClick = { viewModel.openLogsFolder() },
                        tooltip = "Apre la cartella dei log dell'applicazione",
                        modifier = Modifier.weight(1f),
                    )
                    DiagnosticsOutlinedActionButton(
                        label = "Apri cartella dati",
                        icon = Icons.Filled.FolderOpen,
                        onClick = { viewModel.openDataFolder() },
                        tooltip = "Apre la cartella che contiene il database locale",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

            Card(
            modifier = Modifier.fillMaxWidth(),
            shape = sectionCardShape,
            border = sectionCardBorder,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(spacing.xl),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Text("Pulizia dati storici", style = MaterialTheme.typography.titleMedium)
                Text(
                    retentionMeaning(state.selectedRetention),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    CLEANUP_SCOPE_TEXT,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    CLEANUP_EXCLUSIONS_TEXT,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    DiagnosticsRetentionOption.entries.forEach { option ->
                        DiagnosticsRetentionChip(
                            option = option,
                            selected = state.selectedRetention == option,
                            enabled = !state.isCleaning && !state.isExporting,
                            onClick = { viewModel.selectRetention(option) },
                        )
                    }
                }

                DiagnosticsInfoRow(label = "Data limite", value = formatCutoffDate(state.selectedRetention))
                DiagnosticsInfoRow(
                    label = "Anteprima",
                    value = "settimane ${state.cleanupPreview.weekPlans}, " +
                        "parti ${state.cleanupPreview.weeklyParts}, " +
                        "assegnazioni ${state.cleanupPreview.assignments}",
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    DiagnosticsOutlinedActionButton(
                        label = "Aggiorna anteprima",
                        icon = Icons.Filled.Refresh,
                        onClick = { viewModel.refreshCleanupPreview() },
                        enabled = !state.isCleaning && !state.isExporting,
                        tooltip = "Ricalcola quante settimane, parti e assegnazioni verrebbero eliminate",
                        modifier = Modifier.weight(1f),
                    )
                    DiagnosticsTooltipWrap("Avvia la rimozione definitiva dei dati storici inclusi nell'anteprima") {
                        Button(
                            onClick = { viewModel.requestCleanup() },
                            enabled = state.cleanupPreview.hasData && !state.isCleaning && !state.isExporting,
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .handCursorOnHover(
                                    enabled = state.cleanupPreview.hasData && !state.isCleaning && !state.isExporting,
                                ),
                            elevation = diagnosticsFlatButtonElevation(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                        ) {
                            if (state.isCleaning) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .width(18.dp)
                                        .height(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onError,
                                )
                                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                Text("Pulizia in corso...")
                            } else {
                                Icon(Icons.Filled.Delete, contentDescription = "Elimina dati storici")
                                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                Text("Elimina dati storici")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatCutoffDate(option: DiagnosticsRetentionOption): String {
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ITALIAN)
    return option.cutoffDate().format(formatter)
}

private fun retentionMeaning(option: DiagnosticsRetentionOption): String =
    "Con \"${option.label}\" mantieni gli ultimi ${option.months} mesi e rimuovi le settimane piu' vecchie della data limite."

private const val CLEANUP_SCOPE_TEXT =
    "Vengono eliminati: settimane passate, parti collegate e relative assegnazioni."

private const val CLEANUP_EXCLUSIONS_TEXT =
    "Non vengono toccati: studenti, tipi di parte, file di log e file esportati."

@Composable
private fun DiagnosticsRetentionChip(
    option: DiagnosticsRetentionOption,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderWidth = if (isFocused) 2.dp else 1.dp
    val borderColor = when {
        !enabled && selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        !enabled -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        isFocused -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(option.label) },
        modifier = modifier.handCursorOnHover(enabled = enabled),
        interactionSource = interactionSource,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            disabledSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        ),
        elevation = diagnosticsFlatFilterChipElevation(),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = borderColor,
            selectedBorderColor = borderColor,
            disabledBorderColor = borderColor,
            disabledSelectedBorderColor = borderColor,
            borderWidth = borderWidth,
            selectedBorderWidth = borderWidth,
        ),
    )
}

@Composable
private fun DiagnosticsOutlinedActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tooltip: String? = null,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor = when {
        !enabled -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        isFocused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
        isHovered -> MaterialTheme.colorScheme.primary.copy(alpha = 0.58f)
        else -> MaterialTheme.colorScheme.outline
    }
    val containerColor = when {
        !enabled -> MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
        isFocused -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.46f)
        isHovered -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else -> MaterialTheme.colorScheme.surface
    }
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    }
    DiagnosticsTooltipWrap(tooltip) {
        Surface(
            modifier = modifier
                .height(34.dp)
                .handCursorOnHover(enabled = enabled)
                .hoverable(interactionSource, enabled = enabled)
                .focusable(enabled = enabled, interactionSource = interactionSource)
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
            shape = RoundedCornerShape(999.dp),
            border = BorderStroke(if (isFocused) 1.5.dp else 1.dp, borderColor),
            color = containerColor,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    modifier = Modifier.size(14.dp),
                    tint = contentColor,
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiagnosticsTooltipWrap(
    tooltip: String?,
    content: @Composable () -> Unit,
) {
    if (tooltip.isNullOrBlank()) {
        content()
    } else {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                positioning = TooltipAnchorPosition.Above,
            ),
            tooltip = { PlainTooltip { Text(tooltip) } },
            state = rememberTooltipState(),
        ) {
            content()
        }
    }
}

@Composable
private fun diagnosticsFlatButtonElevation(): androidx.compose.material3.ButtonElevation =
    ButtonDefaults.buttonElevation(
        defaultElevation = 0.dp,
        pressedElevation = 0.dp,
        focusedElevation = 0.dp,
        hoveredElevation = 0.dp,
        disabledElevation = 0.dp,
    )

@Composable
private fun diagnosticsFlatFilterChipElevation() =
    FilterChipDefaults.filterChipElevation(
        elevation = 0.dp,
        pressedElevation = 0.dp,
        focusedElevation = 0.dp,
        hoveredElevation = 0.dp,
        draggedElevation = 0.dp,
        disabledElevation = 0.dp,
    )

@Composable
private fun DiagnosticsInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DiagnosticsPathRow(label: String, path: String) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(60.dp),
        )
        SelectionContainer {
            Box(
                modifier = Modifier
                    .wrapContentWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    path,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = -1
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return String.format(Locale.ITALIAN, "%.1f %s", value, units[unitIndex])
}

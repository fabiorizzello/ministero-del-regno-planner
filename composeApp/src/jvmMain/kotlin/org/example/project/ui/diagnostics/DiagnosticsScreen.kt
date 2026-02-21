package org.example.project.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.example.project.ui.components.FeedbackBanner
import org.example.project.ui.components.handCursorOnHover
import org.example.project.ui.theme.spacing
import org.koin.core.context.GlobalContext

@Composable
fun DiagnosticsScreen() {
    val viewModel = remember { GlobalContext.get().get<DiagnosticsViewModel>() }
    val state by viewModel.state.collectAsState()
    val spacing = MaterialTheme.spacing

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
                        "Vengono eliminati solo programmi settimanali storici e relativi collegamenti.",
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        Text("Diagnostica", style = MaterialTheme.typography.headlineMedium)

        FeedbackBanner(
            model = state.notice,
            onDismissRequest = { viewModel.dismissNotice() },
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(spacing.xl),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Text("Informazioni applicazione", style = MaterialTheme.typography.titleMedium)
                SelectionContainer { Text("Versione app: ${state.appVersion}") }
                SelectionContainer { Text("DB: ${state.dbPath}") }
                SelectionContainer { Text("Log: ${state.logsPath}") }
                SelectionContainer { Text("Export: ${state.exportsPath}") }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    Button(
                        onClick = { viewModel.exportDiagnosticsBundle() },
                        enabled = !state.isExporting && !state.isCleaning,
                        modifier = Modifier
                            .weight(1f)
                            .handCursorOnHover(enabled = !state.isExporting && !state.isCleaning),
                        elevation = diagnosticsFlatButtonElevation(),
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
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
                    DiagnosticsOutlinedActionButton(
                        label = "Apri cartella export",
                        icon = Icons.Filled.FolderOpen,
                        onClick = { viewModel.openExportsFolder() },
                        modifier = Modifier.weight(1f),
                    )
                    DiagnosticsOutlinedActionButton(
                        label = "Copia info supporto",
                        icon = Icons.Filled.ContentCopy,
                        onClick = { viewModel.copySupportInfo() },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(spacing.xl),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Text("Occupazione disco", style = MaterialTheme.typography.titleMedium)
                Text("Database: ${formatBytes(state.dbSizeBytes)}")
                Text("Log: ${formatBytes(state.logsSizeBytes)}")
                Text("Totale: ${formatBytes(state.dbSizeBytes + state.logsSizeBytes)}")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    DiagnosticsOutlinedActionButton(
                        label = "Aggiorna spazio",
                        icon = Icons.Filled.Refresh,
                        onClick = { viewModel.refreshStorageUsage() },
                        enabled = !state.isLoading,
                        modifier = Modifier.weight(1f),
                    )
                    DiagnosticsOutlinedActionButton(
                        label = "Apri cartella log",
                        icon = Icons.Filled.FolderOpen,
                        onClick = { viewModel.openLogsFolder() },
                        modifier = Modifier.weight(1f),
                    )
                    DiagnosticsOutlinedActionButton(
                        label = "Apri cartella dati",
                        icon = Icons.Filled.FolderOpen,
                        onClick = { viewModel.openDataFolder() },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(spacing.xl),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Text("Pulizia dati storici", style = MaterialTheme.typography.titleMedium)
                Text(retentionMeaning(state.selectedRetention))
                Text(CLEANUP_SCOPE_TEXT)
                Text(CLEANUP_EXCLUSIONS_TEXT)

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

                Text("Data limite: ${formatCutoffDate(state.selectedRetention)}")
                Text(
                    "Anteprima: settimane ${state.cleanupPreview.weekPlans}, " +
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
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = { viewModel.requestCleanup() },
                        enabled = state.cleanupPreview.hasData && !state.isCleaning && !state.isExporting,
                        modifier = Modifier
                            .weight(1f)
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

private fun formatCutoffDate(option: DiagnosticsRetentionOption): String {
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ITALIAN)
    return option.cutoffDate().format(formatter)
}

private fun retentionMeaning(option: DiagnosticsRetentionOption): String =
    "Con \"${option.label}\" mantieni gli ultimi ${option.months} mesi e rimuovi le settimane piu' vecchie della data limite."

private const val CLEANUP_SCOPE_TEXT =
    "Vengono eliminati: settimane passate, parti collegate e relative assegnazioni."

private const val CLEANUP_EXCLUSIONS_TEXT =
    "Non vengono toccati: proclamatori, tipi di parte, file di log e file esportati."

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
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.handCursorOnHover(enabled = enabled),
        elevation = diagnosticsFlatButtonElevation(),
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    ) {
        Icon(icon, contentDescription = label)
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(label)
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


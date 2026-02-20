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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
                TextButton(onClick = { viewModel.confirmCleanup() }) {
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    Button(
                        onClick = { viewModel.exportDiagnosticsBundle() },
                        enabled = !state.isExporting && !state.isCleaning,
                        modifier = Modifier.handCursorOnHover(),
                    ) {
                        if (state.isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .width(16.dp)
                                    .height(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(spacing.xs))
                            Text("Esportazione...")
                        } else {
                            Text("Esporta diagnostica")
                        }
                    }
                    OutlinedButton(
                        onClick = { viewModel.openExportsFolder() },
                        modifier = Modifier.handCursorOnHover(),
                    ) {
                        Text("Apri cartella export")
                    }
                    OutlinedButton(
                        onClick = { viewModel.copySupportInfo() },
                        modifier = Modifier.handCursorOnHover(),
                    ) {
                        Text("Copia info supporto")
                    }
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

                Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                    OutlinedButton(
                        onClick = { viewModel.refreshStorageUsage() },
                        enabled = !state.isLoading,
                        modifier = Modifier.handCursorOnHover(),
                    ) {
                        Text("Aggiorna spazio")
                    }
                    OutlinedButton(
                        onClick = { viewModel.openLogsFolder() },
                        modifier = Modifier.handCursorOnHover(),
                    ) {
                        Text("Apri cartella log")
                    }
                    OutlinedButton(
                        onClick = { viewModel.openDataFolder() },
                        modifier = Modifier.handCursorOnHover(),
                    ) {
                        Text("Apri cartella dati")
                    }
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
                        FilterChip(
                            selected = state.selectedRetention == option,
                            onClick = { viewModel.selectRetention(option) },
                            label = { Text(option.label) },
                            modifier = Modifier.handCursorOnHover(),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    OutlinedButton(
                        onClick = { viewModel.refreshCleanupPreview() },
                        enabled = !state.isCleaning,
                        modifier = Modifier.handCursorOnHover(),
                    ) {
                        Text("Aggiorna anteprima")
                    }
                    Button(
                        onClick = { viewModel.requestCleanup() },
                        enabled = state.cleanupPreview.hasData && !state.isCleaning,
                        modifier = Modifier.handCursorOnHover(),
                    ) {
                        Text("Elimina dati storici")
                    }
                    if (state.isCleaning) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .width(18.dp)
                                .height(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(spacing.xs))
                        Text("Pulizia in corso...")
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
    "Con \"${option.label}\" mantieni gli ultimi ${option.months} mesi e rimuovi le settimane più vecchie della data limite."

private const val CLEANUP_SCOPE_TEXT =
    "Vengono eliminati: settimane passate, parti collegate e relative assegnazioni."

private const val CLEANUP_EXCLUSIONS_TEXT =
    "Non vengono toccati: proclamatori, tipi di parte, file di log e file esportati."

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

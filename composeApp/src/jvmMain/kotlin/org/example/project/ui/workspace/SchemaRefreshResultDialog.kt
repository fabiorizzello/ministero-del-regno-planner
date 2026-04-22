package org.example.project.ui.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeParseException
import org.example.project.feature.programs.application.SchemaRefreshPreview
import org.example.project.feature.programs.application.SchemaRefreshReport
import org.example.project.feature.schemas.application.SkippedPart
import org.example.project.ui.components.dateFormatter

@Composable
internal fun SchemaRefreshResultDialog(
    downloadedIssues: List<String>,
    pendingRefreshPreview: SchemaRefreshPreview?,
    unknownParts: List<SkippedPart>,
    onConfirmAll: () -> Unit,
    onConfirmOnlyUnassigned: () -> Unit,
    onDismiss: () -> Unit,
) {
    val hasAnyContent = downloadedIssues.isNotEmpty() ||
        pendingRefreshPreview != null ||
        unknownParts.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aggiornamento catalogo schemi") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!hasAnyContent) {
                    Text(
                        "Catalogo già aggiornato. Nessuna modifica da applicare.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                if (downloadedIssues.isNotEmpty()) {
                    SectionHeader("Fascicoli scaricati")
                    Column(
                        modifier = Modifier.padding(start = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        downloadedIssues.forEach { code ->
                            Text(
                                "• ${formatIssueCode(code)}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                if (pendingRefreshPreview != null) {
                    SectionHeader("Cambiamenti al programma selezionato")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Verranno toccate solo le settimane presenti e future.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        SchemaRefreshReportSection(
                            title = "Aggiorna tutto",
                            report = pendingRefreshPreview.allChanges,
                            destructive = true,
                        )
                        SchemaRefreshReportSection(
                            title = "Aggiorna solo non assegnati",
                            report = pendingRefreshPreview.onlyUnassignedChanges,
                            destructive = false,
                        )
                    }
                }

                if (unknownParts.isNotEmpty()) {
                    SectionHeader("Parti ignorate")
                    Column(
                        modifier = Modifier.padding(start = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        unknownParts.forEach { part ->
                            Column {
                                Text(
                                    part.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "Settimana del ${formatIsoDate(part.weekStartDate)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                part.detailLine?.takeIf { it.isNotBlank() }?.let { detail ->
                                    Text(
                                        detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (pendingRefreshPreview != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DesktopInlineAction(
                        label = "Aggiorna programma con tutte le modifiche",
                        onClick = onConfirmAll,
                    )
                    DesktopInlineAction(
                        label = "Solo parti non assegnate",
                        onClick = onConfirmOnlyUnassigned,
                    )
                }
            }
        },
        dismissButton = {
            DesktopInlineAction(
                label = "Chiudi",
                onClick = onDismiss,
            )
        },
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun SchemaRefreshReportSection(
    title: String,
    report: SchemaRefreshReport,
    destructive: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "$title: ${report.weeksUpdated} settimane coinvolte",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (destructive && report.assignmentsRemoved > 0) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface,
        )
        report.weekDetails.forEach { week ->
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    week.weekStartDate.format(dateFormatter),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                val partChanges = buildList {
                    if (week.partsAdded > 0) add("+${week.partsAdded} aggiunte")
                    if (week.partsRemoved > 0) add("-${week.partsRemoved} rimosse")
                    if (week.partsKept > 0 && week.partsAdded == 0 && week.partsRemoved == 0) {
                        add("nessuna modifica alle parti")
                    }
                }
                if (partChanges.isNotEmpty()) {
                    Text(
                        "Parti: ${partChanges.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val assignmentSummary = buildList {
                    if (week.assignmentsPreserved > 0) add("${week.assignmentsPreserved} preservate")
                    if (week.assignmentsRemoved > 0) add("${week.assignmentsRemoved} da rimuovere")
                }
                if (assignmentSummary.isNotEmpty()) {
                    Text(
                        "Assegnazioni: ${assignmentSummary.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (destructive && week.assignmentsRemoved > 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (report.assignmentsPreserved > 0 || report.assignmentsRemoved > 0) {
            Text(
                "Totale: ${report.assignmentsPreserved} preservate, ${report.assignmentsRemoved} rimosse",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (destructive && report.assignmentsRemoved > 0) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private fun formatIsoDate(iso: String): String =
    try {
        LocalDate.parse(iso).format(dateFormatter)
    } catch (_: DateTimeParseException) {
        iso
    }

private fun formatIssueCode(code: String): String = code

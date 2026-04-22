package org.example.project.ui.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeParseException
import org.example.project.feature.programs.application.SchemaRefreshPreview
import org.example.project.feature.programs.application.SchemaRefreshReport
import org.example.project.feature.programs.application.WeekRefreshDetail
import org.example.project.feature.programs.application.hasEffectiveChanges
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
        modifier = Modifier.widthIn(max = 1160.dp),
        onDismissRequest = onDismiss,
        title = { Text("Aggiornamento catalogo schemi") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!hasAnyContent) {
                    Text(
                        "Catalogo gia' allineato. Non e' stato rilevato alcun cambiamento.",
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
                                "- ${formatIssueCode(code)}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                if (pendingRefreshPreview != null) {
                    SectionHeader("Conferma aggiornamento programma")
                    Text(
                        "Confronta le due opzioni prima di confermare. Ogni colonna mostra l'impatto reale per settimana.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SchemaRefreshOptionColumn(
                            modifier = Modifier.weight(1f),
                            title = "Aggiorna tutto",
                            subtitle = "Applica il nuovo schema anche dove sono gia' presenti assegnazioni.",
                            report = pendingRefreshPreview.allChanges,
                            destructive = true,
                            actionLabel = "Conferma tutte le modifiche",
                            onAction = onConfirmAll,
                        )
                        SchemaRefreshOptionColumn(
                            modifier = Modifier.weight(1f),
                            title = "Solo non assegnati",
                            subtitle = "Aggiorna solo le parti senza assegnazioni, lasciando intatte quelle gia' occupate.",
                            report = pendingRefreshPreview.onlyUnassignedChanges,
                            destructive = false,
                            actionLabel = "Conferma solo non assegnati",
                            onAction = onConfirmOnlyUnassigned,
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
        confirmButton = {},
        dismissButton = {
            DesktopInlineAction(
                label = if (pendingRefreshPreview != null) "Annulla" else "Chiudi",
                onClick = onDismiss,
            )
        },
    )
}

@Composable
private fun SchemaRefreshOptionColumn(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    report: SchemaRefreshReport,
    destructive: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val changedWeeks = report.weekDetails.count { it.hasEffectiveChanges() }
    val borderColor = if (destructive && report.assignmentsRemoved > 0) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (destructive && report.assignmentsRemoved > 0) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SummaryStrip(report = report, changedWeeks = changedWeeks, destructive = destructive)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                report.weekDetails.forEach { week ->
                    WeekDiffCard(
                        week = week,
                        destructive = destructive,
                    )
                }
                if (report.weekDetails.isEmpty()) {
                    Text(
                        "Nessuna settimana analizzata per questa opzione.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            DesktopInlineAction(
                label = actionLabel,
                onClick = onAction,
            )
        }
    }
}

@Composable
private fun SummaryStrip(
    report: SchemaRefreshReport,
    changedWeeks: Int,
    destructive: Boolean,
) {
    val partsAdded = report.weekDetails.sumOf(WeekRefreshDetail::partsAdded)
    val partsRemoved = report.weekDetails.sumOf(WeekRefreshDetail::partsRemoved)
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (destructive && report.assignmentsRemoved > 0) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = if (changedWeeks == 0) {
                    "Nessuna settimana cambiera'"
                } else {
                    "$changedWeeks settimane cambieranno su ${report.weeksUpdated} verificate"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Parti: +$partsAdded, -$partsRemoved",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Assegnazioni: ${report.assignmentsPreserved} preservate, ${report.assignmentsRemoved} rimosse",
                style = MaterialTheme.typography.bodySmall,
                color = if (destructive && report.assignmentsRemoved > 0) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WeekDiffCard(
    week: WeekRefreshDetail,
    destructive: Boolean,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                week.weekStartDate.format(dateFormatter),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = buildWeekPartDiff(week),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = buildWeekAssignmentDiff(week),
                style = MaterialTheme.typography.bodySmall,
                color = if (destructive && week.assignmentsRemoved > 0) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun buildWeekPartDiff(week: WeekRefreshDetail): String =
    when {
        week.partsAdded == 0 && week.partsRemoved == 0 ->
            "Parti: nessuna modifica reale"
        else ->
            "Parti: +${week.partsAdded} aggiunte, -${week.partsRemoved} rimosse, ${week.partsKept} invariate"
    }

private fun buildWeekAssignmentDiff(week: WeekRefreshDetail): String =
    when {
        week.assignmentsPreserved == 0 && week.assignmentsRemoved == 0 ->
            "Assegnazioni: nessun impatto"
        else ->
            "Assegnazioni: ${week.assignmentsPreserved} preservate, ${week.assignmentsRemoved} da rimuovere"
    }

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
}

private fun formatIsoDate(iso: String): String =
    try {
        LocalDate.parse(iso).format(dateFormatter)
    } catch (_: DateTimeParseException) {
        iso
    }

private fun formatIssueCode(code: String): String = code

package org.example.project.ui.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import org.example.project.feature.weeklyparts.domain.WeekPlanStatus
import org.example.project.ui.AppSection
import org.example.project.ui.LocalSectionNavigator
import org.example.project.ui.components.FeedbackBanner
import org.example.project.ui.components.handCursorOnHover
import org.example.project.ui.theme.spacing
import org.koin.core.context.GlobalContext

@Composable
fun ProgramWorkspaceScreen() {
    val viewModel = remember { GlobalContext.get().get<ProgramWorkspaceViewModel>() }
    val state by viewModel.state.collectAsState()
    val spacing = MaterialTheme.spacing
    val navigateToSection = LocalSectionNavigator.current

    LaunchedEffect(Unit) { viewModel.onScreenEntered() }

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
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = { viewModel.refreshSchemas() },
                enabled = !state.isRefreshingSchemas,
                modifier = Modifier.handCursorOnHover(),
            ) {
                Text(if (state.isRefreshingSchemas) "Aggiornamento..." else "Aggiorna schemi")
            }
            Button(
                onClick = { viewModel.createNextProgram() },
                enabled = !state.isCreatingProgram,
                modifier = Modifier.handCursorOnHover(),
            ) {
                Text(if (state.isCreatingProgram) "Creazione..." else "Crea prossimo mese")
            }
            Button(
                onClick = { viewModel.autoAssignSelectedProgram() },
                enabled = state.selectedProgramId != null && !state.isAutoAssigning,
                modifier = Modifier.handCursorOnHover(),
            ) {
                Text(if (state.isAutoAssigning) "Autoassegnazione..." else "Autoassegna programma")
            }
            OutlinedButton(
                onClick = { viewModel.deleteFutureProgram() },
                enabled = state.futureProgram != null && !state.isDeletingFutureProgram,
                modifier = Modifier.handCursorOnHover(),
            ) {
                Text(if (state.isDeletingFutureProgram) "Eliminazione..." else "Elimina futuro")
            }
            OutlinedButton(
                onClick = { viewModel.printSelectedProgram() },
                enabled = state.selectedProgramId != null && !state.isPrintingProgram,
                modifier = Modifier.handCursorOnHover(),
            ) {
                Text(if (state.isPrintingProgram) "Stampa..." else "Stampa programma")
            }
            OutlinedButton(
                onClick = { viewModel.requestClearAssignments() },
                enabled = state.selectedProgramId != null && !state.isClearingAssignments,
                modifier = Modifier.handCursorOnHover(),
            ) {
                Text(if (state.isClearingAssignments) "Svuotamento..." else "Svuota assegnazioni")
            }
            OutlinedButton(
                onClick = { viewModel.refreshProgramFromSchemas() },
                enabled = state.selectedProgramId != null && !state.isRefreshingProgramFromSchemas,
                modifier = Modifier.handCursorOnHover(),
            ) {
                Text(if (state.isRefreshingProgramFromSchemas) "Aggiornamento..." else "Aggiorna da schemi")
            }
        }

        ProgramHeader(state = state, onSelectProgram = { viewModel.selectProgram(it) })

        if (state.autoAssignUnresolved.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(spacing.md),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    Text("Slot non assegnati", style = MaterialTheme.typography.titleSmall)
                    state.autoAssignUnresolved.forEach { unresolved ->
                        Text(
                            "• ${unresolved.weekStartDate} | ${unresolved.partLabel} slot ${unresolved.slot}: ${unresolved.reason}",
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
                    Text("Verranno rimosse $count assegnazioni dalle settimane correnti e future. Continuare?")
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
                title = { Text("Conferma aggiornamento da schemi") },
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
            LazyColumn(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                items(state.selectedProgramWeeks, key = { it.id.value }) { week ->
                    val isSkipped = week.status == WeekPlanStatus.SKIPPED
                    val isPast = week.weekStartDate < state.today

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isSkipped) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (isSkipped) 0.dp else 1.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSkipped)
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.surface,
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(spacing.md),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("Settimana ${week.weekStartDate}", style = MaterialTheme.typography.titleMedium)
                                    if (isSkipped) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                        ) {
                                            Text(
                                                "SALTATA",
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            )
                                        }
                                    }
                                }
                                Text("Parti: ${week.parts.size}", style = MaterialTheme.typography.bodySmall)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                                if (isSkipped) {
                                    if (!isPast) {
                                        OutlinedButton(
                                            onClick = { viewModel.reactivateWeek(week) },
                                            modifier = Modifier.handCursorOnHover(),
                                        ) { Text("Riattiva") }
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { viewModel.navigateToWeek(week) },
                                        modifier = Modifier.handCursorOnHover(),
                                    ) { Text("Vai") }
                                    Button(
                                        onClick = { navigateToSection(AppSection.ASSIGNMENTS) },
                                        modifier = Modifier.handCursorOnHover(),
                                    ) { Text("Assegna") }
                                }
                            }
                        }
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
) {
    val spacing = MaterialTheme.spacing
    val current = state.currentProgram
    val future = state.futureProgram

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text("Programmi attivi", style = MaterialTheme.typography.titleLarge)
            if (!state.hasPrograms) {
                Text("Nessun programma disponibile. Crea il prossimo mese.")
                return@Column
            }

            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                current?.let {
                    OutlinedButton(
                        onClick = { onSelectProgram(it.id.value) },
                        enabled = state.selectedProgramId != it.id.value,
                        modifier = Modifier.handCursorOnHover(),
                    ) {
                        Text("Corrente ${it.month}/${it.year}")
                    }
                }
                future?.let {
                    OutlinedButton(
                        onClick = { onSelectProgram(it.id.value) },
                        enabled = state.selectedProgramId != it.id.value,
                        modifier = Modifier.handCursorOnHover(),
                    ) {
                        Text("Futuro ${it.month}/${it.year}")
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
            }
        }
    }
}

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

        if (state.isLoading) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                items(state.selectedProgramWeeks, key = { it.id.value }) { week ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(spacing.md),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text("Settimana ${week.weekStartDate}", style = MaterialTheme.typography.titleMedium)
                                Text("Stato: ${week.statusLabel}", style = MaterialTheme.typography.bodySmall)
                                Text("Parti: ${week.parts.size}", style = MaterialTheme.typography.bodySmall)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                                OutlinedButton(
                                    onClick = { viewModel.navigateToWeek(week) },
                                    modifier = Modifier.handCursorOnHover(),
                                ) {
                                    Text("Vai")
                                }
                                Button(
                                    onClick = { navigateToSection(AppSection.ASSIGNMENTS) },
                                    modifier = Modifier.handCursorOnHover(),
                                ) {
                                    Text("Assegna")
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
                }
            }
        }
    }
}

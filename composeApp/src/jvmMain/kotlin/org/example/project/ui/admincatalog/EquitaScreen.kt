package org.example.project.ui.admincatalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.example.project.ui.components.workspace.WorkspaceStateKind
import org.example.project.ui.components.workspace.WorkspaceStatePane
import org.example.project.ui.theme.spacing
import org.koin.core.context.GlobalContext

@Composable
internal fun EquitaScreen() {
    val viewModel = remember { GlobalContext.get().get<EquitaViewModel>() }
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.onScreenEntered() }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(androidx.compose.material3.MaterialTheme.spacing.md),
    ) {
        when {
            state.isLoading && state.riepilogo == null -> WorkspaceStatePane(
                kind = WorkspaceStateKind.Loading,
                message = "Calcolo rotazione in corso...",
            )

            state.error != null && state.riepilogo == null -> WorkspaceStatePane(
                kind = WorkspaceStateKind.Error,
                message = state.error ?: "Impossibile calcolare la rotazione.",
            )

            state.emptyStateVisible -> WorkspaceStatePane(
                kind = WorkspaceStateKind.Empty,
                message = "Nessun proclamatore attivo.",
            )

            else -> {
                val riepilogo = state.riepilogo ?: return
                EquitaSummaryCard(
                    riepilogo = riepilogo,
                    righe = state.righePanoramica,
                    modifier = Modifier.fillMaxWidth(),
                )
                AdminContentCard(
                    title = "Rotazione e carico",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    EquitaFilterBar(
                        filtroRicerca = state.filtroRicerca,
                        soloLiberi = state.soloLiberi,
                        includiSospesi = state.includiSospesi,
                        sortMode = state.sortMode,
                        onSearchChange = viewModel::onSearchChange,
                        onToggleSoloLiberi = viewModel::onToggleSoloLiberi,
                        onToggleIncludiSospesi = viewModel::onToggleIncludiSospesi,
                        onSortChange = viewModel::onSortChange,
                    )
                    if (state.righe.isEmpty()) {
                        WorkspaceStatePane(
                            kind = WorkspaceStateKind.Empty,
                            message = "Nessun proclamatore corrisponde ai filtri correnti.",
                        )
                    } else {
                        EquitaList(
                            righe = state.righe,
                            riepilogo = riepilogo,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

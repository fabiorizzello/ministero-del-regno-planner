package org.example.project.ui.admincatalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.example.project.ui.components.FeedbackBanner
import org.example.project.ui.components.workspace.WorkspaceStateKind
import org.example.project.ui.components.workspace.WorkspaceStatePane
import org.example.project.ui.theme.spacing
import org.koin.core.context.GlobalContext

@Composable
internal fun PartTypeCatalogScreen() {
    val viewModel = remember { GlobalContext.get().get<PartTypeCatalogViewModel>() }
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.onScreenEntered() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(androidx.compose.material3.MaterialTheme.spacing.md),
    ) {
        FeedbackBanner(
            model = state.notice,
            onDismissRequest = { viewModel.dismissNotice() },
        )

        when {
            shouldShowPartTypeCatalogLoading(state) -> WorkspaceStatePane(
                kind = WorkspaceStateKind.Loading,
                message = "Caricamento tipi di parte...",
            )

            state.emptyStateVisible -> WorkspaceStatePane(
                kind = WorkspaceStateKind.Empty,
                message = "Non ci sono tipi di parte disponibili.",
            )

            shouldShowPartTypeCatalogError(state) -> WorkspaceStatePane(
                kind = WorkspaceStateKind.Error,
                message = "Impossibile caricare i tipi di parte.",
            )

            else -> AdminSplitPane(
                sidebar = {
                    AdminContentCard(
                        title = "Tipi parte",
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(androidx.compose.material3.MaterialTheme.spacing.sm),
                        ) {
                            items(state.items, key = { it.id.value }) { item ->
                                AdminSelectionItem(
                                    title = "${item.code} · ${item.label}",
                                    subtitle = "${partTypeStatusLabel(item.active)} · ${item.peopleCount} persone",
                                    selected = state.selectedId == item.id,
                                    onClick = { viewModel.selectItem(item.id) },
                                    tag = "part-type-item-${item.id.value}",
                                )
                            }
                        }
                    }
                },
                detail = {
                    AdminContentCard(
                        title = state.selectedDetail?.label ?: "Dettaglio tipo parte",
                        subtitle = state.selectedDetail?.code?.let { "Codice $it" },
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .testTag("part-type-detail"),
                    ) {
                        val detail = state.selectedDetail
                        if (detail == null) {
                            WorkspaceStatePane(
                                kind = WorkspaceStateKind.Empty,
                                message = "Seleziona un tipo di parte per vedere i dettagli.",
                            )
                        } else {
                            partTypeDetailRows(detail).forEach { (label, value) ->
                                AdminKeyValueRow(label = label, value = value)
                            }
                        }
                    }
                },
            )
        }
    }
}

internal fun partTypeDetailRows(
    detail: PartTypeCatalogDetail,
): List<Pair<String, String>> = listOf(
    "Codice" to detail.code,
    "Nome" to detail.label,
    "Persone richieste" to detail.peopleCount.toString(),
    "Composizione" to detail.sexRuleLabel,
    "Tipo" to detail.fixedLabel,
    "Stato" to detail.activeLabel,
)

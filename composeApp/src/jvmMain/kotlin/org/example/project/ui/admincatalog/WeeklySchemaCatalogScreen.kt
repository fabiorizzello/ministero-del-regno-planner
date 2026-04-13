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
internal fun WeeklySchemaCatalogScreen() {
    val viewModel = remember { GlobalContext.get().get<WeeklySchemaCatalogViewModel>() }
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
            shouldShowWeeklySchemaCatalogLoading(state) -> WorkspaceStatePane(
                kind = WorkspaceStateKind.Loading,
                message = "Caricamento schemi settimanali...",
            )

            state.emptyStateVisible -> WorkspaceStatePane(
                kind = WorkspaceStateKind.Empty,
                message = "Non ci sono schemi settimanali disponibili.",
            )

            shouldShowWeeklySchemaCatalogError(state) -> WorkspaceStatePane(
                kind = WorkspaceStateKind.Error,
                message = "Impossibile caricare gli schemi settimanali.",
            )

            else -> AdminSplitPane(
                sidebar = {
                    AdminContentCard(
                        title = "Schemi settimanali",
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(androidx.compose.material3.MaterialTheme.spacing.sm),
                        ) {
                            items(state.weeks, key = { it.weekStartDate.toString() }) { item ->
                                AdminSelectionItem(
                                    title = item.summaryLabel,
                                    selected = state.selectedWeekStartDate == item.weekStartDate,
                                    onClick = { viewModel.selectWeek(item.weekStartDate) },
                                    tag = "weekly-schema-item-${item.weekStartDate}",
                                )
                            }
                        }
                    }
                },
                detail = {
                    AdminContentCard(
                        title = state.selectedDetail?.let { weeklySchemaSummaryLabel(it.weekStartDate, it.rows.size) }
                            ?: "Dettaglio schema settimanale",
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .testTag("weekly-schema-detail"),
                    ) {
                        val detail = state.selectedDetail
                        if (detail == null) {
                            WorkspaceStatePane(
                                kind = WorkspaceStateKind.Empty,
                                message = "Seleziona una settimana per vedere i dettagli.",
                            )
                        } else {
                            if (detail.rows.isEmpty()) {
                                WorkspaceStatePane(
                                    kind = WorkspaceStateKind.Empty,
                                    message = "La settimana selezionata non contiene parti.",
                                )
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(androidx.compose.material3.MaterialTheme.spacing.sm),
                                ) {
                                    items(detail.rows, key = { "${detail.weekStartDate}-${it.position}-${it.partTypeId.value}" }) { row ->
                                        AdminSelectionItem(
                                            title = "${row.position}. ${row.partTypeLabel}",
                                            subtitle = describeWeeklySchemaRow(row),
                                            selected = false,
                                            onClick = {},
                                            tag = "weekly-schema-row-${row.position}",
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
            )
        }
    }
}

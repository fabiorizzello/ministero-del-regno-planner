package org.example.project.ui.proclamatori

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import org.example.project.feature.assignments.domain.PersonAssignmentHistory
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.domain.Sesso
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.ui.components.dateFormatter
import org.example.project.ui.components.handCursorOnHover
import org.example.project.ui.theme.spacing

internal data class ProclamatoriElencoEvents(
    val onSearchTermChange: (String) -> Unit,
    val onResetSearch: () -> Unit,
    val onDismissNotice: () -> Unit,
    val onSortChange: (ProclamatoriSort) -> Unit,
    val onToggleSelectPage: (List<ProclamatoreId>, Boolean) -> Unit,
    val onToggleRowSelected: (ProclamatoreId, Boolean) -> Unit,
    val onActivateSelected: () -> Unit,
    val onDeactivateSelected: () -> Unit,
    val onRequestDeleteSelected: () -> Unit,
    val onClearSelection: () -> Unit,
    val onGoNuovo: () -> Unit,
    val onImportJson: () -> Unit,
    val onDismissSchemaAnomalies: () -> Unit,
    val onEdit: (ProclamatoreId) -> Unit,
    val onToggleActive: (ProclamatoreId, Boolean) -> Unit,
    val onDelete: (Proclamatore) -> Unit,
    val onPreviousPage: () -> Unit,
    val onNextPage: () -> Unit,
)

// Backward compatibility wrapper - delegates to extracted component
@Composable
internal fun ConfirmDeleteDialog(
    title: String,
    isLoading: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    ConfirmDeleteDialogComponent(
        title = title,
        isLoading = isLoading,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        content = content,
    )
}

// Backward compatibility wrapper - delegates to extracted component
@Composable
internal fun ColumnScope.ProclamatoriElencoContent(
    state: ProclamatoriListUiState,
    searchFocusRequester: FocusRequester,
    tableListState: LazyListState,
    canImportInitialJson: Boolean,
    events: ProclamatoriElencoEvents,
) {
    ProclamatoriElencoContentTable(
        state = state,
        searchFocusRequester = searchFocusRequester,
        tableListState = tableListState,
        canImportInitialJson = canImportInitialJson,
        events = events,
    )
}

// Backward compatibility wrapper - delegates to extracted component
@Composable
internal fun ProclamatoriFormContent(
    route: ProclamatoriRoute,
    nome: String,
    onNomeChange: (String) -> Unit,
    cognome: String,
    onCognomeChange: (String) -> Unit,
    sesso: Sesso,
    onSessoChange: (Sesso) -> Unit,
    sospeso: Boolean,
    onSospesoChange: (Boolean) -> Unit,
    puoAssistere: Boolean,
    onPuoAssistereChange: (Boolean) -> Unit,
    leadEligibilityOptions: List<LeadEligibilityOptionUi>,
    onLeadEligibilityChange: (PartTypeId, Boolean) -> Unit,
    assignmentHistory: PersonAssignmentHistory?,
    isHistoryExpanded: Boolean,
    onToggleHistoryExpanded: () -> Unit,
    nomeTrim: String,
    cognomeTrim: String,
    showFieldErrors: Boolean,
    duplicateError: String?,
    isCheckingDuplicate: Boolean,
    canSubmitForm: Boolean,
    isLoading: Boolean,
    formError: String?,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    ProclamatoriFormContentForm(
        route = route,
        nome = nome,
        onNomeChange = onNomeChange,
        cognome = cognome,
        onCognomeChange = onCognomeChange,
        sesso = sesso,
        onSessoChange = onSessoChange,
        sospeso = sospeso,
        onSospesoChange = onSospesoChange,
        puoAssistere = puoAssistere,
        onPuoAssistereChange = onPuoAssistereChange,
        leadEligibilityOptions = leadEligibilityOptions,
        onLeadEligibilityChange = onLeadEligibilityChange,
        nomeTrim = nomeTrim,
        cognomeTrim = cognomeTrim,
        showFieldErrors = showFieldErrors,
        duplicateError = duplicateError,
        isCheckingDuplicate = isCheckingDuplicate,
        canSubmitForm = canSubmitForm,
        isLoading = isLoading,
        formError = formError,
        onSubmit = onSubmit,
        onCancel = onCancel,
    )
}

// Backward compatibility wrapper - delegates to extracted component
@Composable
internal fun ProclamatoriFormDialog(
    route: ProclamatoriRoute,
    nome: String,
    onNomeChange: (String) -> Unit,
    cognome: String,
    onCognomeChange: (String) -> Unit,
    sesso: Sesso,
    onSessoChange: (Sesso) -> Unit,
    sospeso: Boolean,
    onSospesoChange: (Boolean) -> Unit,
    puoAssistere: Boolean,
    onPuoAssistereChange: (Boolean) -> Unit,
    leadEligibilityOptions: List<LeadEligibilityOptionUi>,
    onLeadEligibilityChange: (PartTypeId, Boolean) -> Unit,
    assignmentHistory: PersonAssignmentHistory?,
    isHistoryExpanded: Boolean,
    onToggleHistoryExpanded: () -> Unit,
    nomeTrim: String,
    cognomeTrim: String,
    showFieldErrors: Boolean,
    duplicateError: String?,
    isCheckingDuplicate: Boolean,
    canSubmitForm: Boolean,
    isLoading: Boolean,
    formError: String?,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    ProclamatoriFormDialogComponent(
        route = route,
        nome = nome,
        onNomeChange = onNomeChange,
        cognome = cognome,
        onCognomeChange = onCognomeChange,
        sesso = sesso,
        onSessoChange = onSessoChange,
        sospeso = sospeso,
        onSospesoChange = onSospesoChange,
        puoAssistere = puoAssistere,
        onPuoAssistereChange = onPuoAssistereChange,
        leadEligibilityOptions = leadEligibilityOptions,
        onLeadEligibilityChange = onLeadEligibilityChange,
        nomeTrim = nomeTrim,
        cognomeTrim = cognomeTrim,
        showFieldErrors = showFieldErrors,
        duplicateError = duplicateError,
        isCheckingDuplicate = isCheckingDuplicate,
        canSubmitForm = canSubmitForm,
        isLoading = isLoading,
        formError = formError,
        onSubmit = onSubmit,
        onCancel = onCancel,
        onDismiss = onDismiss,
    )
}

@Composable
internal fun AssignmentHistoryPanel(
    history: PersonAssignmentHistory?,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
) {
    val spacing = MaterialTheme.spacing

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpanded)
            .handCursorOnHover(),
        shape = RoundedCornerShape(spacing.cardRadius),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
    ) {
        Column(
            modifier = Modifier.padding(spacing.md),
        ) {
            // Header with toggle icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Storico assegnazioni",
                    style = MaterialTheme.typography.titleMedium,
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Comprimi" else "Espandi",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }

            // Summary counts (always visible)
            if (history != null && !history.isEmpty) {
                Spacer(Modifier.height(spacing.sm))
                Text(
                    text = "Totale: ${history.totalAssignments} assegnazioni",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )

                if (history.summaryByPartType.isNotEmpty()) {
                    Spacer(Modifier.height(spacing.xs))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(spacing.md),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        history.summaryByPartType.entries.sortedByDescending { it.value }.forEach { (partType, count) ->
                            Text(
                                text = "$partType: $count",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(spacing.sm))
                Text(
                    text = "Nessuna assegnazione",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }

            // Expanded details
            if (isExpanded && history != null && !history.isEmpty) {
                Spacer(Modifier.height(spacing.md))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(spacing.cardRadius),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    ),
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .padding(spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(spacing.xs),
                    ) {
                        items(history.entries) { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = spacing.xs, horizontal = spacing.sm),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        text = entry.partTypeLabel,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = entry.weekStartDate.format(dateFormatter),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    )
                                }
                                Text(
                                    text = entry.role,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

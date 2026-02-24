package org.example.project.ui.proclamatori

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.domain.Sesso
import org.example.project.feature.weeklyparts.domain.PartTypeId

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

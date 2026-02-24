package org.example.project.ui.proclamatori

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.ui.theme.spacing
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.domain.Sesso
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.SexRule
import org.example.project.ui.components.FeedbackBanner
import org.example.project.ui.components.FeedbackBannerModel
import org.example.project.ui.components.handCursorOnHover

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

private const val NAME_MAX_LENGTH = 100

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

// Backward compatibility wrapper - delegates to extracted component
@Composable
internal fun TableDataRow(
    proclamatore: Proclamatore,
    loading: Boolean,
    selected: Boolean,
    batchMode: Boolean,
    backgroundColor: Color,
    onToggleSelected: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    ProclamatoriDataRow(
        proclamatore = proclamatore,
        loading = loading,
        selected = selected,
        batchMode = batchMode,
        backgroundColor = backgroundColor,
        onToggleSelected = onToggleSelected,
        onEdit = onEdit,
        onToggleActive = onToggleActive,
        onDelete = onDelete,
    )
}

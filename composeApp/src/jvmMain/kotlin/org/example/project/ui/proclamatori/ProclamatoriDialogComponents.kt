/**
 * Dialog components for Proclamatori screens.
 *
 * Contains reusable dialog wrappers for delete confirmation
 * and form presentation in dialog mode.
 */
package org.example.project.ui.proclamatori

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.example.project.feature.people.domain.Sesso
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.ui.components.handCursorOnHover
import org.example.project.ui.theme.spacing

/**
 * Reusable confirmation dialog for delete operations.
 *
 * Displays a title, custom content, and Rimuovi/Annulla actions.
 * Disables interaction while loading.
 */
@Composable
internal fun ConfirmDeleteDialogComponent(
    title: String,
    isLoading: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    DisableSelection {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = { content() },
            confirmButton = {
                TextButton(
                    modifier = Modifier.handCursorOnHover(enabled = !isLoading),
                    onClick = onConfirm,
                    enabled = !isLoading,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Rimuovi") }
            },
            dismissButton = {
                TextButton(
                    modifier = Modifier.handCursorOnHover(enabled = !isLoading),
                    onClick = onDismiss,
                    enabled = !isLoading,
                ) { Text("Annulla") }
            },
        )
    }
}

/**
 * Dialog wrapper for the Proclamatore form.
 *
 * Displays the ProclamatoriFormContent in a centered dialog with
 * platform-independent width. Delegates all form logic to the
 * nested ProclamatoriFormContent composable.
 */
@Composable
internal fun ProclamatoriFormDialogComponent(
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
    val spacing = MaterialTheme.spacing
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(spacing.cardRadius),
            tonalElevation = 6.dp,
            modifier = Modifier
                .padding(spacing.lg)
                .width(680.dp),
        ) {
            Column(
                modifier = Modifier.padding(spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
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
        }
    }
}

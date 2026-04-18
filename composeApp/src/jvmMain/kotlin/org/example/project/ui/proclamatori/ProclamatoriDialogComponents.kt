/**
 * Dialog components for Proclamatori screens.
 *
 * Contains reusable dialog wrappers for delete confirmation
 * and form presentation in dialog mode.
 */
package org.example.project.ui.proclamatori

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.example.project.feature.people.domain.Sesso
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.ui.components.handCursorOnHover
import org.example.project.ui.theme.spacing
import java.time.LocalDate

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

@Composable
internal fun ConfirmUnsavedNextDialogComponent(
    isLoading: Boolean,
    onSaveAndContinue: () -> Unit,
    onDiscardAndContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    DisableSelection {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Modifiche non salvate") },
            text = {
                Text("Vuoi salvare prima di aprire lo studente successivo?")
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        modifier = Modifier.handCursorOnHover(enabled = !isLoading),
                        onClick = onDiscardAndContinue,
                        enabled = !isLoading,
                    ) {
                        Text("Scarta e continua")
                    }
                    TextButton(
                        modifier = Modifier.handCursorOnHover(enabled = !isLoading),
                        onClick = onSaveAndContinue,
                        enabled = !isLoading,
                    ) {
                        Text("Salva e continua")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    modifier = Modifier.handCursorOnHover(enabled = !isLoading),
                    onClick = onDismiss,
                    enabled = !isLoading,
                ) {
                    Text("Annulla")
                }
            },
        )
    }
}

/**
 * Dialog wrapper for the Proclamatore form.
 *
 * Displays the Proclamatori form in a centered dialog with
 * platform-independent width. Delegates all form logic to the
 * nested ProclamatoriFormContentForm composable.
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
    lastAssistantAssignmentDate: LocalDate?,
    onLeadEligibilityChange: (PartTypeId, Boolean) -> Unit,
    onSetAllEligibilityChange: (Boolean) -> Unit,
    nomeTrim: String,
    cognomeTrim: String,
    showFieldErrors: Boolean,
    duplicateError: String?,
    isCheckingDuplicate: Boolean,
    canSubmitForm: Boolean,
    canGoToNext: Boolean,
    isLoading: Boolean,
    formError: String?,
    onSubmit: () -> Unit,
    onNext: (() -> Unit)?,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val spacing = MaterialTheme.spacing
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(spacing.cardRadius),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier
                .padding(spacing.lg)
                .widthIn(min = 820.dp, max = 1200.dp)
                .fillMaxWidth(0.9f),
        ) {
            Column(
                modifier = Modifier.padding(spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                // ── Header: title + close button ─────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (route == ProclamatoriRoute.Nuovo) "Nuovo studente" else "Modifica studente",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp).handCursorOnHover(),
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Chiudi",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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
                    lastAssistantAssignmentDate = lastAssistantAssignmentDate,
                    onLeadEligibilityChange = onLeadEligibilityChange,
                    onSetAllEligibilityChange = onSetAllEligibilityChange,
                    nomeTrim = nomeTrim,
                    cognomeTrim = cognomeTrim,
                    showFieldErrors = showFieldErrors,
                    duplicateError = duplicateError,
                    isCheckingDuplicate = isCheckingDuplicate,
                    canSubmitForm = canSubmitForm,
                    canGoToNext = canGoToNext,
                    isLoading = isLoading,
                    formError = formError,
                    onSubmit = onSubmit,
                    onNext = onNext,
                    onCancel = onCancel,
                    onDelete = onDelete,
                    showTitle = false,
                )
            }
        }
    }
}

/**
 * Form components for creating and editing Proclamatori.
 *
 * Contains form fields for personal data (nome, cognome, sesso, sospeso)
 * and eligibility settings (assistenza and lead role eligibility).
 */
package org.example.project.ui.proclamatori

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.example.project.feature.people.domain.Sesso
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.ui.components.handCursorOnHover
import org.example.project.ui.theme.spacing

/** Maximum length for nome and cognome fields */
private const val NAME_MAX_LENGTH = 100

/**
 * Proclamatore form content with input fields and eligibility options.
 *
 * Displays fields for:
 * - Nome and cognome with character limits and validation
 * - Sesso radio buttons (M/F)
 * - Sospeso checkbox
 * - Assistenza checkbox
 * - Lead role eligibility checklist from database part types
 *
 * Includes duplicate checking and validation error display.
 */
@Composable
internal fun ProclamatoriFormContentForm(
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
    val isNew = route == ProclamatoriRoute.Nuovo
    val spacing = MaterialTheme.spacing
    Text(
        if (isNew) "Nuovo proclamatore" else "Modifica proclamatore",
        style = MaterialTheme.typography.headlineMedium,
    )
    Spacer(Modifier.height(spacing.lg))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            OutlinedTextField(
                value = nome,
                onValueChange = { if (it.length <= NAME_MAX_LENGTH) onNomeChange(it) },
                label = { Text("Nome") },
                isError = (showFieldErrors && nomeTrim.isBlank()) || duplicateError != null,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = {
                    if (showFieldErrors && nomeTrim.isBlank()) {
                        Text("Campo obbligatorio", color = MaterialTheme.colorScheme.error)
                    } else {
                        Text("${nome.length}/$NAME_MAX_LENGTH")
                    }
                },
            )
            OutlinedTextField(
                value = cognome,
                onValueChange = { if (it.length <= NAME_MAX_LENGTH) onCognomeChange(it) },
                label = { Text("Cognome") },
                isError = (showFieldErrors && cognomeTrim.isBlank()) || duplicateError != null,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = {
                    if (showFieldErrors && cognomeTrim.isBlank()) {
                        Text("Campo obbligatorio", color = MaterialTheme.colorScheme.error)
                    } else if (duplicateError != null) {
                        Text(duplicateError, color = MaterialTheme.colorScheme.error)
                    } else if (isCheckingDuplicate) {
                        Text("Verifica duplicato in corso...")
                    } else {
                        Text("${cognome.length}/$NAME_MAX_LENGTH")
                    }
                },
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = sesso == Sesso.M, onClick = { onSessoChange(Sesso.M) })
                    Text("Uomo")
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = sesso == Sesso.F, onClick = { onSessoChange(Sesso.F) })
                    Text("Donna")
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = sospeso,
                        onCheckedChange = onSospesoChange,
                    )
                    Text("Sospeso")
                }
            }
            val eligibilityListState = rememberLazyListState()
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(spacing.md),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    Text(
                        "Idoneita",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .padding(end = spacing.sm),
                    ) {
                        LazyColumn(
                            state = eligibilityListState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(spacing.xs),
                        ) {
                            item(key = "assistenza") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "Assistente",
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Checkbox(
                                        checked = puoAssistere,
                                        onCheckedChange = onPuoAssistereChange,
                                    )
                                }
                            }
                            if (leadEligibilityOptions.isEmpty()) {
                                item(key = "empty-hint") {
                                    Text(
                                        "Nessun ruolo disponibile. Usa Aggiorna schemi.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else {
                                items(leadEligibilityOptions, key = { it.partTypeId.value }) { option ->
                                    val enabled = option.canSelect
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = option.label,
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (enabled) {
                                                MaterialTheme.colorScheme.onSurface
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        )
                                        Checkbox(
                                            checked = option.checked,
                                            onCheckedChange = { checked ->
                                                onLeadEligibilityChange(option.partTypeId, checked)
                                            },
                                            enabled = enabled,
                                        )
                                    }
                                }
                            }
                        }
                        VerticalScrollbar(
                            adapter = rememberScrollbarAdapter(eligibilityListState),
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight(),
                        )
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    modifier = Modifier.handCursorOnHover(enabled = canSubmitForm),
                    onClick = onSubmit,
                    enabled = canSubmitForm,
                ) {
                    Text(if (isNew) "Salva" else "Aggiorna")
                }
                TextButton(
                    modifier = Modifier.handCursorOnHover(enabled = !isLoading),
                    onClick = onCancel,
                    enabled = !isLoading,
                ) { Text("Annulla") }
            }

            if (formError != null) {
                SelectionContainer {
                    Text(
                        formError,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

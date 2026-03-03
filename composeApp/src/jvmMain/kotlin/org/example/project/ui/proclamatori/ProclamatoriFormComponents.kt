/**
 * Form components for creating and editing Proclamatori.
 *
 * Contains form fields for personal data (nome, cognome, sesso, sospeso)
 * and eligibility settings (assistenza and lead role eligibility).
 */
package org.example.project.ui.proclamatori

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.project.feature.people.domain.Sesso
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.ui.components.handCursorOnHover
import org.example.project.ui.theme.spacing
import org.example.project.ui.theme.workspaceSketch

/** Maximum length for nome and cognome fields */
private const val NAME_MAX_LENGTH = 100

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    onSetAllEligibilityChange: (Boolean) -> Unit,
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
    val sketch = MaterialTheme.workspaceSketch
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
            // ── Nome + Cognome side-by-side ──────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = nome,
                    onValueChange = { if (it.length <= NAME_MAX_LENGTH) onNomeChange(it) },
                    label = { Text("Nome *") },
                    isError = (showFieldErrors && nomeTrim.isBlank()) || duplicateError != null,
                    modifier = Modifier.weight(1f),
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
                    label = { Text("Cognome *") },
                    isError = (showFieldErrors && cognomeTrim.isBlank()) || duplicateError != null,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    supportingText = {
                        when {
                            showFieldErrors && cognomeTrim.isBlank() ->
                                Text("Campo obbligatorio", color = MaterialTheme.colorScheme.error)
                            duplicateError != null ->
                                Text(duplicateError, color = MaterialTheme.colorScheme.error)
                            isCheckingDuplicate ->
                                Text("Verifica duplicato in corso...")
                            else ->
                                Text("${cognome.length}/$NAME_MAX_LENGTH")
                        }
                    },
                )
            }

            // ── Genere — segmented control ───────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Genere",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = sesso == Sesso.M,
                        onClick = { onSessoChange(Sesso.M) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) { Text("Uomo") }
                    SegmentedButton(
                        selected = sesso == Sesso.F,
                        onClick = { onSessoChange(Sesso.F) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) { Text("Donna") }
                }
            }

            // ── Sospeso — toggle card ────────────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSospesoChange(!sospeso) }
                    .handCursorOnHover(),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(
                    1.dp,
                    if (sospeso) sketch.warn.copy(alpha = 0.45f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                ),
                colors = CardDefaults.cardColors(
                    containerColor = if (sospeso) sketch.warn.copy(alpha = 0.07f)
                    else MaterialTheme.colorScheme.surface,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Sospeso",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = if (sospeso) sketch.warn else MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                        Text(
                            "Non viene incluso nell'assegnazione automatica",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = sospeso,
                        onCheckedChange = onSospesoChange,
                    )
                }
            }

            // ── Idoneità — filter chips ──────────────────────────────────────
            val selectableLeadOptions = leadEligibilityOptions.filter { it.canSelect }
            val allEligibilitySelected = puoAssistere && selectableLeadOptions.all { it.checked }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Idoneità", style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // "Tutti" chip
                    FilterChip(
                        selected = allEligibilitySelected,
                        onClick = { onSetAllEligibilityChange(!allEligibilitySelected) },
                        label = { Text("Tutti") },
                        modifier = Modifier.handCursorOnHover(),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    )
                    // Assistente chip
                    FilterChip(
                        selected = puoAssistere,
                        onClick = { onPuoAssistereChange(!puoAssistere) },
                        label = { Text("Assistente") },
                        modifier = Modifier.handCursorOnHover(),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = sketch.accentSoft,
                            selectedLabelColor = sketch.accent,
                        ),
                    )
                    // Lead part type chips
                    leadEligibilityOptions.forEach { option ->
                        FilterChip(
                            selected = option.checked,
                            onClick = { onLeadEligibilityChange(option.partTypeId, !option.checked) },
                            label = { Text(option.label) },
                            enabled = option.canSelect,
                            modifier = Modifier.handCursorOnHover(enabled = option.canSelect),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = sketch.accentSoft,
                                selectedLabelColor = sketch.accent,
                            ),
                        )
                    }
                }
                if (leadEligibilityOptions.isEmpty()) {
                    Text(
                        "Nessun ruolo disponibile. Usa Aggiorna schemi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── Buttons ──────────────────────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        positioning = TooltipAnchorPosition.Above,
                    ),
                    tooltip = { PlainTooltip { Text("Invio per salvare") } },
                    state = rememberTooltipState(),
                ) {
                    Button(
                        modifier = Modifier.handCursorOnHover(enabled = canSubmitForm),
                        onClick = onSubmit,
                        enabled = canSubmitForm,
                    ) {
                        Text(if (isNew) "Salva" else "Aggiorna")
                    }
                }
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        positioning = TooltipAnchorPosition.Above,
                    ),
                    tooltip = { PlainTooltip { Text("Esc per annullare") } },
                    state = rememberTooltipState(),
                ) {
                    TextButton(
                        modifier = Modifier.handCursorOnHover(enabled = !isLoading),
                        onClick = onCancel,
                        enabled = !isLoading,
                    ) { Text("Annulla") }
                }
            }

            if (formError != null) {
                SelectionContainer {
                    Text(formError, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

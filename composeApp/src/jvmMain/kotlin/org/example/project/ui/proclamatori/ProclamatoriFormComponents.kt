/**
 * Form components for creating and editing Proclamatori.
 *
 * Contains form fields for personal data (nome, cognome, sesso, sospeso)
 * and eligibility settings (assistenza and lead role eligibility).
 */
package org.example.project.ui.proclamatori

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.example.project.feature.people.domain.Sesso
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.ui.components.shortDateFormatter
import org.example.project.ui.components.handCursorOnHover
import org.example.project.ui.theme.spacing
import org.example.project.ui.theme.workspaceSketch
import java.time.LocalDate

/** Maximum length for nome and cognome fields */
private const val NAME_MAX_LENGTH = 100
private const val LEAD_ROLE_COLUMNS = 2

@OptIn(ExperimentalMaterial3Api::class)
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
    onDelete: (() -> Unit)? = null,
    showTitle: Boolean = true,
) {
    val isNew = route == ProclamatoriRoute.Nuovo
    val sketch = MaterialTheme.workspaceSketch
    val spacing = MaterialTheme.spacing
    val nameFr = remember { FocusRequester() }
    LaunchedEffect(Unit) { nameFr.requestFocus() }

    if (showTitle) {
        Text(
            if (isNew) "Nuovo studente" else "Modifica studente",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(spacing.lg))
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
                    modifier = Modifier.weight(1f).focusRequester(nameFr),
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

            // ── Genere — pill toggle ─────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Genere",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Sesso.M to "Uomo", Sesso.F to "Donna").forEach { (sVal, label) ->
                        val selected = sesso == sVal
                        val chipShape = RoundedCornerShape(8.dp)
                        Surface(
                            modifier = Modifier
                                .clip(chipShape)
                                .clickable { onSessoChange(sVal) }
                                .handCursorOnHover(),
                            shape = chipShape,
                            color = if (selected) sketch.accentSoft else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.5.dp,
                                if (selected) sketch.accent else sketch.lineStrong,
                            ),
                        ) {
                            Text(
                                text = label,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) sketch.accent else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            // ── Idoneità — filter chips ──────────────────────────────────────
            val selectableLeadOptions = leadEligibilityOptions.filter { it.canSelect }
            val allEligibilitySelected = puoAssistere && selectableLeadOptions.all { it.checked }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Idoneità", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Seleziona i ruoli che può svolgere.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Meta controls: Tutti + Assistente
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    EligibilityFilterChip(
                        selected = allEligibilitySelected,
                        onClick = { onSetAllEligibilityChange(!allEligibilitySelected) },
                        label = "Tutti",
                        metadata = null,
                        modifier = Modifier.handCursorOnHover(),
                    )
                    EligibilityFilterChip(
                        selected = puoAssistere,
                        onClick = { onPuoAssistereChange(!puoAssistere) },
                        label = "Assistente",
                        metadata = null,
                        modifier = Modifier.handCursorOnHover(),
                    )
                }
                if (leadEligibilityOptions.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        leadEligibilityOptions.chunked(LEAD_ROLE_COLUMNS).forEach { rowOptions ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                rowOptions.forEach { option ->
                                    EligibilityFilterChip(
                                        selected = option.checked,
                                        onClick = { onLeadEligibilityChange(option.partTypeId, !option.checked) },
                                        label = option.label,
                                        metadata = if (isNew) null else formatLastAssignmentLabel(option.lastAssignedOn),
                                        enabled = option.canSelect,
                                        modifier = Modifier
                                            .weight(1f)
                                            .handCursorOnHover(enabled = option.canSelect),
                                    )
                                }
                                repeat(LEAD_ROLE_COLUMNS - rowOptions.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        "Nessun ruolo disponibile. Usa Aggiorna schemi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── Sospeso — secondary toggle ───────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isLoading) { onSospesoChange(!sospeso) }
                    .handCursorOnHover(enabled = !isLoading),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        "Sospeso",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (sospeso) {
                            "Escluso dall'assegnazione automatica"
                        } else {
                            "Escludi dall'assegnazione automatica"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (sospeso) {
                            sketch.warn.copy(alpha = 0.82f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Switch(
                    checked = sospeso,
                    onCheckedChange = onSospesoChange,
                    enabled = !isLoading,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = sketch.surface,
                        checkedTrackColor = sketch.warn,
                        checkedBorderColor = sketch.warn,
                        uncheckedTrackColor = sketch.lineStrong,
                        uncheckedThumbColor = sketch.surface,
                        uncheckedBorderColor = sketch.lineStrong,
                        disabledCheckedThumbColor = sketch.surface.copy(alpha = 0.96f),
                        disabledCheckedTrackColor = sketch.warn.copy(alpha = 0.44f),
                        disabledCheckedBorderColor = sketch.warn.copy(alpha = 0.62f),
                        disabledUncheckedThumbColor = sketch.surface,
                        disabledUncheckedTrackColor = sketch.lineSoft,
                        disabledUncheckedBorderColor = sketch.lineSoft,
                    ),
                )
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
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp, hoveredElevation = 0.dp),
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

            // ── Zona pericolosa — solo in modifica ───────────────────────────
            if (!isNew && onDelete != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.workspaceSketch.lineSoft),
                )
                OutlinedButton(
                    onClick = onDelete,
                    enabled = !isLoading,
                    modifier = Modifier.handCursorOnHover(enabled = !isLoading),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp, hoveredElevation = 0.dp),
                ) {
                    Text("Elimina studente")
                }
            }
        }
    }
}

@Composable
private fun EligibilityFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    metadata: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val primary = MaterialTheme.colorScheme.primary
    val chipShape = RoundedCornerShape(10.dp)
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor = if (selected) {
        primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.78f)
    }
    val disabledBorderColor = if (selected) {
        primary.copy(alpha = 0.44f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.44f)
    }

    Surface(
        modifier = modifier
            .heightIn(min = 36.dp)
            .clip(chipShape)
            .clickable(enabled = enabled, onClick = onClick),
        shape = chipShape,
        color = if (enabled) containerColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.5.dp, if (enabled) borderColor else disabledBorderColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = null,
                enabled = enabled,
                colors = CheckboxDefaults.colors(
                    checkedColor = primary,
                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                    disabledCheckedColor = primary.copy(alpha = 0.52f),
                    disabledUncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
                ),
                modifier = Modifier.size(18.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (enabled) contentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                metadata?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (enabled) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun formatLastAssignmentLabel(lastAssignedOn: LocalDate?): String {
    return if (lastAssignedOn == null) {
        "Mai assegnato"
    } else {
        "Ultima volta ${lastAssignedOn.format(shortDateFormatter)}"
    }
}

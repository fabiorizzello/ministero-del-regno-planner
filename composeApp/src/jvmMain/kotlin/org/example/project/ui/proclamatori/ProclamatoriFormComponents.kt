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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.state.ToggleableState
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
import java.time.temporal.ChronoUnit

/** Maximum length for nome and cognome fields */
private const val NAME_MAX_LENGTH = 100
private val IDENTITY_COLUMN_WIDTH = 360.dp
private val ELIGIBILITY_COLUMN_MIN_WIDTH = 420.dp
private val DELAY_COLUMN_WIDTH = 120.dp

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
            modifier = Modifier
                .padding(spacing.lg)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            // ── Two-column layout: identity (left) + idoneità (right) ────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xl),
            ) {
                // ── LEFT: identity ──────────────────────────────────────────
                Column(
                    modifier = Modifier.width(IDENTITY_COLUMN_WIDTH),
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    FormStudentAvatar(
                        nome = nomeTrim.ifBlank { nome },
                        cognome = cognomeTrim.ifBlank { cognome },
                        sesso = sesso,
                    )
                    val textFieldColors = OutlinedTextFieldDefaults.colors(
                        cursorColor = MaterialTheme.colorScheme.primary,
                        errorCursorColor = MaterialTheme.colorScheme.error,
                    )
                    OutlinedTextField(
                        value = nome,
                        onValueChange = { if (it.length <= NAME_MAX_LENGTH) onNomeChange(it) },
                        label = { Text("Nome *") },
                        isError = (showFieldErrors && nomeTrim.isBlank()) || duplicateError != null,
                        modifier = Modifier.fillMaxWidth().focusRequester(nameFr),
                        singleLine = true,
                        colors = textFieldColors,
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
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = textFieldColors,
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

                    // ── Genere — pill toggle ─────────────────────────────
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
                                val accentBg = if (sVal == Sesso.M) sketch.accentSoft else sketch.avatarFemminaBg
                                val accentFg = if (sVal == Sesso.M) sketch.accent else sketch.avatarFemminaFg
                                Surface(
                                    modifier = Modifier
                                        .clip(chipShape)
                                        .clickable { onSessoChange(sVal) }
                                        .handCursorOnHover(),
                                    shape = chipShape,
                                    color = if (selected) accentBg else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(
                                        1.5.dp,
                                        if (selected) accentFg else sketch.lineStrong,
                                    ),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(if (selected) accentFg else accentBg),
                                        )
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (selected) accentFg else MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── Sospeso — secondary toggle ──────────────────────
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
                }

                // ── RIGHT: idoneità ─────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .widthIn(min = ELIGIBILITY_COLUMN_MIN_WIDTH)
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Idoneità", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Seleziona i ruoli che può svolgere.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (leadEligibilityOptions.isNotEmpty()) {
                        EligibilityTable(
                            options = leadEligibilityOptions,
                            onToggle = onLeadEligibilityChange,
                            puoAssistere = puoAssistere,
                            onPuoAssistereChange = onPuoAssistereChange,
                            onSetAllEligibilityChange = onSetAllEligibilityChange,
                            lastAssistantAssignmentDate = lastAssistantAssignmentDate,
                            showHistoryColumns = !isNew,
                            today = remember { LocalDate.now() },
                        )
                    } else {
                        Text(
                            "Nessun ruolo disponibile. Usa Aggiorna schemi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ── Buttons ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                if (onNext != null) {
                    TextButton(
                        modifier = Modifier.handCursorOnHover(enabled = canGoToNext && !isLoading),
                        onClick = onNext,
                        enabled = canGoToNext && !isLoading,
                    ) { Text("Successivo") }
                }
                if (!isNew && onDelete != null) {
                    Spacer(Modifier.weight(1f))
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

            if (formError != null) {
                SelectionContainer {
                    Text(formError, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun FormStudentAvatar(
    nome: String,
    cognome: String,
    sesso: Sesso,
) {
    val sketch = MaterialTheme.workspaceSketch
    val bgColor = if (sesso == Sesso.M) sketch.accentSoft else sketch.avatarFemminaBg
    val fgColor = if (sesso == Sesso.M) sketch.accent else sketch.avatarFemminaFg
    val initials = buildString {
        nome.firstOrNull()?.let { append(it.uppercaseChar()) }
        cognome.firstOrNull()?.let { append(it.uppercaseChar()) }
        if (isEmpty()) append(if (sesso == Sesso.M) "U" else "D")
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(bgColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = fgColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun EligibilityTable(
    options: List<LeadEligibilityOptionUi>,
    onToggle: (PartTypeId, Boolean) -> Unit,
    puoAssistere: Boolean,
    onPuoAssistereChange: (Boolean) -> Unit,
    onSetAllEligibilityChange: (Boolean) -> Unit,
    lastAssistantAssignmentDate: LocalDate?,
    showHistoryColumns: Boolean,
    today: LocalDate,
) {
    val sketch = MaterialTheme.workspaceSketch
    val tableShape = RoundedCornerShape(10.dp)
    // Tristate: the header reflects the union of Assistente + every selectable lead option.
    val selectableLeadOptions = options.filter { it.canSelect }
    val checkedLeadCount = selectableLeadOptions.count { it.checked }
    val allSelected = puoAssistere && checkedLeadCount == selectableLeadOptions.size
    val noneSelected = !puoAssistere && checkedLeadCount == 0
    val headerState = when {
        allSelected -> ToggleableState.On
        noneSelected -> ToggleableState.Off
        else -> ToggleableState.Indeterminate
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(tableShape)
            .border(BorderStroke(1.dp, sketch.lineSoft), tableShape),
    ) {
        // ── Header row (click to toggle all) ─────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(sketch.tableHeaderSurface)
                .clickable { onSetAllEligibilityChange(headerState != ToggleableState.On) }
                .handCursorOnHover()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TriStateCheckbox(
                state = headerState,
                onClick = null,
                colors = CheckboxDefaults.colors(
                    checkedColor = sketch.accent,
                    uncheckedColor = sketch.inkMuted.copy(alpha = 0.75f),
                    checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "Parte",
                modifier = Modifier.weight(1.4f),
                style = MaterialTheme.typography.labelMedium,
                color = sketch.inkMuted,
            )
            if (showHistoryColumns) {
                Text(
                    "Ultima assegnazione",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = sketch.inkMuted,
                )
                Text(
                    "Ritardo",
                    modifier = Modifier.width(DELAY_COLUMN_WIDTH),
                    style = MaterialTheme.typography.labelMedium,
                    color = sketch.inkMuted,
                )
            }
        }

        // ── Assistente row (always first, always selectable) ─────────────
        EligibilityTableRow(
            label = "Assistente",
            checked = puoAssistere,
            enabled = true,
            lastAssignedOn = lastAssistantAssignmentDate,
            showHistoryColumns = showHistoryColumns,
            today = today,
            onClick = { onPuoAssistereChange(!puoAssistere) },
        )

        // ── Lead role rows ───────────────────────────────────────────────
        options.forEach { option ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(sketch.lineSoft),
            )
            EligibilityTableRow(
                label = option.label,
                checked = option.checked,
                enabled = option.canSelect,
                lastAssignedOn = option.lastAssignedOn,
                showHistoryColumns = showHistoryColumns,
                today = today,
                onClick = { onToggle(option.partTypeId, !option.checked) },
            )
        }
    }
}

@Composable
private fun EligibilityTableRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    lastAssignedOn: LocalDate?,
    showHistoryColumns: Boolean,
    today: LocalDate,
    onClick: () -> Unit,
) {
    val sketch = MaterialTheme.workspaceSketch
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .handCursorOnHover(enabled = enabled)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = CheckboxDefaults.colors(
                checkedColor = sketch.accent,
                uncheckedColor = sketch.inkMuted.copy(alpha = 0.75f),
                checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                disabledCheckedColor = sketch.accent.copy(alpha = 0.52f),
                disabledUncheckedColor = sketch.inkMuted.copy(alpha = 0.42f),
            ),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            modifier = Modifier.weight(1.4f),
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) sketch.ink else sketch.inkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (showHistoryColumns) {
            Text(
                text = lastAssignedOn?.format(shortDateFormatter) ?: "—",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = if (lastAssignedOn == null) sketch.inkMuted else sketch.inkSoft,
            )
            Box(
                modifier = Modifier.width(DELAY_COLUMN_WIDTH),
                contentAlignment = Alignment.CenterStart,
            ) {
                DelayPill(spec = computeDelayPill(lastAssignedOn, today))
            }
        }
    }
}

private enum class DelaySeverity { Ok, Neutral, Warn, Strong, Never }

private data class DelayPillSpec(
    val label: String,
    val severity: DelaySeverity,
)

private fun computeDelayPill(lastAssignedOn: LocalDate?, today: LocalDate): DelayPillSpec {
    if (lastAssignedOn == null) return DelayPillSpec("Mai", DelaySeverity.Never)
    val months = ChronoUnit.MONTHS.between(lastAssignedOn, today).toInt().coerceAtLeast(0)
    val severity = when {
        months <= 1 -> DelaySeverity.Ok
        months == 2 -> DelaySeverity.Neutral
        months in 3..5 -> DelaySeverity.Warn
        else -> DelaySeverity.Strong
    }
    val label = when (months) {
        0 -> "< 1 mese"
        1 -> "1 mese"
        else -> "$months mesi"
    }
    return DelayPillSpec(label, severity)
}

@Composable
private fun DelayPill(spec: DelayPillSpec) {
    val sketch = MaterialTheme.workspaceSketch
    val bg: Color
    val fg: Color
    val border: BorderStroke?
    when (spec.severity) {
        DelaySeverity.Ok -> {
            bg = sketch.ok.copy(alpha = 0.12f)
            fg = sketch.ok
            border = null
        }
        DelaySeverity.Neutral -> {
            bg = sketch.inkMuted.copy(alpha = 0.12f)
            fg = sketch.inkSoft
            border = null
        }
        DelaySeverity.Warn -> {
            bg = sketch.warn.copy(alpha = 0.14f)
            fg = sketch.warn
            border = null
        }
        DelaySeverity.Strong -> {
            bg = sketch.bad.copy(alpha = 0.14f)
            fg = sketch.bad
            border = null
        }
        DelaySeverity.Never -> {
            bg = Color.Transparent
            fg = sketch.inkMuted
            border = BorderStroke(1.dp, sketch.lineStrong)
        }
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = bg,
        border = border,
    ) {
        Text(
            text = spec.label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

package org.example.project.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.example.project.ui.components.FeedbackBanner
import org.example.project.ui.theme.spacing
import org.example.project.ui.workspace.AssignmentManagementViewModel
import org.example.project.ui.workspace.AssignmentSettingsUiState
import org.koin.core.context.GlobalContext

internal data class AssignmentSettingsSwitchPalette(
    val checkedThumbColor: Color,
    val checkedTrackColor: Color,
    val uncheckedThumbColor: Color,
    val uncheckedTrackColor: Color,
    val uncheckedBorderColor: Color,
)

internal fun assignmentSettingsSwitchPalette(colorScheme: ColorScheme): AssignmentSettingsSwitchPalette =
    AssignmentSettingsSwitchPalette(
        checkedThumbColor = colorScheme.onPrimary,
        checkedTrackColor = colorScheme.primary,
        uncheckedThumbColor = colorScheme.onSurface,
        uncheckedTrackColor = colorScheme.surfaceVariant,
        uncheckedBorderColor = colorScheme.outline,
    )

@Composable
fun AssignmentEngineSettingsScreen() {
    val viewModel = remember { GlobalContext.get().get<AssignmentManagementViewModel>() }
    val state by viewModel.uiState.collectAsState()
    val spacing = MaterialTheme.spacing

    LaunchedEffect(Unit) { viewModel.onScreenEntered() }

    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(spacing.xl)
            .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } },
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        FeedbackBanner(
            model = state.notice,
            onDismissRequest = { viewModel.dismissNotice() },
        )

        AssignmentEngineSettingsCard(
            state = state.assignmentSettings,
            settingsSaved = state.settingsSaved,
            onLeadWeightChange = { viewModel.setLeadWeight(it) },
            onAssistWeightChange = { viewModel.setAssistWeight(it) },
            onLeadCooldownChange = { viewModel.setLeadCooldownWeeks(it) },
            onAssistCooldownChange = { viewModel.setAssistCooldownWeeks(it) },
            onSave = { viewModel.saveAssignmentSettings() },
            onDismissSavedFeedback = { viewModel.dismissSettingsSaved() },
        )
        AssignmentUxSettingsCard(
            skipRemoveConfirm = state.skipRemoveConfirm,
            onSkipRemoveConfirmChange = { viewModel.setSkipRemoveConfirm(it) },
        )
    }
}

@Composable
private fun AssignmentEngineSettingsCard(
    state: AssignmentSettingsUiState,
    settingsSaved: Boolean,
    onLeadWeightChange: (String) -> Unit,
    onAssistWeightChange: (String) -> Unit,
    onLeadCooldownChange: (String) -> Unit,
    onAssistCooldownChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismissSavedFeedback: () -> Unit,
) {
    LaunchedEffect(settingsSaved) {
        if (settingsSaved) {
            delay(2000)
            onDismissSavedFeedback()
        }
    }
    val spacing = MaterialTheme.spacing
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(spacing.cardRadius),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Impostazioni auto assegnazione", style = MaterialTheme.typography.titleMedium)
                AnimatedVisibility(visible = settingsSaved, enter = fadeIn(), exit = fadeOut()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            "Salvato",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                OutlinedTextField(
                    value = state.leadWeight,
                    onValueChange = onLeadWeightChange,
                    label = { Text("Peso conduzione") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).onFocusChanged { if (!it.isFocused) onSave() },
                    isError = state.leadWeightError != null,
                    supportingText = {
                        if (state.leadWeightError != null) {
                            Text(state.leadWeightError, color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("Numero intero >= 1")
                        }
                    },
                )
                OutlinedTextField(
                    value = state.assistWeight,
                    onValueChange = onAssistWeightChange,
                    label = { Text("Peso assistenza") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).onFocusChanged { if (!it.isFocused) onSave() },
                    isError = state.assistWeightError != null,
                    supportingText = {
                        if (state.assistWeightError != null) {
                            Text(state.assistWeightError, color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("Numero intero >= 1")
                        }
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                OutlinedTextField(
                    value = state.leadCooldownWeeks,
                    onValueChange = onLeadCooldownChange,
                    label = { Text("Cooldown conduzione (sett.)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).onFocusChanged { if (!it.isFocused) onSave() },
                    isError = state.leadCooldownError != null,
                    supportingText = {
                        if (state.leadCooldownError != null) {
                            Text(state.leadCooldownError, color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("Numero intero >= 0 (settimane)")
                        }
                    },
                )
                OutlinedTextField(
                    value = state.assistCooldownWeeks,
                    onValueChange = onAssistCooldownChange,
                    label = { Text("Cooldown assistenza (sett.)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).onFocusChanged { if (!it.isFocused) onSave() },
                    isError = state.assistCooldownError != null,
                    supportingText = {
                        if (state.assistCooldownError != null) {
                            Text(state.assistCooldownError, color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("Numero intero >= 0 (settimane)")
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun AssignmentUxSettingsCard(
    skipRemoveConfirm: Boolean,
    onSkipRemoveConfirmChange: (Boolean) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val switchPalette = assignmentSettingsSwitchPalette(MaterialTheme.colorScheme)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(spacing.cardRadius),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text("Preferenze interfaccia", style = MaterialTheme.typography.titleMedium)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Switch(
                    checked = skipRemoveConfirm,
                    onCheckedChange = onSkipRemoveConfirmChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = switchPalette.checkedThumbColor,
                        checkedTrackColor = switchPalette.checkedTrackColor,
                        checkedBorderColor = switchPalette.checkedTrackColor,
                        uncheckedThumbColor = switchPalette.uncheckedThumbColor,
                        uncheckedTrackColor = switchPalette.uncheckedTrackColor,
                        uncheckedBorderColor = switchPalette.uncheckedBorderColor,
                    ),
                )
                Column {
                    Text("Rimozione assegnazione senza conferma")
                    Text(
                        "Se attivo, le assegnazioni vengono rimosse direttamente senza dialogo di conferma.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

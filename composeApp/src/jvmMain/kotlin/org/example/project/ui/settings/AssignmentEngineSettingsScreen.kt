package org.example.project.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.example.project.ui.components.FeedbackBanner
import org.example.project.ui.components.handCursorOnHover
import org.example.project.ui.theme.spacing
import org.example.project.ui.workspace.AssignmentManagementViewModel
import org.example.project.ui.workspace.AssignmentSettingsUiState
import org.koin.core.context.GlobalContext

@Composable
fun AssignmentEngineSettingsScreen() {
    val viewModel = remember { GlobalContext.get().get<AssignmentManagementViewModel>() }
    val state by viewModel.uiState.collectAsState()
    val spacing = MaterialTheme.spacing

    LaunchedEffect(Unit) { viewModel.onScreenEntered() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        FeedbackBanner(
            model = state.notice,
            onDismissRequest = { viewModel.dismissNotice() },
        )

        AssignmentEngineSettingsCard(
            state = state.assignmentSettings,
            isSaving = state.isSavingAssignmentSettings,
            onStrictCooldownChange = { viewModel.setStrictCooldown(it) },
            onLeadWeightChange = { viewModel.setLeadWeight(it) },
            onAssistWeightChange = { viewModel.setAssistWeight(it) },
            onLeadCooldownChange = { viewModel.setLeadCooldownWeeks(it) },
            onAssistCooldownChange = { viewModel.setAssistCooldownWeeks(it) },
            onSave = { viewModel.saveAssignmentSettings() },
        )
    }
}

@Composable
private fun AssignmentEngineSettingsCard(
    state: AssignmentSettingsUiState,
    isSaving: Boolean,
    onStrictCooldownChange: (Boolean) -> Unit,
    onLeadWeightChange: (String) -> Unit,
    onAssistWeightChange: (String) -> Unit,
    onLeadCooldownChange: (String) -> Unit,
    onAssistCooldownChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text("Impostazioni auto assegnazione", style = MaterialTheme.typography.titleMedium)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Switch(
                    checked = state.strictCooldown,
                    onCheckedChange = onStrictCooldownChange,
                )
                Text("Strict cooldown (default ON)")
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
                    modifier = Modifier.weight(1f),
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
                    modifier = Modifier.weight(1f),
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
                    modifier = Modifier.weight(1f),
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
                    modifier = Modifier.weight(1f),
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    onClick = onSave,
                    enabled = !isSaving,
                    modifier = Modifier.handCursorOnHover(enabled = !isSaving),
                ) {
                    Text(if (isSaving) "Salvataggio..." else "Salva impostazioni")
                }
            }
        }
    }
}

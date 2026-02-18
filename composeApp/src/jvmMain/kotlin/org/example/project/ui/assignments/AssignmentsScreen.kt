package org.example.project.ui.assignments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.example.project.ui.components.FeedbackBanner
import org.example.project.ui.components.WeekNavigator
import org.example.project.ui.theme.spacing
import org.koin.core.context.GlobalContext

@Composable
fun AssignmentsScreen() {
    val viewModel = remember { GlobalContext.get().get<AssignmentsViewModel>() }
    val state by viewModel.state.collectAsState()
    val spacing = MaterialTheme.spacing

    // Person picker dialog
    if (state.isPickerOpen) {
        val part = state.weekPlan?.parts?.find { it.id == state.pickerWeeklyPartId }
        if (part != null) {
            val slotLabel = if (part.partType.peopleCount > 1) {
                if (state.pickerSlot == 1) "Proclamatore" else "Assistente"
            } else null
            PersonPickerDialog(
                partLabel = part.partType.label,
                slotLabel = slotLabel,
                searchTerm = state.pickerSearchTerm,
                sortGlobal = state.pickerSortGlobal,
                suggestions = state.pickerSuggestions,
                isLoading = state.isPickerLoading,
                onSearchChange = { viewModel.setPickerSearchTerm(it) },
                onToggleSort = { viewModel.togglePickerSort() },
                onAssign = { viewModel.confirmAssignment(it) },
                onDismiss = { viewModel.closePersonPicker() },
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        // Feedback banner
        FeedbackBanner(
            model = state.notice,
            onDismissRequest = { viewModel.dismissNotice() },
        )

        // Week navigator
        WeekNavigator(
            monday = state.currentMonday,
            sunday = state.sundayDate,
            indicator = state.weekIndicator,
            enabled = !state.isLoading,
            onPrevious = { viewModel.navigateToPreviousWeek() },
            onNext = { viewModel.navigateToNextWeek() },
        )

        // Status bar: completion count
        if (state.weekPlan != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    "${state.assignedSlotCount}/${state.totalSlotCount} slot assegnati",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Content
        if (state.isLoading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.weekPlan == null) {
            // Week not configured
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = spacing.xxl),
                contentAlignment = Alignment.Center,
            ) {
                Text("Settimana non configurata", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            // Parts list
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                val parts = state.weekPlan!!.parts
                items(parts, key = { it.id.value }) { part ->
                    val partAssignments = state.assignments.filter { it.weeklyPartId == part.id }
                    PartAssignmentCard(
                        part = part,
                        assignments = partAssignments,
                        displayNumber = part.sortOrder + 1,
                        onAssignSlot = { slot -> viewModel.openPersonPicker(part.id, slot) },
                        onRemoveAssignment = { id -> viewModel.removeAssignment(id) },
                    )
                }
            }
        }
    }
}

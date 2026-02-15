package org.example.project.ui.assignments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
fun AssignmentsScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Assegnazioni", style = MaterialTheme.typography.headlineMedium)
        Text("Scaffolding M3/M4 pronto: qui arriveranno validazioni e suggerimenti fuzzy.")
    }
}

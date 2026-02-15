package org.example.project.ui.weeklyparts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
fun WeeklyPartsScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Parti Settimanali", style = MaterialTheme.typography.headlineMedium)
        Text("Scaffolding M2 pronto: qui arriveranno gestione settimana, tabella parti e import JSON.")
    }
}

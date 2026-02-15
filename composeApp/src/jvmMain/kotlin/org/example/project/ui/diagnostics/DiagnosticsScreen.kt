package org.example.project.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import org.example.project.core.config.AppRuntime
import org.example.project.core.config.AppVersion

@Composable
fun DiagnosticsScreen() {
    val paths = AppRuntime.pathsOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Diagnostica", style = MaterialTheme.typography.headlineMedium)
        Text("Versione: ${AppVersion.current}")
        Text("DB: ${paths?.dbFile ?: "n/d"}")
        Text("Log: ${paths?.logsDir ?: "n/d"}")
        Text("Scaffolding M7 pronto: export zip DB + log ultimi 14 giorni da implementare.")
    }
}

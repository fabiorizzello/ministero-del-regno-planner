package org.example.project.ui.components.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.example.project.ui.theme.spacing
import org.example.project.ui.theme.workspaceTokens

enum class WorkspaceStateKind {
    Loading,
    Empty,
    Error,
}

@Composable
fun WorkspaceStatePane(
    kind: WorkspaceStateKind,
    message: String,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.workspaceTokens
    val (icon, tint) = when (kind) {
        WorkspaceStateKind.Loading -> Icons.Filled.HourglassTop to MaterialTheme.colorScheme.primary
        WorkspaceStateKind.Empty -> Icons.Filled.Inbox to MaterialTheme.colorScheme.onSurfaceVariant
        WorkspaceStateKind.Error -> Icons.Filled.ErrorOutline to MaterialTheme.colorScheme.error
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(tokens.cardRadius),
        border = BorderStroke(tokens.panelBorderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
            horizontalAlignment = Alignment.Start,
        ) {
            Icon(imageVector = icon, contentDescription = kind.name, tint = tint)
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

package org.example.project.ui.components.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.example.project.ui.components.handCursorOnHover
import org.example.project.ui.theme.spacing
import org.example.project.ui.theme.workspaceTokens

@Composable
fun WorkspacePanel(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
    content: @Composable () -> Unit,
) {
    val tokens = MaterialTheme.workspaceTokens
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(tokens.panelRadius),
        border = BorderStroke(tokens.panelBorderWidth, borderColor),
        color = containerColor,
    ) {
        Box(Modifier.padding(MaterialTheme.spacing.md)) {
            content()
        }
    }
}

@Composable
fun WorkspaceShellBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
            content = content,
        )
    }
}

@Composable
fun WorkspacePanelHeader(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val tokens = MaterialTheme.workspaceTokens
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(tokens.headerRadius),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        border = BorderStroke(tokens.panelBorderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.sm, vertical = MaterialTheme.spacing.xs),
            style = MaterialTheme.typography.labelLarge,
            color = color,
        )
    }
}

enum class WorkspaceActionTone {
    Primary,
    Positive,
    Neutral,
    DangerOutline,
}

@Composable
fun WorkspaceActionButton(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: WorkspaceActionTone = WorkspaceActionTone.Neutral,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.workspaceTokens
    val (container, content, border) = when (tone) {
        WorkspaceActionTone.Primary -> Triple(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.onPrimary,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
        )
        WorkspaceActionTone.Positive -> Triple(
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.onSecondary,
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f),
        )
        WorkspaceActionTone.Neutral -> Triple(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            MaterialTheme.colorScheme.onSurface,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f),
        )
        WorkspaceActionTone.DangerOutline -> Triple(
            Color.Transparent,
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.error.copy(alpha = 0.75f),
        )
    }
    val alpha = if (enabled) 1f else 0.45f

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .handCursorOnHover(enabled = enabled)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(tokens.controlRadius),
        color = container.copy(alpha = alpha),
        border = BorderStroke(tokens.panelBorderWidth, border.copy(alpha = alpha)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = tokens.compactControlHeight)
                .padding(horizontal = MaterialTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = content.copy(alpha = alpha),
            )
            Text(
                text = "  $label",
                color = content.copy(alpha = alpha),
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

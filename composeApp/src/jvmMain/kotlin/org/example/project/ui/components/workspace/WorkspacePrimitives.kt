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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.example.project.ui.components.handCursorOnHover
import org.example.project.ui.theme.spacing
import org.example.project.ui.theme.workspaceSketch
import org.example.project.ui.theme.workspaceTokens

@Composable
fun WorkspacePanel(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.workspaceSketch.surface,
    borderColor: Color = MaterialTheme.workspaceSketch.lineSoft,
    shape: Shape? = null,
    shadowElevation: Dp = 0.dp,
    contentPadding: Dp = 12.dp,
    content: @Composable () -> Unit,
) {
    val tokens = MaterialTheme.workspaceTokens
    val panelShape = shape ?: RoundedCornerShape(tokens.panelRadius)
    Surface(
        modifier = modifier,
        shape = panelShape,
        border = BorderStroke(tokens.panelBorderWidth, borderColor),
        color = containerColor,
        shadowElevation = shadowElevation,
    ) {
        Box(Modifier.padding(contentPadding)) {
            content()
        }
    }
}

@Composable
fun WorkspaceShellBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val sketch = MaterialTheme.workspaceSketch
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = sketch.toolbarBackground,
        border = BorderStroke(1.dp, sketch.lineSoft.copy(alpha = 0.95f)),
        shadowElevation = 0.dp,
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
    color: Color = MaterialTheme.workspaceSketch.inkMuted,
) {
    val tokens = MaterialTheme.workspaceTokens
    val sketch = MaterialTheme.workspaceSketch
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(tokens.headerRadius),
        color = sketch.surfaceMuted.copy(alpha = 0.92f),
        border = BorderStroke(tokens.panelBorderWidth, sketch.lineSoft),
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.sm, vertical = MaterialTheme.spacing.xs),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
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
    val sketch = MaterialTheme.workspaceSketch
    val (container, content, border) = when (tone) {
        WorkspaceActionTone.Primary -> Triple(
            sketch.accent.copy(alpha = 0.2f),
            sketch.accent,
            sketch.accent.copy(alpha = 0.6f),
        )
        WorkspaceActionTone.Positive -> Triple(
            sketch.ok.copy(alpha = 0.22f),
            sketch.ok,
            sketch.ok.copy(alpha = 0.6f),
        )
        WorkspaceActionTone.Neutral -> Triple(
            sketch.surfaceMuted,
            sketch.inkSoft,
            sketch.lineSoft,
        )
        WorkspaceActionTone.DangerOutline -> Triple(
            sketch.surfaceMuted,
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
        )
    }
    val alpha = if (enabled) 1f else 0.45f
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .handCursorOnHover(enabled = enabled)
            .heightIn(min = tokens.compactControlHeight)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(tokens.controlRadius),
        color = container.copy(alpha = alpha),
        border = BorderStroke(tokens.panelBorderWidth, border.copy(alpha = alpha)),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.xs, vertical = 3.dp),
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

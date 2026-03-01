package org.example.project.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class WorkspaceTokens(
    val windowRadius: Dp = 12.dp,
    val panelRadius: Dp = 16.dp,
    val headerRadius: Dp = 10.dp,
    val controlRadius: Dp = 10.dp,
    val cardRadius: Dp = 14.dp,
    val panelBorderWidth: Dp = 1.dp,
    val compactControlHeight: Dp = 36.dp,
)

val LocalWorkspaceTokens = staticCompositionLocalOf { WorkspaceTokens() }

val MaterialTheme.workspaceTokens: WorkspaceTokens
    @Composable
    @ReadOnlyComposable
    get() = LocalWorkspaceTokens.current

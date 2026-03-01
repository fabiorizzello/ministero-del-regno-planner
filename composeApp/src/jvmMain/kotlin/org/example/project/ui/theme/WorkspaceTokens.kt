package org.example.project.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class WorkspaceTokens(
    val windowRadius: Dp = 10.dp,
    val panelRadius: Dp = 8.dp,
    val headerRadius: Dp = 6.dp,
    val controlRadius: Dp = 6.dp,
    val cardRadius: Dp = 8.dp,
    val panelBorderWidth: Dp = 1.dp,
    val compactControlHeight: Dp = 34.dp,
)

val LocalWorkspaceTokens = staticCompositionLocalOf { WorkspaceTokens() }

val MaterialTheme.workspaceTokens: WorkspaceTokens
    @Composable
    @ReadOnlyComposable
    get() = LocalWorkspaceTokens.current

package org.example.project.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class WorkspaceSketchPalette(
    val windowBackground: Color = Color(0xFF0B0D12),
    val windowBorder: Color = Color(0xFF24304A),
    val toolbarBackground: Color = Color(0xFF111524),
    val panelLeft: Color = Color(0xFF111524),
    val panelMid: Color = Color(0xFF0F1320),
    val panelRight: Color = Color(0xFF111524),
    val surface: Color = Color(0xFF121A2C),
    val surfaceMuted: Color = Color(0xFF0F1320),
    val lineSoft: Color = Color(0xFF24304A),
    val lineStrong: Color = Color(0xFF2F3C59),
    val ink: Color = Color(0xFFE8ECFF),
    val inkSoft: Color = Color(0xFFC3CDEC),
    val inkMuted: Color = Color(0xFFA8B2D8),
    val accent: Color = Color(0xFF6EA8FF),
    val accentSoft: Color = Color(0xFF1B2740),
    val ok: Color = Color(0xFF35D39A),
    val warn: Color = Color(0xFFFFB020),
    val bad: Color = Color(0xFFFF6B7A),
)

val LocalWorkspaceSketchPalette = staticCompositionLocalOf { WorkspaceSketchPalette() }

val MaterialTheme.workspaceSketch: WorkspaceSketchPalette
    @Composable
    @ReadOnlyComposable
    get() = LocalWorkspaceSketchPalette.current

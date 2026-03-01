package org.example.project.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class WorkspaceSketchPalette(
    val windowBackground: Color = Color(0xFFF7F9FC),
    val windowBorder: Color = Color(0xFFD7E0ED),
    val toolbarBackground: Color = Color(0xFFFFFFFF),
    val panelLeft: Color = Color(0xFFF4F7FB),
    val panelMid: Color = Color(0xFFF7F9FC),
    val panelRight: Color = Color(0xFFF8FBFF),
    val surface: Color = Color(0xFFFFFFFF),
    val lineSoft: Color = Color(0xFFD8E1EE),
    val lineStrong: Color = Color(0xFFC7D2E3),
    val ink: Color = Color(0xFF1E293B),
    val inkSoft: Color = Color(0xFF384458),
    val inkMuted: Color = Color(0xFF66758E),
    val accent: Color = Color(0xFF4F8DD8),
    val accentSoft: Color = Color(0xFFE5EBF7),
    val ok: Color = Color(0xFF2D9A63),
    val warn: Color = Color(0xFFCF8A24),
)

val LocalWorkspaceSketchPalette = staticCompositionLocalOf { WorkspaceSketchPalette() }

val MaterialTheme.workspaceSketch: WorkspaceSketchPalette
    @Composable
    @ReadOnlyComposable
    get() = LocalWorkspaceSketchPalette.current

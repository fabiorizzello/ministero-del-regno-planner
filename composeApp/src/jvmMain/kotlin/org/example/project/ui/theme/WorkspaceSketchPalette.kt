package org.example.project.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class WorkspaceSketchPalette(
    val windowBackground: Color = Color(0xFFF5F7FA),
    val windowBorder: Color = Color(0xFFD8DEE9),
    // Toolbar
    val toolbarBackground: Color = Color(0xFFFFFFFF),
    val toolbarBorder: Color = Color(0xFFE4E9F2),
    val toolbarInk: Color = Color(0xFF1A2035),
    val toolbarInkMuted: Color = Color(0xFF4A5568),
    val toolbarSurface: Color = Color(0xFFF0F2F7),
    val toolbarSelectedBg: Color = Color(0xFFE8EDFF),
    val toolbarSelectedBorder: Color = Color(0xFF2B6DE8),
    val toolbarSelectedInk: Color = Color(0xFF2B6DE8),
    // Workspace panels — light
    val panelLeft: Color = Color(0xFFF0F2F7),
    val panelMid: Color = Color(0xFFEBEEF5),
    val panelRight: Color = Color(0xFFF0F2F7),
    val surface: Color = Color(0xFFFFFFFF),
    val surfaceMuted: Color = Color(0xFFF5F7FA),
    val lineSoft: Color = Color(0xFFE2E8F0),
    val lineStrong: Color = Color(0xFFCBD5E1),
    val ink: Color = Color(0xFF1A2035),
    val inkSoft: Color = Color(0xFF2D3A52),
    val inkMuted: Color = Color(0xFF64748B),
    val accent: Color = Color(0xFF2B6DE8),
    val accentSoft: Color = Color(0xFFEBF0FF),
    val ok: Color = Color(0xFF16A34A),
    val warn: Color = Color(0xFFD97706),
    val bad: Color = Color(0xFFDC2626),
    // Avatar gender colors
    val avatarFemminaBg: Color = Color(0xFFF3E8FF),
    val avatarFemminaFg: Color = Color(0xFF7C3AED),
)

val LocalWorkspaceSketchPalette = staticCompositionLocalOf { WorkspaceSketchPalette() }

val MaterialTheme.workspaceSketch: WorkspaceSketchPalette
    @Composable
    @ReadOnlyComposable
    get() = LocalWorkspaceSketchPalette.current

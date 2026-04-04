package org.example.project.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class WorkspaceSketchPalette(
    val windowBackground: Color = Color(0xFFF0F4F8),
    val windowBorder: Color = Color(0xFFD2DAE6),
    // Toolbar
    val toolbarBackground: Color = Color(0xFFF7F9FC),
    val toolbarBorder: Color = Color(0xFFDCE4F0),
    val toolbarInk: Color = Color(0xFF1A2035),
    val toolbarInkMuted: Color = Color(0xFF4A5568),
    val toolbarSurface: Color = Color(0xFFECF1F7),
    val toolbarSelectedBg: Color = Color(0xFFE8EDFF),
    val toolbarSelectedBorder: Color = Color(0xFF2B6DE8),
    val toolbarSelectedInk: Color = Color(0xFF2B6DE8),
    // Workspace panels — light
    val panelLeft: Color = Color(0xFFEEF3F8),
    val panelMid: Color = Color(0xFFE7EDF5),
    val panelRight: Color = Color(0xFFEEF3F8),
    val surface: Color = Color(0xFFFBFCFE),
    val surfaceMuted: Color = Color(0xFFF2F5F9),
    val cardSurface: Color = Color(0xFFFDFEFF),
    val cardSurfaceMuted: Color = Color(0xFFF3F6FA),
    val cardBorder: Color = Color(0xFFD8E0EA),
    val cardBorderStrong: Color = Color(0xFFBFCBDA),
    val menuSurface: Color = Color(0xFFFAFBFD),
    val menuBorder: Color = Color(0xFFD8E0EA),
    val tableHeaderSurface: Color = Color(0xFFF1F5F9),
    val statePaneSurface: Color = Color(0xFFEEF3F7),
    val statePaneBorder: Color = Color(0xFFC7D2DF),
    val selectionSurface: Color = Color(0xFFEBF0FF),
    val selectionSurfaceMuted: Color = Color(0xFFF2F5FF),
    val selectionBorder: Color = Color(0xFF2B6DE8),
    val focusSurface: Color = Color(0xFFEAF0FF),
    val hoverSurface: Color = Color(0xFFEEF3F8),
    val lineSoft: Color = Color(0xFFDAE2EC),
    val lineStrong: Color = Color(0xFFC3CFDD),
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

fun darkWorkspaceSketchPalette() = WorkspaceSketchPalette(
    windowBackground = Color(0xFF111827),
    windowBorder = Color(0xFF2B3648),
    toolbarBackground = Color(0xFF162033),
    toolbarBorder = Color(0xFF2A3648),
    toolbarInk = Color(0xFFE5EDF8),
    toolbarInkMuted = Color(0xFFA8B7CC),
    toolbarSurface = Color(0xFF202C3D),
    toolbarSelectedBg = Color(0xFF223456),
    toolbarSelectedBorder = Color(0xFF69A7FF),
    toolbarSelectedInk = Color(0xFF9CC4FF),
    panelLeft = Color(0xFF182233),
    panelMid = Color(0xFF141D2C),
    panelRight = Color(0xFF182233),
    surface = Color(0xFF1C2636),
    surfaceMuted = Color(0xFF172131),
    cardSurface = Color(0xFF202B3B),
    cardSurfaceMuted = Color(0xFF1A2434),
    cardBorder = Color(0xFF334155),
    cardBorderStrong = Color(0xFF475569),
    menuSurface = Color(0xFF1B2535),
    menuBorder = Color(0xFF334155),
    tableHeaderSurface = Color(0xFF192334),
    statePaneSurface = Color(0xFF182334),
    statePaneBorder = Color(0xFF3C4B60),
    selectionSurface = Color(0xFF223456),
    selectionSurfaceMuted = Color(0xFF1E304F),
    selectionBorder = Color(0xFF69A7FF),
    focusSurface = Color(0xFF21324F),
    hoverSurface = Color(0xFF243042),
    lineSoft = Color(0xFF334155),
    lineStrong = Color(0xFF475569),
    ink = Color(0xFFF3F7FD),
    inkSoft = Color(0xFFD6DFEC),
    inkMuted = Color(0xFF9FB0C6),
    accent = Color(0xFF69A7FF),
    accentSoft = Color(0xFF203457),
    ok = Color(0xFF4ADE80),
    warn = Color(0xFFF59E0B),
    bad = Color(0xFFF87171),
    avatarFemminaBg = Color(0xFF3B2A57),
    avatarFemminaFg = Color(0xFFD8B4FE),
)

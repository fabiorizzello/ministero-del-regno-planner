package org.example.project.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.WindowScope
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import kotlin.math.roundToInt
import org.example.project.ui.components.handCursorOnHover
import org.example.project.ui.components.workspace.TopBarHitTarget
import org.example.project.ui.components.workspace.TopBarInteractionPolicy
import org.example.project.ui.components.workspace.WorkspaceShellBar
import org.example.project.ui.components.workspace.windowToggleOnDoubleClick
import org.example.project.ui.diagnostics.DiagnosticsScreen
import org.example.project.ui.proclamatori.ProclamatoriScreen
import org.example.project.ui.theme.AppTheme
import org.example.project.ui.theme.spacing
import org.example.project.ui.theme.workspaceSketch
import org.example.project.ui.theme.workspaceTokens
import org.example.project.ui.workspace.ProgramWorkspaceScreen

internal val LocalSectionNavigator = staticCompositionLocalOf<(AppSection) -> Unit> { {} }
private const val UI_SCALE_MIN = 0.85f
private const val UI_SCALE_MAX = 1.25f
private const val UI_SCALE_STEP_PERCENT = 5
private const val UI_SCALE_SLIDER_STEPS = 7
private val UI_SCALE_PRESET_PERCENTAGES = listOf(90, 100, 110, 120)
private const val TAG_TOP_BAR = "top-bar"
private const val TAG_SECTION_PROGRAMMA = "top-section-programma"
private const val TAG_SECTION_PROCLAMATORI = "top-section-proclamatori"
private const val TAG_SECTION_DIAGNOSTICA = "top-section-diagnostica"

internal enum class AppSection(
    val label: String,
    val icon: ImageVector,
    val screen: Screen,
) {
    PLANNING("Programma", Icons.Filled.Dashboard, PlanningDashboardSectionScreen),
    PROCLAMATORI("Proclamatori", Icons.Filled.Groups, ProclamatoriSectionScreen),
    DIAGNOSTICS("Diagnostica", Icons.Filled.BugReport, DiagnosticsSectionScreen),
}

@Composable
fun WindowScope.AppScreen(
    initialUiScale: Float = 1f,
    onUiScaleChange: (Float) -> Unit = {},
    isWindowMaximized: Boolean = false,
    onRequestMinimize: () -> Unit = {},
    onRequestToggleMaximize: () -> Unit = {},
    onRequestClose: () -> Unit = {},
) {
    AppTheme {
        val spacing = MaterialTheme.spacing
        val workspaceTokens = MaterialTheme.workspaceTokens
        val sketch = MaterialTheme.workspaceSketch
        val topBarPolicy = remember { TopBarInteractionPolicy() }
        var uiScale by rememberSaveable(initialUiScale) {
            mutableFloatStateOf(initialUiScale.coerceIn(UI_SCALE_MIN, UI_SCALE_MAX))
        }
        var draftUiScale by remember { mutableFloatStateOf(uiScale) }
        var isSizeMenuExpanded by rememberSaveable { mutableStateOf(false) }
        val baseDensity = LocalDensity.current
        val scaledDensity = remember(baseDensity, uiScale) {
            Density(
                density = baseDensity.density * uiScale,
                fontScale = baseDensity.fontScale * uiScale,
            )
        }

        Navigator(AppSection.PLANNING.screen) { navigator ->
                val currentSection = AppSection.entries
                    .firstOrNull { it.screen::class == navigator.lastItem::class }
                    ?: AppSection.PLANNING
                val navigationSections = remember { AppSection.entries.toList() }

                CompositionLocalProvider(
                    LocalSectionNavigator provides { section ->
                        if (currentSection != section) {
                            navigator.replaceAll(section.screen)
                        }
                    },
                    LocalDensity provides scaledDensity,
                ) {
                    val navigateToSection = LocalSectionNavigator.current

                    fun applyUiScale(value: Float) {
                        val updatedScale = snapUiScale(value)
                        if (updatedScale != uiScale) {
                            uiScale = updatedScale
                            onUiScaleChange(updatedScale)
                        }
                        draftUiScale = updatedScale
                    }

                    val windowBackdrop = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF59D0FF).copy(alpha = 0.32f),
                            MaterialTheme.colorScheme.background,
                            Color(0xFFE8EEFA),
                        ),
                        radius = 1800f,
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(windowBackdrop),
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxSize(),
                            shape = RoundedCornerShape(workspaceTokens.windowRadius),
                            color = sketch.windowBackground,
                            border = BorderStroke(1.dp, sketch.windowBorder),
                            shadowElevation = 12.dp,
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                WorkspaceShellBar(
                                    modifier = Modifier
                                        .height(46.dp)
                                        .testTag(TAG_TOP_BAR),
                                ) {
                                    WindowDraggableArea(
                                        modifier = Modifier
                                            .width(180.dp)
                                            .fillMaxHeight()
                                            .windowToggleOnDoubleClick(
                                                enabled = topBarPolicy.canToggleWindowOnDoubleClick(TopBarHitTarget.NonInteractive),
                                                onToggle = onRequestToggleMaximize,
                                            ),
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = spacing.xs),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(workspaceTokens.controlRadius),
                                                color = sketch.accentSoft,
                                                border = BorderStroke(1.dp, sketch.lineStrong),
                                            ) {
                                                Text(
                                                    text = "S",
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = sketch.accent,
                                                )
                                            }
                                            Text(
                                                text = "Scuola di ministero",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = sketch.ink,
                                            )
                                        }
                                    }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                ) {
                                    if (topBarPolicy.canStartWindowDrag(TopBarHitTarget.NonInteractive)) {
                                        WindowDraggableArea(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .windowToggleOnDoubleClick(
                                                    enabled = topBarPolicy.canToggleWindowOnDoubleClick(TopBarHitTarget.NonInteractive),
                                                    onToggle = onRequestToggleMaximize,
                                                ),
                                        ) {
                                            Box(modifier = Modifier.fillMaxSize())
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        navigationSections.forEach { section ->
                                            TopBarSectionButton(
                                                selected = currentSection == section,
                                                onClick = { navigateToSection(section) },
                                                section = section,
                                                tag = when (section) {
                                                    AppSection.PLANNING -> TAG_SECTION_PROGRAMMA
                                                    AppSection.PROCLAMATORI -> TAG_SECTION_PROCLAMATORI
                                                    AppSection.DIAGNOSTICS -> TAG_SECTION_DIAGNOSTICA
                                                },
                                            )
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
                                ) {
                                    Box {
                                        ToolbarIconAction(
                                            onClick = {
                                                draftUiScale = uiScale
                                                isSizeMenuExpanded = true
                                            },
                                            icon = Icons.Filled.FormatSize,
                                            contentDescription = "Dimensione testo",
                                        )

                                        DropdownMenu(
                                            expanded = isSizeMenuExpanded,
                                            onDismissRequest = {
                                                applyUiScale(draftUiScale)
                                                isSizeMenuExpanded = false
                                            },
                                            properties = PopupProperties(focusable = true),
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .width(300.dp)
                                                    .padding(spacing.md),
                                                verticalArrangement = Arrangement.spacedBy(spacing.xs),
                                            ) {
                                                val draftPercentage = draftUiScale.toUiScalePercentage()
                                                Text("Dimensione testo")
                                                Text("$draftPercentage%", style = MaterialTheme.typography.bodySmall)
                                                Slider(
                                                    value = draftUiScale,
                                                    onValueChange = { draftUiScale = snapUiScale(it) },
                                                    onValueChangeFinished = { applyUiScale(draftUiScale) },
                                                    valueRange = UI_SCALE_MIN..UI_SCALE_MAX,
                                                    steps = UI_SCALE_SLIDER_STEPS,
                                                    modifier = Modifier.fillMaxWidth(),
                                                )
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                ) {
                                                    Text("${UI_SCALE_MIN.toUiScalePercentage()}%", style = MaterialTheme.typography.labelSmall)
                                                    Text("${UI_SCALE_MAX.toUiScalePercentage()}%", style = MaterialTheme.typography.labelSmall)
                                                }
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .horizontalScroll(rememberScrollState()),
                                                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                                                ) {
                                                    UI_SCALE_PRESET_PERCENTAGES.forEach { preset ->
                                                        val presetScale = preset.toUiScale()
                                                        ScaleMenuButton(
                                                            onClick = { applyUiScale(presetScale) },
                                                            enabled = draftPercentage != preset,
                                                            label = "$preset%",
                                                        )
                                                    }
                                                }
                                                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                                                    ScaleMenuButton(
                                                        onClick = { applyUiScale((draftPercentage - UI_SCALE_STEP_PERCENT).toUiScale()) },
                                                        enabled = draftPercentage > UI_SCALE_MIN.toUiScalePercentage(),
                                                        label = "-$UI_SCALE_STEP_PERCENT%",
                                                    )
                                                    ScaleMenuButton(
                                                        onClick = { applyUiScale((draftPercentage + UI_SCALE_STEP_PERCENT).toUiScale()) },
                                                        enabled = draftPercentage < UI_SCALE_MAX.toUiScalePercentage(),
                                                        label = "+$UI_SCALE_STEP_PERCENT%",
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    WindowActionButton(
                                        onClick = onRequestMinimize,
                                        icon = Icons.Filled.Remove,
                                        contentDescription = "Minimizza finestra",
                                    )
                                    WindowActionButton(
                                        onClick = onRequestToggleMaximize,
                                        icon = if (isWindowMaximized) Icons.Filled.FilterNone else Icons.Filled.CropSquare,
                                        contentDescription = if (isWindowMaximized) "Ripristina finestra" else "Massimizza finestra",
                                    )
                                    WindowActionButton(
                                        onClick = onRequestClose,
                                        icon = Icons.Filled.Close,
                                        contentDescription = "Chiudi finestra",
                                        isDestructive = true,
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize(),
                            ) {
                                CurrentScreen()
                            }
                            }
                        }
                    }
                }
            }
        }
    }
@Composable
private fun TopBarSectionButton(
    selected: Boolean,
    onClick: () -> Unit,
    section: AppSection,
    tag: String,
) {
    val sketch = MaterialTheme.workspaceSketch
    val shape = RoundedCornerShape(MaterialTheme.workspaceTokens.controlRadius)
    val containerColor = if (selected) {
        sketch.accent.copy(alpha = 0.12f)
    } else {
        sketch.surface
    }
    val borderColor = if (selected) {
        sketch.accent.copy(alpha = 0.45f)
    } else {
        sketch.lineSoft
    }
    val contentColor = if (selected) {
        sketch.accent
    } else {
        sketch.inkSoft
    }

    Surface(
        modifier = Modifier
            .testTag(tag)
            .handCursorOnHover()
            .clickable(onClick = onClick),
        shape = shape,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = section.icon,
                contentDescription = section.label,
                tint = contentColor,
                modifier = Modifier.width(15.dp),
            )
            Text(
                text = section.label,
                color = contentColor,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun WindowActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    isDestructive: Boolean = false,
) {
    ToolbarIconAction(
        onClick = onClick,
        icon = icon,
        contentDescription = contentDescription,
        isDestructive = isDestructive,
    )
}

private data object ProclamatoriSectionScreen : Screen {
    @Composable
    override fun Content() {
        ProclamatoriScreen()
    }
}

private data object PlanningDashboardSectionScreen : Screen {
    @Composable
    override fun Content() {
        ProgramWorkspaceScreen()
    }
}

private data object DiagnosticsSectionScreen : Screen {
    @Composable
    override fun Content() {
        DiagnosticsScreen()
    }
}

private fun Float.toUiScalePercentage(): Int = (this * 100f).roundToInt()

private fun Int.toUiScale(): Float {
    val minPercentage = UI_SCALE_MIN.toUiScalePercentage()
    val maxPercentage = UI_SCALE_MAX.toUiScalePercentage()
    return this.coerceIn(minPercentage, maxPercentage) / 100f
}

private fun snapUiScale(value: Float): Float {
    val snappedPercentage = ((value * 100f) / UI_SCALE_STEP_PERCENT).roundToInt() * UI_SCALE_STEP_PERCENT
    return snappedPercentage.toUiScale()
}

@Composable
private fun ScaleMenuButton(
    onClick: () -> Unit,
    label: String,
    enabled: Boolean,
) {
    val alpha = if (enabled) 1f else 0.45f
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.9f * alpha)),
        modifier = Modifier
            .handCursorOnHover(enabled = enabled)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun ToolbarIconAction(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    isDestructive: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val sketch = MaterialTheme.workspaceSketch
    val alpha = if (enabled) 1f else 0.45f
    val bg = if (isDestructive) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.16f * alpha)
    } else {
        sketch.surface
    }
    val border = if (isDestructive) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.45f * alpha)
    } else {
        sketch.lineSoft.copy(alpha = alpha)
    }
    val tint = if (isDestructive) {
        MaterialTheme.colorScheme.error
    } else {
        sketch.inkSoft
    }
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = bg,
        border = BorderStroke(1.dp, border),
        modifier = modifier
            .handCursorOnHover(enabled = enabled)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .width(30.dp)
                .height(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint.copy(alpha = alpha),
            )
        }
    }
}

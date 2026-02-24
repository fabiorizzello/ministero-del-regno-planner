package org.example.project.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import org.example.project.ui.components.handCursorOnHover
import org.example.project.ui.diagnostics.DiagnosticsScreen
import org.example.project.ui.proclamatori.ProclamatoriScreen
import org.example.project.ui.settings.AssignmentEngineSettingsScreen
import org.example.project.ui.theme.AppTheme
import org.example.project.ui.theme.spacing
import org.example.project.ui.workspace.ProgramWorkspaceScreen
import kotlin.math.roundToInt

internal val LocalSectionNavigator = staticCompositionLocalOf<(AppSection) -> Unit> { {} }
private const val UI_SCALE_MIN = 0.85f
private const val UI_SCALE_MAX = 1.25f
private const val UI_SCALE_STEP_PERCENT = 5
private const val UI_SCALE_SLIDER_STEPS = 7
private val UI_SCALE_PRESET_PERCENTAGES = listOf(90, 100, 110, 120)

internal enum class AppSection(
    val label: String,
    val icon: ImageVector,
    val screen: Screen,
) {
    PLANNING("Programma", Icons.Filled.Dashboard, PlanningDashboardSectionScreen),
    PROCLAMATORI("Proclamatori", Icons.Filled.Groups, ProclamatoriSectionScreen),
    ASSIGNMENT_SETTINGS("Impostazioni", Icons.Filled.Settings, AssignmentEngineSettingsSectionScreen),
    DIAGNOSTICS("Diagnostica", Icons.Filled.BugReport, DiagnosticsSectionScreen),
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AppScreen(
    initialUiScale: Float = 1f,
    onUiScaleChange: (Float) -> Unit = {},
) {
    AppTheme {
        val spacing = MaterialTheme.spacing
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

        // replaceAll is used for all navigation so the back stack never exceeds one
        // entry, preventing accidental back navigation between sections.
        Navigator(AppSection.PLANNING.screen) { navigator ->
            val currentSection = AppSection.entries
                .firstOrNull { it.screen::class == navigator.lastItem::class }
                ?: AppSection.PLANNING
            val navigationSections = remember {
                listOf(AppSection.PLANNING, AppSection.PROCLAMATORI)
            }

            CompositionLocalProvider(
                LocalSectionNavigator provides { section ->
                    if (currentSection != section) {
                        navigator.replaceAll(section.screen)
                    }
                },
                LocalDensity provides scaledDensity,
            ) {
                val navigateToSection = LocalSectionNavigator.current
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f),
                                    MaterialTheme.colorScheme.background,
                                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.16f),
                                ),
                            ),
                        ),
                ) {
                    Scaffold(
                        containerColor = Color.Transparent,
                        topBar = {
                            TopAppBar(
                                title = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = currentSection.icon,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Text(
                                            text = currentSection.label,
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                                    scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                                ),
                                actions = {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        navigationSections.forEach { section ->
                                            TopBarSectionIconButton(
                                                selected = currentSection == section,
                                                onClick = { navigateToSection(section) },
                                                section = section,
                                            )
                                        }

                                        IconButton(
                                            onClick = { navigateToSection(AppSection.ASSIGNMENT_SETTINGS) },
                                            modifier = Modifier.handCursorOnHover(),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Settings,
                                                contentDescription = "Impostazioni assegnazione",
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }

                                        IconButton(
                                            onClick = { navigateToSection(AppSection.DIAGNOSTICS) },
                                            modifier = Modifier.handCursorOnHover(),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.BugReport,
                                                contentDescription = "Diagnostica",
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }

                                        Box {
                                            fun applyUiScale(value: Float) {
                                                val updatedScale = snapUiScale(value)
                                                if (updatedScale != uiScale) {
                                                    uiScale = updatedScale
                                                    onUiScaleChange(updatedScale)
                                                }
                                                draftUiScale = updatedScale
                                            }
                                            IconButton(
                                                onClick = {
                                                    draftUiScale = uiScale
                                                    isSizeMenuExpanded = true
                                                },
                                                modifier = Modifier.handCursorOnHover(),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.TextFields,
                                                    contentDescription = "Dimensione testo",
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            }

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
                                                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
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
                                                            OutlinedButton(
                                                                onClick = { applyUiScale(presetScale) },
                                                                enabled = draftPercentage != preset,
                                                            ) {
                                                                Text("$preset%")
                                                            }
                                                        }
                                                    }
                                                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                                                        OutlinedButton(
                                                            onClick = { applyUiScale((draftPercentage - UI_SCALE_STEP_PERCENT).toUiScale()) },
                                                            enabled = draftPercentage > UI_SCALE_MIN.toUiScalePercentage(),
                                                        ) {
                                                            Text("-$UI_SCALE_STEP_PERCENT%")
                                                        }
                                                        OutlinedButton(
                                                            onClick = { applyUiScale((draftPercentage + UI_SCALE_STEP_PERCENT).toUiScale()) },
                                                            enabled = draftPercentage < UI_SCALE_MAX.toUiScalePercentage(),
                                                        ) {
                                                            Text("+$UI_SCALE_STEP_PERCENT%")
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                            )
                        },
                    ) { paddingValues ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                                .padding(horizontal = spacing.md, vertical = spacing.sm),
                        ) {
                            CurrentScreen()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBarSectionIconButton(
    selected: Boolean,
    onClick: () -> Unit,
    section: AppSection,
) {
    val shape = RoundedCornerShape(10.dp)
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier
            .handCursorOnHover()
            .clickable(onClick = onClick)
            .size(36.dp),
        shape = shape,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Icon(
            imageVector = section.icon,
            contentDescription = section.label,
            tint = contentColor,
            modifier = Modifier.padding(8.dp).size(18.dp),
        )
    }
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

private data object AssignmentEngineSettingsSectionScreen : Screen {
    @Composable
    override fun Content() {
        AssignmentEngineSettingsScreen()
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

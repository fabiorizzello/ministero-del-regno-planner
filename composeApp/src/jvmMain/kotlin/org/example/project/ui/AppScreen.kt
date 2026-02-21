package org.example.project.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import org.example.project.ui.components.handCursorOnHover
import org.example.project.ui.assignments.AssignmentsScreen
import org.example.project.ui.diagnostics.DiagnosticsScreen
import org.example.project.ui.planning.PlanningDashboardScreen
import org.example.project.ui.proclamatori.ProclamatoriScreen
import org.example.project.ui.theme.AppTheme
import org.example.project.ui.theme.spacing
import org.example.project.ui.weeklyparts.WeeklyPartsScreen
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
    PLANNING("Cruscotto", Icons.Filled.Dashboard, PlanningDashboardSectionScreen),
    PROCLAMATORI("Proclamatori", Icons.Filled.Groups, ProclamatoriSectionScreen),
    WEEKLY_PARTS("Schemi", Icons.Filled.ViewWeek, WeeklyPartsSectionScreen),
    ASSIGNMENTS("Assegnazioni", Icons.Filled.Checklist, AssignmentsSectionScreen),
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

            CompositionLocalProvider(
                LocalSectionNavigator provides { section ->
                    if (currentSection != section) {
                        navigator.replaceAll(section.screen)
                    }
                },
                LocalDensity provides scaledDensity,
            ) {
                val navigateToSection = LocalSectionNavigator.current
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    AppSection.entries.forEach { section ->
                                        TopBarSectionButton(
                                            selected = currentSection == section,
                                            onClick = { navigateToSection(section) },
                                            section = section,
                                        )
                                    }
                                }
                            },
                            actions = {
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

@Composable
private fun TopBarSectionButton(
    selected: Boolean,
    onClick: () -> Unit,
    section: AppSection,
) {
    val spacing = MaterialTheme.spacing
    val shape = RoundedCornerShape(999.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val containerColor = when {
        selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isHovered) 0.95f else 0.85f)
        isHovered -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.60f)
        else -> MaterialTheme.colorScheme.surface
    }
    val borderColor = when {
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
        isHovered -> MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .handCursorOnHover()
            .clip(shape)
            .background(containerColor, shape)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .hoverable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Icon(
            imageVector = section.icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = section.label,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

private data object ProclamatoriSectionScreen : Screen {
    @Composable
    override fun Content() {
        ProclamatoriScreen()
    }
}

private data object WeeklyPartsSectionScreen : Screen {
    @Composable
    override fun Content() {
        WeeklyPartsScreen()
    }
}

private data object AssignmentsSectionScreen : Screen {
    @Composable
    override fun Content() {
        AssignmentsScreen()
    }
}

private data object PlanningDashboardSectionScreen : Screen {
    @Composable
    override fun Content() {
        PlanningDashboardScreen()
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

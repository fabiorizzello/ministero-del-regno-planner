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
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import org.example.project.ui.components.handCursorOnHover
import org.example.project.ui.assignments.AssignmentsScreen
import org.example.project.ui.diagnostics.DiagnosticsScreen
import org.example.project.ui.proclamatori.ProclamatoriScreen
import org.example.project.ui.theme.AppTheme
import org.example.project.ui.theme.spacing
import org.example.project.ui.weeklyparts.WeeklyPartsScreen
import kotlin.math.roundToInt

internal val LocalSectionNavigator = staticCompositionLocalOf<(AppSection) -> Unit> { {} }
private const val UI_SCALE_MIN = 0.85f
private const val UI_SCALE_MAX = 1.25f
private const val UI_SCALE_STEP_PERCENT = 5

internal enum class AppSection(
    val label: String,
    val icon: ImageVector,
    val screen: Screen,
) {
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
        Navigator(AppSection.PROCLAMATORI.screen) { navigator ->
            val currentSection = AppSection.entries
                .firstOrNull { it.screen::class == navigator.lastItem::class }
                ?: AppSection.PROCLAMATORI

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
                                    IconButton(
                                        onClick = { isSizeMenuExpanded = true },
                                        modifier = Modifier.handCursorOnHover(),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.TextFields,
                                            contentDescription = "Dimensione interfaccia",
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = isSizeMenuExpanded,
                                        onDismissRequest = { isSizeMenuExpanded = false },
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .width(260.dp)
                                                .padding(spacing.md),
                                            verticalArrangement = Arrangement.spacedBy(spacing.sm),
                                        ) {
                                            val uiScalePercentage = (uiScale * 100f).roundToInt()
                                            val minPercentage = (UI_SCALE_MIN * 100f).roundToInt()
                                            val maxPercentage = (UI_SCALE_MAX * 100f).roundToInt()
                                            fun setUiScalePercentage(value: Int) {
                                                val updatedPercentage = value.coerceIn(minPercentage, maxPercentage)
                                                val updatedScale = updatedPercentage / 100f
                                                if (updatedScale != uiScale) {
                                                    uiScale = updatedScale
                                                    onUiScaleChange(updatedScale)
                                                }
                                            }
                                            Text("Dimensione interfaccia")
                                            Text("$uiScalePercentage%", style = MaterialTheme.typography.bodySmall)
                                            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                                                OutlinedButton(
                                                    onClick = { setUiScalePercentage(uiScalePercentage - UI_SCALE_STEP_PERCENT) },
                                                    enabled = uiScalePercentage > minPercentage,
                                                ) {
                                                    Text("-$UI_SCALE_STEP_PERCENT%")
                                                }
                                                OutlinedButton(
                                                    onClick = { setUiScalePercentage(100) },
                                                    enabled = uiScalePercentage != 100,
                                                ) {
                                                    Text("100%")
                                                }
                                                OutlinedButton(
                                                    onClick = { setUiScalePercentage(uiScalePercentage + UI_SCALE_STEP_PERCENT) },
                                                    enabled = uiScalePercentage < maxPercentage,
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

private data object DiagnosticsSectionScreen : Screen {
    @Composable
    override fun Content() {
        DiagnosticsScreen()
    }
}

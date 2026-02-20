package org.example.project.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
fun AppScreen() {
    AppTheme {
        val spacing = MaterialTheme.spacing
        var uiScale by rememberSaveable { mutableFloatStateOf(1f) }
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
                                        FilterChip(
                                            selected = currentSection == section,
                                            onClick = { navigateToSection(section) },
                                            label = { Text(section.label) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = section.icon,
                                                    contentDescription = null,
                                                )
                                            },
                                            modifier = Modifier.handCursorOnHover(),
                                        )
                                    }
                                }
                            },
                            actions = {
                                Box {
                                    TextButton(
                                        onClick = { isSizeMenuExpanded = true },
                                        modifier = Modifier.handCursorOnHover(),
                                    ) {
                                        Icon(Icons.Filled.Settings, contentDescription = null)
                                        Spacer(modifier = Modifier.width(spacing.xs))
                                        Text("Dimensione")
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
                                            Text("Dimensione interfaccia")
                                            Text("$uiScalePercentage%", style = MaterialTheme.typography.bodySmall)
                                            Slider(
                                                value = uiScale,
                                                onValueChange = { uiScale = it },
                                                valueRange = 0.85f..1.25f,
                                                steps = 7,
                                                modifier = Modifier.handCursorOnHover(),
                                            )
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

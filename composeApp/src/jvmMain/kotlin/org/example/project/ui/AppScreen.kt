package org.example.project.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
fun AppScreen() {
    AppTheme {
        val spacing = MaterialTheme.spacing
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
            ) {
                Scaffold { paddingValues ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        horizontalArrangement = Arrangement.spacedBy(spacing.md),
                    ) {
                        NavigationRail(
                            modifier = Modifier.padding(vertical = spacing.md, horizontal = spacing.xs),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(vertical = spacing.sm),
                                verticalArrangement = Arrangement.spacedBy(spacing.md),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                val navigateToSection = LocalSectionNavigator.current
                                AppSection.entries.forEach { section ->
                                    NavigationRailItem(
                                        selected = currentSection == section,
                                        onClick = { navigateToSection(section) },
                                        icon = { Icon(section.icon, contentDescription = section.label) },
                                        label = { Text(section.label) },
                                        modifier = Modifier
                                            .width(128.dp)
                                            .handCursorOnHover(),
                                    )
                                }
                            }
                        }
                        VerticalDivider(modifier = Modifier.fillMaxHeight().padding(vertical = spacing.md))

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(spacing.md),
                        ) {
                            CurrentScreen()
                        }
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

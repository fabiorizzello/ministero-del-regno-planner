package org.example.project

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import efficaci_nel_ministero.composeapp.generated.resources.Res
import efficaci_nel_ministero.composeapp.generated.resources.icon
import org.example.project.core.bootstrap.AppBootstrap
import org.example.project.core.config.SingleInstanceGuard
import org.example.project.core.config.UiPreferencesStore
import org.example.project.core.config.WindowSettings
import org.example.project.core.config.WindowSettingsStore
import org.example.project.core.config.toSettingsSnapshot
import org.example.project.core.di.coreModule
import org.example.project.feature.assignments.di.assignmentsModule
import org.example.project.feature.output.di.outputModule
import org.example.project.feature.people.di.peopleModule
import org.example.project.feature.programs.di.programsModule
import org.example.project.feature.schemas.di.schemasModule
import org.example.project.feature.updates.di.updatesModule
import org.example.project.feature.diagnostics.di.diagnosticsModule
import org.example.project.feature.weeklyparts.di.weeklyPartsModule
import org.example.project.ui.di.viewModelsModule
import org.example.project.ui.theme.ThemeMode
import org.example.project.ui.theme.WorkspaceSketchPalette
import org.example.project.ui.theme.darkWorkspaceSketchPalette
import kotlinx.coroutines.flow.distinctUntilChanged
import org.example.project.ui.AppScreen
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.default
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.intui.window.decoratedWindow
import org.jetbrains.jewel.intui.window.styling.dark
import org.jetbrains.jewel.intui.window.styling.defaults
import org.jetbrains.jewel.intui.window.styling.light
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.window.DecoratedWindow
import org.jetbrains.jewel.window.styling.DecoratedWindowColors
import org.jetbrains.jewel.window.styling.DecoratedWindowStyle
import org.jetbrains.jewel.window.styling.TitleBarColors
import org.jetbrains.jewel.window.styling.TitleBarMetrics
import org.jetbrains.jewel.window.styling.TitleBarStyle
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.Dimension

fun main() {
    System.getenv("SKIKO_RENDER_API")?.let { System.setProperty("skiko.renderApi", it) }
    val uncaughtExceptionLogger = KotlinLogging.logger("UncaughtException")
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        uncaughtExceptionLogger.error(throwable) { "Eccezione non gestita nel thread ${thread.name}" }
    }
    val startupLogger = KotlinLogging.logger("Startup")

    if (!SingleInstanceGuard.acquire()) {
        startupLogger.info { "Richiesta seconda istanza ignorata: applicazione già in esecuzione." }
        return
    }

    try {
        AppBootstrap.initialize()
        if (GlobalContext.getOrNull() == null) {
            startKoin {
                modules(
                    coreModule,
                    peopleModule,
                    programsModule,
                    schemasModule,
                    weeklyPartsModule,
                    assignmentsModule,
                    diagnosticsModule,
                    outputModule,
                    updatesModule,
                    viewModelsModule,
                )
            }
        }

        application {
            val settingsStore = remember { GlobalContext.get().get<WindowSettingsStore>() }
            val uiPreferencesStore = remember { GlobalContext.get().get<UiPreferencesStore>() }
            val initialWindowSettings = remember { settingsStore.load() }
            val initialUiScale = remember { settingsStore.loadUiScale() }
            val initialThemeMode = remember(uiPreferencesStore) {
                runCatching {
                    ThemeMode.valueOf(uiPreferencesStore.loadThemeMode(ThemeMode.LIGHT.name))
                }.getOrDefault(ThemeMode.LIGHT)
            }
            var themeMode by remember { mutableStateOf(initialThemeMode) }
            val initialPosition = if (
                initialWindowSettings.positionXDp != WindowSettings.POSITION_UNSET &&
                initialWindowSettings.positionYDp != WindowSettings.POSITION_UNSET
            ) {
                WindowPosition.Absolute(
                    x = initialWindowSettings.positionXDp.dp,
                    y = initialWindowSettings.positionYDp.dp,
                )
            } else {
                WindowPosition.PlatformDefault
            }
            val windowState = rememberWindowState(
                width = initialWindowSettings.widthDp.dp,
                height = initialWindowSettings.heightDp.dp,
                placement = initialWindowSettings.placement,
                position = initialPosition,
            )
            val jewelThemeDefinition = remember(themeMode) {
                if (themeMode == ThemeMode.DARK) JewelTheme.darkThemeDefinition()
                else JewelTheme.lightThemeDefinition()
            }
            val workspaceSketch = remember(themeMode) {
                if (themeMode == ThemeMode.DARK) darkWorkspaceSketchPalette() else WorkspaceSketchPalette()
            }
            val jewelWindowStyle = remember(workspaceSketch, themeMode) {
                if (themeMode == ThemeMode.DARK) {
                    DecoratedWindowStyle.dark(
                        colors = DecoratedWindowColors.dark(
                            borderColor = workspaceSketch.windowBorder,
                            inactiveBorderColor = workspaceSketch.toolbarBorder,
                        ),
                    )
                } else {
                    DecoratedWindowStyle.light(
                        colors = DecoratedWindowColors.light(
                            borderColor = workspaceSketch.windowBorder,
                            inactiveBorderColor = workspaceSketch.toolbarBorder,
                        ),
                    )
                }
            }
            val jewelTitleBarStyle = if (themeMode == ThemeMode.DARK) {
                TitleBarStyle.dark(
                    colors = TitleBarColors.dark(
                        backgroundColor = workspaceSketch.toolbarBackground,
                        inactiveBackground = workspaceSketch.surfaceMuted,
                        contentColor = workspaceSketch.toolbarInk,
                        borderColor = workspaceSketch.toolbarBorder,
                        titlePaneButtonHoveredBackground = workspaceSketch.toolbarSurface,
                        titlePaneButtonPressedBackground = workspaceSketch.lineSoft,
                        titlePaneCloseButtonHoveredBackground = workspaceSketch.bad,
                        titlePaneCloseButtonPressedBackground = workspaceSketch.bad.copy(alpha = 0.82f),
                        iconButtonHoveredBackground = workspaceSketch.toolbarSurface,
                        iconButtonPressedBackground = workspaceSketch.lineSoft,
                        dropdownHoveredBackground = workspaceSketch.toolbarSurface,
                        dropdownPressedBackground = workspaceSketch.lineSoft,
                    ),
                    metrics = TitleBarMetrics.defaults(
                        height = 48.dp,
                        titlePaneButtonSize = DpSize(48.dp, 48.dp),
                    ),
                )
            } else {
                TitleBarStyle.light(
                    colors = TitleBarColors.light(
                        backgroundColor = workspaceSketch.toolbarBackground,
                        inactiveBackground = workspaceSketch.surfaceMuted,
                        contentColor = workspaceSketch.toolbarInk,
                        borderColor = workspaceSketch.toolbarBorder,
                        titlePaneButtonHoveredBackground = workspaceSketch.toolbarSurface,
                        titlePaneButtonPressedBackground = workspaceSketch.lineSoft,
                        titlePaneCloseButtonHoveredBackground = workspaceSketch.bad,
                        titlePaneCloseButtonPressedBackground = workspaceSketch.bad.copy(alpha = 0.82f),
                        iconButtonHoveredBackground = workspaceSketch.toolbarSurface,
                        iconButtonPressedBackground = workspaceSketch.lineSoft,
                        dropdownHoveredBackground = workspaceSketch.toolbarSurface,
                        dropdownPressedBackground = workspaceSketch.lineSoft,
                    ),
                    metrics = TitleBarMetrics.defaults(
                        height = 48.dp,
                        titlePaneButtonSize = DpSize(48.dp, 48.dp),
                    ),
                )
            }

            LaunchedEffect(windowState) {
                snapshotFlow { windowState.toSettingsSnapshot() }
                    .distinctUntilChanged()
                    .collect(settingsStore::save)
            }

            IntUiTheme(
                theme = jewelThemeDefinition,
                styling = ComponentStyling.default().decoratedWindow(
                    windowStyle = jewelWindowStyle,
                    titleBarStyle = jewelTitleBarStyle,
                ),
            ) {
                DecoratedWindow(
                    state = windowState,
                    style = jewelWindowStyle,
                    onCloseRequest = {
                        settingsStore.save(windowState.toSettingsSnapshot())
                        exitApplication()
                    },
                    title = "Scuola di ministero",
                    icon = painterResource(Res.drawable.icon),
                ) {
                    LaunchedEffect(Unit) {
                        window.minimumSize = Dimension(
                            WindowSettings.MIN_WIDTH_DP,
                            WindowSettings.MIN_HEIGHT_DP,
                        )
                    }
                    AppScreen(
                        initialUiScale = initialUiScale,
                        initialThemeMode = themeMode,
                        onUiScaleChange = settingsStore::saveUiScale,
                        onThemeModeChange = {
                            themeMode = it
                            uiPreferencesStore.saveThemeMode(it.name)
                        },
                        onRestartRequested = {
                            settingsStore.save(windowState.toSettingsSnapshot())
                            exitApplication()
                        },
                    )
                }
            }
        }
    } finally {
        SingleInstanceGuard.release()
    }
}

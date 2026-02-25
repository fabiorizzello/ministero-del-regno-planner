package org.example.project

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.unit.dp
import efficaci_nel_ministero.composeapp.generated.resources.Res
import efficaci_nel_ministero.composeapp.generated.resources.icon
import org.example.project.core.bootstrap.AppBootstrap
import org.example.project.core.config.WindowSettings
import org.example.project.core.config.WindowSettingsStore
import org.example.project.core.config.toSettingsSnapshot
import org.example.project.core.di.coreModule
import org.example.project.feature.assignments.di.assignmentsModule
import org.example.project.feature.output.di.outputModule
import org.example.project.feature.people.di.peopleModule
import org.example.project.feature.planning.di.planningModule
import org.example.project.feature.programs.di.programsModule
import org.example.project.feature.schemas.di.schemasModule
import org.example.project.feature.updates.di.updatesModule
import org.example.project.feature.weeklyparts.di.weeklyPartsModule
import org.example.project.ui.di.viewModelsModule
import kotlinx.coroutines.flow.distinctUntilChanged
import org.example.project.ui.AppScreen
import org.jetbrains.compose.resources.painterResource
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.slf4j.LoggerFactory

fun main() {
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        val logger = LoggerFactory.getLogger("UncaughtException")
        logger.error("Eccezione non gestita nel thread {}", thread.name, throwable)
    }

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
                outputModule,
                updatesModule,
                planningModule,
                viewModelsModule,
            )
        }
    }

    application {
        val settingsStore = remember { GlobalContext.get().get<WindowSettingsStore>() }
        val initialWindowSettings = remember { settingsStore.load() }
        val initialUiScale = remember { settingsStore.loadUiScale() }
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

        LaunchedEffect(windowState) {
            snapshotFlow { windowState.toSettingsSnapshot() }
                .distinctUntilChanged()
                .collect(settingsStore::save)
        }

        Window(
            state = windowState,
            onCloseRequest = {
                settingsStore.save(windowState.toSettingsSnapshot())
                exitApplication()
            },
            title = "Efficaci Nel Ministero",
            icon = painterResource(Res.drawable.icon),
        ) {
            AppScreen(
                initialUiScale = initialUiScale,
                onUiScaleChange = settingsStore::saveUiScale,
            )
        }
    }
}

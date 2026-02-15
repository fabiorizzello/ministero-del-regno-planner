package org.example.project

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.application
import org.example.project.core.bootstrap.AppBootstrap
import org.example.project.core.config.WindowSettingsStore
import org.example.project.core.config.toSettingsSnapshot
import org.example.project.core.di.appModule
import kotlinx.coroutines.flow.distinctUntilChanged
import org.example.project.ui.AppScreen
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

fun main() {
    AppBootstrap.initialize()
    if (GlobalContext.getOrNull() == null) {
        startKoin { modules(appModule) }
    }

    application {
        val settingsStore = remember { GlobalContext.get().get<WindowSettingsStore>() }
        val initialWindowSettings = remember { settingsStore.load() }
        val windowState = rememberWindowState(
            width = initialWindowSettings.widthDp.dp,
            height = initialWindowSettings.heightDp.dp,
            placement = initialWindowSettings.placement,
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
        ) {
            AppScreen()
        }
    }
}

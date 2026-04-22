package org.example.project.core.di

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import java.util.prefs.Preferences
import org.example.project.core.config.AppPaths
import org.example.project.core.config.AppRuntime
import org.example.project.core.config.PathsResolver
import org.example.project.core.config.UiPreferencesStore
import org.example.project.core.config.UserConfigStore
import org.example.project.core.config.UpdateSettingsStore
import org.example.project.core.config.WindowSettingsStore
import org.example.project.core.persistence.DatabaseProvider
import org.example.project.core.persistence.SqlDelightTransactionRunner
import org.example.project.core.persistence.TransactionRunner
import io.ktor.client.HttpClient
import org.example.project.core.infrastructure.createAppHttpClient
import org.koin.dsl.module

val coreModule = module {
    single<Settings> {
        val node = Preferences.userRoot().node("org/example/project/efficaci_nel_ministero")
        PreferencesSettings(node)
    }
    single<AppPaths> { AppRuntime.paths() }
    single { WindowSettingsStore(get()) }
    single { UiPreferencesStore(get()) }
    single { UpdateSettingsStore(get()) }
    single {
        UserConfigStore(
            file = PathsResolver.userConfigFile(),
            defaultDatabaseFile = PathsResolver.defaultDatabaseFile(),
        )
    }

    single { DatabaseProvider.database() }
    single<TransactionRunner> { SqlDelightTransactionRunner(get()) }

    single<HttpClient> { createAppHttpClient() }
}

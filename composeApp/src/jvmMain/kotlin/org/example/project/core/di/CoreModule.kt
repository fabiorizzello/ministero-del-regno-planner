package org.example.project.core.di

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import java.util.prefs.Preferences
import org.example.project.core.application.SharedWeekState
import org.example.project.core.config.UpdateSettingsStore
import org.example.project.core.config.WindowSettingsStore
import org.example.project.core.persistence.DatabaseProvider
import org.example.project.core.persistence.SqlDelightTransactionRunner
import org.example.project.core.persistence.TransactionRunner
import org.koin.dsl.module

val coreModule = module {
    single<Settings> {
        val node = Preferences.userRoot().node("org/example/project/efficaci_nel_ministero")
        PreferencesSettings(node)
    }
    single { WindowSettingsStore(get()) }
    single { UpdateSettingsStore(get()) }

    single { DatabaseProvider.database() }
    single<TransactionRunner> { SqlDelightTransactionRunner(get()) }

    // Shared state
    single { SharedWeekState() }
}

package org.example.project.feature.diagnostics.di

import java.sql.DriverManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.project.core.config.AppRuntime
import org.example.project.feature.diagnostics.application.ContaStoricoUseCase
import org.example.project.feature.diagnostics.application.DiagnosticsStore
import org.example.project.feature.diagnostics.application.EliminaStoricoUseCase
import org.example.project.feature.diagnostics.application.ImportaSeedApplicazioneDaJsonUseCase
import org.example.project.feature.diagnostics.infrastructure.SqlDelightDiagnosticsStore
import org.koin.dsl.module

val diagnosticsModule = module {
    single<DiagnosticsStore> { SqlDelightDiagnosticsStore(get()) }
    factory { ContaStoricoUseCase(get()) }
    factory { ImportaSeedApplicazioneDaJsonUseCase(get(), get(), get(), get(), get()) }
    factory {
        EliminaStoricoUseCase(
            store = get(),
            transactionRunner = get(),
            vacuumDatabase = {
                withContext(Dispatchers.IO) {
                    val dbPath = AppRuntime.paths().dbFile.toAbsolutePath().toString()
                    runCatching {
                        DriverManager.getConnection("jdbc:sqlite:$dbPath").use { connection ->
                            connection.createStatement().use { statement ->
                                statement.execute("VACUUM;")
                            }
                        }
                        true
                    }.getOrDefault(false)
                }
            },
        )
    }
}

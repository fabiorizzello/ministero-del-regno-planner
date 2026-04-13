package org.example.project.feature.diagnostics.di

import java.sql.DriverManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.project.core.config.AppRuntime
import org.example.project.feature.diagnostics.application.CalcolaEquitaProclamatoriUseCase
import org.example.project.feature.diagnostics.application.ContaStoricoUseCase
import org.example.project.feature.diagnostics.application.DiagnosticsStore
import org.example.project.feature.diagnostics.application.EquitaQuery
import org.example.project.feature.diagnostics.application.EliminaStoricoUseCase
import org.example.project.feature.diagnostics.application.ImportaSeedApplicazioneDaJsonUseCase
import org.example.project.feature.diagnostics.infrastructure.SqlDelightDiagnosticsStore
import org.example.project.feature.diagnostics.infrastructure.SqlDelightEquitaQuery
import org.koin.dsl.module

val diagnosticsModule = module {
    single<DiagnosticsStore> { SqlDelightDiagnosticsStore(get()) }
    single<EquitaQuery> { SqlDelightEquitaQuery(get()) }
    factory { ContaStoricoUseCase(get()) }
    factory { CalcolaEquitaProclamatoriUseCase(get(), get()) }
    factory { ImportaSeedApplicazioneDaJsonUseCase(get(), get(), get(), get(), get(), get()) }
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

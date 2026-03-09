package org.example.project.feature.diagnostics.di

import org.example.project.feature.diagnostics.application.ContaStoricoUseCase
import org.example.project.feature.diagnostics.application.EliminaStoricoUseCase
import org.koin.dsl.module

val diagnosticsModule = module {
    single { ContaStoricoUseCase(get()) }
    single { EliminaStoricoUseCase(get(), get()) }
}

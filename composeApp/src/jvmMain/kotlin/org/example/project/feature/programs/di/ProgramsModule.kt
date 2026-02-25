package org.example.project.feature.programs.di

import org.example.project.feature.programs.application.AggiornaProgrammaDaSchemiUseCase
import org.example.project.feature.programs.application.CaricaProgrammiAttiviUseCase
import org.example.project.feature.programs.application.CreaProssimoProgrammaUseCase
import org.example.project.feature.programs.application.EliminaProgrammaFuturoUseCase
import org.example.project.feature.programs.application.GeneraSettimaneProgrammaUseCase
import org.example.project.feature.programs.application.ProgramStore
import org.example.project.feature.programs.infrastructure.SqlDelightProgramStore
import org.koin.dsl.module

val programsModule = module {
    // Programs (monthly)
    single<ProgramStore> { SqlDelightProgramStore(get()) }
    single { CreaProssimoProgrammaUseCase(get()) }
    single { CaricaProgrammiAttiviUseCase(get()) }
    single { EliminaProgrammaFuturoUseCase(get(), get(), get()) }
    single { GeneraSettimaneProgrammaUseCase(get(), get(), get(), get(), get()) }
    single { AggiornaProgrammaDaSchemiUseCase(get(), get(), get(), get(), get()) }
}

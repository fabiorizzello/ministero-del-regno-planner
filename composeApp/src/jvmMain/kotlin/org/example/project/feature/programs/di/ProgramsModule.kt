package org.example.project.feature.programs.di

import org.example.project.feature.programs.application.AggiornaProgrammaDaSchemiOperation
import org.example.project.feature.programs.application.AggiornaProgrammaDaSchemiUseCase
import org.example.project.feature.programs.application.CaricaProgrammiAttiviOperation
import org.example.project.feature.programs.application.CaricaProgrammiAttiviUseCase
import org.example.project.feature.programs.application.CreaProssimoProgrammaUseCase
import org.example.project.feature.programs.application.EliminaProgrammaUseCase
import org.example.project.feature.programs.application.GeneraSettimaneProgrammaUseCase
import org.example.project.feature.programs.application.ProgramStore
import org.example.project.feature.programs.infrastructure.SqlDelightProgramStore
import org.koin.dsl.bind
import org.koin.dsl.module

val programsModule = module {
    // Programs (monthly)
    single<ProgramStore> { SqlDelightProgramStore(get()) }
    factory { CreaProssimoProgrammaUseCase(get(), get()) }
    factory { CaricaProgrammiAttiviUseCase(get()) } bind CaricaProgrammiAttiviOperation::class
    factory { EliminaProgrammaUseCase(get(), get(), get()) }
    factory { GeneraSettimaneProgrammaUseCase(get(), get(), get(), get(), get()) }
    factory { AggiornaProgrammaDaSchemiUseCase(get(), get(), get(), get(), get()) } bind AggiornaProgrammaDaSchemiOperation::class
}

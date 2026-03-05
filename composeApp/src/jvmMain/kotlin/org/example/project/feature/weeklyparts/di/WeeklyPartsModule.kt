package org.example.project.feature.weeklyparts.di

import org.example.project.feature.weeklyparts.application.AggiungiParteUseCase
import org.example.project.feature.weeklyparts.application.AggiornaPartiSettimanaUseCase
import org.example.project.feature.weeklyparts.application.CaricaSettimanaUseCase
import org.example.project.feature.weeklyparts.application.CercaTipiParteUseCase
import org.example.project.feature.weeklyparts.application.CreaSettimanaUseCase
import org.example.project.feature.weeklyparts.application.ImpostaStatoSettimanaUseCase
import org.example.project.feature.weeklyparts.application.PartTypeStore
import org.example.project.feature.weeklyparts.application.RimuoviParteUseCase
import org.example.project.feature.weeklyparts.application.RiordinaPartiUseCase
import org.example.project.feature.weeklyparts.application.WeekPlanQueries
import org.example.project.feature.weeklyparts.application.WeekPlanStore
import org.example.project.feature.weeklyparts.infrastructure.SqlDelightPartTypeStore
import org.example.project.feature.weeklyparts.infrastructure.SqlDelightWeekPlanStore
import org.koin.dsl.module

val weeklyPartsModule = module {
    // Stores
    single<PartTypeStore> { SqlDelightPartTypeStore(get()) }
    single<WeekPlanStore> { SqlDelightWeekPlanStore(get()) }
    single<WeekPlanQueries> { get<WeekPlanStore>() }

    // Use cases
    single { CaricaSettimanaUseCase(get()) }
    single { CreaSettimanaUseCase(get(), get(), get()) }
    single { AggiungiParteUseCase(get(), get(), get()) }
    single { RimuoviParteUseCase(get(), get()) }
    single { RiordinaPartiUseCase(get(), get()) }
    single { AggiornaPartiSettimanaUseCase(get(), get(), get()) }
    single { ImpostaStatoSettimanaUseCase(get(), get()) }
    single { CercaTipiParteUseCase(get()) }
}

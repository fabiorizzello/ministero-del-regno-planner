package org.example.project.feature.weeklyparts.di

import org.example.project.core.config.RemoteConfig
import org.example.project.feature.weeklyparts.application.AggiungiParteUseCase
import org.example.project.feature.weeklyparts.application.AggiornaDatiRemotiUseCase
import org.example.project.feature.weeklyparts.application.CaricaSettimanaUseCase
import org.example.project.feature.weeklyparts.application.CercaTipiParteUseCase
import org.example.project.feature.weeklyparts.application.CreaSettimanaUseCase
import org.example.project.feature.weeklyparts.application.PartTypeStore
import org.example.project.feature.weeklyparts.application.RimuoviParteUseCase
import org.example.project.feature.weeklyparts.application.RiordinaPartiUseCase
import org.example.project.feature.weeklyparts.application.RemoteDataSource
import org.example.project.feature.weeklyparts.application.WeekPlanStore
import org.example.project.feature.weeklyparts.infrastructure.GitHubDataSource
import org.example.project.feature.weeklyparts.infrastructure.SqlDelightPartTypeStore
import org.example.project.feature.weeklyparts.infrastructure.SqlDelightWeekPlanStore
import org.koin.dsl.module

val weeklyPartsModule = module {
    // Stores
    single<PartTypeStore> { SqlDelightPartTypeStore(get()) }
    single<WeekPlanStore> { SqlDelightWeekPlanStore(get()) }
    single<RemoteDataSource> {
        GitHubDataSource(
            partTypesUrl = RemoteConfig.PART_TYPES_URL,
            weeklySchemasUrl = RemoteConfig.WEEKLY_SCHEMAS_URL,
        )
    }

    // Use cases
    single { CaricaSettimanaUseCase(get()) }
    single { CreaSettimanaUseCase(get(), get(), get()) }
    single { AggiungiParteUseCase(get(), get()) }
    single { RimuoviParteUseCase(get()) }
    single { RiordinaPartiUseCase(get()) }
    single { CercaTipiParteUseCase(get()) }
    single { AggiornaDatiRemotiUseCase(get(), get(), get()) }
}

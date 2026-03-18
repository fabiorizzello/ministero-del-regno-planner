package org.example.project.feature.people.di

import org.example.project.feature.people.application.AggiornaProclamatoreUseCase
import org.example.project.feature.people.application.CaricaIdoneitaProclamatoreUseCase
import org.example.project.feature.people.application.CaricaProclamatoreUseCase
import org.example.project.feature.people.application.CercaProclamatoriUseCase
import org.example.project.feature.people.application.CreaProclamatoreUseCase
import org.example.project.feature.people.application.EligibilityStore
import org.example.project.feature.people.application.EliminaProclamatoreUseCase
import org.example.project.feature.people.application.ImportaProclamatoriDaJsonUseCase
import org.example.project.feature.people.application.ImpostaIdoneitaAssistenzaUseCase
import org.example.project.feature.people.application.ImpostaIdoneitaConduzioneUseCase
import org.example.project.feature.people.application.ProclamatoriAggregateStore
import org.example.project.feature.people.application.ProclamatoriQuery
import org.example.project.feature.people.application.VerificaDuplicatoProclamatoreUseCase
import org.example.project.feature.people.infrastructure.SqlDelightEligibilityStore
import org.example.project.feature.people.infrastructure.SqlDelightProclamatoriQuery
import org.example.project.feature.people.infrastructure.SqlDelightProclamatoriStore
import org.koin.dsl.module

val peopleModule = module {
    // Stores
    single<ProclamatoriQuery> { SqlDelightProclamatoriQuery(get()) }
    single<ProclamatoriAggregateStore> { SqlDelightProclamatoriStore(get()) }
    single<EligibilityStore> { SqlDelightEligibilityStore(get()) }

    // Use Cases
    factory { CercaProclamatoriUseCase(get()) }
    factory { CaricaProclamatoreUseCase(get()) }
    factory { CreaProclamatoreUseCase(get(), get(), get()) }
    factory { ImportaProclamatoriDaJsonUseCase(get(), get(), get()) }
    factory { AggiornaProclamatoreUseCase(get(), get(), get(), get()) }
    factory { ImpostaIdoneitaAssistenzaUseCase(get(), get()) }
    factory { ImpostaIdoneitaConduzioneUseCase(get(), get()) }
    factory { CaricaIdoneitaProclamatoreUseCase(get()) }
    factory { EliminaProclamatoreUseCase(get(), get(), get()) }
    factory { VerificaDuplicatoProclamatoreUseCase(get()) }
}

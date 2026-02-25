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
import org.example.project.feature.people.application.ImpostaSospesoUseCase
import org.example.project.feature.people.application.ImpostaStatoProclamatoreUseCase
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
    single { CercaProclamatoriUseCase(get()) }
    single { CaricaProclamatoreUseCase(get()) }
    single { CreaProclamatoreUseCase(get(), get()) }
    single { ImportaProclamatoriDaJsonUseCase(get(), get()) }
    single { AggiornaProclamatoreUseCase(get(), get()) }
    single { ImpostaStatoProclamatoreUseCase(get()) }
    single { ImpostaSospesoUseCase(get()) }
    single { ImpostaIdoneitaAssistenzaUseCase(get()) }
    single { ImpostaIdoneitaConduzioneUseCase(get()) }
    single { CaricaIdoneitaProclamatoreUseCase(get()) }
    single { EliminaProclamatoreUseCase(get(), get(), get()) }
    single { VerificaDuplicatoProclamatoreUseCase(get()) }
}

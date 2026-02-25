package org.example.project.feature.assignments.di

import org.example.project.feature.assignments.application.AssegnaPersonaUseCase
import org.example.project.feature.assignments.application.AssignmentRanking
import org.example.project.feature.assignments.application.AssignmentRepository
import org.example.project.feature.assignments.application.AssignmentSettingsStore
import org.example.project.feature.assignments.application.AutoAssegnaProgrammaUseCase
import org.example.project.feature.assignments.application.CaricaImpostazioniAssegnatoreUseCase
import org.example.project.feature.assignments.application.PersonAssignmentLifecycle
import org.example.project.feature.assignments.application.CaricaAssegnazioniUseCase
import org.example.project.feature.assignments.application.CaricaStoricoAssegnazioniPersonaUseCase
import org.example.project.feature.assignments.application.ContaAssegnazioniPersonaUseCase
import org.example.project.feature.assignments.application.RimuoviAssegnazioneUseCase
import org.example.project.feature.assignments.application.RimuoviAssegnazioniSettimanaUseCase
import org.example.project.feature.assignments.application.SalvaImpostazioniAssegnatoreUseCase
import org.example.project.feature.assignments.application.SuggerisciProclamatoriUseCase
import org.example.project.feature.assignments.application.SvuotaAssegnazioniProgrammaUseCase
import org.example.project.feature.assignments.infrastructure.SqlDelightAssignmentStore
import org.example.project.feature.assignments.infrastructure.SqlDelightAssignmentSettingsStore
import org.koin.dsl.module

val assignmentsModule = module {
    // Stores
    single { SqlDelightAssignmentStore(get()) }
    single<AssignmentSettingsStore> { SqlDelightAssignmentSettingsStore(get()) }
    single<AssignmentRepository> { get<SqlDelightAssignmentStore>() }
    single<AssignmentRanking> { get<SqlDelightAssignmentStore>() }
    single<PersonAssignmentLifecycle> { get<SqlDelightAssignmentStore>() }

    // Use Cases
    single { CaricaImpostazioniAssegnatoreUseCase(get()) }
    single { SalvaImpostazioniAssegnatoreUseCase(get()) }
    single { CaricaAssegnazioniUseCase(get(), get()) }
    single { AssegnaPersonaUseCase(get(), get(), get()) }
    single { RimuoviAssegnazioneUseCase(get()) }
    single { RimuoviAssegnazioniSettimanaUseCase(get(), get(), get()) }
    single { SuggerisciProclamatoriUseCase(get(), get(), get(), get()) }
    single { AutoAssegnaProgrammaUseCase(get(), get(), get(), get()) }
    single { ContaAssegnazioniPersonaUseCase(get()) }
    single { CaricaStoricoAssegnazioniPersonaUseCase(get()) }
    single { SvuotaAssegnazioniProgrammaUseCase(get()) }
}

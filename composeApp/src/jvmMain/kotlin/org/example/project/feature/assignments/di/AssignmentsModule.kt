package org.example.project.feature.assignments.di

import org.example.project.feature.assignments.application.AssegnaPersonaUseCase
import org.example.project.feature.assignments.application.AssignmentRanking
import org.example.project.feature.people.application.ProclamatoriAggregateStore
import org.example.project.feature.assignments.application.AssignmentRepository
import org.example.project.feature.assignments.application.AssignmentSettingsStore
import org.example.project.feature.assignments.application.AutoAssegnaProgrammaUseCase
import org.example.project.feature.assignments.application.CaricaImpostazioniAssegnatoreUseCase
import org.example.project.feature.assignments.application.PersonAssignmentLifecycle
import org.example.project.feature.assignments.application.CaricaAssegnazioniUseCase
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
    // Assignments
    single { SqlDelightAssignmentStore(get()) }
    single<AssignmentSettingsStore> { SqlDelightAssignmentSettingsStore(get()) }
    single<AssignmentRepository> { get<SqlDelightAssignmentStore>() }
    single<AssignmentRanking> { get<SqlDelightAssignmentStore>() }
    single<PersonAssignmentLifecycle> { get<SqlDelightAssignmentStore>() }
    factory { CaricaImpostazioniAssegnatoreUseCase(get()) }
    factory { SalvaImpostazioniAssegnatoreUseCase(get(), get()) }
    factory { CaricaAssegnazioniUseCase(get(), get()) }
    factory { AssegnaPersonaUseCase(get(), get(), get<ProclamatoriAggregateStore>()) }
    factory { RimuoviAssegnazioneUseCase(get(), get()) }
    factory { RimuoviAssegnazioniSettimanaUseCase(get(), get()) }
    factory { SuggerisciProclamatoriUseCase(get(), get(), get(), get(), get()) }
    // single: class-level Mutex must be shared across all callers
    single { AutoAssegnaProgrammaUseCase(get(), get(), get(), get(), get(), get(), get()) }
    factory { ContaAssegnazioniPersonaUseCase(get()) }
    factory { SvuotaAssegnazioniProgrammaUseCase(get(), get()) }
}

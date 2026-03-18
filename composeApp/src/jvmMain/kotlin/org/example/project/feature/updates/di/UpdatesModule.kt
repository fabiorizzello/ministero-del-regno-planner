package org.example.project.feature.updates.di

import org.example.project.feature.updates.application.AggiornaApplicazione
import org.example.project.feature.updates.application.UpdateReleaseSource
import org.example.project.feature.updates.application.UpdateStatusStore
import org.example.project.feature.updates.application.VerificaAggiornamenti
import org.example.project.feature.updates.infrastructure.GitHubReleasesClient
import org.koin.dsl.module

val updatesModule = module {
    single { UpdateStatusStore() }
    single<UpdateReleaseSource> { GitHubReleasesClient(get()) }
    factory { VerificaAggiornamenti(get(), get(), get()) }
    factory { AggiornaApplicazione(get()) }
}

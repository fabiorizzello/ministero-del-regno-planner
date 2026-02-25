package org.example.project.feature.updates.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.example.project.feature.updates.application.AggiornaApplicazione
import org.example.project.feature.updates.application.UpdateScheduler
import org.example.project.feature.updates.application.UpdateStatusStore
import org.example.project.feature.updates.application.VerificaAggiornamenti
import org.example.project.feature.updates.infrastructure.GitHubReleasesClient
import org.koin.dsl.module

val updatesModule = module {
    single { UpdateStatusStore() }
    single { GitHubReleasesClient() }
    single { VerificaAggiornamenti(get(), get(), get()) }
    single { AggiornaApplicazione() }
    single(createdAtStart = true) {
        UpdateScheduler(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            verificaAggiornamenti = get(),
        )
    }
}

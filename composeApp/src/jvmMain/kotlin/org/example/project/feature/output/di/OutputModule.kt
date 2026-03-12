package org.example.project.feature.output.di

import org.example.project.feature.output.application.AnnullaConsegnaUseCase
import org.example.project.feature.output.application.CaricaRiepilogoConsegneProgrammaUseCase
import org.example.project.feature.output.application.CaricaStatoConsegneUseCase
import org.example.project.feature.output.application.FileOpener
import org.example.project.feature.output.application.GeneraImmaginiAssegnazioni
import org.example.project.feature.output.application.SegnaComInviatoUseCase
import org.example.project.feature.output.application.SlipDeliveryStore
import org.example.project.feature.output.application.StampaProgrammaUseCase
import org.example.project.feature.output.application.VerificaConsegnaPreAssegnazioneUseCase
import org.example.project.feature.output.infrastructure.DesktopFileOpener
import org.example.project.feature.output.infrastructure.PdfAssignmentsRenderer
import org.example.project.feature.output.infrastructure.PdfProgramRenderer
import org.example.project.feature.output.infrastructure.SqlDelightSlipDeliveryStore
import org.koin.dsl.module

val outputModule = module {
    // Output
    single { PdfAssignmentsRenderer() }
    single { PdfProgramRenderer() }
    single<FileOpener> { DesktopFileOpener() }
    single { GeneraImmaginiAssegnazioni(get(), get(), get(), get(), get()) }
    single { StampaProgrammaUseCase(get(), get(), get(), get(), get()) }

    // Slip delivery
    single<SlipDeliveryStore> { SqlDelightSlipDeliveryStore(get()) }
    single { SegnaComInviatoUseCase(get(), get()) }
    single { AnnullaConsegnaUseCase(get(), get()) }
    single { CaricaStatoConsegneUseCase(get()) }
    single { VerificaConsegnaPreAssegnazioneUseCase(get()) }
    single { CaricaRiepilogoConsegneProgrammaUseCase(get(), get(), get()) }
}

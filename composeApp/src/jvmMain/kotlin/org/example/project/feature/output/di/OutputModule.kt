package org.example.project.feature.output.di

import org.example.project.feature.output.application.GeneraImmaginiAssegnazioni
import org.example.project.feature.output.application.GeneraPdfAssegnazioni
import org.example.project.feature.output.application.StampaProgrammaUseCase
import org.example.project.feature.output.infrastructure.PdfAssignmentsRenderer
import org.example.project.feature.output.infrastructure.PdfProgramRenderer
import org.koin.dsl.module

val outputModule = module {
    // Output
    single { PdfAssignmentsRenderer() }
    single { PdfProgramRenderer() }
    single { GeneraPdfAssegnazioni(get(), get(), get()) }
    single { GeneraImmaginiAssegnazioni(get(), get(), get()) }
    single { StampaProgrammaUseCase(get(), get(), get(), get()) }
}

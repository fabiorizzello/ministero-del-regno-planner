package org.example.project.feature.planning.di

import org.example.project.feature.planning.application.CalcolaProgressoPianificazione
import org.example.project.feature.planning.application.CaricaPanoramicaPianificazioneFutura
import org.example.project.feature.planning.application.GeneraAlertCoperturaSettimane
import org.example.project.feature.planning.application.GeneraAlertValidazioneAssegnazioni
import org.koin.dsl.module

val planningModule = module {
    // Planning dashboard
    single { CalcolaProgressoPianificazione() }
    single { GeneraAlertCoperturaSettimane() }
    single { GeneraAlertValidazioneAssegnazioni(get(), get(), get(), get(), get(), get()) }
    single { CaricaPanoramicaPianificazioneFutura(get(), get(), get(), get(), get()) }
}

package org.example.project.core.di

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import java.util.prefs.Preferences
import org.example.project.core.application.SharedWeekState
import org.example.project.core.config.WindowSettingsStore
import org.example.project.core.persistence.DatabaseProvider
import org.example.project.core.persistence.SqlDelightTransactionRunner
import org.example.project.core.persistence.TransactionRunner
import org.example.project.feature.people.application.AggiornaProclamatoreUseCase
import org.example.project.feature.people.application.CaricaProclamatoreUseCase
import org.example.project.feature.people.application.CercaProclamatoriUseCase
import org.example.project.feature.people.application.CreaProclamatoreUseCase
import org.example.project.feature.people.application.EliminaProclamatoreUseCase
import org.example.project.feature.people.application.ImpostaStatoProclamatoreUseCase
import org.example.project.feature.people.application.ImportaProclamatoriDaJsonUseCase
import org.example.project.feature.people.application.ProclamatoriAggregateStore
import org.example.project.feature.people.application.ProclamatoriQuery
import org.example.project.feature.people.application.VerificaDuplicatoProclamatoreUseCase
import org.example.project.feature.people.infrastructure.SqlDelightProclamatoriQuery
import org.example.project.feature.people.infrastructure.SqlDelightProclamatoriStore
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.example.project.feature.assignments.application.AssegnaPersonaUseCase
import org.example.project.feature.assignments.application.AssignmentStore
import org.example.project.feature.assignments.application.CaricaAssegnazioniUseCase
import org.example.project.feature.assignments.application.ContaAssegnazioniPersonaUseCase
import org.example.project.feature.assignments.application.RimuoviAssegnazioneUseCase
import org.example.project.feature.assignments.application.SuggerisciProclamatoriUseCase
import org.example.project.feature.assignments.infrastructure.SqlDelightAssignmentStore
import org.example.project.ui.assignments.AssignmentsViewModel
import org.example.project.ui.proclamatori.ProclamatoreFormViewModel
import org.example.project.ui.proclamatori.ProclamatoriListViewModel
import org.example.project.ui.weeklyparts.WeeklyPartsViewModel
import org.koin.dsl.module

val appModule = module {
    single<Settings> {
        val node = Preferences.userRoot().node("org/example/project/efficaci_nel_ministero")
        PreferencesSettings(node)
    }
    single { WindowSettingsStore(get()) }

    single { DatabaseProvider.database() }
    single<TransactionRunner> { SqlDelightTransactionRunner(get()) }

    single<ProclamatoriQuery> { SqlDelightProclamatoriQuery(get()) }
    single<ProclamatoriAggregateStore> { SqlDelightProclamatoriStore(get()) }

    single { CercaProclamatoriUseCase(get()) }
    single { CaricaProclamatoreUseCase(get()) }
    single { CreaProclamatoreUseCase(get(), get()) }
    single { ImportaProclamatoriDaJsonUseCase(get(), get()) }
    single { AggiornaProclamatoreUseCase(get(), get()) }
    single { ImpostaStatoProclamatoreUseCase(get()) }
    single { EliminaProclamatoreUseCase(get(), get(), get()) }
    single { VerificaDuplicatoProclamatoreUseCase(get()) }

    // Weekly parts
    single<PartTypeStore> { SqlDelightPartTypeStore(get()) }
    single<WeekPlanStore> { SqlDelightWeekPlanStore(get()) }
    single<RemoteDataSource> {
        GitHubDataSource(
            partTypesUrl = "https://raw.githubusercontent.com/fabiooo4/efficaci-nel-ministero-data/main/part-types.json",
            weeklySchemasUrl = "https://raw.githubusercontent.com/fabiooo4/efficaci-nel-ministero-data/main/weekly-schemas.json",
        )
    }

    single { CaricaSettimanaUseCase(get()) }
    single { CreaSettimanaUseCase(get(), get(), get()) }
    single { AggiungiParteUseCase(get(), get()) }
    single { RimuoviParteUseCase(get()) }
    single { RiordinaPartiUseCase(get()) }
    single { CercaTipiParteUseCase(get()) }
    single { AggiornaDatiRemotiUseCase(get(), get(), get()) }

    // Assignments
    single<AssignmentStore> { SqlDelightAssignmentStore(get()) }
    single { CaricaAssegnazioniUseCase(get(), get()) }
    single { AssegnaPersonaUseCase(get(), get(), get()) }
    single { RimuoviAssegnazioneUseCase(get()) }
    single { SuggerisciProclamatoriUseCase(get(), get()) }
    single { ContaAssegnazioniPersonaUseCase(get()) }

    // Shared state
    single { SharedWeekState() }

    // ViewModels — singleton so they survive tab switches
    single {
        WeeklyPartsViewModel(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            sharedWeekState = get(),
            caricaSettimana = get(),
            creaSettimana = get(),
            aggiungiParte = get(),
            rimuoviParte = get(),
            riordinaParti = get(),
            cercaTipiParte = get(),
            aggiornaDatiRemoti = get(),
        )
    }
    single {
        AssignmentsViewModel(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            sharedWeekState = get(),
            caricaSettimana = get(),
            caricaAssegnazioni = get(),
            assegnaPersona = get(),
            rimuoviAssegnazione = get(),
            suggerisciProclamatori = get(),
        )
    }
    single {
        ProclamatoriListViewModel(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            cerca = get(),
            impostaStato = get(),
            elimina = get(),
            importaDaJson = get(),
            contaAssegnazioni = get(),
        )
    }
    single {
        ProclamatoreFormViewModel(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            carica = get(),
            crea = get(),
            aggiorna = get(),
            verificaDuplicato = get(),
        )
    }
}

package org.example.project.core.di

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import java.util.prefs.Preferences
import org.example.project.core.application.SharedWeekState
import org.example.project.core.config.RemoteConfig
import org.example.project.core.config.UpdateSettingsStore
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
import org.example.project.feature.people.application.CaricaIdoneitaProclamatoreUseCase
import org.example.project.feature.people.application.EligibilityStore
import org.example.project.feature.people.application.ImpostaIdoneitaAssistenzaUseCase
import org.example.project.feature.people.application.ImpostaIdoneitaConduzioneUseCase
import org.example.project.feature.people.application.ImpostaSospesoUseCase
import org.example.project.feature.people.application.ProclamatoriAggregateStore
import org.example.project.feature.people.application.ProclamatoriQuery
import org.example.project.feature.people.application.VerificaDuplicatoProclamatoreUseCase
import org.example.project.feature.people.infrastructure.SqlDelightEligibilityStore
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
import org.example.project.feature.output.application.GeneraImmaginiAssegnazioni
import org.example.project.feature.output.application.GeneraPdfAssegnazioni
import org.example.project.feature.output.application.StampaProgrammaUseCase
import org.example.project.feature.output.infrastructure.PdfProgramRenderer
import org.example.project.feature.output.infrastructure.PdfAssignmentsRenderer
import org.example.project.feature.planning.application.CalcolaProgressoPianificazione
import org.example.project.feature.planning.application.CaricaPanoramicaPianificazioneFutura
import org.example.project.feature.planning.application.GeneraAlertCoperturaSettimane
import org.example.project.feature.planning.application.GeneraAlertValidazioneAssegnazioni
import org.example.project.feature.updates.application.AggiornaApplicazione
import org.example.project.feature.updates.application.UpdateScheduler
import org.example.project.feature.updates.application.UpdateStatusStore
import org.example.project.feature.updates.application.VerificaAggiornamenti
import org.example.project.feature.updates.infrastructure.GitHubReleasesClient
import org.example.project.feature.programs.application.AggiornaProgrammaDaSchemiUseCase
import org.example.project.feature.programs.application.CaricaProgrammiAttiviUseCase
import org.example.project.feature.programs.application.CreaProssimoProgrammaUseCase
import org.example.project.feature.programs.application.EliminaProgrammaFuturoUseCase
import org.example.project.feature.programs.application.GeneraSettimaneProgrammaUseCase
import org.example.project.feature.programs.application.ProgramStore
import org.example.project.feature.programs.infrastructure.SqlDelightProgramStore
import org.example.project.feature.schemas.application.AggiornaSchemiUseCase
import org.example.project.feature.schemas.application.SchemaCatalogRemoteSource
import org.example.project.feature.schemas.application.SchemaTemplateStore
import org.example.project.feature.schemas.application.SchemaUpdateAnomalyStore
import org.example.project.feature.schemas.infrastructure.GitHubSchemaCatalogDataSource
import org.example.project.feature.schemas.infrastructure.SqlDelightSchemaTemplateStore
import org.example.project.feature.schemas.infrastructure.SqlDelightSchemaUpdateAnomalyStore
import org.example.project.ui.diagnostics.DiagnosticsViewModel
import org.example.project.ui.planning.PlanningDashboardViewModel
import org.example.project.ui.proclamatori.ProclamatoreFormViewModel
import org.example.project.ui.proclamatori.ProclamatoriListViewModel
import org.example.project.ui.workspace.AssignmentManagementViewModel
import org.example.project.ui.workspace.PartEditorViewModel
import org.example.project.ui.workspace.PersonPickerViewModel
import org.example.project.ui.workspace.ProgramLifecycleViewModel
import org.example.project.ui.workspace.SchemaManagementViewModel
import org.koin.dsl.module

val appModule = module {
    single<Settings> {
        val node = Preferences.userRoot().node("org/example/project/efficaci_nel_ministero")
        PreferencesSettings(node)
    }
    single { WindowSettingsStore(get()) }
    single { UpdateSettingsStore(get()) }

    single { DatabaseProvider.database() }
    single<TransactionRunner> { SqlDelightTransactionRunner(get()) }

    single<ProclamatoriQuery> { SqlDelightProclamatoriQuery(get()) }
    single<ProclamatoriAggregateStore> { SqlDelightProclamatoriStore(get()) }
    single<EligibilityStore> { SqlDelightEligibilityStore(get()) }

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

    // Programs (monthly)
    single<ProgramStore> { SqlDelightProgramStore(get()) }
    single { CreaProssimoProgrammaUseCase(get()) }
    single { CaricaProgrammiAttiviUseCase(get()) }
    single { EliminaProgrammaFuturoUseCase(get(), get(), get()) }
    single { GeneraSettimaneProgrammaUseCase(get(), get(), get(), get(), get()) }
    single { AggiornaProgrammaDaSchemiUseCase(get(), get(), get(), get(), get()) }

    // Local schema templates
    single<SchemaTemplateStore> { SqlDelightSchemaTemplateStore(get()) }
    single<SchemaUpdateAnomalyStore> { SqlDelightSchemaUpdateAnomalyStore(get()) }
    single<SchemaCatalogRemoteSource> {
        GitHubSchemaCatalogDataSource(
            schemasCatalogUrl = RemoteConfig.SCHEMAS_CATALOG_URL,
        )
    }
    single { AggiornaSchemiUseCase(get(), get(), get(), get(), get(), get(), get()) }

    // Weekly parts
    single<PartTypeStore> { SqlDelightPartTypeStore(get()) }
    single<WeekPlanStore> { SqlDelightWeekPlanStore(get()) }
    single<RemoteDataSource> {
        GitHubDataSource(
            partTypesUrl = RemoteConfig.PART_TYPES_URL,
            weeklySchemasUrl = RemoteConfig.WEEKLY_SCHEMAS_URL,
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
    single { SqlDelightAssignmentStore(get()) }
    single<AssignmentSettingsStore> { SqlDelightAssignmentSettingsStore(get()) }
    single<AssignmentRepository> { get<SqlDelightAssignmentStore>() }
    single<AssignmentRanking> { get<SqlDelightAssignmentStore>() }
    single<PersonAssignmentLifecycle> { get<SqlDelightAssignmentStore>() }
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

    // Output
    single { PdfAssignmentsRenderer() }
    single { PdfProgramRenderer() }
    single { GeneraPdfAssegnazioni(get(), get(), get()) }
    single { GeneraImmaginiAssegnazioni(get(), get(), get()) }
    single { StampaProgrammaUseCase(get(), get(), get(), get()) }

    // Updates
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

    // Planning dashboard
    single { CalcolaProgressoPianificazione() }
    single { GeneraAlertCoperturaSettimane() }
    single { GeneraAlertValidazioneAssegnazioni(get(), get(), get(), get(), get(), get()) }
    single { CaricaPanoramicaPianificazioneFutura(get(), get(), get(), get(), get()) }

    // Shared state
    single { SharedWeekState() }

    // ViewModels — factory: screen-local state, data always reloaded from DB
    factory {
        PlanningDashboardViewModel(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            sharedWeekState = get(),
            caricaPanoramica = get(),
        )
    }
    factory {
        ProgramLifecycleViewModel(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            caricaProgrammiAttivi = get(),
            creaProssimoProgramma = get(),
            eliminaProgrammaFuturo = get(),
            generaSettimaneProgramma = get(),
            schemaTemplateStore = get(),
            weekPlanStore = get(),
            caricaAssegnazioni = get(),
            cercaTipiParte = get(),
        )
    }
    factory {
        SchemaManagementViewModel(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            aggiornaSchemi = get(),
            aggiornaProgrammaDaSchemi = get(),
            schemaTemplateStore = get(),
            settings = get(),
        )
    }
    factory {
        AssignmentManagementViewModel(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            autoAssegnaProgramma = get(),
            caricaImpostazioniAssegnatore = get(),
            salvaImpostazioniAssegnatore = get(),
            svuotaAssegnazioni = get(),
            rimuoviAssegnazioniSettimana = get(),
            stampaProgramma = get(),
        )
    }
    factory {
        PersonPickerViewModel(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            assegnaPersona = get(),
            rimuoviAssegnazione = get(),
            suggerisciProclamatori = get(),
            caricaAssegnazioni = get(),
        )
    }
    factory {
        PartEditorViewModel(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            weekPlanStore = get(),
            cercaTipiParte = get(),
        )
    }
    factory {
        ProclamatoriListViewModel(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            cerca = get(),
            impostaStato = get(),
            elimina = get(),
            importaDaJson = get(),
            contaAssegnazioni = get(),
            schemaUpdateAnomalyStore = get(),
            partTypeStore = get(),
        )
    }
    factory {
        DiagnosticsViewModel(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            database = get(),
            verificaAggiornamenti = get(),
            aggiornaApplicazione = get(),
            updateStatusStore = get(),
            updateSettingsStore = get(),
        )
    }
    factory {
        ProclamatoreFormViewModel(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            carica = get(),
            caricaIdoneita = get(),
            crea = get(),
            aggiorna = get(),
            impostaIdoneitaConduzione = get(),
            partTypeStore = get(),
            verificaDuplicato = get(),
            caricaStoricoAssegnazioni = get(),
        )
    }
}

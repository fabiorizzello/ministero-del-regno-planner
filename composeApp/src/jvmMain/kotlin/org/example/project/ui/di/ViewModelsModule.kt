package org.example.project.ui.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

val viewModelsModule = module {
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

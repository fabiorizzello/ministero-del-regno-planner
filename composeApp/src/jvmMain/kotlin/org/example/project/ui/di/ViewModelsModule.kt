package org.example.project.ui.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.example.project.feature.diagnostics.application.ContaStoricoUseCase
import org.example.project.feature.diagnostics.application.EliminaStoricoUseCase
import org.example.project.ui.diagnostics.DiagnosticsViewModel
import org.example.project.ui.proclamatori.ProclamatoreFormViewModel
import org.example.project.ui.proclamatori.ProclamatoriListViewModel
import org.example.project.ui.updates.UpdateCenterViewModel
import org.example.project.ui.workspace.AssignmentManagementViewModel
import org.example.project.ui.workspace.PartEditorViewModel
import org.example.project.ui.workspace.PersonPickerViewModel
import org.example.project.ui.workspace.ProgramLifecycleViewModel
import org.example.project.ui.workspace.SchemaManagementViewModel
import org.koin.dsl.module

val viewModelsModule = module {
    // ViewModels — factory: screen-local state, data always reloaded from DB
    factory {
        ProgramLifecycleViewModel(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            caricaProgrammiAttivi = get(),
            creaProssimoProgramma = get(),
            eliminaProgramma = get(),
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
            caricaProgrammiAttivi = get(),
            schemaTemplateStore = get(),
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
            generaImmaginiAssegnazioni = get(),
            settings = get(),
            segnaComInviato = get(),
            annullaConsegna = get(),
            caricaStatoConsegne = get(),
            caricaRiepilogo = get(),
        )
    }
    factory {
        PersonPickerViewModel(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            assegnaPersona = get(),
            rimuoviAssegnazione = get(),
            suggerisciProclamatori = get(),
            verificaConsegna = get(),
            annullaConsegna = get(),
        )
    }
    factory {
        PartEditorViewModel(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            aggiornaPartiSettimana = get(),
            impostaStatoSettimana = get(),
            cercaTipiParte = get(),
        )
    }
    factory {
        ProclamatoriListViewModel(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            cerca = get(),
            elimina = get(),
            importaDaJson = get(),
            contaAssegnazioni = get(),
            archivaAnomalieSchema = get(),
            schemaUpdateAnomalyStore = get(),
            partTypeStore = get(),
        )
    }
    factory {
        DiagnosticsViewModel(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            contaStorico = get<ContaStoricoUseCase>(),
            eliminaStorico = get<EliminaStoricoUseCase>(),
        )
    }
    factory {
        UpdateCenterViewModel(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
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
        )
    }
}

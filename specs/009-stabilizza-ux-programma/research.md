# Research: Stabilizzazione UX Programma e Studenti

## Decision 1: Il cambio del filtro di riposo deve ricalcolare i suggerimenti dal boundary applicativo, non solo aggiornare lo stato UI

**Decision**: trattare il toggle di riposo nella modale di assegnazione come trigger di ricarica completa dei suggerimenti, facendo dipendere il refresh dal boundary `SuggerisciProclamatoriUseCase` e dalle impostazioni correnti salvate in `AssignmentSettingsStore`.

**Rationale**:
- `ProgramWorkspaceScreen` gia' invoca `assignmentVM.setStrictCooldown(it)` e poi `personPickerVM.reloadSuggestions()`, ma `PersonPickerViewModel` oggi non collega il refresh a ricerca e impostazioni in modo robusto.
- `SuggerisciProclamatoriUseCase` legge le impostazioni ad ogni invocazione, quindi e' il punto corretto per ottenere risultati coerenti col nuovo stato.
- La costituzione richiede no business logic in UI; la UI deve solo orchestrare un nuovo caricamento.

**Alternatives considered**:
- Filtrare lato Composable la lista gia' caricata: scartato perche' lascerebbe ranking e filtri stale.
- Mutare manualmente l'elenco nel ViewModel senza reinvocare il use case: scartato perche' duplica logica del ranking.

## Decision 2: La preservazione delle assegnazioni va basata su chiavi logiche di continuita' della parte, non su reset integrale della settimana

**Decision**: estendere il comportamento di refresh/aggiornamento della settimana affinche' preservi le assegnazioni ancora compatibili tramite chiavi logiche della parte (`partType`, occorrenza, slot), rimuovendo solo quelle che non hanno piu' un match reale nella nuova composizione.

**Rationale**:
- `GeneraSettimaneProgrammaUseCase` contiene gia' una strategia esplicita di `AssignmentRestoreKey`, quindi esiste un pattern interno da riusare anche nei flussi di modifica manuale.
- Il bug descritto indica che il path di modifica settimana non applica la stessa granularita' e resetta troppo.
- Riutilizzare lo stesso criterio riduce incoerenze tra rigenerazione programma e modifica manuale delle parti.

**Alternatives considered**:
- Azzerare sempre le assegnazioni e mostrare un warning: scartato per perdita di lavoro valida.
- Tentare un match solo per indice posizionale puro: scartato per fragilita' quando si inseriscono/rimuovono parti in mezzo.

## Decision 3: La fuzzy search deve essere locale, deterministica e ordinata per distanza, con fallback alfabetico stabile

**Decision**: introdurre una ricerca fuzzy condivisa tra modale assegnazione e sezione studenti che tolleri refusi e input incompleto, ordini i risultati per distanza/similarita' e usi il fallback alfabetico solo a parita' di score.

**Rationale**:
- La query SQL attuale `searchProclaimers` usa solo `LIKE` con ordinamento alfabetico, quindi non soddisfa la richiesta.
- Il dataset persone e' adatto a ranking locale in memoria senza costo infrastrutturale significativo.
- Le linee guida `ui-ux-pro-max` indicano search-first, ordinamento utile e stato vuoto esplicito come pattern migliore per directory/admin productivity.

**Alternatives considered**:
- Restare su `contains` SQL e aggiungere solo evidenziazione: scartato perche' non risolve refusi.
- Demandare il fuzzy ranking al database con logica SQL complessa: scartato per aumento inutile di complessita' e scarsa leggibilita'.

## Decision 4: Gli affinamenti UX devono preservare il linguaggio visuale corrente e migliorare stabilita' visiva, non introdurre un nuovo stile

**Decision**: applicare i suggerimenti `ui-ux-pro-max` solo come raffinamenti al design system esistente: layout search-first, micro-feedback sobri, toolbar a altezza costante, scroll reset esplicito, etichette italiane comprensibili e empty state non muti.

**Rationale**:
- Il progetto ha gia' un linguaggio visivo definito (`workspaceSketch`, Material 3, light theme); cambiare palette o font sarebbe fuori scope.
- `ui-ux-pro-max` consiglia pattern directory/marketplace con search bar come CTA e micro-interactions sobrie, che qui si traducono in accesso rapido alla ricerca e feedback coerenti.
- Le card studenti mostrano gia' instabilita' quando aumentano le capability; il fix giusto e' la stabilita' del layout, non una riprogettazione estetica completa.

**Alternatives considered**:
- Reimpaginare completamente schermata studenti e modale: scartato per rischio regressione e scope eccessivo.
- Lasciare le UI correnti e intervenire solo sui bug logici: scartato perche' la spec include esplicitamente i difetti visuali/testuali.

## Decision 5: Paginazione, scroll e stati di lista vanno modellati come contratto UI esplicito

**Decision**: formalizzare il comportamento di paginazione e lista in contratti UI dedicati, includendo reset scroll in alto al cambio pagina, ordinamento stabile, toolbar coerenti e stato vuoto italiano per ricerche senza risultati.

**Rationale**:
- `ProclamatoriListViewModel` cambia pagina ma non governa lo scroll visivo; serve un contratto chiaro tra ViewModel e Composable.
- La sezione studenti ha doppia modalita' tabella/card, quindi serve chiarezza su cosa deve rimanere coerente in entrambe.
- I contratti UI facilitano la successiva stesura di task e test di regressione.

**Alternatives considered**:
- Demandare questi dettagli solo ai task implementativi: scartato perche' la feature ha molte piccole interazioni facili da interpretare in modo incoerente.

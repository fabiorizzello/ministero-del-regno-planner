# Backlog Tecnico

Miglioramenti tracciati emersi da code review e sviluppo. Non bloccanti per il rilascio.

## Refactoring

### Estrarre sundayDate in utilita' condivisa
- **Origine:** Code review stabilita' (DRY-2)
- **Problema:** `val sundayDate get() = currentMonday.plusDays(6)` e' duplicato in `WeeklyPartsUiState` e `AssignmentsUiState`.
- **Fix:** Estrarre in una funzione utility accanto a `computeWeekIndicator()`, oppure esporre da `SharedWeekState`.
- **File coinvolti:** `WeeklyPartsViewModel.kt`, `AssignmentsViewModel.kt`, `WeekNavigator.kt`

### Usare AssignmentId tipizzato in remove()
- **Origine:** Code review M3+M4 (M-11)
- **Problema:** `AssignmentStore.remove()` e `RimuoviAssegnazioneUseCase.invoke()` accettano `String` invece di `AssignmentId`. Incoerente con il resto del dominio che usa value class tipizzate.
- **Fix:** Cambiare firma a `remove(assignmentId: AssignmentId)` e propagare il tipo.
- **File coinvolti:** `AssignmentStore.kt`, `SqlDelightAssignmentStore.kt`, `RimuoviAssegnazioneUseCase.kt`, `AssignmentsViewModel.kt`

### Eliminare duplicazione validazione form Proclamatori
- **Origine:** Code review stabilita' (DRY-1)
- **Problema:** La logica `requiredFieldsValid`, `hasFormChanges` e `canSubmitForm` e' calcolata identicamente sia in `ProclamatoriViewModel.submitForm()` che in `ProclamatoriScreen`. Rischio divergenza se una viene aggiornata senza l'altra.
- **Fix:** Esporre `canSubmitForm` come proprieta' calcolata su `ProclamatoriUiState` (richiede `route` nello state), oppure far si che il ViewModel non riduplichi la validazione ma si fidi della guardia UI.
- **File coinvolti:** `ProclamatoriViewModel.kt`, `ProclamatoriScreen.kt`

### Ridurre responsabilita' ProclamatoriViewModel
- **Origine:** Code review stabilita' (SOLID-1)
- **Problema:** Il ViewModel gestisce: lista, paginazione, ordinamento, selezione, form (crea+modifica), verifica duplicati, eliminazione singola, eliminazione batch, attivazione/disattivazione batch, import JSON. Ha 10 dipendenze e ~520 righe.
- **Fix:** Considerare split in `ProclamatoriListViewModel` e `ProclamatoreFormViewModel`.
- **File coinvolti:** `ProclamatoriViewModel.kt`, `ProclamatoriScreen.kt`

### Disaccoppiare ProclamatoriViewModel da AssignmentStore
- **Origine:** Code review stabilita' (SOLID-2)
- **Problema:** `ProclamatoriViewModel` dipende direttamente da `AssignmentStore` (feature assignments) per contare le assegnazioni prima della cancellazione. Viola la direzione delle dipendenze tra feature.
- **Fix:** Creare un use case `ContaAssegnazioniPerProclamatoreUseCase` nella feature assignments, oppure integrare il conteggio nella risposta di `EliminaProclamatoreUseCase`.
- **File coinvolti:** `ProclamatoriViewModel.kt`, `AppModules.kt`

## Bug minori

### Aggiungere protezione doppio-click su removeAssignment
- **Origine:** Code review stabilita' (B-2)
- **Problema:** `removeAssignment` non ha guardia contro doppio-click, a differenza di `confirmAssignment` che usa `isAssigning`. Un doppio-click rapido potrebbe causare due tentativi di rimozione concorrenti.
- **Fix:** Aggiungere flag `isRemoving` o riusare un pattern di guardia simile a `isAssigning`.
- **File coinvolti:** `AssignmentsViewModel.kt`

## UI

### Sostituire weekPlan!! con safe unwrap nelle Screen
- **Origine:** Code review stabilita' (O-1)
- **Problema:** `state.weekPlan!!.parts` in `AssignmentsScreen` e `WeeklyPartsScreen` usa force-unwrap. E' sicuro grazie al branch null-check precedente, ma `!!` e' sconsigliato.
- **Fix:** Usare `val plan = state.weekPlan ?: return` o `checkNotNull()`.
- **File coinvolti:** `AssignmentsScreen.kt`, `WeeklyPartsScreen.kt`

### Estrarre colori semantici per chip regola sesso
- **Origine:** Code review M3+M4 (M-9)
- **Problema:** `Color(0xFF2196F3)` e `Color(0xFF9E9E9E)` sono hardcoded in `SexRuleChip` e anche in `WeekNavigator.kt`. Non seguono il pattern del tema.
- **Fix:** Estrarre in una palette condivisa o token colore semantici.
- **File coinvolti:** `AssignmentsComponents.kt`, `WeekNavigator.kt`

### Estrarre costanti larghezza colonne nel dialog suggerimenti
- **Origine:** Code review M3+M4 (M-8/S-2)
- **Problema:** Le larghezze colonne (120.dp, 110.dp) sono duplicate tra `SuggestionHeaderRow` e `SuggestionRow`.
- **Fix:** Estrarre come costanti private nel file.
- **File coinvolti:** `AssignmentsComponents.kt`

### Altezza adattiva per lista suggerimenti nel dialog
- **Origine:** Code review M3+M4 (M-10)
- **Problema:** `Modifier.height(300.dp)` e' fisso. Su finestre piccole potrebbe essere eccessivo, su grandi insufficiente.
- **Fix:** Usare `Modifier.heightIn(max = 400.dp)` o una frazione della dimensione finestra.
- **File coinvolti:** `AssignmentsComponents.kt`

### Aggiungere query dedicata allActiveProclaimers
- **Origine:** Code review M3+M4 (M-8)
- **Problema:** `searchProclaimers(1L, "", "", "")` e' usato come workaround per ottenere tutti i proclamatori attivi. I parametri "magic" riducono la leggibilita'.
- **Fix:** Aggiungere query SQL dedicata `allActiveProclaimers` in `MinisteroDatabase.sq`.
- **File coinvolti:** `MinisteroDatabase.sq`, `SqlDelightAssignmentStore.kt`

## Testing

### Test unitari per use case assegnazioni
- **Origine:** Code review M3+M4 (I-7)
- **Problema:** Nessun test per la logica di dominio delle assegnazioni. I use case `AssegnaPersonaUseCase` e `SuggerisciProclamatoriUseCase` contengono validazioni importanti (range slot, duplicati settimana, filtri sesso, ranking slot-aware) che non sono testate.
- **Fix:** Aggiungere test unitari con mock di `AssignmentStore` e `WeekPlanStore`. Test di integrazione con SQLite in-memory per `SqlDelightAssignmentStore`.
- **Priorita':** Alta — da pianificare come sprint dedicato.

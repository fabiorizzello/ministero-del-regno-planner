# Backlog Tecnico

Miglioramenti tracciati emersi da code review e sviluppo. Non bloccanti per il rilascio.

## Refactoring

### Estrarre weekIndicator e sundayDate in SharedWeekState
- **Origine:** Code review M3+M4 (I-4)
- **Problema:** `weekIndicator` e `sundayDate` sono calcolati in modo identico in `WeeklyPartsUiState` e `AssignmentsUiState`. Violazione DRY. Inoltre `weekIndicator` chiama `LocalDate.now()` a ogni accesso, rischiando inconsistenze al cambio giorno.
- **Fix:** Spostare il calcolo in `SharedWeekState` come proprieta' derivata, oppure estrarre funzioni di utilita' condivise.
- **File coinvolti:** `WeeklyPartsViewModel.kt`, `AssignmentsViewModel.kt`, `SharedWeekState.kt`

### Ottimizzare query in SuggerisciProclamatoriUseCase
- **Origine:** Code review M3+M4 (I-5)
- **Problema:** `assignmentStore.listByWeek(plan.id)` carica TUTTE le assegnazioni della settimana per poi filtrare solo quelle della parte corrente. Esiste gia' la query SQL `assignmentsForWeeklyPart` che interroga per singola parte.
- **Fix:** Aggiungere metodo `listByPart(weeklyPartId)` a `AssignmentStore` e usarlo nel use case al posto di `listByWeek` + filtro client-side.
- **File coinvolti:** `AssignmentStore.kt`, `SqlDelightAssignmentStore.kt`, `SuggerisciProclamatoriUseCase.kt`

### Usare AssignmentId tipizzato in remove()
- **Origine:** Code review M3+M4 (M-11)
- **Problema:** `AssignmentStore.remove()` e `RimuoviAssegnazioneUseCase.invoke()` accettano `String` invece di `AssignmentId`. Incoerente con il resto del dominio che usa value class tipizzate.
- **Fix:** Cambiare firma a `remove(assignmentId: AssignmentId)` e propagare il tipo.
- **File coinvolti:** `AssignmentStore.kt`, `SqlDelightAssignmentStore.kt`, `RimuoviAssegnazioneUseCase.kt`, `AssignmentsViewModel.kt`

## UI

### Estrarre colori semantici per chip regola sesso
- **Origine:** Code review M3+M4 (M-9)
- **Problema:** `Color(0xFF2196F3)` e `Color(0xFF9E9E9E)` sono hardcoded in `SexRuleChip` e anche in `WeekNavigator.kt`. Non seguono il pattern del tema.
- **Fix:** Estrarre in una palette condivisa o token colore semantici.
- **File coinvolti:** `AssignmentsComponents.kt`, `WeekNavigator.kt`

### Estrarre costanti larghezza colonne nel dialog suggerimenti
- **Origine:** Code review M3+M4 (M-8/S-2)
- **Problema:** Le larghezze colonne (120.dp, 80.dp) sono duplicate tra `SuggestionHeaderRow` e `SuggestionRow`.
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
- **Problema:** Nessun test per la logica di dominio delle assegnazioni. I use case `AssegnaPersonaUseCase` e `SuggerisciProclamatoriUseCase` contengono validazioni importanti (parte fissa, range slot, duplicati, filtri sesso, ranking slot-aware) che non sono testate.
- **Fix:** Aggiungere test unitari con mock di `AssignmentStore` e `WeekPlanStore`. Test di integrazione con SQLite in-memory per `SqlDelightAssignmentStore`.
- **Priorita':** Alta — da pianificare come sprint dedicato.

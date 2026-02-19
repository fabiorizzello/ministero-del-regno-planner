# Backlog Tecnico

## BUG — Integrità dati e correttezza

### BUG-1: EliminaProclamatoreUseCase senza transazione
- **File:** `feature/people/application/EliminaProclamatoreUseCase.kt:13-21`
- `removeAllForPerson(id)` e `store.remove(id)` eseguiti come operazioni separate. Se `remove` fallisce dopo `removeAllForPerson`, le assegnazioni vengono cancellate ma il proclamatore resta — stato parziale corrotto.
- **Fix:** wrappare in `database.transaction { }` o creare un metodo store dedicato.

### BUG-2: Numbering display inconsistente tra schermate
- **File:** `ui/assignments/AssignmentsScreen.kt:138` → `sortOrder + 1`
- **File:** `ui/weeklyparts/WeeklyPartsScreen.kt:242,256` → `sortOrder + 3`
- Lo stesso `WeeklyPart.sortOrder` viene visualizzato con offset diversi. Il `+3` è un magic number non documentato (presumibilmente le 3 parti fisse di apertura). Estrarre in costante nominata e unificare.

### BUG-3: GitHubDataSource senza timeout HTTP
- **File:** `feature/weeklyparts/infrastructure/GitHubDataSource.kt:26`
- `HttpClient.newHttpClient()` usa default senza `connectTimeout`/`readTimeout`. Un endpoint lento blocca il thread IO indefinitamente.
- **Fix:** aggiungere timeout (es. 15s connect, 30s read).

### BUG-4: Race condition in WeeklyPartsViewModel.loadWeek()
- **File:** `ui/weeklyparts/WeeklyPartsViewModel.kt:194-208`
- `loadWeek()` lancia una nuova coroutine senza cancellare la precedente. Se l'utente naviga rapidamente tra settimane, la risposta della query precedente può sovrascrivere quella corrente. `AssignmentsViewModel` gestisce correttamente con `loadJob?.cancel()`.
- **Fix:** aggiungere `private var loadJob: Job? = null` e cancellare prima di ogni nuova invocazione.

### BUG-5: movePart() fire-and-forget senza gestione errori
- **File:** `ui/weeklyparts/WeeklyPartsViewModel.kt:118-129`
- Update ottimistico della UI poi `riordinaParti(reordered)` senza error handling. Se il DB fallisce, la UI mostra l'ordine nuovo ma il DB mantiene il vecchio. Al prossimo reload la lista torna indietro senza feedback.
- **Fix:** aggiungere `.fold()` o try/catch che revert l'update ottimistico e mostra errore. Nota: `RiordinaPartiUseCase` non ritorna `Either` (vedi BUG-10).

### BUG-6: importSchema() ignora silenziosamente codici parte sconosciuti
- **File:** `feature/weeklyparts/application/AggiornaDatiRemotiUseCase.kt:83-85`
- `mapNotNull { partTypeStore.findByCode(code)?.id }` scarta codici non trovati senza warning. Il piano settimanale viene creato con meno parti del previsto.
- **Fix:** raccogliere codici non risolti e includerli nel messaggio di successo o avvisare l'utente.

### BUG-7: LocalDate.parse() può lanciare DateTimeParseException non catturata
- **File:** `feature/weeklyparts/application/AggiornaDatiRemotiUseCase.kt:40,63,74`
- `LocalDate.parse(schema.weekStartDate)` in blocchi `either { }` — ma `either` cattura solo `raise()`, non eccezioni arbitrarie. Dati remoti malformati crashano l'app.
- **Fix:** wrappare in try/catch e `raise(DomainError.Validation(...))`.

### BUG-8: Sesso.valueOf() / SexRule.valueOf() nei mapper possono crashare
- **File:** `feature/people/infrastructure/ProclamatoreRowMapper.kt:18`
- **File:** `feature/assignments/infrastructure/SqlDelightAssignmentStore.kt:92`
- **File:** `feature/weeklyparts/infrastructure/PartTypeRowMapper.kt:21`
- Usano `valueOf()` che lancia `IllegalArgumentException` su valori DB inattesi. Non ci sono CHECK constraint SQL sulle colonne `TEXT NOT NULL`.
- **Fix:** `runCatching { }.getOrDefault()` oppure aggiungere `CHECK(sex IN ('M','F'))` allo schema SQL.

### BUG-9: selectJsonFileForImport() blocca il thread UI
- **File:** `ui/proclamatori/ProclamatoriUiSupport.kt:158-170`
- AWT `FileDialog` con `isVisible = true` è bloccante. Chiamata direttamente da un lambda composable. Funziona perché AWT gestisce il proprio event pump, ma il pattern è fragile.
- **Fix:** considerare `JFileChooser` su `Dispatchers.IO` o il composable `FileDialog` di Compose Desktop.

### BUG-10: RiordinaPartiUseCase non ritorna Either — contratto inconsistente
- **File:** `feature/weeklyparts/application/RiordinaPartiUseCase.kt:5-11`
- Unico use case di write che è un `suspend fun` senza `Either<DomainError, Unit>`. Le eccezioni propagano non gestite nel `CoroutineScope`.
- **Fix:** allineare al contratto degli altri use case con `Either`.

### BUG-11: Search proclamatori senza debounce
- **File:** `ui/proclamatori/ProclamatoriListViewModel.kt:57-60`
- `setSearchTerm()` chiama `refreshList()` immediatamente ad ogni carattere. Query DB completa ad ogni keystroke, a differenza del duplicate check che usa 250ms debounce.
- **Fix:** aggiungere `searchJob` con `delay(250)` prima della query.

### BUG-12: loadPartTypes() ingoia silenziosamente tutte le eccezioni
- **File:** `ui/weeklyparts/WeeklyPartsViewModel.kt:211-219`
- `catch (_: Exception)` ignora l'errore — il bottone "Aggiungi parte" sparisce senza feedback. DB corrotto/locked è indistinguibile da "nessun tipo parte".
- **Fix:** loggare l'eccezione e/o mostrare un flag `partTypesLoadFailed`.

### BUG-13: requestDeleteCandidate() report 0 assegnazioni in caso di errore
- **File:** `ui/proclamatori/ProclamatoriListViewModel.kt:118-127`
- `catch (_: Exception) { 0 }` mostra "0 assegnazioni" anche su errore DB. L'utente potrebbe cancellare pensando non ci siano assegnazioni.
- **Fix:** mostrare "conteggio non disponibile" anziché 0.

### BUG-14: CreaSettimanaUseCase senza transazione
- **File:** `feature/weeklyparts/application/CreaSettimanaUseCase.kt:27-28`
- `save(weekPlan)` e `addPart(...)` sono due operazioni separate. Se `addPart` fallisce, resta un piano senza parti.
- **Fix:** wrappare in `transaction { }`.

### BUG-15: AggiungiParteUseCase tre operazioni DB senza transazione
- **File:** `feature/weeklyparts/application/AggiungiParteUseCase.kt:17-24`
- `findByDate` → `addPart` → `findByDate` sono tre chiamate separate. Il secondo `findByDate` ricarica l'intero piano inutilmente.
- **Fix:** wrappare in transazione e ottimizzare.

### BUG-16: AssegnaPersonaUseCase non verifica slot già occupato
- **File:** `feature/assignments/application/AssegnaPersonaUseCase.kt:34-36`
- Verifica `isPersonAssignedInWeek` ma non se lo slot `(weeklyPartId, slot)` è già occupato. Il SQL `ON CONFLICT DO UPDATE` sovrascrive silenziosamente l'assegnazione esistente senza conferma.
- **Fix:** controllare slot occupato e avvisare o richiedere conferma.

### BUG-17: DatabaseProvider manca @Volatile — double-checked locking non sicuro
- **File:** `core/persistence/DatabaseProvider.kt:8-14`
- `private var instance` senza `@Volatile` con double-checked locking. Il JVM memory model potrebbe restituire un valore stale nel check esterno.
- **Fix:** aggiungere `@Volatile`.

### BUG-18: AppBootstrap.initialized non thread-safe
- **File:** `core/bootstrap/AppBootstrap.kt:9-10`
- `private var initialized = false` senza sincronizzazione. Usare `@Volatile` o `AtomicBoolean`.

### BUG-19: upsertAssignment aggiorna id su conflict — mutazione silente della PK
- **File:** `MinisteroDatabase.sq:193-198`
- `ON CONFLICT(weekly_part_id, slot) DO UPDATE SET id = excluded.id` sovrascrive la primary key esistente. Qualsiasi `AssignmentId` in memoria diventa stale. `removeAssignment(oldId)` non troverà la riga.
- **Fix:** rimuovere `id = excluded.id` dal `SET`.

### BUG-20: loadSuggestions() senza job tracking — race condition possibile
- **File:** `ui/assignments/AssignmentsViewModel.kt:194-217`
- `loadSuggestions()` lancia `scope.launch` senza cancellare il precedente. Se l'utente apre rapidamente il picker per parti diverse, i suggerimenti della prima query possono sovrascrivere quelli della seconda.
- **Fix:** aggiungere `suggestionsJob?.cancel()` come in `loadWeekData()`.

### BUG-21: Batch operations senza guard anti double-click
- **File:** `ui/proclamatori/ProclamatoriListViewModel.kt:172-202`
- `confirmBatchDelete`, `activateSelected`, `deactivateSelected` lanciano `scope.launch` senza guard. Double-click prima che `isLoading` flippi = operazioni duplicate.
- **Fix:** aggiungere flag `isBatchInProgress` o job cancellation.

### BUG-22: collect in init di WeeklyPartsVM — eccezione in loadWeek() termina la subscription
- **File:** `ui/weeklyparts/WeeklyPartsViewModel.kt:62-69`
- `loadWeek()` chiamata dentro `collect {}`. Se lancia sincrona, cancella il flow collector — navigazione settimane rotta per il resto della sessione senza errore visibile.
- **Fix:** wrappare `loadWeek()` in try/catch dentro il collect, o usare `catch { }` sul flow.

### BUG-23: GitHubDataSource usa `!!` su tutti i campi JSON — NPE su dati remoti parziali
- **File:** `feature/weeklyparts/infrastructure/GitHubDataSource.kt:36-43,52-53`
- Ogni accesso campo usa `!!`. Se un campo manca, `NullPointerException` con messaggio `"null"` — poco diagnostico per l'utente.
- **Fix:** usare null-safe access con messaggi di errore descrittivi.

### BUG-24: Import JSON senza limite dimensione file
- **File:** `ui/proclamatori/ProclamatoriListViewModel.kt:237-238`
- `File.readText()` carica tutto in memoria senza controllo dimensione. Un file enorme causa `OutOfMemoryError` (non catturato da `runCatching` — è un `Error`, non `Exception`).
- **Fix:** controllare `selectedFile.length()` prima e rifiutare file > 10MB.

### BUG-25: ImportaProclamatoriDaJsonUseCase scarta la causa originale dell'eccezione
- **File:** `feature/people/application/ImportaProclamatoriDaJsonUseCase.kt:40-44`
- `catch (_: Exception)` perde il messaggio di errore originale. L'utente vede solo "Import non completato" senza dettagli diagnostici.
- **Fix:** includere `e.message` nel `DomainError.Validation`.

### BUG-26: Nessun uncaught exception handler — crash mostra dialog AWT grezzo
- **File:** `main.kt:19`
- `main()` non ha try/catch né `Thread.setDefaultUncaughtExceptionHandler`. Errori fatali (DB locked, paths non scrivibili) mostrano stack trace grezzi.
- **Fix:** aggiungere handler globale con log + dialog user-friendly.

### BUG-27: Bootstrap failure lascia AppRuntime non inizializzato — errore fuorviante downstream
- **File:** `core/bootstrap/AppBootstrap.kt:14-22`
- Se `PathsResolver.resolve()` fallisce (disco pieno, permessi), `AppRuntime.paths()` lancia `IllegalStateException("AppRuntime non inizializzato")` dentro Koin — errore non correlato alla causa root.
- **Fix:** propagare l'eccezione da `initialize()` per gestirla in `main()`.

### BUG-28: Inner Navigator con Screen a Content() vuoto — pattern fragile
- **File:** `ui/proclamatori/ProclamatoriScreen.kt:37-52,84`
- `ProclamatoriFlowScreen` implementations hanno `Content() {}` vuoto — usate solo come chiavi di navigazione. `DisposableEffect` futuri verrebbero firing/disposing ad ogni switch tab.
- **Fix:** sostituire con `var route by remember { mutableStateOf<ProclamatoriRoute>(...) }`.

### BUG-29: Outer Navigator senza guard su back-navigation
- **File:** `ui/AppScreen.kt:58-92`
- `replaceAll` previene crescita back stack, ma Alt+Left / Back Mouse Button di Voyager può causare `pop()` che desincronizza il `NavigationRailItem` evidenziato dal contenuto visibile.
- **Fix:** disabilitare back gesture o aggiungere guard `canPop`.

### BUG-30: AppRuntime.initialize() senza guard contro re-inizializzazione
- **File:** `core/config/AppRuntime.kt:7-9`
- `initialize()` è public e sovrascrive `runtimePaths` silenziosamente se chiamata due volte. `AppBootstrap` ha un guard `if (initialized) return`, ma `AppRuntime` è direttamente accessibile.
- **Fix:** aggiungere `check(runtimePaths == null) { "AppRuntime già inizializzato" }`.

### BUG-31: WeekPlan.weekStartDate non validato come lunedì
- **File:** `feature/weeklyparts/domain/WeekPlan.kt:8-12`
- `weekStartDate` accetta qualsiasi `LocalDate`. Un JSON remoto con `"2024-03-13"` (mercoledì) crea un `WeekPlan` non trovabile dalla navigazione che usa solo lunedì.
- **Fix:** `init { require(weekStartDate.dayOfWeek == DayOfWeek.MONDAY) }`.

### BUG-32: PartType.code e label accettano stringhe vuote
- **File:** `feature/weeklyparts/domain/PartType.kt:6-14`
- Nessun check su `code.isNotBlank()` e `label.isNotBlank()`. Un `code = ""` causerebbe match falsi in `findByCode()`. Un `label = ""` è invisibile nella UI.
- **Fix:** aggiungere `init { require(code.isNotBlank()); require(label.isNotBlank()) }`.

### BUG-33: AssignmentWithPerson.slot senza invariante e fullName con spazi
- **File:** `feature/assignments/domain/AssignmentWithPerson.kt:11,17`
- `slot: Int` senza check `>= 1` (diverge da `Assignment` dopo fix SOLID-5). `fullName = "$firstName $lastName"` produce spazio leading/trailing se uno è blank.
- **Fix:** `init { require(slot >= 1) }` e `fullName = "${firstName.trim()} ${lastName.trim()}"`.

### BUG-34: SharedWeekState navigazione senza limiti — anni assurdi raggiungibili
- **File:** `core/application/SharedWeekState.kt:17-23`
- `navigateToPreviousWeek()`/`navigateToNextWeek()` non hanno bound. Click ripetuti portano in anni senza dati pratici senza feedback.
- **Fix:** aggiungere limiti (es. 2020–2099) e ignorare navigazione fuori range.

---

## SQL — Schema e migrazioni

### SQL-1: Missing index su weekly_part(week_plan_id)
- **File:** `MinisteroDatabase.sq:27-34`
- FK senza indice. SQLite non crea indici automatici su FK. Ogni caricamento settimana fa full scan di `weekly_part`.
- **Fix:** `CREATE INDEX IF NOT EXISTS weekly_part_week_plan_id_idx ON weekly_part(week_plan_id);`

### SQL-2: Missing index su assignment(person_id)
- **File:** `MinisteroDatabase.sq:36-43`
- Usato in `countAssignmentsForPerson`, `deleteAssignmentsForPerson`, e tutte le ranking query. Senza indice: full scan.
- **Fix:** `CREATE INDEX IF NOT EXISTS assignment_person_id_idx ON assignment(person_id);`

### SQL-3: Missing CHECK constraint su colonne con bounds semantici
- **File:** `MinisteroDatabase.sq:12-20`
- `part_type.people_count` accetta 0 o negativi (loop `1..0` silenziosamente vuoto). `assignment.slot` accetta 0 o negativi.
- **Fix:** `people_count INTEGER NOT NULL CHECK(people_count >= 1)`, `slot INTEGER NOT NULL CHECK(slot >= 1)`.

### SQL-4: Colonne boolean senza CHECK(IN (0,1))
- **File:** `MinisteroDatabase.sq:1-7, 12-20`
- `person.active` e `part_type.fixed` accettano qualsiasi intero. Un valore `2` è silenziosamente `true` in Kotlin.
- **Fix:** `CHECK(active IN (0, 1))`, `CHECK(fixed IN (0, 1))`.

### SQL-5: ON DELETE mancante per weekly_part.part_type_id FK
- **File:** `MinisteroDatabase.sq:33`
- Default SQLite è `RESTRICT`. Tentare di cancellare un `part_type` referenziato fallisce con errore constraint non tipizzato. Comportamento previsto ma non esplicito.
- **Fix:** aggiungere `ON DELETE RESTRICT` esplicitamente e documentare.

### SQL-6: Nessuna strategia di migrazione DB
- **File:** `core/persistence/DatabaseProvider.kt:18-20`
- `JdbcSqliteDriver(schema = MinisteroDatabase.Schema)` auto-crea tabelle su DB nuovo. Per DB esistenti, cambiamenti schema non vengono applicati — nessun file di migrazione `.sqm` nel progetto.
- **Fix:** aggiungere directory `migrations/` con file `.sqm` per ogni cambio schema futuro. Critico per field additions.

### SQL-7: lastPartTypeAssignmentPerPerson esclude persone assegnate ad altre parti
- **File:** `MinisteroDatabase.sq:226-237,250-261`
- La condizione `pt.id = ? OR a.id IS NULL` esclude persone che hanno assegnazioni solo ad altri tipi parte (hanno `a.id NOT NULL` e `pt.id != ?`). Queste spariscono dal ranking invece di apparire come "mai assegnate a questo tipo". Il bug è mascherato perché `suggestedProclamatori()` tratta le chiavi mancanti come `null`.
- **Fix:** ristrutturare con `pt.id IS NULL OR pt.id = ?` o subquery correlata.

---

## PERF — Performance

### PERF-1: suggestedProclamatori() — 3 query SQL sequenziali
- **File:** `feature/assignments/infrastructure/SqlDelightAssignmentStore.kt:54-111`
- Tre query separate + join in memoria. Considerare una singola query con LEFT JOIN o almeno wrappare in `transaction { }` per letture consistenti.

### PERF-2: SuggerisciProclamatoriUseCase query ridondante
- **File:** `feature/assignments/application/SuggerisciProclamatoriUseCase.kt:29-31`
- `listByWeek(plan.id)` ricalcola dati che il ViewModel ha già caricato in `loadWeekData()`.
- **Fix:** accettare gli ID già assegnati come parametro.

### PERF-3: indexOf O(n) in items() — O(n²) totale
- **File:** `ui/weeklyparts/WeeklyPartsScreen.kt:231` — `parts.indexOf(part)`
- **File:** `ui/proclamatori/ProclamatoriComponents.kt:422` — `pageItems.indexOf(item)`
- **Fix:** usare `itemsIndexed()` invece di `items()` + `indexOf()`.

### PERF-4: persistInternal() esegue UPDATE + INSERT per ogni persist
- **File:** `feature/people/infrastructure/SqlDelightProclamatoriStore.kt:30-48`
- Sempre UPDATE poi INSERT IF NOT EXISTS. Per record esistenti l'INSERT è no-op; per nuovi l'UPDATE non tocca righe. Due statement SQL per operazione.
- **Fix:** usare `INSERT ... ON CONFLICT DO UPDATE` singolo.

### PERF-5: Filter e sort senza remember in PersonPickerDialog
- **File:** `ui/assignments/AssignmentsComponents.kt:235-250`
- `suggestions.filter { }.sortedWith { }` ricalcolato ad ogni ricomposizione, anche per state changes non correlati (es. `isAssigning` toggle). Con 200+ proclamatori è O(n log n) inutile.
- **Fix:** `remember(suggestions, searchTerm, sortGlobal) { filteredAndSorted }`.

### PERF-6: assignments.filter { } senza groupBy in AssignmentsScreen
- **File:** `ui/assignments/AssignmentsScreen.kt:134`
- `state.assignments.filter { it.weeklyPartId == part.id }` dentro `items()` — O(parts × assignments) ad ogni ricomposizione.
- **Fix:** pre-raggruppare con `remember(state.assignments) { assignments.groupBy { it.weeklyPartId } }`.

---

## DRY — Codice duplicato

### DRY-1: Parsing PartType JSON duplicato
- **File 1:** `feature/weeklyparts/infrastructure/GitHubDataSource.kt:32-43`
- **File 2:** `core/cli/SeedDatabase.kt:30-41`
- Blocco identico di mapping `JsonObject → PartType`. Estrarre in una funzione condivisa (es. `PartType.fromJson()`).

### DRY-2: currentMonday() triplicata
- **File 1:** `core/application/SharedWeekState.kt:13`
- **File 2:** `ui/weeklyparts/WeeklyPartsViewModel.kt:34` (default UiState)
- **File 3:** `ui/assignments/AssignmentsViewModel.kt:34` (default UiState)
- I default nei data class UiState sono ridondanti perché i VM sovrascrivono immediatamente dal SharedWeekState. Estrarre `currentMonday()` in un helper e/o rimuovere default ridondanti.

### DRY-3: successNotice/errorNotice confinati a proclamatori
- **File:** `ui/proclamatori/ProclamatoriUiSupport.kt:42-55` (internal)
- Gli altri package (assignments, weeklyparts) costruiscono `FeedbackBannerModel` inline. Promuovere gli helper in `ui/components/` come funzioni pubbliche.

### DRY-4: Dialog di eliminazione quasi identici
- **File:** `ui/proclamatori/ProclamatoriComponents.kt:89-134` (`ProclamatoreDeleteDialog`)
- **File:** `ui/proclamatori/ProclamatoriComponents.kt:137-167` (`ProclamatoriDeleteDialog`)
- Struttura identica (AlertDialog + stessi bottoni + stessi stili). Unificare in un singolo composable parametrizzato `ConfirmDeleteDialog`.

### DRY-5: Mapper Proclamatore parzialmente duplicato in AssignmentStore
- **File:** `feature/assignments/infrastructure/SqlDelightAssignmentStore.kt:87-94`
- Lambda inline che mappa le stesse colonne di `ProclamatoreRowMapper` ma con `attivo = true` hardcoded. Valutare riuso del mapper condiviso con parametro.

### DRY-7: SexRuleChip privata in AssignmentsComponents, reinventata inline in WeeklyPartsScreen
- **File 1:** `ui/assignments/AssignmentsComponents.kt:124-140` (composable con mapping corretto)
- **File 2:** `ui/weeklyparts/WeeklyPartsScreen.kt:350,408` (usa `sexRule.name` grezzo)
- Stessa informazione visualizzata in modo diverso. Promuovere `SexRuleChip` in `ui/components/` e riusarla.

### DRY-6: NavigationRail onClick duplicato con LocalSectionNavigator
- **File:** `ui/AppScreen.kt:64-67` (definizione provider)
- **File:** `ui/AppScreen.kt:90-93` (onClick inline)
- La stessa logica `if (currentSection != section) navigator.replaceAll(...)` appare due volte. Il `NavigationRailItem` dovrebbe usare `LocalSectionNavigator.current`.

---

## SOLID — Principi architetturali

### SOLID-1: AssignmentStore viola Interface Segregation
- **File:** `feature/assignments/application/AssignmentStore.kt:12-24`
- L'interfaccia mescola: CRUD assegnazioni, ranking/suggerimenti, e operazioni persona-lifecycle (`countAssignmentsForPerson`, `removeAllForPerson`). Separare in interfacce dedicate (es. `AssignmentReader`, `AssignmentWriter`, `AssignmentRanking`).

### SOLID-2: DomainError manca variante Network/IO
- **File:** `core/domain/DomainError.kt:3-6`
- `GitHubDataSource` lancia `RuntimeException` non tipizzato per errori HTTP. Aggiungere `DomainError.Network(message, cause)` e gestirlo nel use case.

### SOLID-3: ProclamatoriElencoContent con 28 parametri
- **File:** `ui/proclamatori/ProclamatoriComponents.kt:170-199`
- Troppi parametri. Raggruppare in data class dedicate (es. `SearchState`, `SelectionState`, `PaginationState`, callbacks in interface).

### SOLID-4: URL GitHub hardcoded nel modulo DI
- **File:** `core/di/AppModules.kt:75-78`
- URL raw GitHub come stringhe letterali. Estrarre in file di configurazione, variabile d'ambiente, o almeno `const val` in un oggetto config dedicato.

### SOLID-5: Domain model senza invarianti — PartType, Assignment, Proclamatore
- **File:** `feature/weeklyparts/domain/PartType.kt` — `peopleCount` accetta ≤0
- **File:** `feature/assignments/domain/Assignment.kt:13` — `slot` accetta ≤0
- **File:** `feature/people/domain/Proclamatore.kt` — `nome`/`cognome` accettano blank
- I domain object non validano i propri invarianti. La validazione avviene solo nei use case di creazione, ma mapper DB e import li bypassano.
- **Fix:** aggiungere `init { require(...) }` nelle data class domain.

### SOLID-6: ProclamatoreFormViewModel è singleton ma gestisce stato form effimero
- **File:** `core/di/AppModules.kt:135-143`
- Lo scope `single` è sbagliato per un VM con stato form mutabile. Il codice compensa con `clearForm()` ad ogni navigazione, ma se un path dimentica la chiamata il form riapre con dati stale.
- **Fix:** cambiare in `factory` scope o gestire lo stato form con `remember` nel composable.

---

## UI/UX — Miglioramenti interfaccia

### UX-1: DiagnosticsScreen mostra testo TODO agli utenti
- **File:** `ui/diagnostics/DiagnosticsScreen.kt:20`
- `Text("Scaffolding M7 pronto: export zip DB + log ultimi 14 giorni da implementare.")` è visibile all'utente finale. Rimuovere o sostituire con UI placeholder appropriato.

### UX-2: Sorting eseguito nel composable anziché nel ViewModel
- **File:** `ui/proclamatori/ProclamatoriComponents.kt:258` → `allItems.applySort(sort)`
- La logica di ordinamento viene eseguita ad ogni ricomposizione. Spostare nel ViewModel e esporre `sortedItems` già ordinati nello stato.

### UX-3: Nessun supporto dark mode
- **File:** `ui/theme/AppTheme.kt:14`
- `lightColorScheme()` hardcoded. Aggiungere `isSystemInDarkTheme()` e `darkColorScheme()`.

### UX-4: Bottoni conferma delete non disabilitati durante loading
- **File:** `ui/proclamatori/ProclamatoriComponents.kt:89-134` (singolo)
- **File:** `ui/proclamatori/ProclamatoriComponents.kt:137-167` (batch)
- `isLoading` passato ma non usato per disabilitare i bottoni. `handCursorOnHover(enabled = !isLoading)` cambia solo il cursore, non l'interattività. Double-click possibile.
- **Fix:** aggiungere `enabled = !isLoading` ai `TextButton`.

### UX-5: Nessun indicatore di caricamento durante ricerca proclamatori
- **File:** `ui/proclamatori/ProclamatoriListViewModel.kt:53-55`
- `refreshList()` imposta `isLoading` ma la tabella lampeggia ad ogni keystroke (legato a BUG-11).

### UX-6: FeedbackBanner colori success hardcoded (ma con fallback dark)
- **File:** `ui/components/FeedbackBanner.kt:43-45`
- Colori hex hardcoded con branching `isDark` basato su luminanza background. Funzionale ma fuori dal sistema colori Material — il componente error usa `colorScheme.errorContainer` mentre success no.
- **Fix:** allineare usando `SemanticColors` o `FeedbackBannerColors` nel tema per coerenza.

### UX-7: Overwrite schema remoto non mostra conteggio assegnazioni perse
- **File:** `feature/weeklyparts/application/AggiornaDatiRemotiUseCase.kt:59-71`
- `weekPlanStore.delete(existing.id)` cancella a cascata tutte le assegnazioni. Il dialog avvisa genericamente ma non indica quante assegnazioni verranno eliminate.
- **Fix:** contare e mostrare le assegnazioni che verranno perse.

### UX-8: Nessun limite lunghezza sui campi nome/cognome
- **File:** `feature/people/application/CreaProclamatoreUseCase.kt`
- **File:** `ui/proclamatori/ProclamatoriComponents.kt:510-538`
- Nessun max length nel use case né nel TextField. Stringhe lunghissime da JSON import o input manuale possono rompere il layout tabella.
- **Fix:** aggiungere max 100 chars nel use case e `maxLength` sul TextField.

### UX-9: Accessibilità — contentDescription null su molte icone
- **File:** `ui/weeklyparts/WeeklyPartsScreen.kt:132,431` — Icon con `contentDescription = null`
- **File:** `ui/proclamatori/ProclamatoriComponents.kt:216,300,310,324,334,679,693`
- Screen reader non annuncerà lo scopo delle icone in `IconButton` senza testo adiacente.
- **Fix:** aggiungere `contentDescription` descrittive.

### UX-11: Posizione finestra non salvata — riapre alla posizione OS default
- **File:** `core/config/WindowSettingsStore.kt` + `main.kt:28-38`
- `WindowSettings` salva `widthDp`, `heightDp`, `placement` ma non `position` (x, y). L'utente che sposta la finestra la ritrova altrove al prossimo lancio.
- **Fix:** aggiungere `positionX`/`positionY` a `WindowSettings` con sentinel `-1` per "usa default OS".

### UX-10: Disallineamento colonne PartsHeader vs righe
- **File:** `ui/weeklyparts/WeeklyPartsScreen.kt:291` — header `Spacer(40.dp)`
- **File:** `ui/weeklyparts/WeeklyPartsScreen.kt:385-386` — righe `Spacer(28.dp)`
- Il placeholder drag handle nell'header è 40dp ma nelle righe è 28dp, causando disallineamento colonne.
- **Fix:** estrarre costante condivisa per la larghezza dell'area drag handle.

---

## Dead code — Codice morto

### DEAD-1: DomainError.NotImplemented mai usato
- **File:** `core/domain/DomainError.kt:5`
- Nessun call site. Rimuovere la variante e il relativo ramo in `toMessage()`.

### DEAD-2: maxSortOrderForWeek query SQL inutilizzata
- **File:** `MinisteroDatabase.sq:173-174`
- La query non è mai invocata. Il calcolo avviene in Kotlin in `AggiungiParteUseCase.kt:20`. Rimuovere.

### DEAD-3: arrow-optics dependency inutilizzata
- **File:** `gradle/libs.versions.toml:34`, `composeApp/build.gradle.kts:26`
- Nessun import `arrow.optics` nel codice sorgente. Rimuovere la dipendenza per ridurre dimensione build. Nota: manca anche il KSP processor — la dipendenza non potrebbe funzionare neanche con annotazioni `@optics`.

### DEAD-4: kotlin-testJunit e junit nel catalogo ma mai referenziati
- **File:** `gradle/libs.versions.toml:6,21-22`
- `kotlin-testJunit` e `junit` dichiarati nel version catalog ma mai usati in `build.gradle.kts`. Rimuovere le entry.

---

## Build — Configurazione build

### BUILD-1: Nessun JVM target pinned — rischio bytecode/JRE mismatch
- **File:** `composeApp/build.gradle.kts`
- Nessun `jvmToolchain` o `jvmTarget` nel blocco `kotlin { }`. Se la macchina build ha JDK 21 ma il runtime bundled è JDK 17, `UnsupportedClassVersionError` al lancio.
- **Fix:** `kotlin { jvmToolchain(17) }`.

### BUILD-2: packageName placeholder e versione out-of-sync nell'installer
- **File:** `composeApp/build.gradle.kts:72-73`
- `packageName = "org.example.project"` è il placeholder del template. `AppVersion.current = "0.1.0-dev"` ma `packageVersion = "1.0.0"` — out-of-sync. Su Windows, cambiare packageName in futuro installa come app separata anziché upgrade.
- **Fix:** usare il vero reverse-domain e sincronizzare `packageVersion` con `AppVersion.current`.

### BUILD-4: AppVersion.current hardcoded — non generato dal build
- **File:** `core/config/AppVersion.kt:5`
- `const val current = "0.1.0-dev"` deve essere aggiornato manualmente. Root cause di BUILD-2 (out-of-sync con `packageVersion`). Se entrambi esistono indipendentemente, divergeranno di nuovo.
- **Fix:** generare da Gradle via `buildConfigField` o `version.properties` risorsa scritta dal build.

### BUILD-3: Nessun vendor/copyright/icon nella distribuzione nativa
- **File:** `composeApp/build.gradle.kts:69-75`
- `nativeDistributions` senza `vendor`, `copyright`, `description`, `icon`. MSI/EXE mostra "Unknown Publisher" nel dialog UAC.
- **Fix:** aggiungere metadata e icona.

---

## Sicurezza

### SEC-1: LOCALAPPDATA usata senza sanitizzazione per costruire path
- **File:** `core/config/PathsResolver.kt:31-36`
- Variabile d'ambiente user-controlled usata direttamente in `Paths.get()`. Una variabile malconfigurata potrebbe puntare a percorsi inattesi (UNC injection su Windows).
- **Fix:** normalizzare e validare che il path resti sotto `user.home`, o usare sempre `user.home`.

---

## Internazionalizzazione

### I18N-1: DateTimeFormatter senza Locale esplicito
- **File:** `ui/proclamatori/ProclamatoriUiSupport.kt:16`
- `DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")` senza `Locale`. Usa `Locale.getDefault()` a runtime. Incoerenziacon `WeekNavigator.kt:39` che specifica `Locale.ITALIAN`.
- **Fix:** aggiungere `Locale.ITALIAN` come in WeekNavigator.

### I18N-2: SexRule.name mostrato direttamente nella UI
- **File:** `ui/weeklyparts/WeeklyPartsScreen.kt:350,408`
- `part.partType.sexRule.name` mostra l'identifier Kotlin grezzo. `AssignmentsComponents` ha un mapping corretto via `SexRuleChip`. Bypassa il mapping display.
- **Fix:** riusare `SexRuleChip` condiviso (vedi DRY-7).

---

## Documentazione

### DOC-1: UI_STANDARD.md riferisce file eliminato
- **File:** `docs/UI_STANDARD.md:15`
- Riferimento a `TableStandard.kt` che non esiste più. Aggiornare la documentazione.

---

## Feature gap

### FEAT-1: Nessun test nel progetto
- Non esistono directory `jvmTest`/`commonTest` né file `*Test.kt`. Priorità alta per aggiungere test unitari almeno per use case e ViewModel.

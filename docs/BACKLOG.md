# Backlog Tecnico

## BUG — Integrità dati e correttezza

### BUG-9: selectJsonFileForImport() blocca il thread UI
- **File:** `ui/proclamatori/ProclamatoriUiSupport.kt:158-170`
- AWT `FileDialog` con `isVisible = true` è bloccante. Chiamata direttamente da un lambda composable. Funziona perché AWT gestisce il proprio event pump, ma il pattern è fragile.
- **Fix:** considerare `JFileChooser` su `Dispatchers.IO` o il composable `FileDialog` di Compose Desktop.

---

## SQL — Schema e migrazioni

### SQL-6: Nessuna strategia di migrazione DB
- **File:** `core/persistence/DatabaseProvider.kt:18-20`
- `JdbcSqliteDriver(schema = MinisteroDatabase.Schema)` auto-crea tabelle su DB nuovo. Per DB esistenti, cambiamenti schema non vengono applicati — nessun file di migrazione `.sqm` nel progetto.
- **Fix:** aggiungere directory `migrations/` con file `.sqm` per ogni cambio schema futuro. Critico per field additions.

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

## SOLID — Principi architetturali

### SOLID-1: AssignmentStore viola Interface Segregation
- **File:** `feature/assignments/application/AssignmentStore.kt:12-24`
- L'interfaccia mescola: CRUD assegnazioni, ranking/suggerimenti, e operazioni persona-lifecycle (`countAssignmentsForPerson`, `removeAllForPerson`). Separare in interfacce dedicate (es. `AssignmentReader`, `AssignmentWriter`, `AssignmentRanking`).

### SOLID-3: ProclamatoriElencoContent con 28 parametri
- **File:** `ui/proclamatori/ProclamatoriComponents.kt:170-199`
- Troppi parametri. Raggruppare in data class dedicate (es. `SearchState`, `SelectionState`, `PaginationState`, callbacks in interface).

### SOLID-4: URL GitHub hardcoded nel modulo DI
- **File:** `core/di/AppModules.kt:75-78`
- URL raw GitHub come stringhe letterali. Estrarre in file di configurazione, variabile d'ambiente, o almeno `const val` in un oggetto config dedicato.

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

### UX-10: Disallineamento colonne PartsHeader vs righe
- **File:** `ui/weeklyparts/WeeklyPartsScreen.kt:291` — header `Spacer(40.dp)`
- **File:** `ui/weeklyparts/WeeklyPartsScreen.kt:385-386` — righe `Spacer(28.dp)`
- Il placeholder drag handle nell'header è 40dp ma nelle righe è 28dp, causando disallineamento colonne.
- **Fix:** estrarre costante condivisa per la larghezza dell'area drag handle.

### UX-11: Posizione finestra non salvata — riapre alla posizione OS default
- **File:** `core/config/WindowSettingsStore.kt` + `main.kt:28-38`
- `WindowSettings` salva `widthDp`, `heightDp`, `placement` ma non `position` (x, y). L'utente che sposta la finestra la ritrova altrove al prossimo lancio.
- **Fix:** aggiungere `positionX`/`positionY` a `WindowSettings` con sentinel `-1` per "usa default OS".

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

### BUILD-3: Nessun vendor/copyright/icon nella distribuzione nativa
- **File:** `composeApp/build.gradle.kts:69-75`
- `nativeDistributions` senza `vendor`, `copyright`, `description`, `icon`. MSI/EXE mostra "Unknown Publisher" nel dialog UAC.
- **Fix:** aggiungere metadata e icona.

### BUILD-4: AppVersion.current hardcoded — non generato dal build
- **File:** `core/config/AppVersion.kt:5`
- `const val current = "0.1.0-dev"` deve essere aggiornato manualmente. Root cause di BUILD-2 (out-of-sync con `packageVersion`). Se entrambi esistono indipendentemente, divergeranno di nuovo.
- **Fix:** generare da Gradle via `buildConfigField` o `version.properties` risorsa scritta dal build.

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

---

## Documentazione

### DOC-1: UI_STANDARD.md riferisce file eliminato
- **File:** `docs/UI_STANDARD.md:15`
- Riferimento a `TableStandard.kt` che non esiste più. Aggiornare la documentazione.

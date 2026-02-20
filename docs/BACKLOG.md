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

## SOLID — Principi architetturali

### SOLID-1: AssignmentStore viola Interface Segregation
- **File:** `feature/assignments/application/AssignmentStore.kt:12-24`
- L'interfaccia mescola: CRUD assegnazioni, ranking/suggerimenti, e operazioni persona-lifecycle (`countAssignmentsForPerson`, `removeAllForPerson`). Separare in interfacce dedicate (es. `AssignmentReader`, `AssignmentWriter`, `AssignmentRanking`).

### SOLID-3: ProclamatoriElencoContent con 28 parametri
- **File:** `ui/proclamatori/ProclamatoriComponents.kt:170-199`
- Troppi parametri. Raggruppare in data class dedicate (es. `SearchState`, `SelectionState`, `PaginationState`, callbacks in interface).

---

## UI/UX — Miglioramenti interfaccia

### UX-1: DiagnosticsScreen mostra testo TODO agli utenti
- **File:** `ui/diagnostics/DiagnosticsScreen.kt:20`
- `Text("Scaffolding M7 pronto: export zip DB + log ultimi 14 giorni da implementare.")` è visibile all'utente finale. Rimuovere o sostituire con UI placeholder appropriato.

### UX-3: Nessun supporto dark mode
- **File:** `ui/theme/AppTheme.kt:14`
- `lightColorScheme()` hardcoded. Aggiungere `isSystemInDarkTheme()` e `darkColorScheme()`.

### UX-7: Overwrite schema remoto non mostra conteggio assegnazioni perse
- **File:** `feature/weeklyparts/application/AggiornaDatiRemotiUseCase.kt:59-71`
- `weekPlanStore.delete(existing.id)` cancella a cascata tutte le assegnazioni. Il dialog avvisa genericamente ma non indica quante assegnazioni verranno eliminate.
- **Fix:** contare e mostrare le assegnazioni che verranno perse.

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

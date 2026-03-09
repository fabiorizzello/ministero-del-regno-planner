# Review Notes — Consolidato e Deduplicato (2026-03-09)

## Findings aperti (ordinati per severità)

### High

2. `feature/output`: tutti e tre i use case usano `throw IllegalStateException` invece di `Either<DomainError, T>`.
   - Pattern inconsistente con tutto il resto del codebase; il ViewModel usa `runCatching` come workaround.
   - `GeneraImmaginiAssegnazioni` aggiunge un ulteriore throw in `renderTicketImage()` — espansione del problema con nuovi metodi.
   - Evidenze (righe aggiornate): `StampaProgrammaUseCase.kt:116`, `GeneraImmaginiAssegnazioni.kt:86,107,264`.

46. `DiagnosticsViewModel` bypassa use case e chiama direttamente `MinisteroDatabase` per mutazioni e letture.
    - `database.ministeroDatabaseQueries.deleteWeekPlansBeforeDate()` (riga 224) è una mutazione senza TransactionRunner e senza use case. Le letture count (righe 430-432) bypassano analogamente il layer application.
    - Il modulo DI wira `MinisteroDatabase` direttamente nel ViewModel (ViewModelsModule.kt:84).
    - Soluzione: estrarre `EliminaStoricoUseCase` (mutante, con TransactionRunner) e `ContaStoricoUseCase` (read-only).
    - Evidenze: `DiagnosticsViewModel.kt:224,430-432`, `ui/di/ViewModelsModule.kt:84`.

47. `AggiornaApplicazione.kt:31` — `throw IllegalStateException()` in use case invece di `Either<DomainError, T>`.
    - Viola il pattern Arrow Either adottato da tutti gli altri use case dell'application layer.
    - Evidenza: `feature/updates/application/AggiornaApplicazione.kt:31`.

### Medium

2. Copertura integration migliorabile sui boundary esterni.
   - HTTP client e PDF rendering ancora poco coperti.
   - `PdfAssignmentsRenderer` ha zero test unitari su `renderWeeklyAssignmentsPdf()` e `renderPersonSheetPdf()`.
   - Evidenze: `GitHubSchemaCatalogDataSource.kt:40`, `GitHubReleasesClient.kt:38`, `PdfAssignmentsRenderer.kt`.

4. `GeneraImmaginiAssegnazioni`: logica PDF→PNG (`renderPdfToPngFile`) nel layer application.
   - `Loader.loadPDF`, `PDFRenderer`, `ImageIO.write` sono infrastruttura; dovrebbero stare in `infrastructure/`.
   - Evidenza: `GeneraImmaginiAssegnazioni.kt:335-341`, `:13-14`.


14. `KonformValidation.requireValid()` lancia `IllegalArgumentException` invece di restituire `Either`.
   - Violazione della regola "domain non lancia eccezioni".
   - Callers: `PartType.init` (weeklyparts/domain/PartType.kt:38-40), `Proclamatore.init` (people/domain/Proclamatore.kt:39-44) — construction-time exception non mappata a DomainError.
   - Evidenza: `core/domain/KonformValidation.kt:10`.

15. `feature/updates` — zero test coverage.
    - `VerificaAggiornamenti`, `AggiornaApplicazione`, `GitHubReleasesClient`, `UpdateScheduler` non hanno nessun test.
    - Evidenza: `feature/updates/application/*.kt`, `feature/updates/infrastructure/*.kt`.

### Low

- `SqlDelightSchemaUpdateAnomalyStore.append()` non è idempotente: ogni chiamata genera nuovo UUID, retry accumula duplicati.
- **Finding 24**: `WeekPlan` init block lancia `IllegalArgumentException` se `weekStartDate` non è lunedì. Pattern non-funzionale. Alternativa DDD: smart constructor `WeekPlan.of()` → `Either`.
  - Evidenze: `WeekPlan.kt:22-26`, `DomainErrorMappingWeeklyPartsUseCaseTest.kt:92`.
- **Finding 49**: `AggiornaApplicazione.kt:47-57` — fallback `"explorer.exe"` hardcoded per apertura file; non funziona su Linux/macOS. `UpdateScheduler.kt:28` — `runCatching` con solo `WARN` log; failure silenzioso se il check aggiornamenti fallisce.
## Findings risolti (Batch 1 — 2026-03-09)

- **Finding 43**: Rimossi dead params `storedProgramWeeks`/`storedProgramAssignments` da `PersonPickerViewModel` e caller `ProgramWorkspaceScreen.kt`.
- **Finding 44**: `mapAssignmentWithPersonRow` → `private` in `AssignmentRowMapper.kt`.
- **Finding 45**: `SchemaManagementViewModel.loadCurrentAndFuturePrograms()` ora restituisce `Either<DomainError, List<ProgramMonth>>` direttamente; eliminato `runCatching+throw` al call site.
- **Finding 48**: Invalidato — il codice in `PdfAssignmentsRenderer.kt` e `PdfProgramRenderer.kt` lancia già `IOException` se `mkdirs()` fallisce. Finding basato su osservazione errata.
- **Medium 10**: `TransactionRunner` usa `withContext(Dispatchers.IO)` + `runBlocking(coroutineContext)`; rimosso `@Suppress("BlockingMethodInNonBlockingContext")`; `@Suppress("UNCHECKED_CAST")` mantenuto con commento esplicativo.

## Findings risolti (Batch 2 — 2026-03-09)

- **Medium 11**: `VerificaAggiornamenti` → `Either<DomainError, UpdateCheckResult>`; rimosso `error: String?` da `UpdateCheckResult`; `UpdateStatusStore` aggiornato al nuovo tipo; `DiagnosticsViewModel.applyUpdateResult` e `checkUpdates` aggiornati.
- **High 47**: `AggiornaApplicazione.invoke()` → `Either<DomainError, Path>`; `throw IllegalStateException` → `raise(DomainError.Network(...))`; `DiagnosticsViewModel.startUpdate()` → `executeEitherOperation`.
- **Finding 49**: Rimosso fallback `"explorer.exe"` da `AggiornaApplicazione.openFile()` — ramo `else` eliminato.

## Verifiche eseguite

- `./gradlew :composeApp:jvmTest` → `BUILD SUCCESSFUL` (2026-03-06, post-fix low effort ciclo 4)
- Totale test JVM: `208` | Failure: `0` | Error: `0`
- Kover baseline: Line 39.9%, Method 35.8%, Branch 33.4% (esclusi `ui/`, `db/`, `core/cli/`)
- `./gradlew :composeApp:jvmTest` → `BUILD SUCCESSFUL` (2026-03-09, post library updates + context parameters migration + bug fix 3 test)
- Totale test JVM: `222` | Failure: `0` | Error: `0`
- `./gradlew :composeApp:jvmTest` → `BUILD SUCCESSFUL` (2026-03-09, Ralph Loop iterazione 6 — fix 13 low-effort findings)
- Totale test JVM: `222` | Failure: `0` | Error: `0`
- `./gradlew :composeApp:jvmTest` → `BUILD SUCCESSFUL` (2026-03-09, fix High 16 + Medium 35)
- Totale test JVM: `226` | Failure: `0` | Error: `0`
- `./gradlew :composeApp:jvmTest` → `BUILD SUCCESSFUL` (2026-03-09, Batch 0+1 findings fixer — 7 finding risolti)
- Totale test JVM: `226` | Failure: `0` | Error: `0`
- `./gradlew :composeApp:jvmTest` → `BUILD SUCCESSFUL` (2026-03-09, High 42 — context(TransactionScope) su AssignmentRepository)
- Totale test JVM: `226` | Failure: `0` | Error: `0`

## Stato finale sintetico

Con i vincoli richiesti (DDD rigoroso, aggregate-root centric, transazione unica per use case mutante), stato attuale: **non ancora production-ready**.

Aree più problematiche: (1) feature/output fuori dal modello Either (High 2), (2) copertura test zero su feature/updates (Medium 15), (3) `KonformValidation` lancia IAE (Medium 14). Medium 10 risolto.

Sessione 2026-03-09 (1): verificata feature/output post-implementazione biglietti. High 1 risolto. Aggiunti Finding 33 (GeneraPdfAssegnazioni dead code), Finding 34 (mkdirs unchecked). Aggiornate evidenze linea per High 2 e Medium 6.
Sessione 2026-03-09 (2): aggiunto ordinamento biglietti per sortOrder parte + partNumber in AssignmentTicketLine. Aggiunto Finding 41 (PART_DISPLAY_NUMBER_OFFSET DRY violation). 222 test, 0 failure.
Sessione 2026-03-09 (3): review di verifica post-modifiche. Confermati validi: High 2, High 16, Medium 35, Medium 36, Finding 33. `AutoAssegnaProgrammaUseCase` non restituisce `Either` ma gestisce errori via lista `unresolved` — pattern intenzionale (partial success), non aggiunto come finding. Nessun nuovo finding.
Ralph Loop iterazioni 2-5: analisi parallela di feature/people, feature/programs, feature/assignments, core/, feature/schemas, feature/updates. Aggiunti Medium 35 (ImpostaIdoneita senza TX), Medium 36 (RefreshFailed anti-pattern), Finding 37 (CaricaProgrammiAttiviUseCase no Either), Finding 38 (alreadyAssignedIds fragile contract), Finding 39 (MAX_FUTURE_PROGRAMS domain→application layer violation), Finding 40 (InMemoryProgramStore DRY), High 42 (AssignmentRepository interface senza context(TransactionScope) — diversamente da WeekPlanStore che lo ha). Aggiornato Medium 14 con callers PartType e Proclamatore. Confermato: no TODO nel codebase; tutte le weeklyparts mutation use case hanno TransactionRunner; AggiornaProgrammaDaSchemiUseCase correttamente wired con TransactionRunner; dryRun=true path testato.
Ralph Loop iterazione 6 (fix low-effort): risolti High 5, Medium 6, Medium 12, Medium 18, Finding 29, 30, 31, 32, 33, 34, 39, 40, 41. Finding 37 lasciato aperto intenzionalmente (troppi caller VM da aggiornare, rischio sproporzionato per use case read-only). BUILD SUCCESSFUL, test tutti verdi.
Sessione successiva: risolti High 16 e Medium 35 (TransactionRunner per use case people). Estratto ImmediateTransactionRunner in PeopleTestFixtures.kt. Aggiunti 4 test ImpostaIdoneita. 226 test, 0 failure.
Sessione 2026-03-09 (findings fixer Batch 0+1): risolti Medium 36, Medium 21, Finding 38, Finding 37, Finding 25, High 3, High 4, High 17. Aggiunti Finding 43 (dead code PersonPickerViewModel), Finding 44 (mapAssignmentWithPersonRow non private), Finding 45 (SchemaManagementViewModel wrapping Either). 226 test, 0 failure.
Sessione successiva (High 42): aggiunto `context(tx: TransactionScope)` a tutti i metodi di mutazione in `AssignmentRepository` e `PersonAssignmentLifecycle`. Aggiornati SqlDelightAssignmentStore e tutti i test fake. 226 test, 0 failure.
Sessione 2026-03-09 (Batch 1 — 5 finding): Finding 43, 44, 45, 48 (invalidato), Medium 10. Build non riverificata localmente (agenti hanno eseguito jvmTest in worktree separati con esito SUCCESS).

# Review Notes — Consolidato e Deduplicato (2026-03-09)

## Findings aperti (ordinati per severità)

### High

2. `feature/output`: tutti e tre i use case usano `throw IllegalStateException` invece di `Either<DomainError, T>`.
   - Pattern inconsistente con tutto il resto del codebase; il ViewModel usa `runCatching` come workaround.
   - `GeneraImmaginiAssegnazioni` aggiunge un ulteriore throw in `renderTicketImage()` (line 258-261) — espansione del problema con nuovi metodi.
   - Evidenze: `StampaProgrammaUseCase.kt:117`, `GeneraPdfAssegnazioni.kt:30`, `GeneraImmaginiAssegnazioni.kt:83,104,258-261`.

42. `AssignmentRepository` interface — tutti i metodi di mutazione privi di `context(TransactionScope)`.
    - Il problema è nell'**interfaccia** `AssignmentStore.kt` (righe 17-23): `save`, `remove`, `removeAllByWeekPlan`, `removeAllForPerson`, `deleteByProgramFromDate` sono `suspend` plain. A differenza di `WeekPlanStore` che dichiara `context(tx: TransactionScope) suspend fun saveAggregate(...)`, il boundary assignment non ha nessun enforcement statico.
    - Il compilatore non impedisce di chiamare queste mutation fuori da `runInTransaction`. Altri use case che chiamano `save()` dentro `runInTransaction` sono corretti solo per disciplina, non per contratto.
    - Evidenze: `AssignmentStore.kt:17-23`, `WeekPlanStore.kt:25-27` (confronto — pattern atteso), `SqlDelightAssignmentStore.kt:43,52,56,223,227`.

### Medium

2. Copertura integration migliorabile sui boundary esterni.
   - HTTP client e PDF rendering ancora poco coperti.
   - Evidenze: `GitHubSchemaCatalogDataSource.kt:40`, `GitHubReleasesClient.kt:38`.

4. `GeneraImmaginiAssegnazioni`: logica PDF→PNG (`renderPdfToPng`) nel layer application; inietta use case invece di store.
   - `Loader.loadPDF`, `PDFRenderer`, `ImageIO.write` sono infrastruttura; dovrebbero stare in `infrastructure/`.
   - Evidenza: `GeneraImmaginiAssegnazioni.kt:107-112`, `:13-15`.

10. `TransactionRunner.kt:29-30` — `runBlocking` dentro la coroutine della transazione.
    - `@Suppress("BlockingMethodInNonBlockingContext")` nasconde il problema. `@Suppress("UNCHECKED_CAST")` a riga 39 nasconde un unsafe cast di `Any` a `T`.
    - Basso rischio in produzione (desktop single-user), ma fragilità architettonica.
    - Evidenza: `core/persistence/TransactionRunner.kt:29,39`.

11. `VerificaAggiornamenti` non usa `Either<DomainError, T>`.
    - Restituisce `UpdateCheckResult` con `error: String?` invece di `Either`. Inconsistente con il resto del codebase.
    - Evidenza: `feature/updates/application/VerificaAggiornamenti.kt:21-59`.

14. `KonformValidation.requireValid()` lancia `IllegalArgumentException` invece di restituire `Either`.
   - Violazione della regola "domain non lancia eccezioni".
   - Callers: `PartType.init` (weeklyparts/domain/PartType.kt:38-40), `Proclamatore.init` (people/domain/Proclamatore.kt:39-44) — construction-time exception non mappata a DomainError.
   - Evidenza: `core/domain/KonformValidation.kt:10`.

15. `feature/updates` — zero test coverage.
    - `VerificaAggiornamenti`, `AggiornaApplicazione`, `GitHubReleasesClient`, `UpdateScheduler` non hanno nessun test.
    - Evidenza: `feature/updates/application/*.kt`, `feature/updates/infrastructure/*.kt`.

### Low

- `SqlDelightSchemaUpdateAnomalyStore.append()` non è idempotente: ogni chiamata genera nuovo UUID, retry accumula duplicati.
- **Finding 24**: `WeekPlan` init block lancia `IllegalArgumentException` se `weekStartDate` non è lunedì. Pattern non-funzionale. Alcuni test passano date non-lunedì senza conseguenze visibili oggi. Alternativa DDD: smart constructor `WeekPlan.of()` → `Either`.
  - Evidenze: `WeekPlan.kt:22-26`, `DomainErrorMappingWeeklyPartsUseCaseTest.kt:92`.
- **Finding 43** (emerso da review post-fix Medium 21/38): `PersonPickerViewModel` porta parametri inutilizzati `selectedProgramWeeks`/`selectedProgramAssignments` in `openPersonPicker`, `loadSuggestions`, `reloadSuggestions` e i campi `storedProgramWeeks`/`storedProgramAssignments`. Dead code confusionario dopo il fix che ha spostato il carico degli assignments dentro il use case. Il caller `ProgramWorkspaceScreen.kt` passa ancora questi valori inutilmente.
  - Evidenze: `PersonPickerViewModel.kt:51-52,65-66,147-148`, `ProgramWorkspaceScreen.kt:698-699`.
- **Finding 44** (emerso da review post-fix Finding 25): `mapAssignmentWithPersonRow` in `AssignmentRowMapper.kt` è `internal` ma ancora callable direttamente dal package `infrastructure`, bypassando la guard `slot >= 1` in `toAssignmentWithPersonOrNull()`. Considerare renderla `private`.
  - Evidenza: `AssignmentRowMapper.kt`.
- **Finding 45** (emerso da review post-fix Finding 37/25): `SchemaManagementViewModel.loadCurrentAndFuturePrograms()` usa `.getOrElse { throw RuntimeException(...) }` + `runCatching` invece di propagare l'Either direttamente. Anti-pattern wrapping/unwrapping non necessario.
  - Evidenza: `SchemaManagementViewModel.kt`.

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

## Stato finale sintetico

Con i vincoli richiesti (DDD rigoroso, aggregate-root centric, transazione unica per use case mutante), stato attuale: **non ancora production-ready**.

Aree più problematiche: (1) feature/output fuori dal modello Either (High 2), (2) `AssignmentRepository` interface senza capability token (High 42), (3) copertura test zero su feature/updates (Medium 15), (4) `KonformValidation` lancia IAE (Medium 14), (5) `TransactionRunner` runBlocking (Medium 10).

Sessione 2026-03-09 (1): verificata feature/output post-implementazione biglietti. High 1 risolto. Aggiunti Finding 33 (GeneraPdfAssegnazioni dead code), Finding 34 (mkdirs unchecked). Aggiornate evidenze linea per High 2 e Medium 6.
Sessione 2026-03-09 (2): aggiunto ordinamento biglietti per sortOrder parte + partNumber in AssignmentTicketLine. Aggiunto Finding 41 (PART_DISPLAY_NUMBER_OFFSET DRY violation). 222 test, 0 failure.
Sessione 2026-03-09 (3): review di verifica post-modifiche. Confermati validi: High 2, High 16, Medium 35, Medium 36, Finding 33. `AutoAssegnaProgrammaUseCase` non restituisce `Either` ma gestisce errori via lista `unresolved` — pattern intenzionale (partial success), non aggiunto come finding. Nessun nuovo finding.
Ralph Loop iterazioni 2-5: analisi parallela di feature/people, feature/programs, feature/assignments, core/, feature/schemas, feature/updates. Aggiunti Medium 35 (ImpostaIdoneita senza TX), Medium 36 (RefreshFailed anti-pattern), Finding 37 (CaricaProgrammiAttiviUseCase no Either), Finding 38 (alreadyAssignedIds fragile contract), Finding 39 (MAX_FUTURE_PROGRAMS domain→application layer violation), Finding 40 (InMemoryProgramStore DRY), High 42 (AssignmentRepository interface senza context(TransactionScope) — diversamente da WeekPlanStore che lo ha). Aggiornato Medium 14 con callers PartType e Proclamatore. Confermato: no TODO nel codebase; tutte le weeklyparts mutation use case hanno TransactionRunner; AggiornaProgrammaDaSchemiUseCase correttamente wired con TransactionRunner; dryRun=true path testato.
Ralph Loop iterazione 6 (fix low-effort): risolti High 5, Medium 6, Medium 12, Medium 18, Finding 29, 30, 31, 32, 33, 34, 39, 40, 41. Finding 37 lasciato aperto intenzionalmente (troppi caller VM da aggiornare, rischio sproporzionato per use case read-only). BUILD SUCCESSFUL, test tutti verdi.
Sessione successiva: risolti High 16 e Medium 35 (TransactionRunner per use case people). Estratto ImmediateTransactionRunner in PeopleTestFixtures.kt. Aggiunti 4 test ImpostaIdoneita. 226 test, 0 failure.
Sessione 2026-03-09 (findings fixer Batch 0+1): risolti Medium 36, Medium 21, Finding 38, Finding 37, Finding 25, High 3, High 4, High 17. Aggiunti Finding 43 (dead code PersonPickerViewModel), Finding 44 (mapAssignmentWithPersonRow non private), Finding 45 (SchemaManagementViewModel wrapping Either). 226 test, 0 failure.

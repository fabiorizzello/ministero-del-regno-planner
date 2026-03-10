# Review Notes — Consolidato e Deduplicato (2026-03-09)

## Findings aperti (ordinati per severità)

### Medium

2. Copertura integration migliorabile sui boundary esterni.
   - HTTP client e PDF rendering ancora poco coperti.
   - `PdfAssignmentsRenderer` ha zero test unitari su `renderWeeklyAssignmentsPdf()` e `renderPersonSheetPdf()`.
   - Evidenze: `GitHubSchemaCatalogDataSource.kt:40`, `GitHubReleasesClient.kt:38`, `PdfAssignmentsRenderer.kt`.

15. `feature/updates` — zero test coverage. *(in standby — architettura in evoluzione)*
    - `VerificaAggiornamenti`, `AggiornaApplicazione`, `GitHubReleasesClient` non hanno nessun test.
    - `UpdateScheduler` rimosso (non più schedulato automaticamente).
    - Evidenza: `feature/updates/application/*.kt`, `feature/updates/infrastructure/*.kt`.

---

## Findings risolti (Batch 4 — 2026-03-10)

- **High 2**: `StampaProgrammaUseCase` e `GeneraImmaginiAssegnazioni` convertiti a `Either<DomainError, T>`; `throw IllegalStateException` rimpiazzati con `raise(DomainError.NotFound)`; `renderTicketImage` ora ritorna `Either<DomainError, Path>` con try/catch al boundary infrastruttura; `AssignmentManagementViewModel` migrato da `executeAsyncOperation` a `executeEitherOperation`. Aggiunti test per percorsi di errore (NotFound, rendering fallito).
- **Medium 4**: `renderPdfToPngFile` (`Loader.loadPDF`, `PDFRenderer`, `ImageIO.write`) estratta da application layer a `infrastructure/PdfToImageConverter.kt`; iniettata come lambda `(Path, Path) -> Unit` — comportamento e testabilità invariati.

## Findings risolti (Batch A — 2026-03-10)

- **SqlDelightSchemaUpdateAnomalyStore**: ID deterministico da `"${personId}|${partTypeId}|${reason}".hashCode()` + `INSERT OR IGNORE`. Rimosso UUID casuale.
- **Finding 24**: `WeekPlan` smart constructor `of()` → `Either<DomainError, WeekPlan>`; costruttore `internal`; `init` IAE rimosso; `GeneraSettimaneProgrammaUseCase` aggiornato con `.bind()`.

## Findings risolti (Batch 3 — 2026-03-09)

- **High 46**: Estratti `ContaStoricoUseCase` (read-only) e `EliminaStoricoUseCase` (mutante con TransactionRunner + Either) da `DiagnosticsViewModel`. Nuovo modulo DI `DiagnosticsModule`. `MinisteroDatabase` rimosso dal ViewModel. VACUUM eseguito fuori transazione come da vincoli SQLite.
- **Medium 14**: Smart constructor `of()` aggiunto a `PartType` e `Proclamatore`; costruttori primari resi `internal`; `init` block rimosso; `KonformValidation.validate()` aggiunto; `PartTypeJsonParser` e `ImportaProclamatoriDaJsonUseCase` aggiornati.

## Findings risolti (Batch 2 — 2026-03-09)

- **Medium 11**: `VerificaAggiornamenti` → `Either<DomainError, UpdateCheckResult>`; rimosso `error: String?` da `UpdateCheckResult`; `UpdateStatusStore` aggiornato al nuovo tipo; `DiagnosticsViewModel.applyUpdateResult` e `checkUpdates` aggiornati.
- **High 47**: `AggiornaApplicazione.invoke()` → `Either<DomainError, Path>`; `throw IllegalStateException` → `raise(DomainError.Network(...))`; `DiagnosticsViewModel.startUpdate()` → `executeEitherOperation`.
- **Finding 49**: Rimosso fallback `"explorer.exe"` da `AggiornaApplicazione.openFile()` — ramo `else` eliminato.

## Findings risolti (Batch 1 — 2026-03-09)

- **Finding 43**: Rimossi dead params `storedProgramWeeks`/`storedProgramAssignments` da `PersonPickerViewModel` e caller `ProgramWorkspaceScreen.kt`.
- **Finding 44**: `mapAssignmentWithPersonRow` → `private` in `AssignmentRowMapper.kt`.
- **Finding 45**: `SchemaManagementViewModel.loadCurrentAndFuturePrograms()` ora restituisce `Either<DomainError, List<ProgramMonth>>` direttamente; eliminato `runCatching+throw` al call site.
- **Finding 48**: Invalidato — il codice in `PdfAssignmentsRenderer.kt` e `PdfProgramRenderer.kt` lancia già `IOException` se `mkdirs()` fallisce. Finding basato su osservazione errata.
- **Medium 10**: `TransactionRunner` usa `withContext(Dispatchers.IO)` + `runBlocking(coroutineContext)`; rimosso `@Suppress("BlockingMethodInNonBlockingContext")`; `@Suppress("UNCHECKED_CAST")` mantenuto con commento esplicativo.

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
- `./gradlew :composeApp:jvmTest` → `BUILD SUCCESSFUL` (2026-03-10, Batch 1+2+3+A findings fixer — 14 finding risolti)
- Totale test JVM: `226` | Failure: `0` | Error: `0`
- `./gradlew :composeApp:jvmTest` → `BUILD SUCCESSFUL` (2026-03-10, Batch 4 — High 2 + Medium 4)
- Totale test JVM: `231` | Failure: `0` | Error: `0`

## Stato finale sintetico

Con i vincoli richiesti (DDD rigoroso, aggregate-root centric, transazione unica per use case mutante), stato attuale: **quasi production-ready**.

Rimasto aperto: (1) PdfAssignmentsRenderer zero test (Medium 2), (2) feature/updates test in standby (Medium 15).

Sessione 2026-03-10: risolti SqlDelightSchemaUpdateAnomalyStore (idempotenza), Finding 24 (WeekPlan smart constructor). Rimosso UpdateScheduler (check solo su richiesta). Aggiunto spec 007 aggiornamento applicazione. Aggiunto GitHub Actions workflow release. Prima release v0.1.0 taggata e pushata.

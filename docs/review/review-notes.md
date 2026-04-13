# Review Notes — Ministero del Regno Planner

Prompt sorgente: 5x review-codebase su 5 feature slice (people, weeklyparts, programs,
schemas, print+assignments), 2026-03-12.
Review round 13: 5-round full-codebase scan (people, assignments+output, weeklyparts+programs+schemas,
updates+diagnostics+core, test+SQL schema), 2026-03-18.
Review round 14: post-commit review sulle modifiche di `983c623` (historical import + past
assignment editing), `d6f0b38` (UI preferences + seed import tooling), `4e9d486` (assignment
feedback refinements), 2026-04-10.
Review round 15: deep scan mirato su criticità da import storico + edit del passato (commit
`983c623` revisitato), 2026-04-10.
Review round 16: post-commit review sulle modifiche admin catalog (`8be9cc9`) + program sidebar
status (`7f101f2`) + assignment ranking dimensional fairness (`593720b`), 2026-04-13.

---

## Findings aperti

### MEDIUM

**R14-004 (MEDIUM)** — ~~`ImportaSeedApplicazioneDaJsonUseCase.persistHistoricalAssignment:393-439`:
frammentazione delle parti storiche~~ — **debito accettato** (2026-04-10)

Motivazione: `LETTURA_DELLA_BIBBIA` e `DISCORSO` hanno `peopleCount=1` quindi non possono
frammentarsi. Per le parti a 2 slot (`INIZIARE_CONVERSAZIONE`, `COLTIVARE_INTERESSE`,
`FARE_DISCEPOLI`) il secondo slot è `assistente` e lo storico frammentato in due parti
separate è semanticamente accettabile (conduttore e assistente sono ruoli distinti, non
equivalenti). Nessuna azione.

**R16-002 (MEDIUM)** — `ProgramLifecycleViewModel.loadProgramSidebarStates`: per ogni programma
visibile in sidebar viene caricato l'intero set di settimane + assegnazioni in parallelo per
calcolare lo status di completamento. Con N programmi futuri il fan-out è N × (1 query weeks +
M query assignments). Decisione di prodotto: introdurre **paginazione/lazy load** sulla sidebar
(carica lo stato solo per i programmi visibili o on-demand all'espansione). Tracciato come
work item dedicato, non parte del batch round 16. Effort: 1-2h.

**R16-008 (MEDIUM)** — `GeneraSettimaneProgrammaUseCase.kt:180`:
`snapshotAssignmentsByRestoreKey` usa `error("Assignment references unknown part ...")` quando
un'assegnazione punta a una `weeklyPartId` non presente nelle parti dell'aggregato. È difensivo
contro un invariant violato dal DB (assignments.weekly_part_id orfano), ma `error()` produce
un crash non recuperabile mentre il caller (`runInTransactionEither`) si aspetterebbe un
`DomainError` propagabile. Soluzione: convertire in `raise(DomainError.InvarianteAggregatoViolato)`
o equivalente (richiede l'introduzione di un nuovo case `DomainError`), dato che il use case è
già dentro un `either {}`. Emerso post-fix R14-008.
Effort: 15m.

### LOW

**R15-011 (LOW)** — `ImportaSeedApplicazioneDaJsonUseCaseTest.kt`: manca un test per il ramo
`existingAggregate != null && programId == null` in `persistHistoricalAssignment`, cioè l'append
di una seconda assegnazione storica sulla stessa settimana-frammento (quando due studenti hanno
`ultimaParte` nella stessa data). Il ramo è permesso by design (historical-only fragments sono
OK), ma non è blindato da regressione. Emerso durante la review post-fix di R15-001. Effort: 15m.

### Debito accettato

- MEDIUM-020 — DiagnosticsViewModel I/O diretto: utility screen senza logica di dominio, debito accettato
- R10-003 — `findProgramByYearMonth` / `deleteWeekPlan` usate solo dal seed CLI: debito accettato, tooling code
- R10-002 — `insertWeekPlan` usata solo da test: debito accettato, stessa logica di R10-003 (tooling/test code)
- R10-010 — Date comparisons basate su ordinamento stringhe ISO-8601: by design, SQLite non ha tipo DATE nativo
- **R14-010** — `CaricaUltimeAssegnazioniPerParteProclamatoreUseCase` wrapper triviale: il layer use case è un contract boundary, mantenerlo è difendibile. Nessuna azione (2026-04-13)
- **R15-008** — Import seed N+1 query pattern: per i seed attuali (decine di studenti, 1-3 parti) il costo è irrilevante; il batching è un'ottimizzazione prematura senza un dataset reale che la giustifichi (2026-04-13)

---

## Findings risolti

### Batch low-effort (2026-03-18)

- **R8-001** (HIGH) — `requireNotNull` → `raise(DomainError.Validation)` in either block. Commit: `a72aa8e`
- **R7-005** (MEDIUM) — `Either.catch` → `runInTransactionEither` in EliminaStoricoUseCase. Commit: `e343ab2`
- **R7-010** (MEDIUM) — `Either.catch` → `runInTransactionEither` in SegnaComInviato + AnnullaConsegna. Commit: `8d4fa83`
- **R8-008** (MEDIUM) — Added error handling to `loadAssignmentSettings`. Commit: `b826999`
- **R8-009** (MEDIUM) — Added error handling to `loadPartTypes` via `executeAsyncOperation`. Commit: `1e1fe2c`
- **R10-001** (MEDIUM) — Deleted 5 orphan SQL queries. Commit: `8d7dbd8`
- **R7-004** (LOW) — `requireNotNull` → safe `Instant.MIN` fallback. Commit: `737827e`
- **R7-007** (LOW) — Deleted dead `requireValid` function. Commit: `7531272`
- **R8-010** (LOW) — Removed dead `caricaAssegnazioni` dependency from PersonPickerViewModel. Commit: `cb5ee42`
- **R11-001** (LOW) — Deleted dead `mapProclamatoreRow` function. Commit: `b1d533a`
- **R11-004** (LOW) — Silent `return@forEach` → `error()` for impossible state. Commit: `053b7c3`
- **R11-006** (LOW) — Removed redundant `.sortedBy` after SQL `ORDER BY`. Commit: `cf352e6`
- **SA-001** (MEDIUM) — Spec 002 updated: "nessuna parte" → "con la parte fissa". Commit: `bd8e6e1`
- **SA-005** (LOW) — Spec 002 updated: `programId: String?` → `ProgramMonthId?`. Commit: `bd8e6e1`
- **SA-006** (LOW) — Spec 005 updated: `slotToRoleLabel()` → `roleLabel` property. Commit: `bd8e6e1`
- **SA-007** (LOW) — Spec 005 updated: removed stale `AssignmentHistoryEntry` reference. Commit: `bd8e6e1`
- **SA-008** (LOW) — Spec 007 updated: documented third `firstOrNull()` fallback. Commit: `bd8e6e1`
- **R11-007** (LOW) — Invalidato: `sospeso=false` è il default del data class Proclamatore; l'assignment query non ha accesso allo stato sospensione
- **R8-015** (LOW) — Invalidato: `SchemaManagementViewModelTest.kt` esiste con 9 test su helper functions

### Batch impatto-rapido (2026-03-18)

- **R10-004** (MEDIUM) — Added `UNIQUE(week_plan_id, sort_order)` on `weekly_part`. Commit: `f35a69e`
- **R10-005** (LOW) — Added `UNIQUE(schema_week_id, sort_order)` on `schema_week_part`. Commit: `ff66296`
- **R10-006** (LOW) — Added CHECK constraints on TEXT enum columns (`sex`, `sex_rule`). Commit: `b565e8f`
- **R10-007** (LOW) — Added `CHECK(id = 'singleton')` on `assignment_settings` + fixed store to use fixed id. Commit: `22af4d5`
- **R10-008** (LOW) — Added partial unique index on `slip_delivery` for active deliveries. Commit: `a0f48a7`
- **R11-002** (MEDIUM) — Replaced silent `Sesso.M` fallback with `error()` for impossible state. Commit: `4fdd67a`
- **R11-003** (LOW) — Replaced silent `SexRule.STESSO_SESSO` fallback with `error()` for impossible state. Commit: `4fdd67a`
- **SA-002** (MEDIUM) — Added coroutine `Mutex` to `AutoAssegnaProgrammaUseCase` per spec requirement. Commit: `c372e3b`

### Batch architetturale (2026-03-18)

- **R7-009** (LOW) — Moved print DTOs from infrastructure to application layer. Commit: `da35e0d`
- **R7-001** (MEDIUM) — Introduced `ProgramRenderer`/`AssignmentsRenderer` interfaces to remove application→infrastructure imports. Commit: `d88c2fb`
- **R7-002** (MEDIUM) — Moved `dateFormatter`/`formatMonthYearLabel` to `core/formatting`. Commit: `7423a24`
- **R7-014** (LOW) — Renamed `GeneraImmaginiAssegnazioni` → `GeneraImmaginiAssegnazioniUseCase`. Commit: `2c44527`
- **R9-001** (MEDIUM) — `SalvaImpostazioniAssegnatoreUseCase`: return `Either<DomainError, Unit>`. Commit: `a855dd2`
- **R9-002** (MEDIUM) — `AutoAssegnaProgrammaUseCase`: return `Either<DomainError, AutoAssignProgramResult>`. Commit: `865381b`
- **R11-005** (MEDIUM) — Standardized defensive pattern to `error()` for impossible DB states. Commit: `7d52413`
- **R10-009** (LOW) — Added 52-week time window to `allAssignmentRankingData` query. Commit: `a914ff5`
- **R8-014** (LOW) — Made eligibility persistence atomic with batch transaction. Commit: `2f6430d`
- **R9-003** (LOW) — Changed use case DI from `single` to `factory` (except `AutoAssegnaProgrammaUseCase` — Mutex). Commit: `c80edf3`
- **R10-002** (LOW) — Moved to debito accettato: `insertWeekPlan` test-only query, stessa logica di R10-003

### Batch R12-001 — Either.catch → runInTransactionEither (2026-03-18)

- **R12-001** (MEDIUM) — Replaced `Either.catch { runInTransaction {} }.mapLeft { ... }` with `runInTransactionEither` across 20 use cases in 5 features. 5 parallel worktrees, all merged with `--no-ff`. Commits: people `3ccc1a6`, assignments `4623cb8`, programs `d9168bb`, schemas `1620cbc`, weeklyparts `d8ad560`. Tests updated: `DomainErrorMappingAssignmentsUseCaseTest`, `PersonPickerViewModelTest`, `AssignmentManagementViewModelTest`, `ArchivaAnomalieSchemaUseCaseTest`, `RiordinaPartiUseCaseTest` (error message assertions aligned to `runInTransactionEither` wrapping).

### Batch finale — test gaps + spec (2026-03-18)

- **R8-016** (LOW) — Added 32-test suite for `PartEditorViewModel`. Commit: `bf6cf4d`
- **R8-017** (LOW) — Added 22-test suite for `PersonPickerViewModel`. Commit: `fd6abf6`
- **R8-018** (LOW) — Added 34-test suite for `ProclamatoriListViewModel`. Commit: `37e2b18`
- **SA-003** (LOW) — Spec 002 FR-006 updated: `CercaTipiParteUseCase` loads all, filtering is client-side. Commit: `b738323`
- **SA-004** (LOW) — Spec 002 Key Entities updated: added `snapshot` and `partTypeRevisionId` to WeeklyPart. Commit: `71bbdea`

### Batch round 15 (2026-04-10)

- **R15-001** (HIGH) — Aggiunta guardia chirurgica in `ImportaSeedApplicazioneDaJsonUseCase.persistHistoricalAssignment`: se l'aggregato esistente ha `programId != null`, raise del nuovo `DomainError.ImportConflittoProgrammaEsistente` (messaggio italiano in `toMessage()`). Impedisce che l'import storico alleghi parti spurie a settimane di programmi reali quando il DB ha programmi ma 0 studenti. Test di regressione aggiunto. Commit: `a46ed5b`. Worktree: `fix/finding-r15-001` (merged `6ec5ba9`).
- **R15-002** (HIGH) — Refactor di `RimuoviAssegnazioneUseCase` attraverso `WeekPlanAggregate`. Aggiunti: (a) query SQL `weekPlanIdByAssignmentId` con JOIN assignment/weekly_part; (b) `AssignmentRepository.findWeekPlanIdByAssignmentId` + impl in `SqlDelightAssignmentStore`; (c) `WeekPlanAggregate.removeAssignment(id)` con guardia `canBeEditedManually()`. Use case ora: load weekPlanId → load aggregate → removeAssignment → saveAggregate, tutto dentro `runInTransactionEither`. DI aggiornata (3 dipendenze). Nuovo test `RimuoviAssegnazioneUseCaseTest` con 4 casi inclusa la regressione SKIPPED. Commit: `2a28836`. Worktree: `fix/finding-r15-002` (merged `dbdd704`).
- **R15-003** (MEDIUM) — `ImpostaStatoSettimanaUseCase`: la guardia passato-immutabile si applica simmetricamente a qualunque transizione di status (`aggregate.weekPlan.status != status`) con check diretto `weekStartDate < currentMonday` (non via `canBeMutated` per evitare il caso circolare SKIPPED→ACTIVE su settimana futura). No-op (stesso status) passa senza guardia. Test estesi a 7 casi inclusa la regressione SKIPPED→ACTIVE passato. Commit: `0a773f4`. Worktree: `fix/finding-r15-003` (merged `80af9a3`).
- **R15-004** (MEDIUM) — `WeekPlanAggregate.addAssignment` ora verifica `canBeEditedManually()` come primo statement, coerente con `addPart/removePart/reorderParts/replaceParts`. 2 test aggiunti (rifiuto SKIPPED + happy path su ACTIVE passato). Commit: `652b59e` (nel worktree di R15-002, merged `dbdd704`).
- **R15-009** (LOW) — Risolto collaterale: il rewrite di `RimuoviAssegnazioneUseCase` in R15-002 usa `either {}` invece del vecchio `Either.Right(...)` inline.
- **R15-010** (HIGH) — `WeekPlanAggregate.clearAssignments()` convertita a `Either<DomainError, WeekPlanAggregate>` con guardia `canBeEditedManually()` (parallelo di R15-002 sul bulk-clear). `RimuoviAssegnazioniSettimanaUseCase.invoke` riscritta in stile idiomatico `runInTransactionEither { either { … .bind() } }` con `loadAggregateByDate` dentro la transazione. Test di regressione SKIPPED→`SettimanaImmutabile` aggiunti sia su `WeekPlanAggregateTest` che su `RimuoviAssegnazioniSettimanaUseCaseTest`. Commit: `a661720`. Worktree: `fix/finding-r15-010` (merged `c6946c9`). Full suite: 441 test, 0 fallimenti.

### Batch round 14 (2026-04-10)

- **R14-001** (HIGH) — Rimosso branch logicamente irraggiungibile in `ImpostaStatoSettimanaUseCase.kt:30-32`. La guardia combinava `!canBeEditedManually()` e `status != SKIPPED` che sono mutuamente esclusive con 2 soli valori di `WeekPlanStatus`. Commit: `4a5de22`. Worktree: `fix/finding-r14-001` (merged).
- **R14-002** (HIGH) — Rimossa mutazione silenziosa di date future nell'import seed. `normalizeHistoricalAssignmentDate` eliminato; `validateLastAssignments` ora rifiuta le date future con `DomainError.ImportContenutoNonValido` ("data nel futuro ... non ammessa per dati storici"). Test aggiornato (`future ultimaParte date is rejected with ImportContenutoNonValido`). Il fix è stato riapplicato sopra il refactor `ultimaParte` singleton→list (`bc5e65d`) dopo che il branch originario `fix/finding-r14-002` è diventato non mergiabile; branch orfano scartato.
- **R14-003** (MEDIUM) — Rimosso parametro `referenceDate` morto da `WeekPlanAggregate.addPart/removePart/reorderParts/replaceParts` e dai 4 use case che lo propagavano (`AggiungiParte`, `RimuoviParte`, `RiordinaParti`, `AggiornaPartiSettimana`) e dal privato `applyRefreshCandidate` in `AggiornaProgrammaDaSchemi`. `AggiornaProgrammaDaSchemi.invoke` mantiene `referenceDate` perché filtra le settimane (`week.weekStartDate < referenceDate`). Commit: `51dfb4d`. Worktree: `fix/finding-r14-003` (merged). 13 file, -52/+19.

### Batch round 16 (2026-04-13) — 4 worktree paralleli, 11 finding low/medium

**WT-A — Cleanup ImportaSeed + ImpostaStato** (commit `aea6851`, merge `66f601b`):
- **R14-005** (LOW) — `ImportaSeedApplicazioneDaJsonUseCase.kt`: `catch (_: Exception)` sostituito con catch specifici (`SerializationException`, `DateTimeParseException`).
- **R14-006** (LOW) — `ImportaSeedApplicazioneDaJsonUseCase`: `referenceDate: LocalDate` ora parametro esplicito del `invoke()`. Test usano data fissata, no più `LocalDate.now()` flaky.
- **R14-009** (LOW) — `context(_: TransactionScope)` anonimo → `context(tx: TransactionScope)` per consistency con il resto del codebase.
- **R15-005** (LOW) — Rimosso parametro `slot` morto da `validateLastAssignments`/`persistHistoricalAssignment`; inlinato `slot = 1` con commento esplicativo che documenta il limite del seed schema attuale (no `ruolo` campo).
- **R15-006** (LOW) — Aggiunto commento di blocco su `persistHistoricalAssignment` che dichiara la giustificazione del bypass di `addPart` per i fragment storici (la guardia `programId != null` di R15-001 è il vero gate di sicurezza).
- **R15-007** (LOW) — `ImpostaStatoSettimanaUseCase`: `referenceDate: LocalDate` ora parametro esplicito (no default `LocalDate.now()`); chiamanti aggiornati (`DiagnosticsViewModel`, `PartEditorViewModel`).

**WT-B — Cleanup GeneraSettimaneProgrammaUseCase** (commit `9fc8579`, merge `690b70d`):
- **R14-007** (LOW) — Blocco transazione riscritto in stile idiomatico `runInTransactionEither { either { ... } }`, eliminato `Either.Right(Unit)` finale.
- **R14-008** (LOW) — Aggiunto commento di blocco prima di `AssignmentRestoreKey` che spiega la strategia di restore posizionale `(partType, occurrenceIndex, slot)` durante schema refresh.
- **R16-008** (NUOVO, MEDIUM) — Emerso durante la review post-fix: `snapshotAssignmentsByRestoreKey:180` usa `error()` invece di `raise(DomainError)`. Tracciato in "Findings aperti", non fixato in WT-B per non allargare lo scope.

**WT-C — Cleanup program sidebar** (commit `6d27ccf`, merge `42c3fef`):
- **R16-001b** (HIGH) — `ProgramLifecycleViewModel.loadProgramsAndWeeks`: `runCatching { loadProgramSidebarStates(programs) }.getOrElse { emptyMap() }` sostituito con `Either.catch { ... }.fold(...)` che emette un `errorNotice` esplicito invece di mascherare silenziosamente il fallimento.
- **R16-004** (LOW) — Composable `programSidebarIndicator` rinominato `ProgramSidebarIndicator` (PascalCase per @Composable convention).
- **R16-005** (LOW) — `ProgramSidebarState` data class con 3 campi inutilizzati collassata a `ProgramSidebarStatus` enum singolo; funzione `calculateProgramSidebarState` rinominata `calculateProgramSidebarStatus`. 4 test aggiornati.

**WT-D — Refactor admin catalog con use cases** (commit `f3f5322`, merge `7adc84f`):
- **R16-001** (HIGH) — `PartTypeCatalogViewModel` + `WeeklySchemaCatalogViewModel`: eliminato anti-pattern `runCatching { store.x() }.fold(...)` sostituendolo con `executeAsyncOperation` + thin throwing use cases. Pattern coerente con `CercaTipiParteUseCase`.
- **R16-003** (HIGH) — Eliminato layer skipping VM→Store. Introdotti due nuovi use case nell'application layer: `CaricaCatalogoTipiParteUseCase` (`feature/weeklyparts/application`) e `CaricaCatalogoSchemiSettimanaliUseCase` (`feature/schemas/application`, contiene `data class CatalogoSchemiSettimanali` per la composizione cross-feature templates+partTypes).
- **R16-006** (LOW) — Nuovo componente `AdminReadonlyListRow` in `AdminCatalogComponents.kt`. `WeeklySchemaCatalogScreen` non usa più `AdminSelectionItem(selected=false, onClick={})` per le righe di sola lettura del dettaglio settimana — eliminata l'ambiguità semantica.
- **R16-007** (LOW) — Eliminato `detailByWeek: mutableMapOf` da `WeeklySchemaCatalogViewModel`. La cache è ora `cachedDetails: Map<LocalDate, WeeklySchemaDetail>` dentro `WeeklySchemaCatalogUiState`, immutabile e snapshottabile come il resto dello state.

---

## Verifiche eseguite

| Data | Comando | Test totali | Fallimenti |
|------|---------|-------------|------------|
| 2026-03-12 | `./gradlew :composeApp:jvmTest` | full suite | 0 |
| 2026-03-12 | post-merge batch 1 | full suite | 0 |
| 2026-03-12 | post-merge batch 2 | full suite | 0 |
| 2026-03-12 | post-merge round 3 (MEDIUM-018/019) | full suite | 0 |
| 2026-03-12 | post-merge round 4 (MEDIUM-021/022) | full suite | 0 |
| 2026-03-13 | post-merge round 5 (UPD-M01/UPD-M02) | full suite | 0 |
| 2026-03-18 | review round 6 (update feature) | update tests | 0 |
| 2026-03-18 | post-fix UPD-M03 (worktree) | full suite | 0 |
| 2026-03-18 | post-merge batch UPD-H01/M05/M06/L01/L02 | full suite | 0 |
| 2026-03-18 | review round 7-9 (core, VMs, cross-cutting) | analisi statica | — |
| 2026-03-18 | review round 10-11 (.sq, row mappers, stores) | analisi statica | — |
| 2026-03-18 | review round 12 (spec alignment, 7 spec) | analisi statica | — |
| 2026-03-18 | post-merge batch low-effort (4 worktree: dead-code, error-handling, vm-fixes, spec-align) | full suite | 0 |
| 2026-03-18 | post-merge batch impatto-rapido (3 worktree: sql-constraints, parser-fallbacks, auto-assign-mutex) | full suite | 0 |
| 2026-03-18 | post-merge batch architetturale (4 worktree: layer-inversions, use-case-either, minor-patterns, di-factory) | full suite | 0 |
| 2026-03-18 | post-merge batch finale (4 worktree: spec-align-2, test-part-editor, test-person-picker, test-proclamatori) | full suite | 0 |
| 2026-03-18 | review round 13: 5-round full-codebase scan (5 parallel agents) | analisi statica | — |
| 2026-03-18 | post-merge batch R12-001 (5 worktree: people, assignments, programs, schemas, weeklyparts) | full suite | 0 |
| 2026-04-10 | review round 14 (post-commit 983c623/d6f0b38/4e9d486: historical import, past editing, seed tooling) | analisi statica | — |
| 2026-04-10 | post-merge batch R14-001 + R14-003 (2 worktree, R14-002 pending refactor) | 432 test | 0 |
| 2026-04-10 | review round 15 (deep scan import storico + edit passato, focus su `983c623`) | analisi statica | — |
| 2026-04-10 | post-merge batch R15-001 + R15-002 + R15-003 + R15-004 (3 worktree paralleli, 4 finding) | 438 test | 0 |
| 2026-04-10 | post-merge R15-010 (worktree isolato, parallelo di R15-002) | 441 test | 0 |
| 2026-04-13 | review round 16 (post-commit admin catalog + program sidebar + ranking) | analisi statica | — |
| 2026-04-13 | post-merge batch round 16 (4 worktree: WT-A import-seed, WT-B GeneraSettimane, WT-C program-sidebar, WT-D admin-catalog) | 465 test | 0 |

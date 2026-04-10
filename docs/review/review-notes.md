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

---

## Findings aperti

### HIGH

**R15-001 (HIGH)** — `ImportaSeedApplicazioneDaJsonUseCase.kt:106-109,396-431`: la precondizione di
import controlla solo `proclamatoriQuery.cerca()` — se gli studenti sono vuoti l'import procede,
ma non verifica che siano vuoti anche `program_month` / `week_plan`. Scenario: l'utente crea
manualmente un programma (genera weeks reali con `programId` valorizzato), poi cancella tutti gli
studenti, poi rilancia l'import seed. `persistHistoricalAssignment` chiama
`weekPlanStore.loadAggregateByDate(weekStartDate)` e — se la data storica casca dentro un programma
reale — entra nel branch `existingAggregate != null`, fabbricando un nuovo `WeeklyPart` storico
attaccato a una settimana di programma reale e salvando l'aggregato modificato. La settimana reale
si vede arricchita di parti spurie. Causa strutturale: l'invariante "import = bootstrap su DB
vergine" è dichiarato solo su `proclamatori`, non sull'intero stato persistente. Fix: estendere la
precondizione anche a `weekPlanStore` / `programStore`, oppure dentro `persistHistoricalAssignment`
rifiutare il caso `existingAggregate != null && existingAggregate.weekPlan.programId != null`.
Effort: 20m.

**R15-002 (HIGH)** — `RimuoviAssegnazioneUseCase.kt:12-15`: rimuove l'assegnazione direttamente via
`assignmentStore.remove(assignmentId)` **bypassando completamente `WeekPlanAggregate`**. Nessun
load dell'aggregato, nessuna verifica di `canBeEditedManually()` né dello status `SKIPPED`.
Tutti gli altri use case mutanti (`AggiungiParte`, `RimuoviParte`, `RiordinaParti`, `AssegnaPersona`)
passano per l'aggregato che enforce le invarianti. Conseguenza: una settimana `SKIPPED` (o
qualsiasi altro stato futuro che marchi una settimana come immutabile) accetta comunque la
rimozione di assegnazioni. Causa strutturale: manca un metodo `WeekPlanAggregate.removeAssignment`
che faccia da pendant a `addAssignment`, quindi il use case scorciatoia il livello aggregate.
Fix: aggiungere `WeekPlanAggregate.removeAssignment(assignmentId)` con guardia status, e routare
il use case attraverso load → mutate → save. Effort: 30m.

### MEDIUM

**R15-003 (MEDIUM)** — `ImpostaStatoSettimanaUseCase.kt:25-28`: la guardia
`canBeMutated(currentMonday)` viene applicata **solo** quando `status == SKIPPED`. La transizione
inversa (riattivazione `SKIPPED → ACTIVE`) non ha alcun controllo: una settimana SKIPPED nel
passato può essere riattivata e — siccome `canBeEditedManually()` ignora la data — diventa di
nuovo completamente mutabile (parti, assegnazioni). Asimmetria semantica vs il path di skip.
Decisione richiesta: o si blocca anche la riattivazione passata in modo simmetrico, o si
documenta esplicitamente che la riattivazione bypassa l'invariante "passato immutabile" by design.
Effort: 15m (fix) o 5m (commento esplicativo + nota in spec 003).

**R15-004 (MEDIUM)** — `WeekPlanAggregate.addAssignment:105-116`: a differenza di `addPart`,
`removePart`, `reorderParts` e `replaceParts`, `addAssignment` **non verifica**
`canBeEditedManually()`. Se un use case caricasse e mutasse un aggregato `SKIPPED`, l'aggiunta di
un'assegnazione passerebbe (la `validateAssignment` controlla solo part/slot/persona). Oggi
`AssegnaPersonaUseCase.kt:55` non controlla lo stato a monte (delega all'aggregato), quindi una
settimana SKIPPED può ricevere nuove assegnazioni via use case standard. Causa strutturale:
l'invariante "settimana immutabile" non è completa lato aggregato per le assegnazioni. Fix:
aggiungere `if (!weekPlan.canBeEditedManually()) return DomainError.SettimanaImmutabile.left()`
all'inizio di `addAssignment`. Effort: 5m + test.

**R14-004 (MEDIUM)** — ~~`ImportaSeedApplicazioneDaJsonUseCase.persistHistoricalAssignment:393-439`:
frammentazione delle parti storiche~~ — **debito accettato** (2026-04-10)

Motivazione: `LETTURA_DELLA_BIBBIA` e `DISCORSO` hanno `peopleCount=1` quindi non possono
frammentarsi. Per le parti a 2 slot (`INIZIARE_CONVERSAZIONE`, `COLTIVARE_INTERESSE`,
`FARE_DISCEPOLI`) il secondo slot è `assistente` e lo storico frammentato in due parti
separate è semanticamente accettabile (conduttore e assistente sono ruoli distinti, non
equivalenti). Nessuna azione.

### LOW

**R14-005 (LOW)** — `ImportaSeedApplicazioneDaJsonUseCase.kt:169,337`: `catch (_: Exception)`
troppo generico. A riga 169 catturare `SerializationException`, a riga 337 `DateTimeParseException`.
Catturare `Exception` nasconde errori di programmazione (OOM, NPE). Effort: 5m.

**R14-006 (LOW)** — `ImportaSeedApplicazioneDaJsonUseCase.validateLastAssignments`: la rejezione
`date.isAfter(LocalDate.now())` e il test che usa `LocalDate.now().plusYears(1)` fanno entrambi
riferimento a `LocalDate.now()` → test potenzialmente flaky se la chiamata attraversa la
mezzanotte. Il use case non accetta un `Clock`. Fix: iniettare un `Clock` nel use case o rendere
`referenceDate` parametro esplicito. Effort: 15m.

**R14-007 (LOW)** — `GeneraSettimaneProgrammaUseCase.kt:100-104`: stile misto nel blocco di
transazione. Usa `Either.Right(Unit)` finale invece di wrappare in `either { ... }` come altri
use case. Inconsistenza stilistica minore, leggibilità. Effort: 2m.

**R14-008 (LOW)** — `GeneraSettimaneProgrammaUseCase.kt:140-210`: logica `AssignmentRestoreKey` +
`associateByRestoreKey` + `flatMapValuesPerSlot` non auto-esplicativa. Aggiungere un commento di
blocco che spieghi: "restore assignments by (partType, occurrenceIndex, slot) positional key across
schema refresh". Effort: 10m.

**R14-009 (LOW)** — `ImportaSeedApplicazioneDaJsonUseCase.kt:393`: `context(_: TransactionScope)`
anonimo, convenzione altrove (`AssignmentStore.kt:18-23`) usa `context(tx: TransactionScope)`.
Consistency issue. Effort: 1m.

**R15-005 (LOW)** — `ImportaSeedApplicazioneDaJsonUseCase.kt:363,393-407`: lo `slot` è hard-coded
a `1` in `validateLastAssignments` ma poi propagato come parametro fino a `Assignment.of`. Tutte
le assegnazioni storiche importate finiscono come slot 1 (= conduttore). Conseguenza:
`SqlDelightAssignmentStore.lastSlot1GlobalAssignmentPerPerson` (usata dal cooldown per capire se
l'ultima assegnazione era da conduttore) include rumore — uno studente che storicamente faceva
solo da assistente viene trattato come ex-conduttore, e il `lastConductorWeeks` ne risulta
sfalsato. Fix minimo: rimuovere il parametro morto e inlinare `slot = 1` con commento esplicativo;
fix completo: estendere lo schema JSON con `ruolo: conduttore|assistente` e mappare lo slot
correttamente. Effort: 5m (cleanup) o 30m (estensione schema + test).

**R15-006 (LOW)** — `ImportaSeedApplicazioneDaJsonUseCase.persistHistoricalAssignment:413-431`:
quando `existingAggregate != null` il codice fa `existingAggregate.copy(weekPlan = ... + newPart)`
direttamente, bypassando `WeekPlanAggregate.addPart` (che enforce `canBeEditedManually()`). Per i
fragment storici creati ex-novo dall'import il bypass è intenzionale, ma combinato con R15-001
diventa il vettore concreto: una volta che il branch entra perché un programma reale esiste già,
si arriva ad attaccare parti a una settimana che potrebbe anche essere `SKIPPED`. Aggiungere un
commento di blocco che dichiari la giustificazione del bypass, oppure introdurre un metodo
`addHistoricalPart` esplicito sull'aggregato che documenti l'eccezione. Effort: 5m (commento) o
20m (metodo dedicato).

**R15-007 (LOW)** — `ImpostaStatoSettimanaUseCase.kt:21`: `referenceDate: LocalDate = LocalDate.now()`
ha lo stesso footgun di R14-006 (clock implicito al call site, test potenzialmente flaky a
mezzanotte). Soluzione coerente: iniettare un `Clock` o forzare i caller a passare la data. Già
catalogato come pattern in R14-006 — aggregare i due fix in un singolo passaggio "Clock injection
across use cases con dipendenza temporale". Effort: 10m.

**R15-008 (LOW)** — `ImportaSeedApplicazioneDaJsonUseCase.invoke:139-154` + `persistHistoricalAssignment`:
l'intero import gira in una singola transazione monolitica e per ogni assegnazione storica esegue
`loadAggregateByDate` + `saveAggregate` separato. Per N studenti × K parti storiche le round-trip
DB scalano O(N·K). Per i seed attuali (decine di studenti, 1-3 parti ciascuno) è irrilevante, ma
manca il batching o un upper bound. Considerare di raggruppare per `weekStartDate` e fare un
unico `saveAggregate` per settimana. Effort: 1h.

**R15-009 (LOW)** — `RimuoviAssegnazioneUseCase.kt:14`: `Either.Right(assignmentStore.remove(...))`
inline invece di `either { ... }`. Stesso pattern stilistico di R14-007 (`GeneraSettimaneProgramma`).
Da unificare quando si tocca il file per il fix di R15-002. Effort: 1m.

**R14-010 (LOW)** — `CaricaUltimeAssegnazioniPerParteProclamatoreUseCase`: wrapper triviale (6 righe
effettive) intorno a `PersonAssignmentHistoryQuery.lastAssignmentDatesByPartType`. Discutibile come
debito: il layer use case è un contract boundary, quindi tenerlo è difendibile; in alternativa,
il ViewModel potrebbe iniettare direttamente la query. Nessuna azione obbligatoria, solo
osservazione.

### Debito accettato

- MEDIUM-020 — DiagnosticsViewModel I/O diretto: utility screen senza logica di dominio, debito accettato
- R10-003 — `findProgramByYearMonth` / `deleteWeekPlan` usate solo dal seed CLI: debito accettato, tooling code
- R10-002 — `insertWeekPlan` usata solo da test: debito accettato, stessa logica di R10-003 (tooling/test code)
- R10-010 — Date comparisons basate su ordinamento stringhe ISO-8601: by design, SQLite non ha tipo DATE nativo

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

### Batch round 14 (2026-04-10)

- **R14-001** (HIGH) — Rimosso branch logicamente irraggiungibile in `ImpostaStatoSettimanaUseCase.kt:30-32`. La guardia combinava `!canBeEditedManually()` e `status != SKIPPED` che sono mutuamente esclusive con 2 soli valori di `WeekPlanStatus`. Commit: `4a5de22`. Worktree: `fix/finding-r14-001` (merged).
- **R14-002** (HIGH) — Rimossa mutazione silenziosa di date future nell'import seed. `normalizeHistoricalAssignmentDate` eliminato; `validateLastAssignments` ora rifiuta le date future con `DomainError.ImportContenutoNonValido` ("data nel futuro ... non ammessa per dati storici"). Test aggiornato (`future ultimaParte date is rejected with ImportContenutoNonValido`). Il fix è stato riapplicato sopra il refactor `ultimaParte` singleton→list (`bc5e65d`) dopo che il branch originario `fix/finding-r14-002` è diventato non mergiabile; branch orfano scartato.
- **R14-003** (MEDIUM) — Rimosso parametro `referenceDate` morto da `WeekPlanAggregate.addPart/removePart/reorderParts/replaceParts` e dai 4 use case che lo propagavano (`AggiungiParte`, `RimuoviParte`, `RiordinaParti`, `AggiornaPartiSettimana`) e dal privato `applyRefreshCandidate` in `AggiornaProgrammaDaSchemi`. `AggiornaProgrammaDaSchemi.invoke` mantiene `referenceDate` perché filtra le settimane (`week.weekStartDate < referenceDate`). Commit: `51dfb4d`. Worktree: `fix/finding-r14-003` (merged). 13 file, -52/+19.

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

# Review Notes — Ministero del Regno Planner

Prompt sorgente: 5x review-codebase su 5 feature slice (people, weeklyparts, programs,
schemas, print+assignments), 2026-03-12.
Review round 13: 5-round full-codebase scan (people, assignments+output, weeklyparts+programs+schemas,
updates+diagnostics+core, test+SQL schema), 2026-03-18.

---

## Findings aperti

Nessun finding aperto.

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

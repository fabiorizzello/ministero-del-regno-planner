# Review Notes — ministero-del-regno-planner

Review aggiornato dopo le correzioni applicate in questa sessione.

## Punti ancora aperti

### PERF-1 — `AutoAssegnaProgrammaUseCase` N+1 SQL per auto-assign — P2
Per ogni slot vuoto, `suggerisciProclamatori` esegue ~7 query SQL in `transactionWithResult`.
Per un programma completo (~40 slot) = ~280 query. Potenzialmente 5-10s con 84 proclamatori.
Fix: pre-calcolare ranking globale/per-tipo una volta sola e passarlo al loop.
Da profilare prima di ottimizzare.

### ~~GAP-1 — Storico assegnazioni (spec 005, US5)~~ — RIMOSSO DALLA SPEC
Feature eliminata per decisione: US5, FR-010 e `PersonAssignmentHistory` rimossi da
spec 005. `ContaAssegnazioniPersonaUseCase` (conteggio semplice per la lista persone)
non è parte dello storico e rimane.

### ~~GAP-2 — Dialogo eliminazione: testo "azione irreversibile" mancante~~ — CHIUSO
Aggiunto "Questa azione e' irreversibile." in `ProclamatoriScreen` (single e batch delete)
e in `ProgramWorkspaceScreen` (Elimina mese dialog). `buildDeleteProgramImpactMessage`
invariato — il testo aggiunto nel composable `text` block con `Column` + `Text` separato.

### ~~GAP-3 — Batch delete senza conteggio assegnazioni~~ — CHIUSO
`ProclamatoriListUiState` ora porta `batchDeleteAssignmentCount: Int`. Il metodo
`requestBatchDeleteConfirm()` calcola il totale assegnazioni con `sumOf { contaAssegnazioni(id) }`
prima di mostrare la dialog — simmetrico con la delete singola.

### Minor — `StampaProgrammaUseCase` throws invece di Either — P3
Lancia eccezione non gestita su programma non trovato invece di `Left(DomainError)`.

### ~~Minor — `ProgramLifecycleViewModel` accede a `WeekPlanStore` direttamente~~ — CHIUSO
Il `deleteImpactConfirm` è calcolato dalla state in memoria (`selectedProgramWeeks.size`,
`selectedProgramAssignments.values.sumOf`), non via store. L'unica chiamata diretta è
`weekPlanStore.listByProgram` per caricare le settimane in stato. Nessuna logica di business
aggiunta — accettato come pattern UI (P4, nessuna azione).

## Review approfondita — Cicli 1–2 (DRY, SOLID, domain logic, parse-don't-validate)

### ~~Aperto — DOMAIN-001 — `WeekPlan.canBeMutated()` mai invocato dai use case~~ — CHIUSO
`canBeMutated(referenceDate)` aggiunto come guard in `AggiungiParteUseCase` e
`RimuoviParteUseCase` con `referenceDate: LocalDate = LocalDate.now()`. Il gate calcola
`currentMonday = referenceDate.previousOrSame(MONDAY)` per coerenza con il gate UI in
`PartEditorViewModel`. Se `!weekPlan.canBeMutated(currentMonday)` → `DomainError.Validation`
prima di qualsiasi mutazione SQL. Il gate UI rimane per UX immediata senza roundtrip.

### ~~Aperto — TYPE-001 — `WeekPlan.programId: String?` invece di `ProgramMonthId?`~~ — CHIUSO
`WeekPlan.programId`, `WeekPlanStore` (tutti i metodi `*ByProgram`/`saveWithProgram`),
`AssignmentRepository.deleteByProgramFromDate`/`countByProgramFromDate`, i relativi use case
(`GeneraSettimane`, `AggiornaDaSchemi`, `AutoAssegna`, `Svuota`, `Stampa`, `Elimina`)
e le ViewModel (`ProgramLifecycleViewModel.selectedProgramId`, `AssignmentManagementVM`,
`SchemaManagementVM.impactedProgramIds`) ora usano tutti `ProgramMonthId`.
SQL invariato (`program_id TEXT`); boundary Kotlin-SQL: `.value` in input, `ProgramMonthId(it)` in output.

### ~~Aperto — SPEC-NEBULA-001 — `SexRule.STESSO_SESSO` vs comportamento~~ — CHIUSO
Decisione utente: `LIBERO` non esiste e non deve esistere nemmeno nei commenti o nella spec.
`STESSO_SESSO` rimane con il comportamento attuale come definitivo: non filtrante in
assegnazione manuale (solo annotation visiva), soft-hard in auto-assign (esclude con mismatch
se esiste un candidato senza). KDoc aggiornato in `SexRule.kt` a riflettere questa scelta.

### ~~Aperto — SOLID-ISP-001 — `WeekPlanStore` god interface~~ — CHIUSO
Estratta `WeekPlanQueries` (5 metodi read-only: `findByDate`, `findByDateAndProgram`,
`listByProgram`, `listInRange`, `totalSlotsByWeekInRange`). `WeekPlanStore extends WeekPlanQueries`
con defaults per `findByDateAndProgram` e `listByProgram` (backward compat test doubles).
7 use case aggiornati a iniettare `WeekPlanQueries`: `CaricaSettimana`, `SuggerisciProclamatori`,
`AssegnaPersona`, `CaricaAssegnazioni`, `RimuoviAssegnazioniSettimana`, `AutoAssegna`,
`StampaProgramma`. Koin binding aggiunto: `single<WeekPlanQueries> { get<WeekPlanStore>() }`.
`SqlDelightWeekPlanStore` invariato. Test esistenti invariati.

## Review approfondita — Cicli 3–5 (SuggerisciProclamatori, dead fields, spec nebulos)

### Aperto — SPEC-NEBULA-002 — `sexMismatch` soft in manuale, hard in auto-assign — P2
`SuggerisciProclamatoriUseCase` annota `sexMismatch = true` ma non filtra (comportamento
soft per l'utente manuale). `AutoAssegnaProgrammaUseCase` usa `firstOrNull { !it.sexMismatch
&& !it.inCooldown }` — trattandolo come filtro hard. La spec 005 dice `STESSO_SESSO` è
"non filtrante" ma non specifica il comportamento in auto-assign. Decisione necessaria:
uniformare (a) soft in entrambi i contesti, o (b) hard in auto (comportamento attuale, più
conservativo). Aggiungere nota in spec 005.

### ~~Aperto — TECH-DEBT-001 — `programId: String` non tipato (~20 occorrenze)~~ — CHIUSO
Chiuso insieme a TYPE-001 nella stessa sessione. Vedi TYPE-001.

### ~~Aperto — TECH-DEBT-002~~ — CHIUSO
`AssignmentWithPerson` ora porta `val proclamatore: Proclamatore` invece di
`firstName/lastName/sex` esplosi. `fullName` e `sex` rimangono come proprietà delegate
(`proclamatore.nome/cognome/sesso`). Mapper `AssignmentRowMapper` aggiornato;
test aggiornati. Tutti i consumer erano già su `.fullName` — zero breaking changes.

## Punti chiusi — Review approfondita Ciclo 3–5

- **CLARITY-001 — `slot <= 1` → `slot == 1`**: Tre occorrenze in `SuggerisciProclamatoriUseCase`
  usavano `<=` invece di `==` per determinare ruolo conduttore. Il dominio garantisce
  `slot >= 1` (invariante `Assignment.init`), quindi `<= 1` era equivalente ma fuorviante.
  Sostituito con `== 1` per allineamento esplicito alla spec 005.
- **MAGIC-001 — `COOLDOWN_PENALTY = 10_000` named constant**: il valore era inlined in
  `weightedScore()`. Estratto come `const val COOLDOWN_PENALTY` in `AssignmentSettings.kt`
  con riferimento alla spec 005 (US2, scenario 3). `SuggerisciProclamatoriUseCase` lo importa.
- **DEAD-001 — rimosse 4 dead fields da `SuggestedProclamatore`**: `lastGlobalDays`,
  `lastForPartTypeDays`, `lastGlobalInFuture`, `lastForPartTypeInFuture` erano calcolate
  nell'infrastruttura ma mai usate nella UI (solo `Before/AfterWeeks` sono usate in
  `AssignmentsComponents.kt`). Rimossi da domain model, store SQL e test.
- **SPEC-NEBULA-001 documentata — `SexRule.STESSO_SESSO`**: aggiunto KDoc all'enum con
  spiegazione dell'ambiguità naming vs. comportamento attuale, distinzione manuale/auto-assign,
  e TODO esplicito per risoluzione futura.

## Punti chiusi — Review approfondita Ciclo 1–2

- **DRY-002 — slot→role label centralizzato**: le 3 use case di output (`StampaProgrammaUseCase`,
  `GeneraPdfAssegnazioni`, `GeneraImmaginiAssegnazioni`) avevano ognuna `if (slot == 1) "Studente"
  else "Assistente"` inline. Estratta `slotToRoleLabel(slot: Int): String` nel domain model
  `Assignment.kt` (spec 005); le 3 use case ora la importano.
- **PARSE-001 — `Proclamatore.init` con vincoli max-length**: `ImportaProclamatoriDaJsonUseCase`
  validava `isBlank()` ma non il limite 100 caratteri (presente in `CreaProclamatoreUseCase`).
  Aggiunto `require(nome.length <= 100)` e `require(cognome.length <= 100)` nel blocco `init`
  di `Proclamatore` — ora nessuna path può creare un `Proclamatore` con nome/cognome troppo lungo
  indipendentemente da quale use case lo costruisce.
- **DRY-003 — `MAX_FUTURE_PROGRAMS = 2` unica fonte di verità**: la costante era duplicata come
  `private const val` in `CreaProssimoProgrammaUseCase`, `CaricaProgrammiAttiviUseCase` e
  `ProgramLifecycleViewModel`. Centralizzata in `ProgramStore.kt` come `const val` top-level;
  le 3 classi ora la importano.

## Punti chiusi in questa sessione (Cicli 1–5)

- **Ciclo 5 — `AssignmentManagementViewModel.confirmClearWeekAssignments` onSuccess incondizionale**:
  `executeAsyncOperation` è `suspend fun` che ingoia le eccezioni (non le rilancia). Il
  `onSuccess()` dopo di esso veniva sempre invocato anche in caso di errore (dialog chiuso
  + errore mostrato in schermata sbagliata). Introdotta flag locale `succeeded` e cambiato in
  `if (succeeded) onSuccess()`.
- **Ciclo 4** — nessun bug trovato; documentato PERF-1 (N+1 SQL auto-assign).

## Punti chiusi in questa sessione (Cicli 1–3)

- **Ciclo 3 — `AggiornaProgrammaDaSchemiUseCase` silent data-loss**: il `?: return` a line 142
  in `applyRefreshCandidate` causava che, se `findByDateAndProgram` restituiva null dopo
  `replaceAllParts`, le assegnazioni venivano cancellate ma non ripristinate e la transazione
  committava silenziosamente. Sostituito con `checkNotNull(...)` che lancia eccezione e forza
  il rollback dell'intera transazione.
- **Ciclo 2 — `AssegnaPersonaUseCase` exception-as-control-flow**: la guard
  `isPersonAssignedInWeek` era dentro `runInTransaction` con un `throw IllegalStateException`
  come meccanismo di controllo, causando rollback non necessari su validazioni ordinarie.
  Spostato il check PRIMA della transazione con `raise(DomainError.Validation(...))`.

## Punti chiusi in questa sessione (Ciclo 1)

- **N+1 eligibility queries in `SuggerisciProclamatoriUseCase`**: sostituito N loop individuali
  `eligibilityStore.listLeadEligibility(personId)` con 1 batch call
  `listLeadEligibilityCandidatesForPartTypes(setOf(part.partType.id))`. Risparmio O(N) query SQL
  per ogni richiesta suggerimenti.
- **FR-019 duplicata in `ProgramLifecycleViewModel.computeCreatableTargets`**: la regola di
  contiguità mesi era assente nel ViewModel UI (era già stata corretta nel UseCase). Aggiunta la
  guard `if (target == referenceMonth.plusMonths(2) && referenceMonth.plusMonths(1) !in existingByMonth)`.
  Test corrispondenti allineati (2 test con aspettative errate corretti).

## Punti chiusi e rimossi dalla review precedente

- Rimozione del modulo `feature/planning` e relativo wiring DI.
- Allineamento spec: nessun flag `attivo`, solo `sospeso` + hard delete.
- `AggiornaSchemiUseCase`: timestamp import salvato nella stessa transazione.
- `RimuoviParteUseCase`: remove + ricompattazione dentro transazione.
- `CreaSettimanaUseCase`: validazione lunedì convertita in `DomainError.Validation`.
- Naming SQL query persone: eliminato riferimento `allActiveProclaimers`.
- Rimosso flusso legacy `AggiornaDatiRemotiUseCase` con `RemoteDataSource`/`GitHubDataSource`.
- Utility CLI in `jvmMain`: decisione di mantenimento (non trattato come problema).
- `EliminaProgrammaUseCase`: naming allineato al comportamento `CURRENT/FUTURE` + commento cascade chiarito.
- `AggiornaProgrammaDaSchemiUseCase`: refactor con fase analisi unica, rimozione lookup O(N²), filtro esplicito sole settimane `ACTIVE`.
- Rimosso coupling `ProgramLifecycleViewModel` ↔ `SchemaManagementViewModel` (eliminata sincronizzazione `updateSelection`).
- `week_plan.status`: aggiunto `CHECK(status IN ('ACTIVE', 'SKIPPED'))` a livello schema SQL.
- `StampaProgrammaUseCase`: sostituito enum raw con label user-friendly (`Attiva`/`Saltata`).
- `part_type_revision` implementato end-to-end: `upsertAll` crea revisioni + aggiorna `current_revision_id`; `insertWeeklyPart` salva `part_type_revision_id`; `partsForWeek` fa LEFT JOIN su `part_type_revision` e popola `WeeklyPart.snapshot: PartTypeSnapshot?`; 4 use case passano il revision ID alla creazione delle parti.
- BUG-1 (`EliminaProgrammaUseCase`): rimosso loop N+1 `listByProgram + delete(id)`; sostituito con `deleteByProgram` (singola DELETE SQL con FK cascade su `weekly_part` e `assignment_weekly_part`).
- BUG-2 (`CreaProssimoProgrammaUseCase`): implementata regola FR-019 di contiguità mesi; `corrente+2` ora bloccato se `corrente+1` non esiste; test corretto da "allows" a "blocks" + aggiunto test positivo "allows when plus one exists".
- BUG-3 (`AggiornaSchemiUseCase`): date dei template ora validate con `raise(DomainError.Validation(...))` prima di entrare nella transazione, eliminando il `throw IllegalArgumentException` che sfuggiva alla gestione `Either`.

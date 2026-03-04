# Review Notes — ministero-del-regno-planner

Iterative code review against specs in `/specs/`. Single desktop user, single session.

---

## Ciclo 1 — Rilevamento iniziale

### CRITICO: Feature mancanti / gap di spec

#### 1. Campo `attivo` mancante — `Proclamatore` domain + DB
- **Spec 001 FR-001**: elenca `attivo` tra i campi del dominio
- **Spec 001 FR-011**: richiede `ImpostaStatoProclamatoreUseCase`
- **Realtà**: `person` table ha solo `suspended`/`can_assist`, nessun campo `active`. `Proclamatore.kt` non ha `attivo`.
- `ImpostaStatoProclamatoreUseCase` non esiste nel codebase.
- **Impatto**: abilitare/disabilitare un proclamatore (diverso da sospenderlo) non è implementato.
- **Nota**: potrebbe essere una semplificazione intenzionale — `sospeso` copre entrambi i casi. Ma la spec lo distingue esplicitamente.

#### 2. `ImpostaSospesoUseCase` standalone non esiste
- **Spec 001 FR-005**: richiede use case dedicato che ritorna `SospensioneOutcome(futureWeeksWhereAssigned)`
- **Realtà**: logica di sospensione è dentro `AggiornaProclamatoreUseCase.AggiornamentoOutcome` (campi equivalenti)
- Non è un bug funzionale ma la API devia dalla spec

#### 3. `AutoAssegnaProgrammaUseCase` — nessun mutex
- **Spec 005 FR-007**: "mutex per evitare esecuzioni parallele"
- **Realtà**: nessuna sincronizzazione. In un'app desktop single-user il rischio reale è basso (double-click), ma la spec lo richiede esplicitamente.
- Se eseguito in parallelo: `alreadyAssignedIds` è in-memory per ogni chiamata. Il check `isPersonAssignedInWeek` nel `AssegnaPersonaUseCase` cattura conflitti a livello DB ma genera errori invece di retry.

#### 4. Feature `planning` — implementata ma mai esposta in UI
- `CaricaPanoramicaPianificazioneFutura`, `GeneraAlertValidazioneAssegnazioni`, `PlanningOverviewModels`, etc. esistono e sono registrati in `PlanningModule.kt` (Koin).
- **Nessun ViewModel usa questi servizi**. `ViewModelsModule.kt` non include alcun VM che chiama `CaricaPanoramicaPianificazioneFutura`.
- **Nessuna route** in `AppScreen.kt` verso una planning screen.
- Spec non scritta per questa feature (confermato da memory).
- **Dead feature**: codice compilato, registrato in DI, ma non raggiungibile dall'utente.

---

### NAMING / SEMANTICS

#### 5. `SexRule.STESSO_SESSO` — nome ambiguo
- Rinominato da `LIBERO` nel branch corrente (commit "SexRule LIBERO→STESSO_SESSO")
- **Semantica attuale** (da `SuggerisciProclamatoriUseCase.kt`):
  - `UOMO`: hard filter — solo maschi
  - `STESSO_SESSO`: nessun filtro hard (`passaSesso = true`), ma genera `sexMismatch` soft warning se i sessi differiscono
- **Problema**: il nome `STESSO_SESSO` suggerisce "stesso sesso richiesto" (restrizione), mentre il comportamento è "stesso sesso preferito ma non obbligatorio"
- La vecchia spec usava `LIBERO` = "nessuna restrizione", il che è più chiaro. Il rename introduce ambiguità semantica.
- `PartTypeJsonParser.kt:14` mantiene backward compat: `if (value == "LIBERO") SexRule.STESSO_SESSO`

#### 6. `allActiveProclaimers` — query mal nominata
- `MinisteroDatabase.sq:149`: `SELECT * FROM person` senza filtro `suspended`
- Ritorna TUTTI i proclamatori inclusi i sospesi
- Usato per l'elenco proclamatori in `ProclamatoriListViewModel`
- Nome fuorviante; dovrebbe chiamarsi `allProclaimers` o aggiungere filtro se la separazione è intenzionale

---

### ORPHAN CODE / DEAD CODE

#### 7. `part_type_revision` table — infrastruttura DB inutilizzata
- Tabella `part_type_revision` presente nel DB schema con FK a `part_type` (CASCADE delete)
- Colonna `weekly_part.part_type_revision_id` punta a questa tabella (`ON DELETE SET NULL`)
- Colonna `part_type.current_revision_id` (TEXT NULL)
- **Nessun codice Kotlin** scrive o legge questa tabella (grep conferma: solo nello schema `.sq`)
- Sistema di revisioni pianificato ma non implementato. Peso DB senza beneficio.

#### 8. `SeedHistoricalDemoData.kt` + `GenerateWolEfficaciCatalog.kt`
- Percorso: `jvmMain/kotlin/org/example/project/core/cli/`
- Sono in `jvmMain` (compilati nel binary di produzione), ma nessun file in `jvmMain` li referenzia
- Probabilmente CLI utilities da eseguire stand-alone tramite `main()`
- Non sono raggiunte da nessuna UI o ViewModel
- **Da valutare**: spostarli in `jvmTest` o in un source set separato per evitare bloat nel binary

---

### POTENZIALI BUG

#### 9. `AggiornaDatiRemotiUseCase.fetchAndImport()` — upsertAll fuori transazione
- `partTypeStore.upsertAll(remoteTypes)` alla riga 32 avviene PRIMA della transazione degli schemi
- Se il fetch degli schemi fallisce dopo, i part types sono già aggiornati nel DB
- Stato parzialmente inconsistente: catalogo aggiornato ma settimane non importate
- Non catastrofico (upsert idempotente al retry), ma viola l'atomicità attesa dalla spec

#### 10. `EliminaProgrammaFuturoUseCase` — elimina anche il programma corrente
- Il nome dice "futuro" ma `timelineStatus == PAST` è l'unica restrizione
- Permette la cancellazione del programma `CURRENT` (mese in corso)
- Spec 003 FR-013 parla di "elimina programma futuro" — incoerenza semantica
- `canDeleteSelectedProgram` in `ProgramLifecycleViewModel` non distingue current vs future

#### 11. `EliminaProgrammaFuturoUseCase` — cascata mal commentata
- Commento: "DELETE week plans first — CASCADE will remove parts and assignments"
- **Realtà DB**: `week_plan → program_monthly` FK è `ON DELETE SET NULL` (non CASCADE)
- La cascade corretta è: `weekly_part → week_plan` CASCADE + `assignment → weekly_part` CASCADE
- Il codice funziona correttamente ma il commento inganna chi lo legge

#### 12. `SvuotaAssegnazioniProgrammaUseCase.execute()` — nessuna transazione
- Chiama `assignmentRepository.deleteByProgramFromDate(programId, fromDate)` senza transazione
- Se l'operazione è interrotta a metà (crash, OOM), lo stato è parzialmente cancellato senza rollback
- Spec 005 FR-016: descrive l'operazione come atomica

#### 13. `GeneraAlertValidazioneAssegnazioni` — N+1 query
- `weekPlanStore.listInRange()` ritorna `WeekPlanSummary` (riga 32)
- Poi per ogni settimana: `weekPlanStore.findByDate(weekSummary.weekStartDate)` (riga 35)
- Questo è un N+1 pattern: 1 query per lista + N query per dettaglio
- Su 4+ settimane genera query extra. `WeekPlanStore` dovrebbe offrire `listInRangeFull()` o simile.

#### 14. `AggiornaProgrammaDaSchemiUseCase` — O(N²) nel write phase
- Nel write phase (riga 104): `weekPlanStore.listByProgram(programId).find { it.id == week.id }`
- Eseguito per OGNI settimana dentro il loop → O(N²) fetch
- Per un programma mensile (4-5 settimane) è tollerabile, ma è un pattern da rifattorizzare

---

### DRY VIOLATIONS

#### 15. `AggiornaProgrammaDaSchemiUseCase` — logica snapshot duplicata
- La costruzione di `snapshotByKey` (assignments keyed by partTypeId+sortOrder) è duplicata quasi identicamente:
  - Analysis phase: righe 55-71
  - Write phase: righe 84-99
- Estrazione in metodo privato `buildAssignmentSnapshot(week)` eliminerebbe la duplicazione

#### 16. `AggiornaDatiRemotiUseCase.importSchema()` — usata in contesti diversi
- Chiamata da `fetchAndImport()` (fuori transazione) e da `importSchemas()` (dentro transazione)
- Il comportamento è "abbastanza" coerente ma la doppia semantica è nascosta nella firma
- Nessun commento che documenta questa differenza contestuale

---

### TIGHT COUPLING

#### 17. `ProgramLifecycleViewModel` ↔ `SchemaManagementViewModel`
- Entrambi mantengono copie di `currentProgram`/`futurePrograms`
- `SchemaManagementViewModel.updateSelection()` deve essere chiamato manualmente ogni volta che lo stato del programma cambia
- In `ProgramWorkspaceScreen.kt` questo è gestito con `LaunchedEffect` che sincronizza i due VM
- Soluzione più robusta: `SchemaManagementViewModel` legge direttamente dal DB invece di ricevere dati dal VM fratello

---

### UI/UX MINORI

#### 18. `StampaProgrammaUseCase` — `statusLabel` usa nome enum raw
- `statusLabel = week.status.name` produce "ACTIVE" nel PDF
- Probabilmente non visibile nell'output finale (non usato nel render), ma potenziale bug latente

#### 19. `AutoAssegnaProgrammaUseCase` — settimane INACTIVE silenziosamente skippate
- `.filter { it.status == WeekPlanStatus.ACTIVE }` senza feedback all'utente
- L'`AutoAssignProgramResult` non include informazioni sulle settimane skippate per stato
- L'utente potrebbe non capire perché alcune settimane non vengono assegnate

#### 20. Validazione nome/cognome asimmetrica
- `AggiornaProclamatoreUseCase`: controlla `nome.length > 100` e `cognome.length > 100`
- `CreaProclamatoreUseCase`: da verificare se ha le stesse validazioni (probabile che sì, ma non confermato in questa sessione)
- Da verificare consistenza

---

## Da approfondire nei cicli successivi

- [ ] `CreaProclamatoreUseCase` — verificare validazioni vs `AggiornaProclamatoreUseCase`
- [ ] `EliminaProclamatoreUseCase` — verificare atomicità (transaction?) del remove + removeAllForPerson
- [ ] `spec 006` — verificare completezza output: `StampaProgrammaUseCase`, `GeneraPdfAssegnazioni`, `GeneraImmaginiAssegnazioni` vs spec
- [ ] `AggiornaDatiRemotiUseCase` — verificare il flow UI per `weeksNeedingConfirmation` (viene mostrata dialog di conferma?)
- [ ] `EliminaProgrammaFuturoUseCase` — verificare spec 003 FR-018: "conferma con impatto"
- [ ] `ProclamatoreFormViewModel` — come gestisce `AggiornamentoOutcome.futureWeeksWhereAssigned`?
- [ ] `AggiornaSchemiUseCase` — scrive mai in `part_type_revision`?
- [ ] `SexRule.STESSO_SESSO` sexMismatch: è solo soft warning o blocca assegnazione?

---

## Ciclo 2 — Approfondimento

### Conferme ciclo 1

- `CreaProclamatoreUseCase` ✅ ha le stesse validazioni di `AggiornaProclamatoreUseCase` (nome/cognome max 100 char, not blank) — nessuna asimmetria
- `EliminaProclamatoreUseCase` ✅ usa `transactionRunner.runInTransaction { removeAllForPerson + remove }` — correttamente atomico
- `AggiornaSchemiUseCase` ✅ NON scrive mai in `part_type_revision` — confermato: usa `partTypeStore.upsertAll()` direttamente su `part_type`
- Spec 006 (output) ✅ completamente implementata: `StampaProgrammaUseCase`, `GeneraPdfAssegnazioni`, `GeneraImmaginiAssegnazioni` — spec reverse-engineered dal codice esistente, nessun gap

### CRITICO: `AggiornaDatiRemotiUseCase` — feature orfana (spec 002)

- **Spec 002 User Story 4**: "Aggiornamento dati remoti (tipi di parte)" — richiede download da sorgente remoto
- `AggiornaDatiRemotiUseCase` è registrato in `WeeklyPartsModule.kt` (Koin) ma **nessun ViewModel** lo usa
- È il sistema "vecchio": importa `WeekPlan` direttamente da remote, con phase 1 (skip existing) e phase 2 (force replace)
- Sostituito architetturalmente da `AggiornaSchemiUseCase` (schemas feature) → importa `SchemaWeekTemplate` → poi `AggiornaProgrammaDaSchemiUseCase` applica i template ai programmi
- **Due remote data sources paralleli**:
  - `GitHubDataSource` (weeklyparts) → usato da `AggiornaDatiRemotiUseCase` (orphan)
  - `GitHubSchemaCatalogDataSource` (schemas) → usato da `AggiornaSchemiUseCase` (active)
- La flow UI "weeksNeedingConfirmation" (phase 2 dialog di conferma) non è mai raggiungibile
- **Conclusione**: `AggiornaDatiRemotiUseCase` + `GitHubDataSource` sono dead code. L'architettura è migrata agli schemi ma la vecchia implementazione non è stata rimossa.

### CRITICO: Feature `planning` — architettura orfana

- Tutto il modulo `feature/planning/` (use cases, domain models, DI) è registrato ma non consumato da nessun ViewModel
- `CaricaPanoramicaPianificazioneFutura`, `GeneraAlertValidazioneAssegnazioni`, `GeneraAlertCoperturaSettimane`, `CalcolaProgressoPianificazione` — mai chiamati dall'UI
- Non c'è spec scritta per questa feature
- Probabilmente era in sviluppo, poi sospeso. Il codice è maturo (DI configurato, use cases composti), manca solo la UI.
- **Performance note**: `GeneraAlertValidazioneAssegnazioni` ha un N+1 query pattern. Se mai esposta in UI su molte settimane, sarà lenta.

### EliminaProgrammaFuturoUseCase — verifica spec 003 FR-013

- Spec 003 FR-013: "Elimina il programma corrente o futuro" — la spec include esplicitamente CURRENT
- **Nessun gap**: il nome "FuturoUseCase" è fuorviante ma il comportamento è conforme alla spec
- UI: `canDeleteSelectedProgram = selectedProgram != null` — non distingue current vs future per il bottone di delete
- La dialog mostra impatto (weeksCount, assignmentsCount) ✅

### AggiornaSchemiUseCase — workflow completo verificato

- ✅ Tutto atomico dentro `transactionRunner.runInTransaction`:
  - Cleanup eligibilities + anomaly logging
  - `partTypeStore.upsertAll()` (catalogo)
  - `partTypeStore.deactivateMissingCodes()` (disattiva rimossi)
  - `schemaTemplateStore.replaceAll()` (template settimane)
- `settings.putString("last_schema_import_at", ...)` avviene **dopo** la transazione — possibile inconsistenza se il processo crasha tra la fine della transazione e il salvataggio del timestamp. L'effetto: `isSchemaRefreshNeeded()` in `SchemaManagementViewModel` potrebbe non rilevare che un refresh è avvenuto.

### SexRule.STESSO_SESSO — semantica confermata

- `STESSO_SESSO` = "stesso sesso preferito, soft warning" — `passaSesso = true` (anyone assignable)
- Da `ProclamatoreFormViewModel:484`: `SexRule.STESSO_SESSO -> true` (eligibility check: eligible)
- Da `SuggerisciProclamatoriUseCase:56`: `val isSexMismatch = part.partType.sexRule == SexRule.STESSO_SESSO && <sex_comparison>` — calcola il soft warning
- **Il nome è confuso**: STESSO_SESSO suona come "stesso sesso obbligatorio" ma significa "stesso sesso preferito". UOMO invece è hard filter.
- Impatto UI: il campo `sexMismatch: Boolean` in `SuggestedProclamatore` viene mostrato come warning nella lista suggeriti, ma non blocca l'assegnazione.
- `PartTypeJsonParser:14`: compatibilità backward `if (value == "LIBERO") SexRule.STESSO_SESSO` — corretto

### DB schema — dettagli aggiuntivi

- `week_plan.status` default è `'ACTIVE'` — ma non c'è controllo che il valore sia valido (no CHECK constraint). `WeekPlanStatus` enum ha `ACTIVE`/`INACTIVE` ma il DB accetta qualsiasi stringa.
- `assignment.UNIQUE(weekly_part_id, slot)` — protegge da doppi slot ✅
- `person_part_type_eligibility.UNIQUE(person_id, part_type_id)` — protegge da doppie eligibility ✅
- `program_monthly.UNIQUE(year, month)` — un solo programma per mese ✅

---

## Ciclo 3 — Correzioni e chiarimenti

### P0 rivalutazioni

#### SvuotaAssegnazioniProgrammaUseCase — rivalutazione
- `deleteByProgramFromDate` esegue un singolo `DELETE ... WHERE weekly_part_id IN (...)` — atomico a livello SQL
- La mancanza di transazione esplicita nella use case NON è un bug: l'operazione è intrinsecamente atomica
- Il double-call (count + delete) nell'infrastruttura è un'inefficienza ma non un rischio di integrità
- **Downgrade da P0 a non-issue** per single-user desktop

#### AutoAssegnaProgrammaUseCase — mutex presente nel ViewModel
- `AssignmentManagementViewModel:143` ha guard `if (_uiState.value.isAutoAssigning) return`
- Il pulsante "Autoassegna" viene disabilitato durante l'operazione (UI feedback)
- **Spec FR-007 è soddisfatta de facto** dal ViewModel — il mutex non è nella use case ma nella UI layer, accettabile per single-user desktop
- **Downgrade da P1 a low-risk** per single-user desktop

#### `AggiornaDatiRemotiUseCase.fetchAndImport()` — rivalutazione
- `partTypeStore.upsertAll()` fuori transazione: in caso di errore, il catalogo viene aggiornato ma i week plans no
- Ma `upsertAll` è idempotente: al retry successivo ri-scarica e re-applica
- Non causa corruzione dei dati, solo stato temporaneamente inconsistente
- **Mantiene P0** come inconsistenza atomicità, ma è moot se la use case è orphan (ciclo 2 finding)

### CercaProclamatoriUseCase — comportamento confermato corretto
- `searchProclaimers` query ritorna TUTTI i proclamatori inclusi sospesi — **intenzionale**
- L'elenco deve mostrare anche i sospesi per permettere di de-sospenderli
- Il nome `allActiveProclaimers` è fuorviante ma usato solo nell'aggregate store (load by ID), non nell'elenco
- UI mostra i sospesi con badge visivo diverso ✅

### AssignmentManagementViewModel — dettaglio auto-assign
- `AssignmentManagementViewModel` (workspace) gestisce: autoAssegna, svuota, stampa, rimuoviSettimana, settings
- Tutte le operazioni hanno guard `isXxx: Boolean` nel state + `if (...) return` nel handler
- Pattern consistente e corretto ✅

### Recap P0 aggiornato

| # | Problema | Severità reale |
|---|----------|----------------|
| 1 | `attivo`/`ImpostaStatoProclamatoreUseCase` mancanti | **P1** (spec gap, non bug runtime) |
| 2 | `AggiornaDatiRemotiUseCase` orphan (spec 002 phase 2 mai raggiungibile) | **P1** (feature gap) |
| 3 | Feature `planning` mai esposta in UI | **P2** (dead code, non runtime bug) |

Non ci sono **P0 veri** (crash, corruzione dati, perdita dati non intenzionale).

---

## Ciclo 4 — Analisi finale e coerenza globale

### PersonPickerViewModel — comportamento corretto
- Guards `isAssigning` e `isRemovingAssignment` presenti ✅
- `alreadyAssignedIds` calcolato da snapshot memorizzato al momento dell'apertura del picker
- **Edge case**: se un'altra assegnazione viene fatta mentre il picker è aperto (impossibile per single-user ma teoricamente possibile), `alreadyAssignedIds` potrebbe essere stale
- Non è un bug per single-user desktop; `AssegnaPersonaUseCase` ha comunque il check DB `isPersonAssignedInWeek` come ultimo guard

### Gestione sospensione — flow completo verificato
- `AggiornaProclamatoreUseCase` → ritorna `AggiornamentoOutcome(proclamatore, futureWeeksWhereAssigned)`
- La UI (`ProclamatoreFormViewModel`) riceve l'outcome: se `futureWeeksWhereAssigned.isNotEmpty()`, mostra avviso settimane future con assegnazioni
- Le assegnazioni NON vengono rimosse automaticamente — utente deve gestire manualmente ✅ (spec 001 conforme)

### Workflow importazione JSON proclamatori — verificato
- `ImportaProclamatoriDaJsonUseCase` → wired in `ProclamatoriListViewModel` ✅
- Bottone "Importa da JSON" visibile solo quando la lista è vuota (`canImportInitialJson`) — protezione da import multipli
- Non ha transazione esplicita nella use case (da verificare nell'infrastruttura), ma è un'operazione di bootstrap

### Coerenza validazioni form
- `CreaProclamatoreUseCase` e `AggiornaProclamatoreUseCase`: identiche validazioni nome/cognome ✅
- `AssegnaPersonaUseCase`: verifica `slot >= 1 && slot <= partType.peopleCount` ✅
- `CreaSettimanaUseCase`: probabilmente verifica che la data sia un lunedì (spec 002) — non verificato in profondità

### Pattern generale dei ViewModel — valutazione
- Tutti i ViewModel hanno: `MutableStateFlow` + `StateFlow` + coroutine scope + guard booleani
- Pattern `executeEitherOperationWithNotice` usato consistentemente per operazioni Either
- `factory { ... CoroutineScope(SupervisorJob() + Dispatchers.Main) ... }` in `ViewModelsModule` — ogni VM ha scope indipendente, non condivide scope globale ✅
- Tutti i ViewModel sono `factory` (non `single`) — creati per ogni screen, destroyed quando non più usati ✅

### Spec 004 (schemi catalogo) — conformità
- `AggiornaSchemiUseCase` è l'implementazione spec 004
- `SchemaManagementViewModel` + `ProgramWorkspaceScreen` espongono "Aggiorna schemi" ✅
- `AggiornaProgrammaDaSchemiUseCase` + dry-run ✅
- Unico gap: `settings.putString(timestamp)` fuori transazione (ciclo 2 finding #17) — low risk

### Orphan code finale definitivo
| Componente | Tipo | Stato |
|-----------|------|-------|
| `AggiornaDatiRemotiUseCase` + `GitHubDataSource` | Dead use case + data source | Mai usato in UI |
| `feature/planning/` intero modulo | Dead feature | Registrato Koin, mai esposto |
| `part_type_revision` table + `weekly_part.part_type_revision_id` | Dead DB schema | Nessun codice legge/scrive |
| `SeedHistoricalDemoData.kt` + `GenerateWolEfficaciCatalog.kt` | CLI utilities | In jvmMain ma non referenziati |

### Positivo — architettura solida
- ✅ Vertical slices ben separati: people, weeklyparts, assignments, programs, schemas, output, planning
- ✅ Use cases piccoli e focalizzati (SRP rispettato)
- ✅ Arrow Either per error handling sistematico
- ✅ TransactionRunner iniettato via DI — testabile
- ✅ Tutti gli output operations su `Dispatchers.IO` (spec 006 FR-011)
- ✅ Koin DI pulito, factory vs single usati correttamente
- ✅ DB constraints appropriati (UNIQUE, CHECK, FK)
- ✅ Guard booleani in tutti i ViewModel per operazioni async

---

## Riepilogo per priorità

### P1 — Feature incomplete / spec deviazione (nessun bug runtime)
| # | Problema | File |
|---|----------|------|
| 1 | `attivo`/`ImpostaStatoProclamatoreUseCase` mancanti (spec 001 FR-001, FR-011) | DB, Proclamatore.kt |
| 2 | `AggiornaDatiRemotiUseCase` mai esposto in UI (spec 002 phase 2 dialog mai raggiungibile) | WeeklyPartsModule.kt |
| 3 | `ImpostaSospesoUseCase` standalone mancante (merged in AggiornaProclamatoreUseCase) | — |

### P2 — Tech debt / dead code / naming
| # | Problema | File |
|---|----------|------|
| 4 | `AggiornaDatiRemotiUseCase` + `GitHubDataSource` dead code (sostituiti da schemas) | weeklyparts/ |
| 5 | Feature `planning` implementata ma mai esposta — dead feature | PlanningModule.kt |
| 6 | `part_type_revision` table + colonne — orphan DB infrastructure | MinisteroDatabase.sq |
| 7 | `SeedHistoricalDemoData.kt` + `GenerateWolEfficaciCatalog.kt` in jvmMain | core/cli/ |
| 8 | `AggiornaProgrammaDaSchemiUseCase` — analysis loop duplicato nel write phase (DRY) | AggiornaProgrammaDaSchemiUseCase.kt |
| 9 | `GeneraAlertValidazioneAssegnazioni` — N+1 query pattern | GeneraAlertValidazioneAssegnazioni.kt |
| 10 | `EliminaProgrammaFuturoUseCase` — nome "Futuro" ma elimina anche current, commento CASCADE errato | EliminaProgrammaFuturoUseCase.kt |
| 11 | `allActiveProclaimers` query mal nominata (ritorna tutti inclusi suspended) | MinisteroDatabase.sq |
| 12 | `SexRule.STESSO_SESSO` — nome ambiguo (sembra hard restriction, è soft warning) | SexRule.kt |
| 13 | `week_plan.status` senza CHECK constraint in DB | MinisteroDatabase.sq |
| 14 | `AggiornaSchemiUseCase` — `settings.putString(timestamp)` fuori transazione | AggiornaSchemiUseCase.kt |
| 15 | `AggiornaProgrammaDaSchemiUseCase` — O(N²) listByProgram nel write phase | AggiornaProgrammaDaSchemiUseCase.kt |
| 16 | `StampaProgrammaUseCase` — `week.status.name` raw enum nel PDF (latent bug) | StampaProgrammaUseCase.kt |
| 17 | `AutoAssegnaProgrammaUseCase` senza mutex (spec 005 FR-007) — soddisfatto dal VM | AutoAssegnaProgrammaUseCase.kt |

### Non-issue (rivalutati)
- `SvuotaAssegnazioniProgrammaUseCase` senza transazione → delete è singola query SQL atomica ✅
- `AutoAssegnaProgrammaUseCase` mutex → `isAutoAssigning` guard nel ViewModel è sufficiente per single-user ✅
- Validazioni `CreaProclamatoreUseCase` vs `AggiornaProclamatoreUseCase` → identiche ✅
- Feature spec 006 (output) → completamente implementata ✅
- `ImportaProclamatoriDaJsonUseCase` transazionalità → `SqlDelightProclamatoriStore.persistAll` usa transaction SQLDelight interno ✅
- Suspension warning UI → `futureWeeksWhereAssigned` mostrato come warning notice nel form ✅

---

## Ciclo 5 — Approfondimento domain invariants e status handling

### `WeekPlanStatus.SKIPPED` (non INACTIVE)
- **Correzione review notes precedenti**: `WeekPlanStatus` ha `ACTIVE`/`SKIPPED` — non `INACTIVE`
- Le settimane "saltate" (es. settimana di nessuna riunione) hanno status `SKIPPED`
- In UI: etichetta "SALTATA", colore diverso, operazioni bloccate (parts editing, assignments)
- `AutoAssegnaProgrammaUseCase` correttamente skippa settimane SKIPPED (`.filter { status == ACTIVE }`)
- `PartEditorViewModel` permette toggle ACTIVE↔SKIPPED ✅

### `CreaSettimanaUseCase` — Monday validation nel domain, non nel use case
- La validazione "data deve essere lunedì" è nel costruttore `WeekPlan.init { require(...) }`
- Se la data NON è lunedì, `WeekPlan(...)` lancia `IllegalArgumentException` — NON convertita in `DomainError`
- **Spec 002 acceptance scenario 3** dice "sistema rifiuta con errore di validazione" — ma il tipo errore è diverso
- **In pratica**: la UI usa sempre `previousOrSame(DayOfWeek.MONDAY)` → mai inviata data non-lunedì
- **Rischio**: se `CreaSettimanaUseCase` fosse chiamata direttamente (test, CLI), lancerebbe eccezione non gestita invece di `Either.Left`
- Classificazione: **P3 minor** — funziona ma il boundary error handling è inconsistente con il pattern Either dell'app

### `RimuoviParteUseCase` — remove + recompact senza transazione
- `weekPlanStore.removePart(weeklyPartId)` poi `weekPlanStore.updateSortOrders(...)` — due operazioni DB separate
- Se crash tra le due: parte rimossa ma sort orders non ricompattati (sortOrder con buchi)
- Impatto UI: l'ordine visivo potrebbe risultare errato ma non causa corruzione funzionale
- La parte rimossa trascina con sé le assignments via FK CASCADE ✅ (questo è atomico)
- **Classificazione**: P3 low-risk — single-user desktop, crash recovery rarissimo

### `AggiornaProgrammaDaSchemiUseCase` — settimane SKIPPED non protette
- Il write phase processa tutte le settimane del programma con `weekStartDate >= referenceDate`
- Non filtra per `status != SKIPPED`
- Una settimana SKIPPED non dovrebbe avere template in `schema_week` → `schemaTemplateStore.findByWeekStartDate` ritornerebbe null → `continue` (implicitamente protetta)
- Ma questo è protection-by-absence, non esplicita. Se per qualche motivo un template esiste per una settimana SKIPPED, verrebbe modificata ugualmente
- **Classificazione**: P3 minor — protezione implicita, non intenzionale

### Gestione sospensione — flow completo verificato ✅
- `AggiornaProclamatoreUseCase` → `AggiornamentoOutcome.futureWeeksWhereAssigned`
- `ProclamatoreFormViewModel:340` → `warningNotice("$operation: $details — sospeso con N assegnazioni future da verificare")`
- Le assegnazioni non vengono rimosse automaticamente — conforme a spec 001 ✅
- Il warning è chiaro e contextual ✅

### Aggiornamenti tabella P2 — correzioni e nuove voci
| # | Problema aggiornato | Note |
|---|---------------------|------|
| 17 | **(Corretto)** AutoAssegna skippa settimane SKIPPED silenziosamente | Status è `SKIPPED` non `INACTIVE` |
| 18 | `CreaSettimanaUseCase` Monday validation: `IllegalArgumentException` vs `DomainError` | P3 in pratica |
| 19 | `RimuoviParteUseCase` remove+recompact non transazionale | P3 low-risk |
| 20 | `AggiornaProgrammaDaSchemiUseCase` processa settimane SKIPPED implicitamente | P3 protection-by-absence |

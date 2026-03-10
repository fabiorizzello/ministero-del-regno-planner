# Review Notes — Findings aperti (2026-03-10)

## High

**High 73**: `annullaConsegna()` Either result silently discarded prima della riassegnazione.
- `PersonPickerViewModel.kt:138` — `annullaConsegna(pickerWeeklyPartId, pickerWeekPlanId)` ritorna `Either<DomainError, Unit>` ma il risultato è ignorato. Se la cancellazione fallisce (errore DB), il flusso procede a `doAssign()`, lasciando il vecchio record di consegna attivo mentre un nuovo proclamatore viene assegnato → inconsistenza dati.
- Confronto: `markAsDelivered()` a `AssignmentManagementViewModel.kt:396` usa correttamente `.fold()`.
- Severità: **High** | Effort: S

**High 74**: Eccezione non catturata in `loadDeliveryStatus` — nessun error handling.
- `AssignmentManagementViewModel.kt:410-415` — `caricaStatoConsegne(weekPlanIds)` è una chiamata DB raw (no Either) senza try-catch dentro `scope.launch`. Errori SQL/IO crashano la coroutine silenziosamente, senza feedback utente. Chiamata da due punti: riga 255 (apertura ticket) e riga 403 (dopo mark delivered).
- Severità: **High** | Effort: S

**High 75**: Eccezione non catturata in `verificaConsegna` — stesso pattern.
- `PersonPickerViewModel.kt:120` — `verificaConsegna(pickerWeeklyPartId, pickerWeekPlanId)` è una chiamata DB raw dentro `scope.launch` senza try-catch. Se fallisce, il flusso di assegnazione si blocca silenziosamente.
- Severità: **High** | Effort: S

---

## Medium

**Medium 52**: Metodi di mutazione senza `context(tx: TransactionScope)` sulle interfacce store — **SISTEMICO**.
- Problema alla radice: `AggregateStore.persist()` (base interface, `core/application/AggregateStore.kt:5`) non dichiara context → tutti i sotto-tipi ereditano il gap.
- Interfacce coinvolte (8+ metodi di mutazione):
  - `AggregateStore.persist()` — base interface
  - `ProclamatoriAggregateStore.remove(id)` — riga 10
  - `ProgramStore.save()`, `delete()`, `updateTemplateAppliedAt()` — righe 18-20
  - `EligibilityStore.setSuspended()`, `setCanAssist()`, `setCanLead()`, `deleteLeadEligibilityForPartTypes()` — righe 18-20, 31
- Severità: **Medium** | Effort: L

**Medium 58**: CLI tools (`core/cli/`) non integrati con Koin — istanziati manualmente, separati dall'albero DI.
- Severità: **Medium** | Effort: M

**Medium 63**: `IOException` non catturata in `either {}` — propagazione eccezione non gestita in 2 use case.
- `StampaProgrammaUseCase` e `GeneraImmaginiAssegnazioni.ensureOutputDir()`.
- Severità: **Medium** | Effort: S

**Medium 66**: Spec 002 disalignment — `AggiornaDatiRemotiUseCase` two-phase pattern non implementato.
- La spec promette `fetchAndImport()` + `importSchemas()` (con conferma utente). L'implementazione è single-phase.
- Fix: allineare spec a implementazione oppure implementare two-phase.
- Severità: **Medium** | Effort: M

**Medium 69**: Spec 006 FR-023 violazione — formato biglietto assegnazione mancante di role label.
- PDF usa `"3 - Studio biblico"` invece di `"3. Studio biblico (Studente)"`.
- Severità: **Medium** | Effort: S

**Medium 15**: `feature/updates` — zero test coverage. *(in standby — architettura in evoluzione)*
- Severità: **Medium** | Effort: M

**Medium 76**: Duplicato `ImmediateTransactionRunner` in `OutputTestFixtures.kt`.
- `OutputTestFixtures.kt:49-52` — copia esatta di `PassthroughTransactionRunner` da `core/TestTransactionRunners.kt`. Medium 62 aveva risolto questo pattern sistemicamente; US4 lo ha reintrodotto.
- Call sites: `SegnaComInviatoUseCaseTest` ×1, `AnnullaConsegnaUseCaseTest` ×1.
- Severità: **Medium** | Effort: S

**Medium 77**: Zero test ViewModel-level per comportamento US4 delivery.
- `AssignmentManagementViewModelTest.kt` — `makeViewModel` accetta `segnaComInviato`/`caricaStatoConsegne` ma nessun test esercita `markAsDelivered()`, `loadDeliveryStatus()`, `ticketBadgeText`, `isMarkingDelivered`.
- Severità: **Medium** | Effort: M

**Medium 78**: Test mancante per `CaricaStatoConsegne` con delivery attiva + cancellata per la stessa key.
- `CaricaStatoConsegneUseCaseTest.kt` — il use case prioritizza `activeDelivery != null` su `lastCancelled != null` nel `when`, ma nessun test verifica questa priorità. Scenario reale: invio → cancellazione → reinvio.
- Severità: **Medium** | Effort: S

**Medium 79**: `SegnaComInviatoUseCaseTest` usa `runBlocking` invece di `runTest`.
- `SegnaComInviatoUseCaseTest.kt:20,41` — tutti gli altri test use case US4 usano `runTest`. Inconsistente.
- Severità: **Medium** | Effort: S

**Medium 80**: Nessun feedback loading sul bottone "Segna come inviato".
- `ProgramWorkspaceComponents.kt` (AssignmentTicketCard) — `isMarkingDelivered` blocca click concorrenti ma non viene passato alla UI. Il bottone non mostra stato disabled/loading durante l'operazione asincrona.
- Severità: **Medium** | Effort: S

---

## Low

**Low 56**: `AutoAssegnaProgrammaUseCaseTransactionTest` testa dettagli implementativi di transazione invece di comportamento di business.
- Effort: M

**Low 60**: `WeekPlan.of()` smart constructor manca test per i percorsi di errore (data non lunedì, ID vuoto).
- Effort: S

**Low 61**: `fetchRankingFromDb()` esegue query sequenziali potenzialmente eccessivi. Basso impatto pratico (1 utente).
- Effort: L

**Low 64**: `runBlocking` usato in 18+ test invece di `runTest`. Basso impatto pratico (test JVM single-thread).
- Effort: M

**Low 69**: `searchProclaimers` SQL manca colonna `can_assist` → `puoAssistere` sempre `false` nei risultati. Impatto attuale nullo.
- Effort: S

**Low 73**: `cancelledAt!!` in `CaricaStatoConsegneUseCase:22` e `FakeSlipDeliveryStore:24` — si affida alla garanzia della query SQL (`cancelled_at IS NOT NULL`) invece che alla type safety. Accettabile.
- Effort: S

**Low 74**: Branch `else` morto in `CaricaStatoConsegneUseCase:40-44` — `allKeys = activeByKey.keys + cancelledByKey.keys` rende ogni key presente in almeno una mappa, quindi `DA_INVIARE` non è mai raggiunto da qui.
- Effort: S

**Low 75**: `ticketBadgeText` ramo `ifEmpty { null }` irraggiungibile — `AssignmentManagementViewModel.kt:86`.
- Effort: S

---

## Findings risolti (Batch 9 — 2026-03-10)

- **High 50**: `AggiornaProgrammaDaSchemiUseCase` — `.bind()` dentro `either{}` dentro `runInTransaction{}` sostituito con `.getOrElse { error(...) }` + `Either.catch{}` esterno. Ora l'errore propaga come eccezione → TransactionRunner fa rollback.
- **High 57 + Medium 71**: Estratto `DiagnosticsStore` interface (application layer), implementato `SqlDelightDiagnosticsStore` (infrastructure). `MinisteroDatabase` rimosso da `ContaStoricoUseCase` e `EliminaStoricoUseCase`. VACUUM estratto come lambda iniettabile. Aggiunti 9 test (4 ContaStorico + 5 EliminaStorico).
- **Medium 51**: 5 use case uniformati al pattern `Either.catch{}.mapLeft{}.bind()` — rimosso `try-catch(Exception)` dentro `either{}`.
- **Medium 53**: `Proclamatore.of()` ora fa trim di nome e cognome.
- **Medium 62**: TransactionRunner fake centralizzati in `core/TestTransactionRunners.kt`. Rimossi 7+ duplicati da 27 file di test.
- **Medium 65**: `DiagnosticsViewModel.confirmCleanup()` usa `executeEitherOperation` — rimosso `throw RuntimeException` per unwrap.
- **Medium 67**: `ProclamatoriListViewModel` propaga errore al banner notice — rimosso silent swallowing.
- **Medium 68**: `ProclamatoreFormViewModel` usa `executeEitherOperation` — rimossi `SubmitFormDomainError` e `ProclamatoreNotFoundException`.
- **Medium 70**: `Assignment` e `AssignmentWithPerson` — costruttori resi `internal`, aggiunto `companion fun of()` → `Either`.
- **Low 54**: `fold()` → `.bind()` in `RiordinaPartiUseCase`.
- **Low 55**: Rimosso `|| true` in `RimuoviParteUseCaseTest`.
- **Low 70**: Rimosso `SharedWeekState` (dead code) + binding Koin.
- **Low 71**: Rimosso import inutilizzato `mapProclamatoreRow`.
- **Low 72**: Rimosso `DomainError.SalvataggioPartiSettimanaFallito` (dead code).

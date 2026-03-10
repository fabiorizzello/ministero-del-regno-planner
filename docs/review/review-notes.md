# Review Notes — Findings aperti (2026-03-10)

## Medium

**Medium 63**: `IOException` non catturata in `either {}` — propagazione eccezione non gestita in 2 use case.
- `StampaProgrammaUseCase` e `GeneraImmaginiAssegnazioni.ensureOutputDir()`.
- Severità: **Medium** | Effort: S
- *(Rimandato — tocca file del plan slip-delivery-tracking)*

**Medium 66**: Spec 002 disalignment — `AggiornaDatiRemotiUseCase` two-phase pattern non implementato.
- La spec promette `fetchAndImport()` + `importSchemas()` (con conferma utente). L'implementazione è single-phase.
- Fix: allineare spec a implementazione oppure implementare two-phase.
- Severità: **Medium** | Effort: M

**Medium 69**: Spec 006 FR-023 violazione — formato biglietto assegnazione mancante di role label.
- PDF usa `"3 - Studio biblico"` invece di `"3. Studio biblico (Studente)"`.
- Severità: **Medium** | Effort: S
- *(Rimandato — tocca file del plan slip-delivery-tracking)*

**Medium 15**: `feature/updates` — zero test coverage. *(in standby — architettura in evoluzione)*
- Severità: **Medium** | Effort: M

**Medium 77**: Zero test ViewModel-level per comportamento US4 delivery.
- `AssignmentManagementViewModelTest.kt` — nessun test esercita `markAsDelivered()`, `loadDeliveryStatus()`, `ticketBadgeText`, `isMarkingDelivered`.
- Severità: **Medium** | Effort: M

---

## Low

**Low 61**: `fetchRankingFromDb()` esegue query sequenziali potenzialmente eccessivi. Basso impatto pratico (1 utente).
- Effort: L

**Low 64**: `runBlocking` usato in 18+ test invece di `runTest`. Basso impatto pratico (test JVM single-thread).
- Effort: M

**Low 69**: `searchProclaimers` SQL manca colonna `can_assist` → `puoAssistere` sempre `false` nei risultati. Impatto attuale nullo.
- Effort: S
- *(Rimandato — tocca MinisteroDatabase.sq del plan slip-delivery-tracking)*

---

## Findings risolti (Batch 11 — 2026-03-10)

- **High 73**: `annullaConsegna()` result ora verificato con `.fold()` — abort assign on failure.
- **High 74**: `loadDeliveryStatus` wrappato in try-catch con error notice.
- **High 75**: `verificaConsegna` wrappato in try-catch con error notice.
- **Medium 76**: Rimosso `ImmediateTransactionRunner` duplicato da `OutputTestFixtures.kt` → usa `PassthroughTransactionRunner` centralizzato.
- **Medium 78**: Aggiunto test active+cancelled stessa key per `CaricaStatoConsegne`.
- **Medium 79**: `SegnaComInviatoUseCaseTest` migrato a `runTest`.
- **Medium 80**: `isMarkingDelivered` passato alla UI — bottone mostra loading/disabled.
- **Low 73**: `cancelledAt!!` → `requireNotNull` con messaggio.
- **Low 74**: Rimosso `else` branch morto in `CaricaStatoConsegneUseCase`.
- **Low 75**: Rimosso `ifEmpty { null }` irraggiungibile in `ticketBadgeText`.

## Findings risolti (Batch 10 — 2026-03-10)

- **Medium 52**: `context(tx: TransactionScope)` aggiunto a tutte le mutation store interfaces (AggregateStore, ProclamatoriAggregateStore, ProgramStore, EligibilityStore). 27 file aggiornati. Fixato `CreaProssimoProgrammaUseCase.save()` fuori transazione.
- **Medium 58**: `SeedHistoricalDemoData` CLI integrato con Koin DI.
- **Low 56**: `AutoAssegnaProgrammaUseCaseTransactionTest` refactored — verifica business outcome.
- **Low 60**: Aggiunti 3 test error path per `WeekPlan.of()`. Aggiunta validazione blank ID.

## Findings risolti (Batch 9 — 2026-03-10)

- **High 50**: `AggiornaProgrammaDaSchemiUseCase` — `.getOrElse { error(...) }` + `Either.catch{}` per rollback.
- **High 57 + Medium 71**: Estratto `DiagnosticsStore` + 9 test.
- **Medium 51**: 5 use case → `Either.catch{}.mapLeft{}.bind()`.
- **Medium 53**: `Proclamatore.of()` trim nome/cognome.
- **Medium 62**: TransactionRunner fake centralizzati.
- **Medium 65**: `DiagnosticsViewModel` → `executeEitherOperation`.
- **Medium 67**: `ProclamatoriListViewModel` errore al banner.
- **Medium 68**: `ProclamatoreFormViewModel` → `executeEitherOperation`.
- **Medium 70**: `Assignment`/`AssignmentWithPerson` smart constructor.
- **Low 54, 55, 70, 71, 72**: Dead code e fix triviali.

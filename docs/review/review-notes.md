# Review Notes — Findings aperti (2026-03-10)

## Medium

**Medium 63**: `IOException` non catturata in `either {}` — propagazione eccezione non gestita in 2 use case.
- `StampaProgrammaUseCase` e `GeneraImmaginiAssegnazioni.ensureOutputDir()`.
- Severità: **Medium** | Effort: S
- *(Rimandato — tocca file del plan slip-delivery-tracking)*

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

**Low 69**: `searchProclaimers` SQL manca colonna `can_assist` → `puoAssistere` sempre `false` nei risultati. Impatto attuale nullo.
- Effort: S
- *(Rimandato — tocca MinisteroDatabase.sq del plan slip-delivery-tracking)*

---

## Findings risolti (Batch 13 — 2026-03-10)

- **Low 61**: `fetchRankingFromDb()` ottimizzato — da ~79 query sequenziali a 2 query bulk (`allAssignmentRankingData` + `allAssignableProclaimers`) + ranking in-memory. `SuggestionRankingCache` invariato.
- **Low 64**: `runBlocking` → `runTest` migrato in 34 file test. Nessun file non-test toccato.

## Findings risolti (Batch 12 — 2026-03-10)

- **Medium 66**: Spec 002 allineata + implementato flusso conferma refresh programma. Import schemi atomico single-phase; dry-run preview con `WeekRefreshDetail` per-settimana; dialog conferma prima di applicare refresh programma. Spec FR-007, US4, Edge Cases, Clarifications aggiornati.

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

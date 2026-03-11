# Review Notes — Findings aperti (2026-03-11)

## Medium

*(Nessun finding medium aperto)*

---

## Low

*(Nessun finding low aperto)*

---

## Findings risolti (Batch 19 — 2026-03-11)

- **Medium 86**: `slip_delivery` table — aggiunte FK constraints `ON DELETE CASCADE` su `weekly_part_id` e `week_plan_id`. Test `SqlDelightSlipDeliveryStoreTest` aggiornato con seed parent records.
- **Low 86**: Rimosso `CREATE UNIQUE INDEX assignment_unique_part_slot` duplicato — la table constraint `UNIQUE(weekly_part_id, slot)` è sufficiente.

---

## Findings risolti (Batch 18 — 2026-03-11)

- **Low 85**: `ArchivaAnomalieSchemaUseCase` — aggiunto return type `Either<DomainError, Unit>` con `Either.catch { }.mapLeft { }`. Caller migrato a `executeEitherOperationWithNotice`.

## Findings risolti (Batch 17 — 2026-03-11)

- **Low 76**: Aggiunti 24 test per `UpdateVersionComparator` — coprono prefisso v/V, componenti variabili, garbage input, stringhe vuote, whitespace.
- **Low 80**: Invalidato — `UpdateSettingsStore` e `WindowSettingsStore` sono classi concrete che usano `russhwolf.settings.Settings` (preferenze JVM), non SQLDelight. `context(TransactionScope)` non applicabile.
- **Low 82**: Aggiunti 6 error path test per `AutoAssegnaProgrammaUseCase` — programma inesistente, settimane vuote/skipped, candidati in cooldown, nessun candidato, errore assegnazione, filtro date.
- **Low 83**: Aggiunti 12 edge case test per `GeneraSettimaneProgrammaUseCase` — programma inesistente, nessun template, fallback a parte fissa, mix template/fallback, errore DB, templateAppliedAt.
- **Low 84**: N+1 query eliminata — nuova query `listSchemaWeeksWithParts` con LEFT JOIN + raggruppamento in-memory in `SqlDelightSchemaTemplateStore.listAll()`.

## Findings risolti (Batch 16 — 2026-03-11)

- **Medium 81**: `SchemaUpdateAnomalyStore.dismissAllOpen()` — aggiunto `context(TransactionScope)` su interfaccia + impl. Creato `ArchivaAnomalieSchemaUseCase` per wrappare in transazione. ViewModel aggiornato a usare use case. Fake test aggiornati.
- **Medium 82**: `AssignmentSettingsStore.save()` — aggiunto `context(TransactionScope)` su interfaccia + impl. `SalvaImpostazioniAssegnatoreUseCase` riscritto con `TransactionRunner`. DI e fake test aggiornati.
- **Medium 83**: `runInTransaction` senza `Either.catch` wrapper — tutti 15 use case wrappati con `Either.catch { runInTransaction { } }.mapLeft { DomainError.Validation(...) }.bind()`. Test rollback aggiornato per aspettare `Either.Left` invece di eccezione.
- **Medium 84**: Cleanup biglietti — corretto prefisso da `s89-` a `biglietto-YYYY-MM-`, parametri `year`/`month` ora usati per filtrare. `buildProgramSlipBaseName` riallineato a spec. Test aggiornato.
- **Low 78**: Rimosso `either {}` inutile da `AnnullaConsegnaUseCase` e `SegnaComInviatoUseCase` — wrappati con `Either.catch { runInTransaction { } }.mapLeft { }` (nessun `raise()` era usato).
- **Low 79**: `DateTimeFormatter` consolidato — `timestampFormatter` e `shortDateFormatter` aggiunti a `DateLabels.kt`. 7 file migrati a usare i formatter condivisi. Bug `Locale.ITALIAN` mancante in `ProclamatoriTableComponents.kt` risolto.
- **Low 81**: Spec 006 allineata — tutte le occorrenze di "Settimana non assegnata" cambiate in "Settimana saltata" (codice vince).

## Findings risolti (Batch 15 — 2026-03-11)

- **Medium 63**: `IOException` catturata in `StampaProgrammaUseCase.prepareMonthlyProgramOutputPath()` e `GeneraImmaginiAssegnazioni.ensureOutputDir()` — wrappata in `Either.catch{}.mapLeft{DomainError.Validation}`. 3 test aggiunti.
- **Medium 69**: Role label aggiunto al formato biglietto — `"3. Studio biblico (Studente)"` come da spec FR-023. `AssignmentTicketLine.roleLabel` ora popolato, `PdfAssignmentsRenderer` aggiornato.
- **Medium 77**: 7 test ViewModel-level per delivery: `markAsDelivered` (successo/errore/busy-guard), `cancelDelivery` (successo/errore/busy-guard), `openAssignmentTickets` popola `deliveryStatus`.
- **Low 69**: `searchProclaimers` SQL — aggiunta colonna `can_assist`, mapper cambiato a `mapProclamatoreAssignableRow`.

## Findings risolti (Batch 14 — 2026-03-11)

- **Medium 15**: `feature/updates` — test coverage ora esistente: 3 file test (5 test), coprono `VerificaAggiornamenti`, `AggiornaApplicazione`, `UpdateCenterViewModel`. Gap residui tracciati come Low 76 (UpdateVersionComparator).

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

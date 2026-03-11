# Review Notes — Findings aperti (2026-03-11)

## Medium

**Medium 81**: `SchemaUpdateAnomalyStore.dismissAllOpen()` — mutation senza `context(TransactionScope)`.
- Interfaccia: `SchemaUpdateAnomalyStore.kt:28` — `suspend fun dismissAllOpen()` senza context.
- Implementazione: `SqlDelightSchemaUpdateAnomalyStore.kt:47` — chiama `dismissAllSchemaUpdateAnomalies()` senza transazione.
- Caller: `ProclamatoriListViewModel.kt:219` — chiama via `executeAsyncOperationWithNotice` senza transaction wrapper.
- **Inconsistente**: stessa interfaccia ha `context(tx: TransactionScope)` su `append()` ma non su `dismissAllOpen()`.
- Severità: **Medium** | Effort: S

**Medium 82**: `AssignmentSettingsStore.save()` — mutation senza `context(TransactionScope)`.
- Interfaccia: `AssignmentSettingsStore.kt:5` — `suspend fun save(settings: AssignmentSettings)` senza context.
- Implementazione: `SqlDelightAssignmentSettingsStore.kt:26` — chiama `upsertAssignmentSettings()` senza transazione.
- Use case: `SalvaImpostazioniAssegnatoreUseCase.kt:7` — chiama `store.save()` senza `runInTransaction`.
- **Sfuggito** nel sweep di Batch 10 (Medium 52 — "context(tx:) aggiunto a tutte le mutation store interfaces").
- Severità: **Medium** | Effort: S

**Medium 84**: Spec 006 FR-022 violazione — cleanup biglietti usa prefisso e pattern errato.
- Spec: cleanup MUST usare pattern `biglietto-YYYY-MM-*.png` e preservare file di altri mesi.
- Codice: `GeneraImmaginiAssegnazioni.kt:289` usa prefisso `s89-`, e `cleanupProgramTicketExports()` (righe 283-296) cancella **tutti** i file `s89-*.png` senza filtrare per mese — i parametri `year`/`month` sono ricevuti ma mai usati.
- Anche il naming dei file generati (`buildProgramSlipBaseName`, righe 310-319) usa `s89-YYYYMMDD-{name}` invece di `biglietto-YYYY-MM-{details}`.
- Severità: **Medium** | Effort: S
- *(Rimandato — tocca file del plan slip-delivery-tracking)*

**Medium 83**: `runInTransaction` dentro `either {}` senza `Either.catch` wrapper — pattern sistematico in 15 use case.
- Se `runInTransaction` lancia eccezione DB, `either {}` non la cattura (cattura solo `raise()`). L'eccezione propaga al ViewModel senza conversione in `DomainError`.
- **Impatto reale mitigato**: `AsyncOperationHelper` cattura tutte le eccezioni via `runCatching` — nessun crash app. L'effetto è un messaggio di errore raw (es. `"SQL error: UNIQUE constraint"`) invece di un `DomainError.toMessage()` localizzato.
- 7 use case già usano il pattern corretto `Either.catch { runInTransaction { } }.mapLeft { }.bind()`.
- **Use case affetti** (15):
  - `CreaProssimoProgrammaUseCase.kt:37`
  - `GeneraSettimaneProgrammaUseCase.kt:93`
  - `EliminaProgrammaUseCase.kt:28`
  - `AggiornaProclamatoreUseCase.kt:50`
  - `CreaProclamatoreUseCase.kt:41`
  - `ImpostaIdoneitaConduzioneUseCase.kt:19`
  - `ImpostaIdoneitaAssistenzaUseCase.kt:17`
  - `AggiungiParteUseCase.kt:38`
  - `AggiornaPartiSettimanaUseCase.kt:40`
  - `RimuoviParteUseCase.kt:26`
  - `ImpostaStatoSettimanaUseCase.kt:29`
  - `CreaSettimanaUseCase.kt:38`
  - `EliminaStoricoUseCase.kt:19`
  - `AssegnaPersonaUseCase.kt:27`
  - `AggiornaSchemiUseCase.kt:54`
- Pattern corretto (riferimento): `RiordinaPartiUseCase.kt:22`, `EliminaProclamatoreUseCase.kt:17`, `AggiornaProgrammaDaSchemiUseCase.kt:116`.
- Severità: **Medium** | Effort: L

---

## Low

**Low 76**: `UpdateVersionComparator` — zero test coverage.
- `commonMain/kotlin/.../updates/UpdateVersionComparator.kt` — 26 linee di logica comparazione versioni semantiche con edge case: prefisso `v`/`V`, componenti variabili, pre-release info persa.
- Nessun file test sotto `commonTest` o `jvmTest`.
- Severità: **Low** | Effort: S

**Low 78**: `AnnullaConsegnaUseCase` e `SegnaComInviatoUseCase` — `either {}` dentro `runInTransaction {}`, pattern fragile.
- `AnnullaConsegnaUseCase.kt:18-24` e `SegnaComInviatoUseCase.kt:23-39`.
- Attualmente nessun `raise()` dopo mutazioni → nessun bug. Ma aggiungere un `raise()` dopo `store.cancel()` o `store.insert()` disabiliterebbe silenziosamente il rollback (Arrow cattura raise prima della transazione).
- Pattern corretto: non usare `either {}` dentro `runInTransaction {}`, o usare `error()` per uscire.
- Severità: **Low** | Effort: S

**Low 79**: `DateTimeFormatter.ofPattern()` duplicato in 9+ file — `DateLabels.kt` esiste ma non usato universalmente.
- Pattern duplicati: `"MMMM yyyy"` (3 file), `"d MMMM yyyy"` (3 file), `"dd/MM/yyyy HH:mm"` (2 file), `"dd/MM/yyyy"` (2 file).
- `DateLabels.kt` definisce `monthYearFormatter` e `dateFormatter` ma non sono importati da `StampaProgrammaUseCase.kt:29`, `PdfAssignmentsRenderer.kt:21`, `ProgramLifecycleViewModel.kt:282`.
- Bug minore: `ProclamatoriTableComponents.kt:758` — `DateTimeFormatter.ofPattern("dd/MM/yyyy")` senza `Locale.ITALIAN`.
- Severità: **Low** | Effort: S

**Low 81**: Spec 006 FR-005 disallineamento label — `"Settimana saltata"` vs spec `"Settimana non assegnata"`.
- Spec (righe 32, 159, 194, 339): settimane `SKIPPED` mostrano `"Settimana non assegnata"`.
- Codice: `StampaProgrammaUseCase.kt:61` — `emptyStateLabel = "Settimana saltata"`.
- Severità: **Low** | Effort: S

**Low 80**: `UpdateSettingsStore` e `WindowSettingsStore` — metodi mutazione config senza `context(TransactionScope)`.
- `UpdateSettingsStore.kt:14` (`saveChannel`), `:23` (`saveLastCheck`).
- `WindowSettingsStore.kt:52` (`save`), `:60` (`saveUiScale`).
- Store di configurazione app, non di dominio business — impatto basso.
- Severità: **Low** | Effort: S

**Low 82**: `AutoAssegnaProgrammaUseCase` — solo 1 test (happy path), zero test error path.
- Nessun test per: candidati tutti in cooldown, nessun candidato eligibile, settimane vuote, errore DB durante assegnazione.
- Use case complesso (7 dipendenze iniettate) che merita coverage degli scenari di fallimento.
- Severità: **Low** | Effort: M

**Low 83**: `GeneraSettimaneProgrammaUseCase` — solo 1 test + 1 regenerate test. Coverage minima per logica di generazione settimane.
- Severità: **Low** | Effort: M

**Low 84**: N+1 query in `SqlDelightSchemaTemplateStore.listAll()`.
- `SqlDelightSchemaTemplateStore.kt:54-69` — esegue `listSchemaWeeks()` (1 query), poi `schemaPartsByWeek(week.id)` per ogni settimana (N query). Per 52 settimane = 53 query.
- Impatto mitigato: app single-user desktop, SQLite locale, volume dati basso.
- Fix: una singola query JOIN `schema_week` + `schema_week_part`, poi raggruppamento in-memory.
- Severità: **Low** | Effort: S

---

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

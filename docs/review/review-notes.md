# Review Notes — Findings aperti (2026-03-10)

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
- *(Rimandato — tocca MinisteroDatabase.sq del plan slip-delivery-tracking)*

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

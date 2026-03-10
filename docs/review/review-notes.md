# Review Notes — Findings aperti (2026-03-10)

## High

**High 57**: `ContaStoricoUseCase` e `EliminaStoricoUseCase` — zero test coverage.
- Aggiunti in Batch 3 (High 46), ma nessun test unitario copre i due use case estratti da `DiagnosticsViewModel`.
- `ContaStoricoUseCase`: legge il conteggio storico, nessun test per il valore restituito.
- `EliminaStoricoUseCase`: mutante con `runInTransaction`, VACUUM fuori transazione — zero test su happy path, transazione, e error path.
- Evidenze: `feature/diagnostics/application/ContaStoricoUseCase.kt`, `EliminaStoricoUseCase.kt`; directory test diagnostics assente.
- Severità: **High** | Effort: M

**High 50**: `AggiornaProgrammaDaSchemiUseCase` — `raise()` dentro `either {}` dentro `runInTransaction {}` non causa rollback.
- Arrow `raise()` usa continuations (non eccezioni Java) → `either {}` restituisce `Left` *normalmente* → SQLDelight non vede eccezione → **COMMIT parziale**.
- Scenario concreto: se Week 1 viene aggiornata con successo (`saveAggregate()` scritto nel DB) e Week 2 fallisce in `replaceParts()`, le scritture di Week 1 sono committate ma Week 2 e Week 3 no → schema parzialmente applicato.
- Fix: sostituire `either { ... .bind() }` con `getOrElse { e -> error("Rollback: $e") }` così l'errore esce come eccezione e il TransactionRunner fa rollback.
- Nota: stesso pattern in `AssegnaPersonaUseCase.invoke():27`, ma lì è single-write-at-end → nessun commit parziale possibile. La vulnerabilità reale è solo in `AggiornaProgrammaDaSchemi` (loop di write).
- Evidenze: `AggiornaProgrammaDaSchemiUseCase.kt:89-97`, `TransactionRunner.kt:30-42`
- Severità: **High** | Effort: M

---

## Medium

**Medium 51**: `try-catch(Exception)` dentro `either {}` — anti-pattern, mixing paradigmi, non testato.
- **Cinque** use case wrappano `runInTransaction {}` in `try-catch` generico dentro `either {}` e chiamano `raise()` nel catch. Inconsistente con `SvuotaAssegnazioniProgrammaUseCase` che usa correttamente `Either.catch { runInTransaction {...} }.mapLeft { ... }`.
- Il blocco `catch` è completamente non testato in nessuno dei 5 use case.
- Fix: uniformare a `Either.catch { transactionRunner.runInTransaction { ... } }.mapLeft { DomainError.Xyz(it.message) }` fuori da `either {}`.
- Evidenze: `EliminaProclamatoreUseCase.kt:17-24`, `RimuoviAssegnazioneUseCase.kt:14-20`, `RiordinaPartiUseCase.kt:25-31`, `RimuoviAssegnazioniSettimanaUseCase.kt:22-28`, `ImportaProclamatoriDaJsonUseCase.kt:46-52`
- Severità: **Medium** | Effort: M

**Medium 52**: Metodi di mutazione senza `context(tx: TransactionScope)` sulle interfacce store — **SISTEMICO**.
- Problema alla radice: `AggregateStore.persist()` (base interface, `core/application/AggregateStore.kt:5`) non dichiara context → tutti i sotto-tipi ereditano il gap.
- Interfacce coinvolte (8+ metodi di mutazione):
  - `AggregateStore.persist()` — base interface
  - `ProclamatoriAggregateStore.remove(id)` — riga 10
  - `ProgramStore.save()`, `delete()`, `updateTemplateAppliedAt()` — righe 18-20
  - `EligibilityStore.setSuspended()`, `setCanAssist()`, `setCanLead()`, `deleteLeadEligibilityForPartTypes()` — righe 18-20, 31
- Il compilatore non garantisce staticamente che le mutazioni avvengano solo in transazione.
- Nota: `persistAll()` su `ProclamatoriAggregateStore` e `append()` su `SchemaUpdateAnomalyStore` hanno già il context — incoerenza interna.
- Severità: **Medium** | Effort: L

**Medium 53**: `Proclamatore.of()` non fa trim — entity costruita con spazi.
- Il validator usa `it.trim().isNotEmpty()` (valida il valore trimmato), ma l'entity viene costruita con il valore **non trimmato**. Nomi con padding (`"  Alice  "`) passano la validazione ma vengono salvati con spazi via `ImportaProclamatoriDaJsonUseCase`.
- `ProclamatoreAggregate.create()` e `updateProfile()` fanno correttamente il trim — incoerenza.
- Evidenze: `Proclamatore.kt:55-62`, `ProclamatoreAggregate.kt:21-26`
- Severità: **Medium** | Effort: S

**Medium 58**: CLI tools (`core/cli/`) non integrati con Koin — istanziati manualmente, separati dall'albero DI.
- Severità: **Medium** | Effort: M

**Medium 62**: `ImmediateTransactionRunner` / `PassthroughTransactionRunner` duplicati in più test package — violazione DRY.
- Tre implementazioni identiche in: `AssignmentTestFixtures.kt`, `PeopleTestFixtures.kt`, `GeneraSettimaneProgrammaUseCaseTest.kt`.
- Fix: centralizzare in `composeApp/src/jvmTest/kotlin/org/example/project/core/TestFixtures.kt`.
- Severità: **Medium** | Effort: M

**Medium 63**: `IOException` non catturata in `either {}` — propagazione eccezione non gestita in 2 use case.
- `StampaProgrammaUseCase`: `renderer.renderMonthlyProgramPdf()` e `Files.createDirectories()` lanciano `IOException` dentro `either {}`.
- `GeneraImmaginiAssegnazioni`: `ensureOutputDir()` chiama `Files.createDirectories()` dentro `either {}`.
- `either {}` cattura solo `raise()`, non eccezioni Java → `IOException` propaga non gestita al ViewModel.
- Fix: wrappare le chiamate IO in `try-catch(IOException)` o `Either.catch {}`.
- Evidenze: `StampaProgrammaUseCase.kt:137-141,152`, `GeneraImmaginiAssegnazioni.kt:94,118,272-276`
- Severità: **Medium** | Effort: S

**Medium 65**: `DiagnosticsViewModel.kt:225` — `throw RuntimeException(r.value.toMessage())` per unwrap di `Either.Left`.
- Anti-pattern "Unwrap con throw". Fix: usare `executeEitherOperationWithNotice` invece di `executeAsyncOperationWithNotice`.
- Severità: **Medium** | Effort: S

**Medium 66**: Spec 002 disalignment — `AggiornaDatiRemotiUseCase` two-phase pattern non implementato.
- La spec promette `fetchAndImport()` + `importSchemas()` (con conferma utente). L'implementazione `AggiornaSchemiUseCase` è single-phase.
- Non è un bug funzionale, ma la spec non è allineata.
- Fix: allineare spec a implementazione oppure implementare two-phase.
- Evidenze: `specs/002-parti-settimanali/spec.md:112-115`, `AggiornaSchemiUseCase.kt`
- Severità: **Medium** | Effort: M

**Medium 67**: `ProclamatoriListViewModel` — silent exception swallowing in conteggio assegnazioni.
- `requestDeleteCandidate()`: `catch (_: Exception) { -1 }` — errori DB silenziati.
- `requestBatchDeleteConfirm()`: `catch (_: Exception) { 0 }` — errore silenziato.
- Fix: propagare errore al banner notice.
- Evidenze: `ProclamatoriListViewModel.kt:150-154`, `ProclamatoriListViewModel.kt:180-181`
- Severità: **Medium** | Effort: S

**Medium 68**: `ProclamatoreFormViewModel` — private exception classes usate come control flow.
- `SubmitFormDomainError(val domainError: DomainError) : Exception()` wrappa `DomainError` in eccezione.
- Anche `ProclamatoreNotFoundException()` è lo stesso anti-pattern.
- Fix: usare `executeEitherOperation` al posto di `executeAsyncOperation`.
- Evidenze: `ProclamatoreFormViewModel.kt:49-51`, `ProclamatoreFormViewModel.kt:345-386`
- Severità: **Medium** | Effort: M

**Medium 69**: Spec 006 FR-023 violazione — formato biglietto assegnazione mancante di role label.
- PDF usa `"3 - Studio biblico"` invece di `"3. Studio biblico (Studente)"`.
- `AssignmentSlip` non ha campo `roleLabel`; separatore sbagliato (`-` vs `.`) e role label assente.
- Evidenze: `PdfAssignmentsRenderer.kt:69`, `GeneraImmaginiAssegnazioni.kt:164,233`
- Severità: **Medium** | Effort: S

**Medium 70**: `Assignment` e `AssignmentWithPerson` — costruttore pubblico con `throw` invece di smart constructor `Either`.
- Inconsistente con `Proclamatore`, `PartType`, `WeekPlan` che usano `internal constructor` + `companion fun of()`.
- Nota: in pratica, la validazione slot avviene a monte in `WeekPlanAggregate.validateAssignment()` → impatto reale basso.
- Evidenze: `Assignment.kt:22-33`, `AssignmentWithPerson.kt:14-16`
- Severità: **Medium** | Effort: M

**Medium 71**: `ContaStoricoUseCase` e `EliminaStoricoUseCase` — `MinisteroDatabase` iniettato direttamente nel layer application.
- Viola la direzione delle dipendenze: application → infrastructure. Dovrebbe usare un'interfaccia store.
- `EliminaStoricoUseCase:34` usa anche `AppRuntime.paths()` (singleton globale) — doppio accoppiamento.
- Evidenze: `ContaStoricoUseCase.kt:6,17,21-23`, `EliminaStoricoUseCase.kt:12,19,26,34`
- Severità: **Medium** | Effort: M

**Medium 15**: `feature/updates` — zero test coverage. *(in standby — architettura in evoluzione)*
- `VerificaAggiornamenti`, `AggiornaApplicazione`, `GitHubReleasesClient` non hanno nessun test.
- Severità: **Medium** | Effort: M

---

## Low

**Low 54**: `fold(ifLeft = { raise(it) }, ifRight = { it })` invece di `.bind()` in `RiordinaPartiUseCase.kt:21-24`.
- Effort: S

**Low 55**: `|| true` sempre vero nel test `RimuoviParteUseCaseTest.kt:153` — potrebbe mascherare edge case.
- Effort: S

**Low 56**: `AutoAssegnaProgrammaUseCaseTransactionTest` testa dettagli implementativi di transazione invece di comportamento di business.
- Effort: M

**Low 60**: `WeekPlan.of()` smart constructor manca test per i percorsi di errore (data non lunedì, ID vuoto).
- Effort: S

**Low 61**: `fetchRankingFromDb()` esegue query sequenziali potenzialmente eccessivi. Basso impatto pratico (1 utente).
- Effort: L

**Low 64**: `runBlocking` usato in 18+ test invece di `runTest`. Basso impatto pratico (test JVM single-thread).
- Effort: M

**Low 69**: `searchProclaimers` SQL manca colonna `can_assist` → `puoAssistere` sempre `false` nei risultati. Impatto attuale nullo (nessun consumer legge il campo da ricerca).
- Effort: S

**Low 70**: `SharedWeekState` — dead code registrato in Koin ma mai usato. Fix: rimuovere classe e binding.
- Effort: S

**Low 71**: Import non usato — `mapProclamatoreRow` in `SqlDelightAssignmentStore.kt:16`. Fix: rimuovere.
- Effort: S

**Low 72**: `DomainError.SalvataggioPartiSettimanaFallito` — dead code, mai raised. Fix: rimuovere variante e ramo `when`.
- Effort: S

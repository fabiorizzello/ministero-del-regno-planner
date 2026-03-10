# Review Notes — Consolidato e Deduplicato (2026-03-09)

## Findings aperti (ordinati per severità)

### High

**High 57**: `ContaStoricoUseCase` e `EliminaStoricoUseCase` — zero test coverage.
- Aggiunti in Batch 3 (High 46), ma nessun test unitario copre i due use case estratti da `DiagnosticsViewModel`.
- `ContaStoricoUseCase`: legge il conteggio storico, nessun test per il valore restituito.
- `EliminaStoricoUseCase`: mutante con `runInTransaction`, VACUUM fuori transazione — zero test su happy path, transazione, e error path.
- Confermato da 2 agenti indipendenti.
- Evidenze: `feature/diagnostics/application/ContaStoricoUseCase.kt`, `EliminaStoricoUseCase.kt`; directory test diagnostics assente.
- Severità: **High** | Effort: M

**High 50**: `AggiornaProgrammaDaSchemiUseCase` — `raise()` dentro `either {}` dentro `runInTransaction {}` non causa rollback. **CONFERMATO con analisi profonda del meccanismo.**
- Arrow `raise()` usa continuations (non eccezioni Java) → `either {}` restituisce `Left` *normalmente* → SQLDelight non vede eccezione → **COMMIT parziale**.
- Scenario concreto: se Week 1 viene aggiornata con successo (`saveAggregate()` scritto nel DB) e Week 2 fallisce in `replaceParts()`, le scritture di Week 1 sono committate ma Week 2 e Week 3 no → schema parzialmente applicato.
- Fix: sostituire `either { ... .bind() }` con `getOrElse { e -> error("Rollback: $e") }` così l'errore esce come eccezione e il TransactionRunner fa rollback.
- Nota (iterazione 3): stesso pattern in `AssegnaPersonaUseCase.invoke():27` (`runInTransaction { assignWithoutTransaction(...) }`), ma lì è single-write-at-end → nessun commit parziale possibile. La vulnerabilità reale è solo in `AggiornaProgrammaDaSchemi` (loop di write).
- Evidenze: `AggiornaProgrammaDaSchemiUseCase.kt:89-97`, `TransactionRunner.kt:30-42`, `AssegnaPersonaUseCase.kt:27-34` (pattern analogo, impatto nullo)
- Severità: **High** | Effort: M

**~~High 65~~**: INVALIDATO — Verifica iterazione 3 (2026-03-10). Dettagli in sezione "Findings invalidati".

---

### Medium

**Medium 51**: `try-catch(Exception)` dentro `either {}` — anti-pattern, mixing paradigmi, non testato.
- **Cinque** use case wrappano `runInTransaction {}` in `try-catch` generico dentro `either {}` e chiamano `raise()` nel catch. Inconsistente con `SvuotaAssegnazioniProgrammaUseCase` che usa correttamente `Either.catch { runInTransaction {...} }.mapLeft { ... }`.
- Il blocco `catch` è completamente non testato in nessuno dei 5 use case (nessun test forza un'eccezione da DB).
- Fix: uniformare a `Either.catch { transactionRunner.runInTransaction { ... } }.mapLeft { DomainError.Xyz(it.message) }` fuori da `either {}`.
- Evidenze: `EliminaProclamatoreUseCase.kt:17-24`, `RimuoviAssegnazioneUseCase.kt:14-20`, `RiordinaPartiUseCase.kt:25-31`, `RimuoviAssegnazioniSettimanaUseCase.kt:22-28`, `ImportaProclamatoriDaJsonUseCase.kt:46-52`
- Severità: **Medium** | Effort: M

**Medium 52**: Metodi di mutazione senza `context(tx: TransactionScope)` sulle interfacce store — **SISTEMICO**.
- Problema alla radice: `AggregateStore.persist()` (base interface, `core/application/AggregateStore.kt:5`) non dichiara context → tutti i sotto-tipi ereditano il gap.
- Interfacce coinvolte (8+ metodi di mutazione):
  - `AggregateStore.persist()` — base interface
  - `ProclamatoriAggregateStore.remove(id)` — riga 10
  - `ProgramStore.save()`, `delete()`, `updateTemplateAppliedAt()` — righe 18-20
  - `EligibilityStore.setSuspended()`, `setCanAssist()`, `setCanLead()`, `deleteLeadEligibilityForPartTypes()` — righe 18-20, 31 (chiamati dentro `runInTransaction {}` in `ImpostaIdoneitaConduzioneUseCase`, `ImpostaIdoneitaAssistenzaUseCase`, `AggiornaSchemiUseCase`)
- Il compilatore non garantisce staticamente che le mutazioni avvengano solo in transazione.
- Nota: `persistAll()` su `ProclamatoriAggregateStore` e `append()` su `SchemaUpdateAnomalyStore` hanno già il context — incoerenza interna.
- Nota (iterazione 2): `SchemaUpdateAnomalyStore.dismissAllOpen()` verificato CORRETTO — è chiamato fuori da transazione (ViewModel), non necessita context. Rimosso dalla lista.
- Severità: **Medium** | Effort: L (richiede aggiornamento interfacce + implementazioni + fake di test)

**Medium 53**: `Proclamatore.of()` non fa trim — entity costruita con spazi.
- Il validator usa `it.trim().isNotEmpty()` (valida il valore trimmato), ma l'entity viene costruita con il valore **non trimmato** (`nome = nome`, `cognome = cognome`). Nomi con padding (`"  Alice  "`) passano la validazione ma vengono salvati con spazi via `ImportaProclamatoriDaJsonUseCase`.
- `ProclamatoreAggregate.create()` e `updateProfile()` fanno correttamente il trim — incoerenza tra i due smart constructor.
- Evidenze: `Proclamatore.kt:55-62`, `ProclamatoreAggregate.kt:21-26`
- Severità: **Medium** | Effort: S

**Medium 58**: CLI tools (`core/cli/`) non integrati con Koin — istanziati manualmente, separati dall'albero DI.
- I tool CLI usano le proprie dipendenze istanziate manualmente invece dei binding Koin già configurati. Bypassa la gestione del ciclo di vita e non riusa i binding esistenti.
- Severità: **Medium** | Effort: M

**Medium 62**: `ImmediateTransactionRunner` / `PassthroughTransactionRunner` duplicati in più test package — violazione DRY.
- Tre implementazioni identiche (o quasi) di fake TransactionRunner esistono in package diversi: `AssignmentTestFixtures.kt`, `PeopleTestFixtures.kt`, `GeneraSettimaneProgrammaUseCaseTest.kt`. Se il contratto di `TransactionRunner` cambia, vanno aggiornati in tutti i posti.
- Fix: centralizzare in `composeApp/src/jvmTest/kotlin/org/example/project/core/TestFixtures.kt` condiviso.
- Evidenze: `AssignmentTestFixtures.kt:30-43`, `PeopleTestFixtures.kt:7-10`, `GeneraSettimaneProgrammaUseCaseTest.kt:98-100`
- Severità: **Medium** | Effort: M

**Medium 63**: `IOException` non catturata in `either {}` — propagazione eccezione non gestita in 2 use case.
- `StampaProgrammaUseCase`: `renderer.renderMonthlyProgramPdf()` e `Files.createDirectories()` lanciano `IOException` dentro `either {}`.
- `GeneraImmaginiAssegnazioni`: `ensureOutputDir()` (linee 94, 118) chiama `Files.createDirectories()` dentro `either {}` — stessa vulnerabilità.
- `either {}` cattura solo `raise()` (continuations), non eccezioni Java → `IOException` propaga fuori come eccezione non gestita al ViewModel.
- Contrasto: `GeneraImmaginiAssegnazioni.renderSlipImage()` ha un `try-catch` esplicito che mappa `IOException` a `Either.Left` — pattern corretto applicato solo parzialmente.
- Fix: wrappare le chiamate IO in `try { ... } catch(e: IOException) { raise(DomainError.xxx) }` oppure usare `Either.catch {}`.
- Evidenze: `StampaProgrammaUseCase.kt:137-141,152`, `GeneraImmaginiAssegnazioni.kt:94,118,272-276`
- Severità: **Medium** | Effort: S

**Medium 65**: `DiagnosticsViewModel.kt:225` — `throw RuntimeException(r.value.toMessage())` per unwrap di `Either.Left`.
- Inside `executeAsyncOperationWithNotice`, il blocco `operation` lancia `RuntimeException` quando `eliminaStorico()` restituisce `Left` — anti-pattern documentato in codebase-patterns ("Unwrap con throw").
- Fix: propagare `Either` correttamente; usare `executeEitherOperationWithNotice` invece di `executeAsyncOperationWithNotice`, oppure `.bind()` se dentro `either {}`.
- Evidenze: `DiagnosticsViewModel.kt:222-227`
- Severità: **Medium** | Effort: S

**Medium 66**: Spec 002 disalignment — `AggiornaDatiRemotiUseCase` two-phase pattern non implementato.
- La spec 002 definisce `AggiornaDatiRemotiUseCase` con due fasi: `fetchAndImport()` (scarica, segnala settimane già presenti) e `importSchemas()` (sovrascrive dopo conferma utente). L'implementazione attuale `AggiornaSchemiUseCase` è single-phase: scarica e sovrascrive in una transazione, senza fase di conferma.
- Non è un bug funzionale (il replace-all è atomico e corretto), ma la spec promette un flusso utente con conferma che non esiste.
- Fix: allineare spec a implementazione (documenta scelta single-phase) oppure implementare two-phase se richiesto.
- Evidenze: `specs/002-parti-settimanali/spec.md:112-115`, `AggiornaSchemiUseCase.kt`
- Severità: **Medium** | Effort: M

**Medium 68**: `ProclamatoreFormViewModel` — private exception classes usate come control flow.
- `SubmitFormDomainError(val domainError: DomainError) : Exception()` (riga 51) wrappa `DomainError` in eccezione per trasportarlo fuori da `executeAsyncOperation` → anti-pattern documentato in codebase-patterns ("Private exception come control flow").
- Usato 3 volte in `submitForm()` (righe 359, 374, 382): `throw SubmitFormDomainError(err)` per uscire dal lambda `operation`.
- Anche `ProclamatoreNotFoundException()` (riga 49, usato a riga 281) è lo stesso pattern.
- Fix: usare `executeEitherOperation` al posto di `executeAsyncOperation` — il lambda `operation` restituisce direttamente `Either<DomainError, T>` e il framework gestisce `Left` senza eccezioni.
- Evidenze: `ProclamatoreFormViewModel.kt:49-51`, `ProclamatoreFormViewModel.kt:345-386`
- Severità: **Medium** | Effort: M

**Medium 67**: `ProclamatoriListViewModel` — silent exception swallowing in conteggio assegnazioni.
- `requestDeleteCandidate()` (righe 150-154): `try { contaAssegnazioni(id) } catch (_: Exception) { -1 }` — errori DB silenziati, l'utente vede "-1" senza sapere che il conteggio è fallito.
- `requestBatchDeleteConfirm()` (righe 180-181): `try { contaAssegnazioni(id) } catch (_: Exception) { 0 }` — errore silenziato, il conteggio batch mostra 0 assegnazioni anche se la query è fallita.
- Fix: propagare errore al banner notice (pattern già usato altrove nel ViewModel).
- Evidenze: `ProclamatoriListViewModel.kt:150-154`, `ProclamatoriListViewModel.kt:180-181`
- Severità: **Medium** | Effort: S

**Medium 69**: Spec 006 FR-023 violazione — formato biglietto assegnazione mancante di role label.
- Il PDF del biglietto (ticket) assegnazione usa formato `"3 - Studio biblico"` invece del formato spec `"3. Studio biblico (Studente)"`.
- `AssignmentSlip` (`PdfAssignmentsRenderer.kt:37-43`) non ha campo `roleLabel`; `GeneraImmaginiAssegnazioni.kt:164` setta `roleLabel = null` in `AssignmentTicketLine`.
- `StampaProgrammaUseCase.kt:72` include correttamente il role label nel PDF programma mensile (`partSlotRoleLabel(...)`) — inconsistenza tra i due output.
- Due problemi: separatore sbagliato (`-` vs `.`) e role label assente `(Studente)`/`(Assistente)`.
- Il domain model `Assignment.roleLabel` esiste (`:35`) ma non viene propagato a `AssignmentSlip`.
- Evidenze: `PdfAssignmentsRenderer.kt:69`, `GeneraImmaginiAssegnazioni.kt:164,233`, spec `006-stampa-output/spec.md:235`
- Severità: **Medium** | Effort: S

**Medium 71**: `ContaStoricoUseCase` e `EliminaStoricoUseCase` — `MinisteroDatabase` iniettato direttamente nel layer application.
- Entrambi i use case (`feature/diagnostics/application/`) ricevono `MinisteroDatabase` (tipo generato da SQLDelight, layer infrastructure) come dipendenza del costruttore e chiamano `database.ministeroDatabaseQueries.*` direttamente.
- Viola la direzione delle dipendenze: application → infrastructure. Dovrebbe usare un'interfaccia store (es. `DiagnosticsStore`) dichiarata nel layer application e implementata in infrastructure.
- `EliminaStoricoUseCase:34` usa anche `AppRuntime.paths()` (singleton globale) per il VACUUM — doppio accoppiamento a infrastruttura.
- Nota: High 57 documenta l'assenza di test per questi use case. Questo finding è strutturale (DDD), non funzionale.
- Evidenze: `ContaStoricoUseCase.kt:6,17,21-23`, `EliminaStoricoUseCase.kt:12,19,26,34`
- Severità: **Medium** | Effort: M

**Medium 70**: `Assignment` e `AssignmentWithPerson` — costruttore pubblico con `throw` invece di smart constructor `Either`.
- `Assignment` (`Assignment.kt:22`) usa `data class` con costruttore pubblico + `init { requireValid(...) }` che chiama `throw IllegalArgumentException` (`KonformValidation.kt:13`).
- `AssignmentWithPerson` (`AssignmentWithPerson.kt:7`) usa `require(slot >= 1)` nel `init` — stesso pattern.
- Inconsistente con `Proclamatore`, `PartType`, `WeekPlan` che usano `internal constructor` + `companion fun of()` → `Either<DomainError, T>`.
- Anti-pattern documentato in codebase-patterns: "throw in domain layer".
- Nota: in pratica, la validazione slot avviene a monte in `WeekPlanAggregate.validateAssignment()` → l'`init` è safety net per stato impossibile. Impatto reale basso.
- Evidenze: `Assignment.kt:22-33`, `AssignmentWithPerson.kt:14-16`, `KonformValidation.kt:9-18`
- Severità: **Medium** | Effort: M

**Medium 15**: `feature/updates` — zero test coverage. *(in standby — architettura in evoluzione)*
- `VerificaAggiornamenti`, `AggiornaApplicazione`, `GitHubReleasesClient` non hanno nessun test.
- `UpdateScheduler` rimosso (non più schedulato automaticamente).
- Evidenza: `feature/updates/application/*.kt`, `feature/updates/infrastructure/*.kt`.

---

### Low

**Low 54**: `fold(ifLeft = { raise(it) }, ifRight = { it })` invece di `.bind()` in `RiordinaPartiUseCase.kt:21-24`.
- Evidenze: `RiordinaPartiUseCase.kt:21-24` | Effort: S

**Low 55**: `|| true` sempre vero nel test `RimuoviParteUseCaseTest.kt:153` — l'aggregate non è mai null anche con lista vuota, potrebbe mascherare edge case.
- Evidenze: `RimuoviParteUseCaseTest.kt:153` | Effort: S

**Low 56**: `AutoAssegnaProgrammaUseCaseTransactionTest` testa dettagli implementativi di transazione (`assertEquals(1, autoAssignTx.calls)`, `assertEquals(0, assignmentTx.calls)`) invece di comportamento di business. Un refactor dell'orchestrazione farebbe fallire il test senza regression reali.
- Evidenze: `AutoAssegnaProgrammaUseCaseTransactionTest.kt:27-108` | Effort: M

**Low 60**: `WeekPlan.of()` smart constructor manca test per i percorsi di errore.
- Il Finding 24 ha introdotto `WeekPlan.of()` → `Either<DomainError, WeekPlan>`, ma i test in `WeekPlanAggregateTest.kt` costruiscono ancora `WeekPlan(...)` direttamente (costruttore internal), senza coprire: data non lunedì → Left, ID vuoto → Left.
- Evidenze: `WeekPlanAggregateTest.kt:170-200` | Effort: S

**Low 61**: `fetchRankingFromDb()` esegue query sequenziali potenzialmente eccessivi.
- Se il programma ha 50 settimane future e 10 tipi di parte, il metodo esegue 6 query globali + fino a 350 query per date + 4000 query per tipo+data, anziché query aggregate.
- Basso impatto pratico (1 utente, calcolo periodico), ma potenziale bottleneck su finestre di pianificazione ampie.
- Evidenze: `SqlDelightAssignmentStore.kt:81-148` | Effort: L

**Low 64**: `runBlocking` usato pervasivamente nei test (18+ file) invece di `runTest`.
- `runBlocking` blocca il thread dispatcher; `runTest` fornisce migliore isolamento e supporta TestDispatchers per controllo del timing.
- Basso impatto pratico (test JVM single-thread), ma best practice Kotlin testing.
- Effort: M (refactor sistematico)

**Low 69**: `searchProclaimers` SQL manca colonna `can_assist` → `puoAssistere` sempre `false` nei risultati.
- Query `MinisteroDatabase.sq:163` seleziona `id, first_name, last_name, sex, suspended` ma NON `can_assist`.
- `mapProclamatoreRow` (`ProclamatoreRowMapper.kt:17-31`) crea `Proclamatore` senza `puoAssistere` → default `false`.
- Le query `allAssignableProclaimers` e `findProclaimerByIdExtended` includono correttamente `can_assist`.
- **Impatto attuale nullo**: nessun consumer legge `puoAssistere` dai risultati di ricerca (la lista non lo mostra, il form carica via `findProclaimerByIdExtended`). Bug latente se un futuro codice leggesse il campo.
- Fix: aggiungere `can_assist` a `searchProclaimers` e usare `mapProclamatoreAssignableRow`.
- Evidenze: `MinisteroDatabase.sq:162-171`, `ProclamatoreRowMapper.kt:17-31,33-49`, `SqlDelightProclamatoriQuery.kt:24`
- Severità: **Low** | Effort: S

**Low 70**: `SharedWeekState` — dead code registrato in Koin ma mai usato.
- Classe definita in `core/application/SharedWeekState.kt`, registrata come singleton in `CoreModule.kt:30` (`single { SharedWeekState() }`), ma nessun ViewModel, use case, o componente UI la inietta o referenzia.
- Fix: rimuovere classe e binding Koin.
- Severità: **Low** | Effort: S

**Low 71**: Import non usato — `mapProclamatoreRow` in `SqlDelightAssignmentStore.kt:16`.
- Importato ma non referenziato nel file. Solo `mapProclamatoreAssignableRow` è usato.
- Fix: rimuovere l'import.
- Severità: **Low** | Effort: S

**Low 72**: `DomainError.SalvataggioPartiSettimanaFallito` — dead code.
- Definito in `DomainError.kt:33` e gestito in `toMessage():72`, ma MAI raised/usato in nessun use case, ViewModel, o test.
- Fix: rimuovere la variante e il ramo `when`.
- Severità: **Low** | Effort: S

---

## Findings invalidati (Review Batch 6 — 2026-03-10)

- **Medium 59**: `UpdateStatusStore` espone `StateFlow` nel application layer — **INVALIDATO**. `MutableStateFlow` è `private`, il campo pubblico `state: StateFlow` è read-only. Pattern standard Kotlin Flow, nessuna violazione di incapsulamento.
- **High 65**: `ProgramLifecycleViewModel` accede direttamente a store/repository + `.getOrElse { throw RuntimeException(...) }` — **INVALIDATO** (Iterazione 3, deep verification). Verifica del codice sorgente mostra:
  1. `.getOrElse { throw RuntimeException(...) }` NON presente nel file — il `getOrElse` gestisce l'errore con state update + `return@launch`.
  2. `weekPlanStore.listAggregatesByProgram()` e `assignmentStore.allAssignmentsForProgram()` NON esistono — il ViewModel usa `weekPlanStore.listByProgram()` (via `WeekPlanQueries`, interfaccia read-only del layer application) e `caricaAssegnazioni()` (use case).
  3. Il `throw RuntimeException` effettivo è in `DiagnosticsViewModel.kt:225` — rilocato come **Medium 65**.

## Zone verificate (Review Batch 8 — iterazione 3/3 ralph loop 2026-03-10)

- **High 50 re-verificato**: Arrow 2.2.1.1 `either {}` usa `fold()` con `try-catch(RaiseCancellationException)` al confine. `raise()` lancia `RaiseCancellationException` → `either {}` la cattura PRIMA che raggiunga `TransactionRunner`'s `catch(Throwable)` → restituisce `Either.Left` normalmente → transazione COMMETTE. Un agente ha erroneamente affermato che l'eccezione propagherebbe al TransactionRunner causando rollback — questo è FALSO. High 50 CONFERMATO.
- **Koin DI completeness**: 37 use case verificati, tutti registrati in 8 moduli. Nessun binding orfano, nessuna dipendenza mancante. Interface-based registrations corrette.
- **DomainError exhaustiveness**: 36 varianti, tutte con `toMessage()`. `SalvataggioPartiSettimanaFallito` è dead code (→ Low 72). Tutte le altre varianti sono attivamente usate.
- **AsyncOperationHelper.kt**: 4 varianti corrette. `executeEitherOperation` non cattura eccezioni by design — le operazioni Either devono contenere tutti gli errori in `Either.Left`. Questo conferma la rilevanza di Medium 63 (IOException che escapa da `either {}`).

## Zone verificate (Review Batch 8 — iterazione 2/3 ralph loop 2026-03-10)

- **feature/weeklyparts deep review**: Domain, application, infrastructure analizzati. Row mappers usano costruttore diretto (non smart constructor) — scelta architetturale accettabile per read path. `WeekPlanAggregate.createWeekWithFixedPart()` usa costruttore diretto — mitigato da validazione nel calling use case.
- **feature/schemas deep review**: AggiornaSchemiUseCase corretto per scope single-phase. `SchemaUpdateAnomalyStore.dismissAllOpen()` confermato CORRETTO senza context(tx) — chiamato fuori transazione in ViewModel.
- **Infrastructure layer audit**: row mapper consistency verificata (solo Low 69 noto), exception handling boundary gaps (→ Medium 63 ampliato), dependency direction violations (→ Medium 71).
- **SchemaManagementViewModel**: pulito — usa use case iniettati, `executeEitherOperation`, error handling corretto.
- **ProgramLifecycleViewModel**: pulito — `loadWeeksForSelectedProgram()` usa `runCatching` appropriatamente (le query non restituiscono Either). `loadPartTypes()` senza error handling — minor, pattern consistente per read-only.
- **Medium 51 scope corretto**: 5 use case (non 3): +RimuoviAssegnazioniSettimanaUseCase, +ImportaProclamatoriDaJsonUseCase.

## Zone verificate (Review Batch 8 — iterazione 1/3 ralph loop 2026-03-10)

- **Spec 004 (schemi-catalogo)**: allineato — FR-001 a FR-008 tutti implementati correttamente. `AggiornaSchemiUseCase` single-transaction, upsert part types, deactivate missing codes, replace templates atomically.
- **Spec 006 (stampa-output)**: FR-023 violato (→ Medium 69). Resto allineato.
- **SQL person queries**: inconsistenza `searchProclaimers` vs `allAssignableProclaimers`/`findProclaimerByIdExtended` (→ Low 69). Ranking queries OK (solo aggregati, non Proclamatore).
- **Core layer**: `AppBootstrap`, `AppPaths`, `AppRuntime`, `RemoteConfig` — infrastruttura corretta, nessun finding.
- **Dead code**: `SharedWeekState` unico dead code significativo (→ Low 70). Import orfano `mapProclamatoreRow` (→ Low 71). Nessun altro dead code in `feature/` e `core/`.
- **Domain invariant audit**: `Assignment` e `AssignmentWithPerson` usano throw via `init` (→ Medium 70). `SuggestedProclamatore` e `ProgramMonth` non hanno smart constructor ma sono DTO/value object semplici — non richiedono smart constructor pattern.

## Zone verificate senza nuovi finding (Review Batch 7 — deep verification 2026-03-10)

- **AssignmentManagementViewModel**: pulito — solo use case iniettati, `executeEitherOperation`/`executeAsyncOperation` usati correttamente, nessun throw/swallow.
- **PartEditorViewModel**: pulito — solo use case iniettati, error handling corretto.
- **PersonPickerViewModel**: pulito — `try-catch` in `loadSuggestions()` mostra errore nel banner (NON silent swallowing). `SuggerisciProclamatoriUseCase` non restituisce Either → catch è appropriato.
- **AssegnaPersonaUseCase.invoke()**: pattern `runInTransaction { assignWithoutTransaction() }` — stessa struttura di High 50 ma single-write-at-end → nessun commit parziale possibile.
- **AutoAssegnaProgrammaUseCase**: `doAssign()` chiama `assignWithoutTransaction` in loop ma gestisce `Left` come "unresolved slot" (best-effort) — comportamento intenzionale, non bug.
- **Store interface context(tx) audit completo**: `WeekPlanStore` ✅, `PartTypeStore` ✅, `AssignmentRepository` ✅, `PersonAssignmentLifecycle` ✅, `SchemaTemplateStore` ✅ — tutte le mutazioni hanno context. Solo le interfacce documentate in Medium 52 mancano.

## Zone verificate senza nuovi finding (Review Batch 6 — 2026-03-10)

- **SqlDelightWeekPlanStore**: mutazioni con `context(tx: TransactionScope)` corrette, nessuna nested transaction, helper privati chiamati solo da metodi con context.
- **SqlDelightAssignmentStore**: mutazioni tutte con context, `transactionWithResult` usato solo per lettura (ranking pre-load). Corretto.
- **SvuotaAssegnazioniProgrammaUseCase vs spec 005**: allineato — `count(programId, fromDate)` e `execute(programId, fromDate)` presenti e spec-compliant.
- **WeekPlanAggregate domain**: WeekPlan.of() smart constructor, validateAssignment(), reorderParts(), replaceParts() — tutti restituiscono Either correttamente, nessun IO nel domain, invarianti garantite dall'aggregato.
- **Core layer**: TransactionRunner, TransactionScope, DomainError (42 errori senza duplicati), AggregateStore, DatabaseProvider — tutto corretto.
- **DI modules**: CoreModule (7 binding unici), feature modules (nessun ciclo), ViewModelsModule (factory pattern). Nessun binding doppio.
- **feature/updates**: VerificaAggiornamenti e AggiornaApplicazione convertiti a Either (Batch 2), GitHubReleasesClient usa runCatching come fallback legittimo, DI module corretto.

## Findings risolti (Batch 5 — 2026-03-10)

- **Medium 2**: 6 integration test aggiunti per `PdfAssignmentsRenderer` — coprono `renderPersonSheetPdf` (contenuto testo, mkdir, assegnazioni vuote) e `renderWeeklyAssignmentsPdf` (contenuto, multi-page, mkdir). Pattern: renderer reale + `PDFTextStripper`, stesso stile di `PdfProgramRendererTest`.

## Findings risolti (Batch 4 — 2026-03-10)

- **High 2**: `StampaProgrammaUseCase` e `GeneraImmaginiAssegnazioni` convertiti a `Either<DomainError, T>`; `throw IllegalStateException` rimpiazzati con `raise(DomainError.NotFound)`; `renderTicketImage` ora ritorna `Either<DomainError, Path>` con try/catch al boundary infrastruttura; `AssignmentManagementViewModel` migrato da `executeAsyncOperation` a `executeEitherOperation`. Aggiunti test per percorsi di errore (NotFound, rendering fallito).
- **Medium 4**: `renderPdfToPngFile` (`Loader.loadPDF`, `PDFRenderer`, `ImageIO.write`) estratta da application layer a `infrastructure/PdfToImageConverter.kt`; iniettata come lambda `(Path, Path) -> Unit` — comportamento e testabilità invariati.

## Findings risolti (Batch A — 2026-03-10)

- **SqlDelightSchemaUpdateAnomalyStore**: ID deterministico da `"${personId}|${partTypeId}|${reason}".hashCode()` + `INSERT OR IGNORE`. Rimosso UUID casuale.
- **Finding 24**: `WeekPlan` smart constructor `of()` → `Either<DomainError, WeekPlan>`; costruttore `internal`; `init` IAE rimosso; `GeneraSettimaneProgrammaUseCase` aggiornato con `.bind()`.

## Findings risolti (Batch 3 — 2026-03-09)

- **High 46**: Estratti `ContaStoricoUseCase` (read-only) e `EliminaStoricoUseCase` (mutante con TransactionRunner + Either) da `DiagnosticsViewModel`. Nuovo modulo DI `DiagnosticsModule`. `MinisteroDatabase` rimosso dal ViewModel. VACUUM eseguito fuori transazione come da vincoli SQLite.
- **Medium 14**: Smart constructor `of()` aggiunto a `PartType` e `Proclamatore`; costruttori primari resi `internal`; `init` block rimosso; `KonformValidation.validate()` aggiunto; `PartTypeJsonParser` e `ImportaProclamatoriDaJsonUseCase` aggiornati.

## Findings risolti (Batch 2 — 2026-03-09)

- **Medium 11**: `VerificaAggiornamenti` → `Either<DomainError, UpdateCheckResult>`; rimosso `error: String?` da `UpdateCheckResult`; `UpdateStatusStore` aggiornato al nuovo tipo; `DiagnosticsViewModel.applyUpdateResult` e `checkUpdates` aggiornati.
- **High 47**: `AggiornaApplicazione.invoke()` → `Either<DomainError, Path>`; `throw IllegalStateException` → `raise(DomainError.Network(...))`; `DiagnosticsViewModel.startUpdate()` → `executeEitherOperation`.
- **Finding 49**: Rimosso fallback `"explorer.exe"` da `AggiornaApplicazione.openFile()` — ramo `else` eliminato.

## Findings risolti (Batch 1 — 2026-03-09)

- **Finding 43**: Rimossi dead params `storedProgramWeeks`/`storedProgramAssignments` da `PersonPickerViewModel` e caller `ProgramWorkspaceScreen.kt`.
- **Finding 44**: `mapAssignmentWithPersonRow` → `private` in `AssignmentRowMapper.kt`.
- **Finding 45**: `SchemaManagementViewModel.loadCurrentAndFuturePrograms()` ora restituisce `Either<DomainError, List<ProgramMonth>>` direttamente; eliminato `runCatching+throw` al call site.
- **Finding 48**: Invalidato — il codice in `PdfAssignmentsRenderer.kt` e `PdfProgramRenderer.kt` lancia già `IOException` se `mkdirs()` fallisce. Finding basato su osservazione errata.
- **Medium 10**: `TransactionRunner` usa `withContext(Dispatchers.IO)` + `runBlocking(coroutineContext)`; rimosso `@Suppress("BlockingMethodInNonBlockingContext")`; `@Suppress("UNCHECKED_CAST")` mantenuto con commento esplicativo.

## Verifiche eseguite

- `./gradlew :composeApp:jvmTest` → `BUILD SUCCESSFUL` (2026-03-06, post-fix low effort ciclo 4)
- Totale test JVM: `208` | Failure: `0` | Error: `0`
- Kover baseline: Line 39.9%, Method 35.8%, Branch 33.4% (esclusi `ui/`, `db/`, `core/cli/`)
- `./gradlew :composeApp:jvmTest` → `BUILD SUCCESSFUL` (2026-03-09, post library updates + context parameters migration + bug fix 3 test)
- Totale test JVM: `222` | Failure: `0` | Error: `0`
- `./gradlew :composeApp:jvmTest` → `BUILD SUCCESSFUL` (2026-03-09, Ralph Loop iterazione 6 — fix 13 low-effort findings)
- Totale test JVM: `222` | Failure: `0` | Error: `0`
- `./gradlew :composeApp:jvmTest` → `BUILD SUCCESSFUL` (2026-03-09, fix High 16 + Medium 35)
- Totale test JVM: `226` | Failure: `0` | Error: `0`
- `./gradlew :composeApp:jvmTest` → `BUILD SUCCESSFUL` (2026-03-09, Batch 0+1 findings fixer — 7 finding risolti)
- Totale test JVM: `226` | Failure: `0` | Error: `0`
- `./gradlew :composeApp:jvmTest` → `BUILD SUCCESSFUL` (2026-03-09, High 42 — context(TransactionScope) su AssignmentRepository)
- Totale test JVM: `226` | Failure: `0` | Error: `0`
- `./gradlew :composeApp:jvmTest` → `BUILD SUCCESSFUL` (2026-03-10, Batch 1+2+3+A findings fixer — 14 finding risolti)
- Totale test JVM: `226` | Failure: `0` | Error: `0`
- `./gradlew :composeApp:jvmTest` → `BUILD SUCCESSFUL` (2026-03-10, Batch 4 — High 2 + Medium 4)
- Totale test JVM: `231` | Failure: `0` | Error: `0`
- `./gradlew :composeApp:jvmTest` → `BUILD SUCCESSFUL` (2026-03-10, Batch 5 — Medium 2)
- Totale test JVM: `237` | Failure: `0` | Error: `0`

## Stato finale sintetico

Con i vincoli richiesti (DDD rigoroso, aggregate-root centric, transazione unica per use case mutante), stato attuale: **quasi production-ready**.

Aperti (2026-03-10, Review Batch 8 — 3 iterazioni ralph loop completate):

**High (2):**
- **High 57** — ContaStoricoUseCase/EliminaStoricoUseCase: zero test (confermato da 3+ agenti)
- **High 50** — AggiornaProgrammaDaSchemiUseCase: commit parziale (raise inside either inside runInTransaction) — confermato in iterazione 3 con analisi Arrow 2.2.1.1 `RaiseCancellationException` catching semantics

**Medium (14):**
- **Medium 51** — try-catch dentro either{} in **5** use case (corretto da 3 a 5)
- **Medium 52** — mutazioni senza context(tx) — SISTEMICO: 5 interfacce, 10 metodi (corretto: SchemaUpdateAnomalyStore.dismissAllOpen() verificato corretto)
- **Medium 53** — Proclamatore.of() non fa trim dei nomi
- **Medium 58** — CLI tools non integrati con Koin
- **Medium 62** — TransactionRunner fake duplicati in 3 test package
- **Medium 63** — IOException non catturata in either{} in **2** use case (StampaProgramma + GeneraImmagini.ensureOutputDir)
- **Medium 65** — DiagnosticsViewModel: throw RuntimeException per unwrap Either.Left
- **Medium 66** — Spec 002 disalignment: AggiornaDatiRemotiUseCase two-phase non implementato
- **Medium 67** — ProclamatoriListViewModel: silent exception swallowing nel conteggio assegnazioni
- **Medium 68** — ProclamatoreFormViewModel: private exception classes come control flow
- **Medium 69** — Spec 006 FR-023: ticket PDF mancante role label "(Studente)" nel formato
- **Medium 70** — Assignment/AssignmentWithPerson: throw in domain layer invece di smart constructor Either
- **Medium 71** — ContaStoricoUseCase/EliminaStoricoUseCase: MinisteroDatabase iniettato in application layer
- **Medium 15** — feature/updates zero test (in standby)

**Low (10):**
- **Low 54** — fold vs bind in RiordinaPartiUseCase
- **Low 55** — || true nel test RimuoviParteUseCaseTest
- **Low 56** — AutoAssegnaProgrammaUseCaseTransactionTest testa dettagli implementativi
- **Low 60** — WeekPlan.of() manca test per error paths
- **Low 61** — fetchRankingFromDb query eccessivi
- **Low 64** — runBlocking usato in 18+ test invece di runTest
- **Low 69** — searchProclaimers SQL manca can_assist → puoAssistere sempre false (bug latente, impatto attuale nullo)
- **Low 70** — SharedWeekState dead code (registrato in Koin, mai usato)
- **Low 71** — Import non usato mapProclamatoreRow in SqlDelightAssignmentStore
- **Low 72** — DomainError.SalvataggioPartiSettimanaFallito dead code (mai usato)

**Invalidati:** Medium 59 (UpdateStatusStore StateFlow — incapsulamento corretto), High 65 (ProgramLifecycleViewModel — errori fattuali nel finding, `.getOrElse { throw }` non presente)

Sessione 2026-03-10: risolti SqlDelightSchemaUpdateAnomalyStore (idempotenza), Finding 24 (WeekPlan smart constructor), Medium 2 (PdfAssignmentsRenderer test). Rimosso UpdateScheduler (check solo su richiesta). Aggiunto spec 007 aggiornamento applicazione. Aggiunto GitHub Actions workflow release. Prima release v0.1.0 taggata e pushata.

Review Batch 8 (ralph loop 3 iterazioni, 2026-03-10): 7 nuovi findings (Medium 69-71, Low 69-72), 3 findings corretti/ampliati (Medium 51 5 use case, Medium 63 2 use case, Medium 52 dismissAllOpen corretto). High 50 ri-confermato con analisi Arrow RaiseCancellationException. Koin DI e DomainError verificati completi. Tutte le zone principali coperte.

# Review Notes — Consolidato e Deduplicato (2026-03-09)

## Prompt sorgente di oggi

```text
Prima di iniziare:
1. Leggi docs/review/review-notes.md per conoscere i findings già trattati — non ripetere ciò che è già risolto.
   Verifica anche i findings aperti: se un finding non è più valido (codice cambiato, problema risolto incidentalmente,
   finding superato da refactor), rimuovilo dalla lista e spostalo in "Findings risolti" con una nota sintetica.
2. Identifica le zone "oscure" del codebase: feature o file poco esplorati nelle iterazioni precedenti,
   use case non coperti da test, moduli con nessuna review esistente (es. feature/output, feature/schemas,
   feature/planning, core/). Dai priorità a queste zone nell'analisi.

Valuta il progetto su:

Architettura DDD:
- Vertical slices, aggregate-root centrico, invarianti garantite dall'aggregato (no IO interno)
- Use case (1:1 con azione utente, confine transazionale) per orchestrazione IO
- Application service (riusabile da più entry point) se la stessa logica serve UI + batch + eventi
- Domain service per logica pura che attraversa più aggregati (mai IO)
- Infrastructure service: implementa contratti dichiarati dal dominio (DB, HTTP, PDF, file system)

Modello funzionale:
- Arrow, Either, DomainError usati correttamente
- Valuta se optics, newtypes, ADT/GADT migliorerebbero l'espressività — solo segnala, non implementare

Test:
- Coverage sulla logica pura (domain + use case)
- Integration test sui boundary esterni (HTTP, DB, PDF)
- Qualità dei test esistenti: valuta se ogni test è necessario e porta valore reale.
  Per ogni test considera: testa un comportamento distinto o è ridondante con un altro?
  Può essere rimosso perché copre solo un dettaglio implementativo fragile?
  Può essere accorpato con un test simile senza perdere leggibilità?
  Può essere rafforzato (asserzione più specifica, scenario più rappresentativo)?
  Può essere ridenominato per chiarire meglio l'intento?
  Segnala test che danno falsa sicurezza (passano sempre, non possono fallire per regression reali).

Qualità:
- Assenza di codice orfano, legacy, TODO
- Nessuna violazione DRY/SOLID/DDD
- Spec allineate al codice — in caso di disallineamento segnala senza correggere

Produzione:
- 1 utente, 1 sessione, no saga
- Ogni use case mutante apre esattamente 1 transazione via `TransactionRunner.runInTransaction { }`.
  Il blocco lambda riceve implicitamente `TransactionScope` come receiver: le funzioni di store
  dichiarate `context(TransactionScope)` possono essere chiamate solo dentro quel blocco —
  il compilatore lo forza staticamente (capability token pattern).
  Conseguenze verificabili: nessun use case deve aprire transazioni annidate; nessuna funzione
  `context(TransactionScope)` deve essere chiamata fuori da `runInTransaction`; use case read-only
  non richiedono transazione.

Se i task di analisi sono indipendenti, usa agenti paralleli.
Produci i findings ordinati per severità.
```

## Findings aperti (ordinati per severità)

### High

2. `feature/output`: tutti e tre i use case usano `throw IllegalStateException` invece di `Either<DomainError, T>`.
   - Pattern inconsistente con tutto il resto del codebase; il ViewModel usa `runCatching` come workaround.
   - `GeneraImmaginiAssegnazioni` aggiunge un ulteriore throw in `renderTicketImage()` (line 258-261) — espansione del problema con nuovi metodi.
   - Evidenze: `StampaProgrammaUseCase.kt:117`, `GeneraPdfAssegnazioni.kt:30`, `GeneraImmaginiAssegnazioni.kt:83,104,258-261`.

3. `SqlDelightSchemaTemplateStore.replaceAll()` e `SqlDelightSchemaUpdateAnomalyStore.append()` aprono transazione manuale interna.
   - `database.ministeroDatabaseQueries.transaction { }` in entrambi crea una transazione **nidificata** dentro `transactionRunner.runInTransaction { }` di `AggiornaSchemiUseCase`.
   - Viola il pattern "esattamente 1 transazione per use case mutante". L'atomicità non è garantita.
   - Evidenze: `SqlDelightSchemaTemplateStore.kt:15`, `SqlDelightSchemaUpdateAnomalyStore.kt:16`, `AggiornaSchemiUseCase.kt:56,82`.

4. `AggiornaSchemiUseCase.kt:75` — `checkNotNull()` dentro transazione non mappato a `DomainError`.
   - Se succede, lancia `IllegalStateException` non catturata. Unico punto nel codebase dove un errore interno alla transazione bypassa il modello Either. Fix richiede aggiungere `allByCode()` allo store o ristrutturare la tx (medium effort, misclassificato inizialmente).
   - Evidenza: `AggiornaSchemiUseCase.kt:75`.

17. `SqlDelightProclamatoriStore.persistAll()` e `SqlDelightPartTypeStore.upsertAll()` / `deactivateMissingCodes()` aprono transazioni interne — nidificata come High-3.
    - Quando chiamati da dentro `AggiornaSchemiUseCase` (già in `runInTransaction`), il commit interno avviene prima dell'outer. Colpisce anche `ImportaProclamatoriDaJsonUseCase` (nessuna outer transaction).
    - Evidenze: `SqlDelightProclamatoriStore.kt:23`, `SqlDelightPartTypeStore.kt:46,97`, `AggiornaSchemiUseCase.kt:69-70`, `ImportaProclamatoriDaJsonUseCase.kt:45`.

42. `AssignmentRepository` interface — tutti i metodi di mutazione privi di `context(TransactionScope)`.
    - Il problema è nell'**interfaccia** `AssignmentStore.kt` (righe 17-23): `save`, `remove`, `removeAllByWeekPlan`, `removeAllForPerson`, `deleteByProgramFromDate` sono `suspend` plain. A differenza di `WeekPlanStore` che dichiara `context(tx: TransactionScope) suspend fun saveAggregate(...)`, il boundary assignment non ha nessun enforcement statico.
    - Il compilatore non impedisce di chiamare queste mutation fuori da `runInTransaction`. Altri use case che chiamano `save()` dentro `runInTransaction` sono corretti solo per disciplina, non per contratto.
    - Evidenze: `AssignmentStore.kt:17-23`, `WeekPlanStore.kt:25-27` (confronto — pattern atteso), `SqlDelightAssignmentStore.kt:43,52,56,223,227`.

### Medium

36. `AggiornaProgrammaDaSchemiUseCase` — eccezione privata `RefreshFailed` come control flow interno.
    - `private class RefreshFailed(val error: DomainError) : Exception()` (riga 31) viene lanciata per uscire da un `fold` annidato (riga 139) e catturata immediatamente (riga 99) per convertire a `raise(e.error)`. Anti-pattern: si usa un'eccezione come `goto` dentro un blocco `either{}`. Alternativa: usare `Raise<DomainError>` direttamente nel metodo interno.
    - Rischio contenuto (catch immediato), ma difficile da leggere e fragile se la catena di chiamate cambia.
    - Evidenza: `AggiornaProgrammaDaSchemiUseCase.kt:31,99,139`.

2. Copertura integration migliorabile sui boundary esterni.
   - HTTP client e PDF rendering ancora poco coperti.
   - Evidenze: `GitHubSchemaCatalogDataSource.kt:40`, `GitHubReleasesClient.kt:38`.

4. `GeneraImmaginiAssegnazioni`: logica PDF→PNG (`renderPdfToPng`) nel layer application; inietta use case invece di store.
   - `Loader.loadPDF`, `PDFRenderer`, `ImageIO.write` sono infrastruttura; dovrebbero stare in `infrastructure/`.
   - Evidenza: `GeneraImmaginiAssegnazioni.kt:107-112`, `:13-15`.

10. `TransactionRunner.kt:29-30` — `runBlocking` dentro la coroutine della transazione.
    - `@Suppress("BlockingMethodInNonBlockingContext")` nasconde il problema. `@Suppress("UNCHECKED_CAST")` a riga 39 nasconde un unsafe cast di `Any` a `T`.
    - Basso rischio in produzione (desktop single-user), ma fragilità architettonica.
    - Evidenza: `core/persistence/TransactionRunner.kt:29,39`.

11. `VerificaAggiornamenti` non usa `Either<DomainError, T>`.
    - Restituisce `UpdateCheckResult` con `error: String?` invece di `Either`. Inconsistente con il resto del codebase.
    - Evidenza: `feature/updates/application/VerificaAggiornamenti.kt:21-59`.

14. `KonformValidation.requireValid()` lancia `IllegalArgumentException` invece di restituire `Either`.
   - Violazione della regola "domain non lancia eccezioni".
   - Callers: `PartType.init` (weeklyparts/domain/PartType.kt:38-40), `Proclamatore.init` (people/domain/Proclamatore.kt:39-44) — construction-time exception non mappata a DomainError.
   - Evidenza: `core/domain/KonformValidation.kt:10`.

15. `feature/updates` — zero test coverage.
    - `VerificaAggiornamenti`, `AggiornaApplicazione`, `GitHubReleasesClient`, `UpdateScheduler` non hanno nessun test.
    - Evidenza: `feature/updates/application/*.kt`, `feature/updates/infrastructure/*.kt`.

21. `SuggerisciProclamatoriUseCase` — N+1 residuo su `assignmentRepository.listByWeek()` per rilevazione `requiredSex`.
   - **Parzialmente risolto**: in `AutoAssegnaProgrammaUseCase` il batch load è ottimizzato (`listByWeekPlanIds` + `preloadSuggestionRanking`). Il N+1 persiste solo nel flusso **manuale** (`SuggerisciProclamatoriUseCase.kt:48`).
   - Evidenze: `SuggerisciProclamatoriUseCase.kt:48`, `AutoAssegnaProgrammaUseCase.kt:52,61`.

### Low

- `SqlDelightSchemaUpdateAnomalyStore.append()` non è idempotente: ogni chiamata genera nuovo UUID, retry accumula duplicati.
- **Finding 24**: `WeekPlan` init block lancia `IllegalArgumentException` se `weekStartDate` non è lunedì. Pattern non-funzionale. Alcuni test passano date non-lunedì senza conseguenze visibili oggi. Alternativa DDD: smart constructor `WeekPlan.of()` → `Either`.
  - Evidenze: `WeekPlan.kt:22-26`, `DomainErrorMappingWeeklyPartsUseCaseTest.kt:92`.
- **Finding 25**: `AssignmentWithPerson` init block lancia `IllegalArgumentException` se `slot < 1`. Se un record DB corrotto ha `slot=0`, l'app crasha nel load invece di restituire un `DomainError`.
  - Evidenze: `AssignmentWithPerson.kt:15`, `SqlDelightAssignmentStore.kt:36`.
- **Finding 37**: `CaricaProgrammiAttiviUseCase` — restituisce `ProgramSelectionSnapshot` direttamente senza `Either`. Use case read-only: nessun rischio transazionale, ma inconsistente con il pattern codebase (la maggior parte delle query restituisce Either). Se il repository lancia un'eccezione, il ViewModel non la gestisce come DomainError.
  - Evidenza: `CaricaProgrammiAttiviUseCase.kt:15`.
- **Finding 38**: `SuggerisciProclamatoriUseCase` — `alreadyAssignedIds: Set<ProclamatoreId> = emptySet()` non viene aggiornato internamente tra chiamate successive per slot consecutivi della stessa parte. Il caller (ViewModel) deve passare un set aggiornato ad ogni invocazione. Contratto fragile: se il VM non aggiorna il set, la stessa persona può essere suggerita per slot 1 e slot 2 della stessa parte.
  - Evidenza: `SuggerisciProclamatoriUseCase.kt:24,81`.

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

## Stato finale sintetico

Con i vincoli richiesti (DDD rigoroso, aggregate-root centric, transazione unica per use case mutante), stato attuale: **non ancora production-ready**.

Aree più problematiche: (1) feature/output fuori dal modello Either (High 2), (2) transazioni nidificate/mancanti in schemas (High 3, High 17) e use case senza TX (High 4), (3) `AssignmentRepository` interface senza capability token (High 42), (4) copertura test zero su feature/updates (Medium 15), (5) Medium 36 (RefreshFailed anti-pattern in AggiornaProgrammaDaSchemiUseCase).

Sessione 2026-03-09 (1): verificata feature/output post-implementazione biglietti. High 1 risolto. Aggiunti Finding 33 (GeneraPdfAssegnazioni dead code), Finding 34 (mkdirs unchecked). Aggiornate evidenze linea per High 2 e Medium 6.
Sessione 2026-03-09 (2): aggiunto ordinamento biglietti per sortOrder parte + partNumber in AssignmentTicketLine. Aggiunto Finding 41 (PART_DISPLAY_NUMBER_OFFSET DRY violation). 222 test, 0 failure.
Sessione 2026-03-09 (3): review di verifica post-modifiche. Confermati validi: High 2, High 16, Medium 35, Medium 36, Finding 33. `AutoAssegnaProgrammaUseCase` non restituisce `Either` ma gestisce errori via lista `unresolved` — pattern intenzionale (partial success), non aggiunto come finding. Nessun nuovo finding.
Ralph Loop iterazioni 2-5: analisi parallela di feature/people, feature/programs, feature/assignments, core/, feature/schemas, feature/updates. Aggiunti Medium 35 (ImpostaIdoneita senza TX), Medium 36 (RefreshFailed anti-pattern), Finding 37 (CaricaProgrammiAttiviUseCase no Either), Finding 38 (alreadyAssignedIds fragile contract), Finding 39 (MAX_FUTURE_PROGRAMS domain→application layer violation), Finding 40 (InMemoryProgramStore DRY), High 42 (AssignmentRepository interface senza context(TransactionScope) — diversamente da WeekPlanStore che lo ha). Aggiornato Medium 14 con callers PartType e Proclamatore. Confermato: no TODO nel codebase; tutte le weeklyparts mutation use case hanno TransactionRunner; AggiornaProgrammaDaSchemiUseCase correttamente wired con TransactionRunner; dryRun=true path testato.
Ralph Loop iterazione 6 (fix low-effort): risolti High 5, Medium 6, Medium 12, Medium 18, Finding 29, 30, 31, 32, 33, 34, 39, 40, 41. Finding 37 lasciato aperto intenzionalmente (troppi caller VM da aggiornare, rischio sproporzionato per use case read-only). BUILD SUCCESSFUL, test tutti verdi.
Sessione successiva: risolti High 16 e Medium 35 (TransactionRunner per use case people). Estratto ImmediateTransactionRunner in PeopleTestFixtures.kt. Aggiunti 4 test ImpostaIdoneita. 226 test, 0 failure.

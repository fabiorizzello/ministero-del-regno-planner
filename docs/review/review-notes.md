# Review Notes — Consolidato e Deduplicato (2026-03-06)

## Prompt sorgente di oggi

```text
Prima di iniziare:
1. Leggi docs/review/review-notes.md per conoscere i findings già trattati — non ripetere ciò che è già risolto.
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

1. Output/stampa non pienamente esposto nella UX operativa.
   - Use case e DI presenti, wiring UI incompleto.
   - Evidenze: `ProgramWorkspaceScreen.kt:725`, `AssignmentManagementViewModel.kt:182`, `feature/output/di/OutputModule.kt:14`.

2. `feature/output`: tutti e tre i use case usano `throw IllegalStateException` invece di `Either<DomainError, T>`.
   - Pattern inconsistente con tutto il resto del codebase; il ViewModel usa `runCatching` come workaround.
   - Evidenze: `StampaProgrammaUseCase.kt:31`, `GeneraPdfAssegnazioni.kt:29`, `GeneraImmaginiAssegnazioni.kt:33`.

3. `SqlDelightSchemaTemplateStore.replaceAll()` e `SqlDelightSchemaUpdateAnomalyStore.append()` aprono transazione manuale interna.
   - `database.ministeroDatabaseQueries.transaction { }` in entrambi crea una transazione **nidificata** dentro `transactionRunner.runInTransaction { }` di `AggiornaSchemiUseCase`.
   - Viola il pattern "esattamente 1 transazione per use case mutante". L'atomicità non è garantita.
   - Evidenze: `SqlDelightSchemaTemplateStore.kt:15`, `SqlDelightSchemaUpdateAnomalyStore.kt:16`, `AggiornaSchemiUseCase.kt:56,82`.

4. `AggiornaSchemiUseCase.kt:75` — `checkNotNull()` dentro transazione non mappato a `DomainError`.
   - Se succede, lancia `IllegalStateException` non catturata. Unico punto nel codebase dove un errore interno alla transazione bypassa il modello Either. Fix richiede aggiungere `allByCode()` allo store o ristrutturare la tx (medium effort, misclassificato inizialmente).
   - Evidenza: `AggiornaSchemiUseCase.kt:75`.

5. `core/config/UpdateSettingsStore.kt` importa da `feature/updates` — dipendenza di layer invertita.
   - `UpdateChannel` dovrebbe stare in `core/domain` o `core/config`. Crea accoppiamento circolare logico.
   - Evidenza: `core/config/UpdateSettingsStore.kt:5`.

16. `AggiornaProclamatoreUseCase` e `CreaProclamatoreUseCase` — mutazioni senza transazione.
    - Entrambi modificano la persistenza (`store.persist(...)`) senza aprire `transactionRunner.runInTransaction { }`.
    - Per `AggiornaProclamatoreUseCase`: il profilo può essere persistito mentre la lettura di `listFutureAssignmentWeeks` fallisce — stato inconsistente.
    - Evidenze: `AggiornaProclamatoreUseCase.kt:48`, `CreaProclamatoreUseCase.kt:39`.

17. `SqlDelightProclamatoriStore.persistAll()` e `SqlDelightPartTypeStore.upsertAll()` / `deactivateMissingCodes()` aprono transazioni interne — nidificata come High-3.
    - Quando chiamati da dentro `AggiornaSchemiUseCase` (già in `runInTransaction`), il commit interno avviene prima dell'outer. Colpisce anche `ImportaProclamatoriDaJsonUseCase` (nessuna outer transaction).
    - Evidenze: `SqlDelightProclamatoriStore.kt:23`, `SqlDelightPartTypeStore.kt:46,97`, `AggiornaSchemiUseCase.kt:69-70`, `ImportaProclamatoriDaJsonUseCase.kt:45`.

### Medium

2. Copertura integration migliorabile sui boundary esterni.
   - HTTP client e PDF rendering ancora poco coperti.
   - Evidenze: `GitHubSchemaCatalogDataSource.kt:40`, `GitHubReleasesClient.kt:38`.

4. `GeneraImmaginiAssegnazioni`: logica PDF→PNG (`renderPdfToPng`) nel layer application; inietta use case invece di store.
   - `Loader.loadPDF`, `PDFRenderer`, `ImageIO.write` sono infrastruttura; dovrebbero stare in `infrastructure/`.
   - Evidenza: `GeneraImmaginiAssegnazioni.kt:107-112`, `:13-15`.

6. `AppRuntime.paths()` chiamato direttamente nei use case di output (singleton globale non iniettato).
   - Non testabile; viola dependency inversion.
   - Evidenze: `StampaProgrammaUseCase.kt:57`, `GeneraPdfAssegnazioni.kt:55`, `GeneraImmaginiAssegnazioni.kt:47`.

10. `TransactionRunner.kt:29-30` — `runBlocking` dentro la coroutine della transazione.
    - `@Suppress("BlockingMethodInNonBlockingContext")` nasconde il problema. `@Suppress("UNCHECKED_CAST")` a riga 39 nasconde un unsafe cast di `Any` a `T`.
    - Basso rischio in produzione (desktop single-user), ma fragilità architettonica.
    - Evidenza: `core/persistence/TransactionRunner.kt:29,39`.

11. `VerificaAggiornamenti` non usa `Either<DomainError, T>`.
    - Restituisce `UpdateCheckResult` con `error: String?` invece di `Either`. Inconsistente con il resto del codebase.
    - Evidenza: `feature/updates/application/VerificaAggiornamenti.kt:21-59`.

12. `GeneraPdfAssegnazioni` inietta use case invece di store (stesso problema di Medium 4).
    - Evidenza: `GeneraPdfAssegnazioni.kt:17-18`.

14. `KonformValidation.requireValid()` lancia `IllegalArgumentException` invece di restituire `Either`.
   - Violazione della regola "domain non lancia eccezioni".
   - Evidenza: `core/domain/KonformValidation.kt:10`.

15. `feature/updates` — zero test coverage.
    - `VerificaAggiornamenti`, `AggiornaApplicazione`, `GitHubReleasesClient`, `UpdateScheduler` non hanno nessun test.
    - Evidenza: `feature/updates/application/*.kt`, `feature/updates/infrastructure/*.kt`.

18. `SvuotaAssegnazioniProgrammaUseCase.execute()` — mutazione senza transazione e senza `Either<DomainError, T>`.
    - La firma restituisce `Int` invece di `Either<DomainError, Int>` — il ViewModel gestisce errori via `runCatching`.
    - Evidenze: `SvuotaAssegnazioniProgrammaUseCase.kt:12-13`, `AssignmentManagementViewModel.kt:243`.

21. `SuggerisciProclamatoriUseCase` — N+1 residuo su `assignmentRepository.listByWeek()` per rilevazione `requiredSex`.
   - In `AutoAssegnaProgrammaUseCase` genera N×M query extra; i dati sono già in `assignmentsByWeek` ma non passati al figlio.
   - Evidenze: `SuggerisciProclamatoriUseCase.kt:48`, `AutoAssegnaProgrammaUseCase.kt:65-80`.

### Low

- `SqlDelightSchemaUpdateAnomalyStore.append()` non è idempotente: ogni chiamata genera nuovo UUID, retry accumula duplicati.
- **Finding 24**: `WeekPlan` init block lancia `IllegalArgumentException` se `weekStartDate` non è lunedì. Pattern non-funzionale. Alcuni test passano date non-lunedì senza conseguenze visibili oggi. Alternativa DDD: smart constructor `WeekPlan.of()` → `Either`.
  - Evidenze: `WeekPlan.kt:22-26`, `DomainErrorMappingWeeklyPartsUseCaseTest.kt:92`.
- **Finding 25**: `AssignmentWithPerson` init block lancia `IllegalArgumentException` se `slot < 1`. Se un record DB corrotto ha `slot=0`, l'app crasha nel load invece di restituire un `DomainError`.
  - Evidenze: `AssignmentWithPerson.kt:15`, `SqlDelightAssignmentStore.kt:36`.
- **Finding 29**: `ImportaProclamatoriDaJsonUseCase` — nessuna outer transaction; `persistAll` apre la propria (High 17). Import di N proclamatori non è atomico.
  - Evidenze: `ImportaProclamatoriDaJsonUseCase.kt:44-48`, `SqlDelightProclamatoriStore.kt:22-28`.

---

## Findings risolti

- **DRY creazione mese**, **smart constructor proclamatore**, **invarianti settimana auto-protette**, **encapsulamento assegnazioni**, **VM layer violation** — commit `e71ca70`.
- **Medium 5** `StampaProgrammaUseCase` N+1 — batch con `listByWeekPlanIds`. Commit `e4791ef`.
- **Medium 8** Copertura test gap residui — 202 test, 0 failure. Baseline Kover: Line 39.9% / Method 35.8% / Branch 33.4%.
- **Medium 9** Nessun test ViewModel — aggiunti `AssignmentManagementViewModelTest`, `ProclamatoreFormViewModelTest`, `SchemaManagementViewModelTest`, `ProgramLifecycleViewModelTest`.
- **Bug critico**: `kotlinx.serialization` compiler plugin mancante → `ImportaProclamatoriDaJsonUseCase` silenziosamente rotta. Plugin aggiunto.
- **Bug**: `RimuoviAssegnazioniSettimanaUseCase` — `raise(NotFound)` spostato fuori dal `try/catch`.
- **Low `AggiornaSchemiUseCase.kt:30`** — `runCatching` → `Either.catch + mapLeft + bind`.
- **Finding 27** `WeekPlanAggregate.reorderParts()` — `checkNotNull` → `getValue` (unreachable path).
- **Finding 28** test persona sospesa — rinominato in `persona non inclusa nel ranking SQL non compare nei suggeriti`.
- **Medium 20** `AggiungiParteUseCase` N+1 post-save — restituisce `updated.weekPlan` in-memory.
- **Medium 22** `PartEditorViewModel.loadPartTypes()` — `try/catch` → `executeAsyncOperation`.
- **Medium 13** `GitHubSchemaCatalogDataSource.fetchCatalog()` — `RuntimeException` → `IOException`, con mapping coerente a `DomainError.Network` via `AggiornaSchemiUseCase`. Aggiunto `GitHubSchemaCatalogDataSourceTest`.
- **Medium 19** `SqlDelightAssignmentStore.save()` — rimosso wrapper `IllegalStateException`, ora propaga l'errore DB nativo.
- **Medium 23** `ProgramLifecycleViewModel.loadAssignmentsByWeek()` — rimosso fallback silenzioso: ora mostra notice di errore e non maschera fault DB. Aggiunto `ProgramLifecycleViewModelLoadAssignmentsErrorTest`.
- **Low (rollback test)** `AggiornaSchemiUseCaseTransactionTest` ora copre rollback a metà transazione (`replaceAll` failure).
- **Finding 26** `PartTypeJsonParser.parsePartTypeFromJson()` — `error()` sostituito con `Either<DomainError.ImportContenutoNonValido, PartType>`. Aggiunto `PartTypeJsonParserTest`.

## Verifiche eseguite

- `./gradlew :composeApp:jvmTest` → `BUILD SUCCESSFUL` (2026-03-06, post-fix low effort ciclo 4)
- Totale test JVM: `208` | Failure: `0` | Error: `0`
- Kover baseline: Line 39.9%, Method 35.8%, Branch 33.4% (esclusi `ui/`, `db/`, `core/cli/`)

## Stato finale sintetico

Con i vincoli richiesti (DDD rigoroso, aggregate-root centric, transazione unica per use case mutante), stato attuale: **non ancora production-ready**.

Aree più problematiche: (1) feature/output fuori dal modello Either (High 2), (2) transazioni nidificate/mancanti in people + schemas (High 3, 16, 17), (3) dipendenza invertita core←feature/updates (High 5), (4) copertura test zero su feature/updates (Medium 15).

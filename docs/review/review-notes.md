# Review Notes — Consolidato e Deduplicato (2026-03-05)

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

2. feature/output: tutti e tre i use case usano `throw IllegalStateException` invece di `Either<DomainError, T>`.
   - Pattern inconsistente con tutto il resto del codebase; il ViewModel usa `runCatching` come workaround.
   - Evidenze: `StampaProgrammaUseCase.kt:31`, `GeneraPdfAssegnazioni.kt:29`, `GeneraImmaginiAssegnazioni.kt:33`.

3. `RimuoviAssegnazioneUseCase`: operazione mutante (`assignmentStore.remove(id)`) senza `runInTransaction`.
   - Use case mutante che non apre transazione: viola il contratto "1 mutant use case = 1 transaction". L'errore viene catturato da un `try/catch` inside `either{}`, ma la singola write non è protetta.
   - Evidenza: `RimuoviAssegnazioneUseCase.kt:11-18`.

4. `AggiornaProgrammaDaSchemiUseCase`: `throw IllegalStateException` dentro `context(TransactionScope)` function.
   - La lambda transazionale lancia un'eccezione non tipata anziché tornare `Either.Left`; il rollback avviene ma il caller riceve un'eccezione non strutturata, non un `DomainError`. La firma `Either<DomainError, SchemaRefreshReport>` non può mai produrre `Left` da dentro la transazione.
   - Evidenza: `AggiornaProgrammaDaSchemiUseCase.kt:133` (`ifLeft = { throw IllegalStateException(...) }`).

### Medium

1. Performance aperta su auto-assign (N+1 query).
   - 8 query per slot via `suggerisciProclamatori`; un mese standard genera ~128 query.
   - Evidenze: `SqlDelightAssignmentStore.kt:57`, `AutoAssegnaProgrammaUseCase.kt:52`.

2. Copertura integration migliorabile sui boundary esterni.
   - HTTP client e PDF rendering ancora poco coperti.
   - Evidenze: `GitHubSchemaCatalogDataSource.kt:40`, `GitHubReleasesClient.kt:38`, `StampaProgrammaUseCaseTest.kt:9`.

3. `apriFile()` collocata in `output/application/` ma contiene IO OS (Desktop.open, ProcessBuilder xdg-open).
   - È un infrastructure service, non logica di applicazione. Dovrebbe essere un'interfaccia dichiarata in application e implementata in infrastructure, iniettata nei use case.
   - Evidenze: `ApriFile.kt:9-19`, chiamata diretta da `StampaProgrammaUseCase.kt:67` e `GeneraPdfAssegnazioni.kt:59`.

4. `GeneraImmaginiAssegnazioni`: logica di rendering PDF→PNG (`renderPdfToPng`) nel layer application.
   - `Loader.loadPDF`, `PDFRenderer`, `ImageIO.write` sono infrastruttura; dovrebbero stare in `infrastructure/`.
   - La classe inietta `CaricaSettimanaUseCase` e `CaricaAssegnazioniUseCase` come dipendenze — i use case non dovrebbero comporre altri use case; dovrebbero dipendere da store/repository diretti.
   - Evidenza: `GeneraImmaginiAssegnazioni.kt:107-112` e `:13-15`.

5. `StampaProgrammaUseCase`: N+1 query (1 `listByWeek` per ogni settimana del programma).
   - 4-5 settimane = 5-6 query invece di 1-2. Dovrebbe caricare tutte le assegnazioni del programma in batch.
   - Evidenza: `StampaProgrammaUseCase.kt:33-35`.

6. `AppRuntime.paths()` chiamato direttamente nei use case di output (singleton globale non iniettato).
   - Non testabile; viola dependency inversion. `AppPaths` dovrebbe essere iniettato nel costruttore.
   - Evidenze: `StampaProgrammaUseCase.kt:57`, `GeneraPdfAssegnazioni.kt:55`, `GeneraImmaginiAssegnazioni.kt:47`.

7. `RimuoviParteUseCase`: caricamento post-transazione fuori dal contesto transazionale.
   - Dopo il salvataggio, il use case rilegge l'aggregato fuori transazione per verifica — la verifica è inaffidabile (race window) e l'eventuale errore non è gestito via Either.
   - Evidenza: `RimuoviParteUseCase.kt:31-32`.

8. Copertura test: 44% dei use case senza alcun test (16 su 36).
   - Use case critici non coperti: `AssegnaPersonaUseCase`, `SuggerisciProclamatoriUseCase`, `SvuotaAssegnazioniProgrammaUseCase`, `RimuoviAssegnazioniSettimanaUseCase`, `AggiornaProclamatoreUseCase` (outcome `futureWeeksWhereAssigned`), `ImportaProclamatoriDaJsonUseCase`, `AggiornaPartiSettimanaUseCase`, `RimuoviParteUseCase`, `ImpostaStatoSettimanaUseCase`, `CreaProssimoProgrammaUseCase`, `AggiornaSchemiUseCase`.
   - Il domain layer è ben coperto; il gap è sui boundary con la persistence.

## Findings risolti (commit e71ca70 — 2026-03-06)

- **DRY creazione mese**: `computeCreatableTargets` delegava la logica duplicata — ora delega a `ProgramMonthAggregate.validateCreationTarget`.
- **Smart constructor proclamatore**: `ProclamatoreAggregate.create` e `updateProfile` restituiscono `Either<DomainError, ...>`; validazione nome/cognome spostata dall'application al domain layer.
- **Invarianti settimana auto-protette**: mutatori `addPart`, `removePart`, `reorderParts`, `replaceParts` verificano `canBeMutated` internamente; `AggiornaPartiSettimana` e `RiordinaParti` ora coperti (prima senza guardia); `ImpostaStatoSettimana` blocca solo SKIPPED (riattivazione libera).
- **Encapsulamento assegnazioni**: aggiunti `addAssignment` e `clearAssignments` all'aggregato; `AssegnaPersonaUseCase` e `RimuoviAssegnazioniSettimana` non usano più `aggregate.copy()` diretto.
- **VM layer violation**: `PartEditorViewModel.skipWeek` non applica più regole domain; `openPartEditor` mantiene la guardia UX (nessun use case corrispondente).

## Verifiche eseguite

- `./gradlew :composeApp:jvmTest --rerun-tasks` => `BUILD SUCCESSFUL` (2026-03-06)
- Totale test JVM: `109`
- Failure: `0`
- Error: `0`
- Use case totali: `36`; con test diretto: `7 (19%)`; con test indiretto: `13 (36%)`; senza test: `16 (44%)`

## Stato finale sintetico

Con i vincoli richiesti (DDD rigoroso, aggregate-root centric, transazione unica per use case mutante), stato attuale: **non ancora production-ready**.

Aree più problematiche: (1) feature/output completamente fuori dal modello Either, (2) throw dentro TransactionScope in AggiornaProgrammaDaSchemi, (3) copertura test insufficiente su use case mutanti.

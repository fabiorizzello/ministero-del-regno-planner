# Review Notes — Consolidato e Deduplicato (2026-03-05)

## Prompt sorgente di oggi

```text
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
- 1 utente, 1 sessione, no saga, transazione unica per use case mutante

Se i task di analisi sono indipendenti, usa agenti paralleli.
Produci i findings ordinati per severità.
```

## Findings aperti (ordinati per severità)

### High

1. Output/stampa non pienamente esposto nella UX operativa.
   - Use case e DI presenti, wiring UI incompleto.
   - Evidenze: `ProgramWorkspaceScreen.kt:725`, `AssignmentManagementViewModel.kt:182`, `feature/output/di/OutputModule.kt:14`.

### Medium

1. Performance aperta su auto-assign (N+1 query).
   - 8 query per slot via `suggerisciProclamatori`; un mese standard genera ~128 query.
   - Evidenze: `SqlDelightAssignmentStore.kt:57`, `AutoAssegnaProgrammaUseCase.kt:52`.

2. Copertura integration migliorabile sui boundary esterni.
   - HTTP client e PDF rendering ancora poco coperti.
   - Evidenze: `GitHubSchemaCatalogDataSource.kt:40`, `GitHubReleasesClient.kt:38`, `StampaProgrammaUseCaseTest.kt:9`.

## Findings risolti (commit e71ca70 — 2026-03-06)

- **DRY creazione mese**: `computeCreatableTargets` delegava la logica duplicata — ora delega a `ProgramMonthAggregate.validateCreationTarget`.
- **Smart constructor proclamatore**: `ProclamatoreAggregate.create` e `updateProfile` restituiscono `Either<DomainError, ...>`; validazione nome/cognome spostata dall'application al domain layer.
- **Invarianti settimana auto-protette**: mutatori `addPart`, `removePart`, `reorderParts`, `replaceParts` verificano `canBeMutated` internamente; `AggiornaPartiSettimana` e `RiordinaParti` ora coperti (prima senza guardia); `ImpostaStatoSettimana` blocca solo SKIPPED (riattivazione libera).
- **Encapsulamento assegnazioni**: aggiunti `addAssignment` e `clearAssignments` all'aggregato; `AssegnaPersonaUseCase` e `RimuoviAssegnazioniSettimana` non usano più `aggregate.copy()` diretto.
- **VM layer violation**: `PartEditorViewModel.skipWeek` non applica più regole domain; `openPartEditor` mantiene la guardia UX (nessun use case corrispondente).

## Verifiche eseguite

- `./gradlew :composeApp:jvmTest --rerun-tasks` => `BUILD SUCCESSFUL`
- Totale test JVM: `109`
- Failure: `0`
- Error: `0`

## Stato finale sintetico

Con i vincoli richiesti (DDD rigoroso, aggregate-root centric, transazione unica per use case mutante), stato attuale: **non ancora production-ready**.

# Review Notes — Consolidato e Deduplicato (2026-03-05)

## Prompt sorgente di oggi

### Prompt utente principale
```text
fai valutazione del progetto dimmi come è messo riguardo features complete, DDD vertical slices, rich domain models, invariant, aggregate root centrico. aggregato puro e use case per orchestrazine IO, services per servizi trasversali. test coverage soprattutto di logica pura e poi integration tests. il progetto dovrebbe usare concetti DDD rich model e funzionale, arrow, either, domain error. valuta se necessari optics in applicazione o altre migliorie per scandire meglio il domain(es monoid, semigroup, newtypes, adt, gadt ecc). fammi valutazione se production ready. 1 utente 1 sessione, no saga, unica transazione per use case. valuta assenza codice orfano o legacy. se necessario separa questa attività in più agenti paralleli ordinati e coesi
```

### Prompt operativo complementare
```text
rieffettua verifica per capire se abbiamo tralasciato qualcosa. bugs, orfani legacy, todo, non DRY SOLID DDD vertical slice rich domain e spec non allineate
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

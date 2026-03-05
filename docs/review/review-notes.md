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

## Findings deduplicati (ordinati per severità)

### High

1. Output/stampa non pienamente esposto nella UX operativa.
   - Use case e DI presenti, wiring UI incompleto.
   - Evidenze: `ProgramWorkspaceScreen.kt:725`, `AssignmentManagementViewModel.kt:182`, `feature/output/di/OutputModule.kt:14`.

### Medium

1. Rich domain model non uniforme tra bounded context.
   - `WeekPlanAggregate` più maturo; `ProgramMonth` e `Person` ancora parziali.
   - Evidenze: `WeekPlanAggregate.kt`, `ProgramMonthAggregate.kt:11`, `ProclamatoreAggregate.kt:3`.

2. Invarianti settimana non sempre incapsulate nel root.
   - Controllo mutabilità presente in alcuni use case ma non uniforme sui mutator.
   - Evidenze: `WeekPlan.kt:38`, `AggiornaPartiSettimanaUseCase.kt:24`, `RiordinaPartiUseCase.kt:18`, `ImpostaStatoSettimanaUseCase.kt:18`.

3. Duplicazione regole tra dominio e UI (DRY parziale).
   - Regole creazione mese replicate in viewmodel e aggregate.
   - Evidenze: `ProgramLifecycleViewModel.kt:252`, `ProgramMonthAggregate.kt:25`.

4. Performance aperta su auto-assign (N+1 query).
   - ~7 query per slot via `suggerisciProclamatori`.
   - Evidenze: `SqlDelightAssignmentStore.kt:57`, `AutoAssegnaProgrammaUseCase.kt:52`.

5. Copertura integration migliorabile sui boundary esterni.
   - HTTP client e PDF rendering ancora poco coperti.
   - Evidenze: `GitHubSchemaCatalogDataSource.kt:40`, `GitHubReleasesClient.kt:38`, `StampaProgrammaUseCaseTest.kt:9`.

## Verifiche eseguite

- `./gradlew :composeApp:jvmTest --rerun-tasks` => `BUILD SUCCESSFUL`
- Totale test JVM: `109`
- Failure: `0`
- Error: `0`

## Stato finale sintetico

Con i vincoli richiesti (DDD rigoroso, aggregate-root centric, transazione unica per use case mutante), stato attuale: **non ancora production-ready**.

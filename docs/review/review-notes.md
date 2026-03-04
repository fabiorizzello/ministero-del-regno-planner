# Review Notes — ministero-del-regno-planner

Review aggiornato dopo le correzioni applicate in questa sessione.

## Punti ancora aperti

### P2 — Tech debt / naming / dead code

1. `part_type_revision` da implementare full e usare davvero
- Richiesta implementazione completa end-to-end: creazione revisioni, collegamento `weekly_part.part_type_revision_id`, lettura e uso runtime.
- Questo punto resta bloccante in review finché l'infrastruttura revisioni non è pienamente adottata dal codice applicativo.

## Punti chiusi e rimossi dalla review precedente

- Rimozione del modulo `feature/planning` e relativo wiring DI.
- Allineamento spec: nessun flag `attivo`, solo `sospeso` + hard delete.
- `AggiornaSchemiUseCase`: timestamp import salvato nella stessa transazione.
- `RimuoviParteUseCase`: remove + ricompattazione dentro transazione.
- `CreaSettimanaUseCase`: validazione lunedì convertita in `DomainError.Validation`.
- Naming SQL query persone: eliminato riferimento `allActiveProclaimers`.
- Rimosso flusso legacy `AggiornaDatiRemotiUseCase` con `RemoteDataSource`/`GitHubDataSource`.
- Utility CLI in `jvmMain`: decisione di mantenimento (non trattato come problema).
- `EliminaProgrammaUseCase`: naming allineato al comportamento `CURRENT/FUTURE` + commento cascade chiarito.
- `AggiornaProgrammaDaSchemiUseCase`: refactor con fase analisi unica, rimozione lookup O(N²), filtro esplicito sole settimane `ACTIVE`.
- Rimosso coupling `ProgramLifecycleViewModel` ↔ `SchemaManagementViewModel` (eliminata sincronizzazione `updateSelection`).
- `week_plan.status`: aggiunto `CHECK(status IN ('ACTIVE', 'SKIPPED'))` a livello schema SQL.
- `StampaProgrammaUseCase`: sostituito enum raw con label user-friendly (`Attiva`/`Saltata`).

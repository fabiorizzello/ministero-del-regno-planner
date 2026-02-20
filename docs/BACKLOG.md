# Backlog Tecnico

## SQL — Schema e migrazioni

### SQL-6: Nessuna strategia di migrazione DB
- **File:** `core/persistence/DatabaseProvider.kt:18-20`
- `JdbcSqliteDriver(schema = MinisteroDatabase.Schema)` auto-crea tabelle su DB nuovo. Per DB esistenti, cambiamenti schema non vengono applicati — nessun file di migrazione `.sqm` nel progetto.
- **Fix:** aggiungere directory `migrations/` con file `.sqm` per ogni cambio schema futuro. Critico per field additions.

---

## SOLID — Principi architetturali

### SOLID-1: AssignmentStore viola Interface Segregation
- **File:** `feature/assignments/application/AssignmentStore.kt:12-24`
- L'interfaccia mescola: CRUD assegnazioni, ranking/suggerimenti, e operazioni persona-lifecycle (`countAssignmentsForPerson`, `removeAllForPerson`). Separare in interfacce dedicate (es. `AssignmentReader`, `AssignmentWriter`, `AssignmentRanking`).

### SOLID-3: ProclamatoriElencoContent con 28 parametri
- **File:** `ui/proclamatori/ProclamatoriComponents.kt:170-199`
- Troppi parametri. Raggruppare in data class dedicate (es. `SearchState`, `SelectionState`, `PaginationState`, callbacks in interface).

---

## UI/UX — Miglioramenti interfaccia

### UX-7: Overwrite schema remoto non mostra conteggio assegnazioni perse
- **File:** `feature/weeklyparts/application/AggiornaDatiRemotiUseCase.kt:59-71`
- `weekPlanStore.delete(existing.id)` cancella a cascata tutte le assegnazioni. Il dialog avvisa genericamente ma non indica quante assegnazioni verranno eliminate.
- **Fix:** contare e mostrare le assegnazioni che verranno perse.

# Analisi di Impatto — Refactoring Algoritmo di Assegnazione

Data: 2026-03-12

Riferimento: `docs/assignment-algorithm-fairness-analysis.md`

---

## Mappa dipendenze attuali

| File | Ruolo | Dipendenze ranking |
|---|---|---|
| `SuggestedProclamatore.kt` | Domain model | Definisce i campi ranking |
| `AssignmentSettings.kt` | Config | `leadWeight`, `assistWeight`, `COOLDOWN_PENALTY` |
| `SuggerisciProclamatoriUseCase.kt` | Core algorithm | `weightedScore()`, cooldown logic, sorting |
| `AutoAssegnaProgrammaUseCase.kt` | Batch assign | Chiama `suggerisciProclamatori()`, usa `canBeAutoAssigned()` |
| `AssignmentStore.kt` | Interface | `AssignmentRanking`, `SuggestionRankingCache` |
| `SqlDelightAssignmentStore.kt` | Infra | `fetchRankingFromDb()`, `buildSuggestions()` |
| `SqlDelightAssignmentSettingsStore.kt` | Infra | Persiste settings su DB |
| `MinisteroDatabase.sq` | SQL | Query ranking, tabella `assignment_settings` |
| `AssignmentsModule.kt` | DI | Wiring Koin |
| `PersonPickerViewModel.kt` | ViewModel | Chiama suggerimenti, espone `List<SuggestedProclamatore>` |
| `AssignmentManagementViewModel.kt` | ViewModel | Settings UI (leadWeight, assistWeight, cooldown) |
| `AssignmentsComponents.kt` | UI | `SuggestionRow()`, sort per `lastForPartTypeWeeks`, badge cooldown |
| `ProgramWorkspaceScreen.kt` | UI | Wiring dialog |
| `SuggerisciProclamatoriUseCaseTest.kt` | Test | 5 test su scoring/cooldown |
| `AutoAssegnaProgrammaUseCaseTest.kt` | Test | 7 test su batch assign |
| `SexMismatchPolicyTest.kt` | Test | Test sex mismatch |
| `SqlDelightAssignmentStoreTest.kt` | Test | Test query ranking |
| `AssignmentTestFixtures.kt` | Test | Fake ranking, fake settings |
| `AssignmentManagementViewModelTest.kt` | Test | Test settings UI |
| `specs/005-assegnazioni/spec.md` | Spec | Documenta formula, cooldown, settings |

---

## Impatto per proposta

### A. Rimuovere `lastForPartTypeWeeks` dallo scoring

La componente per-parte esce dalla formula. I dati restano nel domain model per visualizzazione UI.

| File | Modifica | Effort |
|---|---|---|
| `SuggerisciProclamatoriUseCase.kt` | `weightedScore()`: rimuovere `+ safePartWeeks` | Triviale |
| `SuggestedProclamatore.kt` | Nessuna — i campi `lastForPartTypeWeeks`/before/after restano per l'UI | Nessuno |
| `SuggestionRankingCache` | Nessuna — `partTypeLastByType` ecc. restano per l'UI | Nessuno |
| `SqlDelightAssignmentStore.kt` | Nessuna — continua a popolare i campi per-parte | Nessuno |
| `AssignmentsComponents.kt` | Opzionale: riordinare UI per de-enfatizzare il dato per-parte | Basso |
| `SuggerisciProclamatoriUseCaseTest.kt` | Aggiornare test che verificano l'ordine basato su score | Medio |
| `specs/005-assegnazioni/spec.md` | Aggiornare formula scoring | Basso |

**Effort totale: basso** — 1 riga di logica, il resto è test e docs.

---

### B. Slot repeat penalty

Nuova penalità se l'ultimo slot assegnato coincide con il target slot. Costante nel codice, non esposta nelle impostazioni utente.

| File | Modifica | Effort |
|---|---|---|
| `SuggestedProclamatore.kt` | Nessuna — `lastConductorWeeks` già presente, sufficiente per derivare "ultimo slot era 1" | Nessuno |
| `AssignmentSettings.kt` | Aggiungere `const val SLOT_REPEAT_PENALTY = 4` (costante, come `COOLDOWN_PENALTY`) | Triviale |
| `SuggerisciProclamatoriUseCase.kt` | In `weightedScore()`: calcolare `lastWasConductor` (già fatto nel cooldown), sottrarre `SLOT_REPEAT_PENALTY` se `targetSlot matches lastSlot` | Basso |
| `SuggerisciProclamatoriUseCaseTest.kt` | Nuovi test per slot repeat penalty | Medio |
| `specs/005-assegnazioni/spec.md` | Documentare slot repeat penalty | Basso |

**Effort totale: basso** — costante + 3-4 righe in `weightedScore()`, nessun cambio DB/UI.

---

### C. Tiebreaker deterministico non-alfabetico

Sostituire `cognome/nome` con `hash(personId + weekStartDate)` nel comparatore.

| File | Modifica | Effort |
|---|---|---|
| `SuggerisciProclamatoriUseCase.kt` | Cambiare i due `thenBy` finali nel comparatore: da `cognome/nome` a `hash(personId + weekStartDate)`. Serve passare `weekStartDate` a `weightedScore()` o al comparatore | Basso |
| `AssignmentsComponents.kt` | Il sort UI nel PersonPickerDialog ha un suo comparatore indipendente con `cognome/nome` — valutare se allinearlo o lasciarlo (l'utente vede la lista e può cercare per nome) | Decisione |
| `SuggerisciProclamatoriUseCaseTest.kt` | Aggiornare test che verificano ordine a parità | Basso |
| `specs/005-assegnazioni/spec.md` | Documentare tiebreaker | Triviale |

**Effort totale: basso** — 3-4 righe di codice, nessun cambio DB.

**Nota**: nella UI del PersonPickerDialog il sort per cognome è utile all'utente per trovare le persone. Il tiebreaker hash va applicato solo nel ranking algoritmico, non nell'ordinamento visuale opzionale.

---

### D. Unificare i pesi (`leadWeight = assistWeight = 1`)

Rimuovere la distinzione tra pesi per ruolo.

| File | Modifica | Effort |
|---|---|---|
| `AssignmentSettings.kt` | Rimuovere `leadWeight` e `assistWeight`, sostituire con singolo peso fisso (o rimuovere del tutto dato che `weight=1` è neutro) | Basso |
| `SuggerisciProclamatoriUseCase.kt` | `weightedScore()`: rimuovere `* roleWeight` (moltiplicare per 1 è no-op). Rimuovere selezione `leadWeight`/`assistWeight` per slot | Basso |
| `SqlDelightAssignmentSettingsStore.kt` | Rimuovere mapping `lead_weight`, `assist_weight` | Basso |
| `MinisteroDatabase.sq` | Migration: drop colonne `lead_weight`, `assist_weight` da `assignment_settings` | Basso |
| `AssignmentManagementViewModel.kt` | Rimuovere campi `leadWeight`/`assistWeight` dalla UI state e handlers | Medio |
| `AssignmentsComponents.kt` | Rimuovere input `leadWeight`/`assistWeight` dal form settings | Basso |
| `SuggerisciProclamatoriUseCaseTest.kt` | Semplificare — i test che verificano score con pesi diversi vanno rimossi/riscritti | Medio |
| `AssignmentTestFixtures.kt` | Semplificare settings fixtures | Triviale |
| `AssignmentManagementViewModelTest.kt` | Rimuovere test su weight parsing | Basso |
| `specs/005-assegnazioni/spec.md` | Rimuovere documentazione pesi | Basso |

**Effort totale: medio** — rimozione codice in più posti, migration DB, UI cleanup.

**Alternativa a effort minore**: tenere i campi ma forzare `leadWeight = assistWeight = 1` come default e nascondere dall'UI. Meno clean ma meno invasivo.

---

### E. Fairness cumulativa (conteggio totale)

Nuovo fattore: penalità basata sul numero di assegnazioni in una finestra temporale. Costanti nel codice (`COUNT_PENALTY_WEIGHT`, `COUNT_WINDOW_WEEKS`), non esposte nelle impostazioni utente.

| File | Modifica | Effort |
|---|---|---|
| `SuggestedProclamatore.kt` | Aggiungere `val totalAssignmentsInWindow: Int = 0` | Triviale |
| `AssignmentSettings.kt` | Aggiungere `const val COUNT_PENALTY_WEIGHT = 1` e `const val COUNT_WINDOW_WEEKS = 26` | Triviale |
| `SuggestionRankingCache` | Aggiungere `val assignmentCountInWindow: Map<String, Int>` (personId → count) | Basso |
| `SqlDelightAssignmentStore.kt` | In `fetchRankingFromDb()`: calcolare count per persona filtrando `allAssignmentRankingData` per finestra temporale. In `buildSuggestions()`: popolare `totalAssignmentsInWindow` | Medio |
| `SuggerisciProclamatoriUseCase.kt` | In `weightedScore()`: sottrarre `totalAssignmentsInWindow * COUNT_PENALTY_WEIGHT` | Basso |
| `SuggerisciProclamatoriUseCaseTest.kt` | Nuovi test per count penalty | Medio |
| `SqlDelightAssignmentStoreTest.kt` | Test che il count viene calcolato correttamente dalla cache | Medio |
| `AssignmentTestFixtures.kt` | Aggiornare fixtures con nuovo campo | Basso |
| `specs/005-assegnazioni/spec.md` | Documentare count penalty | Basso |

**Effort totale: medio** — nuovo dato nella cache e aggregation in-memory, ma nessun cambio DB/UI.

---

## Riepilogo effort

| Proposta | Effort | File toccati | Migration DB | Cambio UI settings |
|---|---|---|---|---|
| A. Rimuovere `lastForPartTypeWeeks` da scoring | Basso | 3 + test/docs | No | No |
| B. Slot repeat penalty (costante) | Basso | 3 + test/docs | No | No |
| C. Tiebreaker hash | Basso | 2 + test/docs | No | No |
| D. Unificare pesi | Medio | 8 + test/docs | Si (drop 2 colonne) | Si (rimuove 2 campi) |
| E. Fairness cumulativa (costanti) | Medio | 6 + test/docs | No | No |

### Proposte escluse

- **C. Tiebreaker hash** — con 80 persone il bias alfabetico è marginale, non giustifica la complessità aggiunta.

### Ordine di implementazione

1. **A** — rimuovere `lastForPartTypeWeeks` dallo scoring. Solo `weightedScore()`. Nessun cambio DB o UI.
2. **D** — unificare pesi. Unica modifica che tocca DB (migration drop 2 colonne), UI (rimuove 2 campi dal form settings), e ViewModel.
3. **E** — fairness cumulativa. Aggiunge count in cache e aggregation in-memory. Nessun cambio DB o UI.
4. **B** — slot repeat penalty. Costante + poche righe in `weightedScore()`. Nessun cambio DB o UI.

### Rischio

Rischio basso. L'unica modifica che tocca DB e UI è D (rimozione di `leadWeight`/`assistWeight`). L'algoritmo è ben isolato in `weightedScore()` (5 righe di codice). Il grosso del lavoro è nei test (~20 test da riadattare alla nuova formula).

# Design M3+M4: Assegnazioni con Suggerimenti Fuzzy

## Contesto

Milestone M3 (Assegnazioni Core) e M4 (Suggerimenti Fuzzy) implementate insieme.
L'utente assegna proclamatori alle parti settimanali con ranking intelligente basato sullo storico.
Target: utente non tecnico. La UI impedisce errori by design (filtri hard, nessuna validazione manuale).

## 1. Navigazione

- Tab **Assegnazioni** nella sidebar (gia' presente come placeholder).
- **Week navigator** identico a Schemi (frecce, date, chip Corrente/Futura/Passata).
- Settimana selezionata sincronizzata tra Schemi e Assegnazioni tramite `SharedWeekState` singleton (`StateFlow<LocalDate>` in Koin `single`).
- Link bidirezionali:
  - Schemi: pulsante "Vai alle assegnazioni" sulla stessa settimana.
  - Assegnazioni: pulsante "Vai allo schema" sulla stessa settimana.
- Se la settimana non ha schema: messaggio "Settimana non configurata" con link "Vai allo schema per crearla".

## 2. Layout schermata Assegnazioni

Struttura verticale:

1. **Week navigator**
2. **Barra azioni**: link "Vai allo schema" + stato completamento (es. "5/8 slot assegnati")
3. **Lista card parti** (ordinate per sort_order)

### Card parte

Ogni parte e' una card con:
- **Header**: numero parte + label tipo + chip regola sesso ("UOMO" blu / "LIBERO" grigio)
- **Slot**: nome proclamatore assegnato + pulsante "X" per rimuovere, oppure pulsante "Assegna"

Varianti card per `peopleCount`:
- `peopleCount = 1`: slot unico, nessuna label di ruolo.
- `peopleCount = 2`: due righe — "Proclamatore" (slot 1) + "Assistente" (slot 2).
- Parti `fixed`: card visibile solo come label informativo, nessuno slot assegnabile.

Rimozione assegnazione: pulsante "X" → rimozione immediata, nessun dialog di conferma (azione leggera e reversibile).

### Mockup card

Parte con 2 persone:
```
+-- 4. Prima conversazione ----------- [LIBERO] --+
|  Proclamatore:  Mario Rossi                 [X]  |
|  Assistente:    Lucia Bianchi               [X]  |
+--------------------------------------------------+
```

Parte con 1 persona:
```
+-- 3. Lettura della Bibbia ------------ [UOMO] --+
|  Paolo Verdi                                [X]  |
+-------------------------------------------------+
```

Parte fixed:
```
+-- 1. Cantico e preghiera ---------------------- +
|  (parte fissa)                                   |
+-------------------------------------------------+
```

## 3. Dialog selezione proclamatore

Si apre al click su "Assegna" o sul nome gia' assegnato.

### Struttura dialog

- **Titolo**: "Assegna — [nome parte]" + ruolo ("Proclamatore" o "Assistente")
- **Campo ricerca**: filtra per nome/cognome in tempo reale
- **Toggle ordinamento**: "Ordina per: Globale | Per parte" (default: Globale)
- **Lista proclamatori**: tabella con colonne

| Nome Cognome | Ultima (globale) | Ultima (questa parte) | Azione |
|---|---|---|---|
| Mario Rossi | Mai assegnato | Mai assegnato | **[Assegna]** |
| Lucia Bianchi | 1 settimana fa | 5 settimane fa | **[Assegna]** |
| Paolo Verdi | 2 settimane fa | Mai assegnato | **[Assegna]** |

- **Pulsante "Annulla"** per chiudere senza assegnare.
- Se nessun proclamatore disponibile: "Nessun proclamatore disponibile per questa parte".

### Colonne distanza

- **Ultima (globale)**: settimane dall'ultima assegnazione a qualsiasi parte, qualsiasi slot.
- **Ultima (questa parte)**: settimane dall'ultima assegnazione allo stesso tipo di parte, qualsiasi slot.
- Formato: "Mai assegnato", "1 settimana fa", "N settimane fa".

### Filtri hard (proclamatore non appare nel dialog)

- `attivo = false`
- Parte con `sexRule = UOMO` e proclamatore con `sesso = F`
- Proclamatore gia' assegnato a un altro slot della stessa parte

### Ranking

- **Slot 1 (Proclamatore)**: ranking basato solo su assegnazioni come slot 1.
- **Slot 2 (Assistente)**: ranking basato su assegnazioni come slot 1 + slot 2 (qualsiasi ruolo).
- **Toggle globale**: ordina per colonna "Ultima (globale)".
- **Toggle per parte**: ordina per colonna "Ultima (questa parte)".
- "Mai assegnato" → in cima alla lista.

## 4. Regole dominio

- `peopleCount` in {1, 2}.
- `sexRule` in {UOMO, LIBERO}. UOMO: solo proclamatori con `sesso = M`. LIBERO: nessun vincolo.
- Unicita': un proclamatore non puo' occupare due slot della stessa parte.
- Proclamatori inattivi esclusi da nuove assegnazioni.
- Parti `fixed`: non assegnabili.
- Il sistema impedisce stati invalidi by design (filtri hard nel dialog).
- Errori di salvataggio: FeedbackBanner standard.

## 5. Architettura

### Feature structure (vertical slice)

```
feature/assignments/
  domain/         → Assignment, AssignmentId, SuggestedProclamatore
  application/    → Use case + AssignmentStore interface
  infrastructure/ → SqlDelightAssignmentStore + ranking queries

ui/assignments/   → AssignmentsScreen, AssignmentsViewModel, AssignmentsComponents
```

### Use case

| Use case | Input | Output |
|---|---|---|
| `AssegnaProclamatoreAParte` | weeklyPartId, personId, slot | `Either<DomainError, Assignment>` |
| `RimuoviAssegnazione` | assignmentId | `Either<DomainError, Unit>` |
| `CaricaAssegnazioniSettimana` | weekStartDate | Lista parti con assegnazioni |
| `SuggerisciProclamatori` | weeklyPartId, slot | `List<SuggestedProclamatore>` ordinata |

### Modello ranking

```kotlin
data class SuggestedProclamatore(
    val proclamatore: Proclamatore,
    val lastGlobalWeeks: Int?,      // null = mai assegnato
    val lastForPartTypeWeeks: Int?, // null = mai su questo tipo di parte
)
```

### Stato condiviso settimana

```kotlin
class SharedWeekState {
    private val _currentMonday = MutableStateFlow(currentMondayDate())
    val currentMonday: StateFlow<LocalDate> = _currentMonday.asStateFlow()
    fun navigate(monday: LocalDate) { _currentMonday.value = monday }
}
```

Registrato come `single` in Koin. Entrambi i ViewModel (WeeklyParts e Assignments) leggono e scrivono.

### ViewModel

- `AssignmentsViewModel`: stato `AssignmentsUiState` con weekPlan, lista parti con assegnazioni, notice.
- Carica suggerimenti on-demand all'apertura del dialog (non pre-caricati).
- Ranking calcolato in SQL (LEFT JOIN assignment + week_plan per distanza settimane).

## 6. Schema DB

Tabella `assignment` gia' esistente:

```sql
CREATE TABLE assignment (
    id TEXT NOT NULL PRIMARY KEY,
    weekly_part_id TEXT NOT NULL,
    person_id TEXT NOT NULL,
    slot INTEGER NOT NULL,
    FOREIGN KEY (weekly_part_id) REFERENCES weekly_part(id) ON DELETE CASCADE,
    FOREIGN KEY (person_id) REFERENCES person(id) ON DELETE RESTRICT
);
```

Query ranking aggiuntive necessarie:
- Ultima assegnazione globale per proclamatore (MAX week_start_date da assignment JOIN weekly_part JOIN week_plan).
- Ultima assegnazione per tipo parte per proclamatore (stessa join + filtro part_type_id).
- Filtrate per slot a seconda del contesto (slot 1 only vs slot 1+2).

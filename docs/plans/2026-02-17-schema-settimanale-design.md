# Design: Schema Settimanale (Parti della riunione)

Data: 2026-02-17
Milestone: M2 — Parti Settimanali

## Obiettivo

Permettere la configurazione delle parti della riunione infrasettimanale per ogni settimana.
Scope limitato a: **Lettura biblica (parte 3)** e **Efficaci nel ministero (parti 4+)**.
Esclusa in questa fase la parte di assegnazione persone alle parti.

## Modello Dati

### Nuova tabella `part_type` (catalogo tipi)

```sql
CREATE TABLE part_type (
    id TEXT NOT NULL PRIMARY KEY,
    code TEXT NOT NULL UNIQUE,
    label TEXT NOT NULL,
    people_count INTEGER NOT NULL,
    sex_rule TEXT NOT NULL,
    fixed INTEGER NOT NULL DEFAULT 0,
    sort_order INTEGER NOT NULL DEFAULT 0
);
```

Campi:
- `code`: identificativo univoco (es. `LETTURA_BIBLICA`, `INIZIARE_CONVERSAZIONE`)
- `label`: nome leggibile (es. "Lettura biblica", "Iniziare una conversazione")
- `people_count`: 1 o 2 persone richieste
- `sex_rule`: `UOMO` o `LIBERO` — regola non sovrascrivibile, decisa dal tipo
- `fixed`: 1 = parte non rimovibile (Lettura biblica)
- `sort_order`: ordine nel catalogo per l'autocomplete

Popolata tramite import da file JSON remoto su GitHub.

### Tabella `weekly_part` (rivista)

```sql
CREATE TABLE weekly_part (
    id TEXT NOT NULL PRIMARY KEY,
    week_plan_id TEXT NOT NULL,
    part_type_id TEXT NOT NULL,
    sort_order INTEGER NOT NULL,
    FOREIGN KEY (week_plan_id) REFERENCES week_plan(id) ON DELETE CASCADE,
    FOREIGN KEY (part_type_id) REFERENCES part_type(id)
);
```

Differenze dallo schema attuale:
- `title TEXT` rimosso — sostituito da `part_type_id` (FK)
- `number_of_people` e `sex_rule` rimossi — derivati dal `part_type` via JOIN

### Tabella `week_plan` (invariata)

```sql
CREATE TABLE week_plan (
    id TEXT NOT NULL PRIMARY KEY,
    week_start_date TEXT NOT NULL
);
```

## UI

### Layout schermata "Parti"

```
┌──────────────────────────────────────────────────────────┐
│  [Aggiorna dati]                                         │
│                                                          │
│         <  Settimana 16-22 febbraio 2026  >              │
│                   Corrente                                │
│                                                          │
│  N.  | Tipo                    | Persone | Regola  |     │
│  ----+-------------------------+---------+---------+-----│
│  3   | Lettura biblica         |    1    |  UOMO   |     │
│  4   | Iniziare una conv.      |    2    |  LIBERO |  x  │
│  5   | Spiegare...             |    2    |  LIBERO |  x  │
│  6   | Discorso                |    1    |  UOMO   |  x  │
│                                                          │
│  [ + Aggiungi parte ]                                    │
└──────────────────────────────────────────────────────────┘
```

### Navigazione settimana
- Frecce < > spostano di 7 giorni
- La data mostrata e' "lunedi - domenica" della settimana
- Il lunedi viene calcolato automaticamente

### Indicatore temporale
- **Corrente** (verde) — settimana contenente la data odierna
- **Futura** (blu) — settimane successive
- **Passata** (grigio) — settimane precedenti

### Settimana inesistente
- Mostra messaggio "Settimana non configurata"
- Pulsante "Crea settimana" — crea `week_plan` + inserisce automaticamente parte 3 (Lettura biblica, `fixed=true`)

### Tabella parti
- Parti ordinate per `sort_order`
- N. auto-calcolato: `sort_order + 3` (la parte 3 e' sempre Lettura biblica)
- Colonne Persone e Regola sono read-only (derivate da `part_type`)
- Lettura biblica: non eliminabile, non riordinabile
- Parti 4+: eliminabili (pulsante x), riordinabili

### Aggiungi parte
- Dropdown/autocomplete che cerca nella tabella `part_type`
- Selezionando un tipo, la parte viene aggiunta in coda con `sort_order` incrementale

### Aggiorna dati (pulsante unico)
Un click esegue due operazioni in sequenza:
1. Scarica catalogo tipi da GitHub -> upsert in `part_type`
2. Scarica schemi settimanali da GitHub -> import settimane:
   - Settimana non esistente: crea `week_plan` + `weekly_part`
   - Settimana gia' esistente: chiede conferma "Sovrascrivere?"

## Architettura Software

Vertical slice, stessi pattern di Proclamatori.

### Domain (`feature/weeklyparts/domain`)

```kotlin
@JvmInline value class PartTypeId(val value: String)
@JvmInline value class WeekPlanId(val value: String)
@JvmInline value class WeeklyPartId(val value: String)

enum class SexRule { UOMO, LIBERO }

data class PartType(
    val id: PartTypeId,
    val code: String,
    val label: String,
    val peopleCount: Int,
    val sexRule: SexRule,
    val fixed: Boolean,
    val sortOrder: Int,
)

data class WeekPlan(
    val id: WeekPlanId,
    val weekStartDate: LocalDate,
    val parts: List<WeeklyPart>,
)

data class WeeklyPart(
    val id: WeeklyPartId,
    val partType: PartType,
    val sortOrder: Int,
)
```

### Application (`feature/weeklyparts/application`)

| Use Case | Responsabilita |
|----------|----------------|
| `CaricaSettimanaUseCase` | Carica WeekPlan per data (JOIN parti + tipi). Null se non esiste. |
| `CreaSettimanaUseCase` | Crea week_plan + parte fissa Lettura biblica. |
| `AggiungiParteUseCase` | Aggiunge weekly_part a settimana. Auto-assegna sort_order. |
| `RimuoviParteUseCase` | Rimuove parte (blocca se fixed=true). Ricalcola sort_order. |
| `RiordinaPartiUseCase` | Aggiorna sort_order di tutte le parti di una settimana. |
| `AggiornaDatiRemotiUseCase` | Scarica catalogo + schemi da GitHub. Upsert + import con conferma. |
| `CercaTipiParteUseCase` | Lista tipi dal catalogo per autocomplete. |

### Infrastructure (`feature/weeklyparts/infrastructure`)

| Componente | Responsabilita |
|-----------|----------------|
| `SqlDelightPartTypeStore` | CRUD su part_type. Upsert bulk per import. |
| `SqlDelightWeekPlanStore` | CRUD su week_plan + weekly_part con JOIN a part_type. |
| `GitHubDataSource` | HTTP GET a URL GitHub (catalogo + schemi). Parse JSON. |

### Koin

Registrare in appModule: stores, data source, tutti gli use case.

## Fonti dati remote (GitHub)

Due file JSON separati su GitHub:

### 1. Catalogo tipi (`part-types.json`)
```json
{
  "version": 1,
  "partTypes": [
    {
      "code": "LETTURA_BIBLICA",
      "label": "Lettura biblica",
      "peopleCount": 1,
      "sexRule": "UOMO",
      "fixed": true
    },
    {
      "code": "INIZIARE_CONVERSAZIONE",
      "label": "Iniziare una conversazione",
      "peopleCount": 2,
      "sexRule": "LIBERO",
      "fixed": false
    }
  ]
}
```

### 2. Schemi settimanali (`weekly-schemas.json`)
```json
{
  "version": 1,
  "weeks": [
    {
      "weekStartDate": "2026-02-16",
      "parts": [
        { "partTypeCode": "LETTURA_BIBLICA", "sortOrder": 0 },
        { "partTypeCode": "INIZIARE_CONVERSAZIONE", "sortOrder": 1 },
        { "partTypeCode": "SPIEGARE_CIO_CHE_SI_CREDE", "sortOrder": 2 },
        { "partTypeCode": "DISCORSO", "sortOrder": 3 }
      ]
    }
  ]
}
```

## Decisioni di design

1. **Niente template riutilizzabili** — ogni settimana e' configurata individualmente
2. **Regole (persone, sesso) nel tipo, non nella parte** — il tipo di parte decide, non sovrascrivibile
3. **Catalogo tipi in tabella DB** — FK integrity, funziona offline, pattern coerente
4. **Pulsante unico "Aggiorna dati"** — UX semplice, scarica tutto in un click
5. **Conferma su sovrascrittura** — le settimane modificate localmente non vengono perse senza conferma

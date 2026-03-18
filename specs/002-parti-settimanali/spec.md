# Feature Specification: Parti Settimanali

**Feature Branch**: `002-parti-settimanali`
**Created**: 2026-02-25
**Status**: Draft (reverse-engineered from existing code)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Creazione di una settimana con le sue parti (Priority: P1)

L'utente vuole creare il piano settimanale per una settimana specifica, definendo
quali tipi di parte (es. Preghiera pubblica, Lettura della Bibbia) si terranno
quella settimana e in che ordine.

**Why this priority**: Il `WeekPlan` con le sue `WeeklyPart` è la struttura su cui
poggiano le assegnazioni — senza di essa non si può assegnare nessuno.

**Independent Test**: Creare un WeekPlan per la settimana del prossimo lunedì →
aggiungere 3 parti → verificare che le 3 parti siano visibili nell'ordine corretto.

**Acceptance Scenarios**:

1. **Given** una data di lunedì valida, **When** si crea un WeekPlan, **Then** il
   piano viene salvato con status ACTIVE e con la parte fissa (es. "Preghiera") a
   sortOrder 0.
2. **Given** un WeekPlan creato, **When** si aggiunge un tipo di parte, **Then** la
   parte appare nell'elenco con il sortOrder corretto.
3. **Given** si tenta di creare un WeekPlan con una data che non è un lunedì, **Then**
   il sistema rifiuta con errore di validazione.

---

### User Story 2 - Gestione parti di una settimana (Priority: P1)

L'utente vuole aggiungere, rimuovere e riordinare le parti di una settimana
per rispecchiare il programma effettivo di quella settimana.

**Why this priority**: La composizione delle parti varia settimana per settimana
(settimane speciali, parti extra, parti mancanti).

**Independent Test**: Aggiungere 3 parti → rimuovere la seconda → verificare che
restino 2 parti nell'ordine atteso.

**Acceptance Scenarios**:

1. **Given** un WeekPlan, **When** si rimuove una parte, **Then** la parte non appare
   più nell'elenco e le assegnazioni legate a quella parte vengono rimosse.
2. **Given** un WeekPlan con 3 parti, **When** si riordina spostando la terza al primo
   posto, **Then** i sortOrder vengono aggiornati per riflettere il nuovo ordine.
3. **Given** un WeekPlan, **When** si aggiungono più parti dello stesso tipo, **Then**
   viene rispettato il comportamento definito (la spec attuale non blocca duplicati di
   tipo).

---

### User Story 3 - Caricamento e consultazione di una settimana (Priority: P1)

L'utente vuole caricare i dati di una settimana esistente (le sue parti e le
relative informazioni) per visualizzarli o per procedere con le assegnazioni.

**Why this priority**: È la lettura fondamentale che alimenta la UI principale.

**Independent Test**: Salvare un WeekPlan con 2 parti → ricaricare → verificare che
parti e sortOrder siano identici.

**Acceptance Scenarios**:

1. **Given** un WeekPlan persistito, **When** si carica per data di inizio settimana,
   **Then** vengono restituiti il piano e tutte le sue parti in ordine.
2. **Given** nessun WeekPlan per quella data, **When** si tenta il caricamento, **Then**
   viene restituito null/vuoto senza errori.

---

### User Story 4 - Aggiornamento dati remoti (tipi di parte) (Priority: P2)

L'utente vuole aggiornare il catalogo locale dei tipi di parte scaricando i dati
dal sorgente remoto (GitHub), per avere sempre i tipi di parte aggiornati.

**Why this priority**: I tipi di parte sono definiti esternamente e cambiano
periodicamente; senza aggiornamento il catalogo diventa obsoleto.

**Independent Test**: Eseguire AggiornaDatiRemoti → verificare che il numero di tipi
di parte nel DB corrisponda a quelli del sorgente remoto.

**Acceptance Scenarios**:

1. **Given** connessione disponibile, **When** si avvia l'aggiornamento, **Then** i tipi
   di parte e gli schemi settimanali vengono scaricati e applicati atomicamente in una
   singola transazione. Tutti i template settimanali del catalogo remoto sovrascrivono
   quelli locali.
2. **Given** un programma selezionato con settimane impattate dagli schemi aggiornati,
   **When** l'aggiornamento schemi completa, **Then** il sistema esegue un dry-run del
   refresh programma e mostra un dialog di conferma con il dettaglio per-settimana
   (parti aggiunte/rimosse, assegnazioni preservate/da rimuovere).
3. **Given** il dialog di conferma è visibile, **When** l'utente conferma, **Then**
   `AggiornaProgrammaDaSchemiUseCase` viene eseguito (non dry-run) e le settimane del
   programma vengono aggiornate atomicamente. Le assegnazioni matching per chiave
   `(PartTypeId, sortOrder)` vengono preservate.
4. **Given** il dialog di conferma è visibile, **When** l'utente annulla, **Then** gli
   schemi restano aggiornati ma il programma non viene modificato.
5. **Given** nessuna connessione, **When** si avvia l'aggiornamento, **Then** viene
   mostrato un errore di rete comprensibile e il DB non viene modificato.

---

### Edge Cases

- WeekPlan con data non-lunedì: invariante del dominio, rifiutato a livello di modello.
- WeekPlan con status SKIPPED: settimana saltata, viene creata ma senza parti da assegnare.
- Rimozione di una parte con assegnazioni attive: le assegnazioni vengono rimosse in
  cascata.
- Rimozione di una parte con `fixed = true`: il sistema rifiuta con errore
  "La parte '...' non può essere rimossa" — le parti fisse non sono rimovibili.
- Sort order dopo rimozione: i sortOrder vengono ricompattati a 0..n-1 contigui dopo
  ogni rimozione (`weekPlanStore.updateSortOrders`).
- `AggiornaSchemiUseCase` — aggiornamento schemi atomico in fase singola:
  scarica catalogo remoto, valida tipi di parte e date upfront, poi in una singola
  transazione: upsert part types, disattiva quelli rimossi, sovrascrive tutti i template
  settimanali. Nessuna selezione per-settimana — il catalogo remoto sovrascrive sempre
  integralmente quello locale.
- Refresh programma con conferma utente: se un programma è selezionato e ha settimane
  impattate, il ViewModel esegue un dry-run di `AggiornaProgrammaDaSchemiUseCase` e mostra
  un dialog di anteprima con il dettaglio per-settimana (`WeekRefreshDetail`: parti
  aggiunte/rimosse/invariate, assegnazioni preservate/da rimuovere). L'utente può
  confermare (applica) o annullare (schemi aggiornati, programma invariato).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Il sistema MUST creare un WeekPlan solo per date che cadono di lunedì.
- **FR-002**: Il sistema MUST supportare status ACTIVE e SKIPPED per ogni WeekPlan.
- **FR-003**: Il sistema MUST consentire di aggiungere tipi di parte a un WeekPlan
  con un sortOrder progressivo.
- **FR-004**: Il sistema MUST consentire di rimuovere una parte da un WeekPlan,
  rimuovendo in cascata le relative assegnazioni. Le parti con `fixed = true` NON
  possono essere rimosse — il sistema MUST rifiutare con errore di validazione.
  Dopo la rimozione, i sortOrder delle parti rimanenti MUST essere ricompattati
  a valori contigui 0..n-1.
- **FR-005**: Il sistema MUST consentire il riordinamento delle parti (aggiornamento
  sortOrder).
- **FR-006**: Il sistema MUST caricare tutti i tipi di parte disponibili
  (`CercaTipiParteUseCase` restituisce l'elenco completo). Il catalogo è
  sufficientemente piccolo da non richiedere filtro server-side; l'eventuale
  ricerca per nome/codice avviene lato UI.
- **FR-007**: Il sistema MUST aggiornare il catalogo dei tipi di parte e gli schemi
  settimanali da sorgente remoto su richiesta. L'aggiornamento avviene in una **singola
  transazione atomica**: validazione date upfront, poi upsert part types e sovrascrittura
  di tutti i template settimanali. Se un programma è selezionato e ha settimane impattate,
  il sistema MUST mostrare un'anteprima delle modifiche (`SchemaRefreshReport` con
  `WeekRefreshDetail` per-settimana) e richiedere conferma esplicita dell'utente prima
  di applicare il refresh al programma.
- **FR-008**: Il sistema MUST caricare un WeekPlan per data di inizio settimana, o
  restituire null se non esiste.

### Key Entities

- **WeekPlan**: id (UUID), weekStartDate (sempre lunedì), parts (lista ordinata),
  programId (opzionale, FK a ProgramMonth), status (ACTIVE | SKIPPED).
- **WeeklyPart**: id (UUID), partType (FK), sortOrder. Appartiene a un WeekPlan.
- **PartType**: id, code, label, peopleCount (>= 1), sexRule (UOMO | STESSO_SESSO),
  fixed (boolean), sortOrder. Catalogo dei tipi di parte disponibili.
- **SexRule**: UOMO = solo proclamatori maschi per qualsiasi slot;
  STESSO_SESSO = attualmente **non filtrante** (`passaSesso = true` in tutto il codice di
  suggerimento) — nessun candidato viene escluso per sesso. Il flag `sexMismatch` viene
  calcolato e annotato sul `SuggestedProclamatore` (sesso diverso da chi è già assegnato
  nella parte) ma non blocca la selezione manuale. In auto-assign (`AutoAssegnaProgrammaUseCase`)
  i candidati con `sexMismatch = true` sono esclusi (trattati come hard filter).
  La semantica originale "stesso sesso o CONIUGE/GENITORE_FIGLIO" non è implementata —
  è un gap intenzionale documentato (vedere Clarifications).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Il caricamento di un WeekPlan con le sue parti avviene in meno di 100 ms.
- **SC-002**: L'aggiunta/rimozione di una parte viene riflessa nella UI senza reload
  della pagina.
- **SC-003**: L'aggiornamento dati remoti completa in meno di 10 secondi in condizioni
  di rete normali.

## Clarifications

### Session 2026-02-25

- Q: Le spec sono state reverse-engineered dal codice esistente → A: Confermato.
- Q: Il WeekPlan può esistere senza un programId? → A: Sì, `programId` è nullable;
  le settimane possono essere create indipendentemente da un programma mensile.
- Q: Le parti fixed possono essere rimosse? → A: No — RimuoviParteUseCase controlla
  `part.partType.fixed` e restituisce errore di validazione se true.
- Q: I sortOrder vengono aggiornati dopo la rimozione di una parte? → A: Sì — dopo
  ogni rimozione i sortOrder vengono ricompattati a valori contigui 0..n-1.
- Q: AggiornaDatiRemotiUseCase ha fasi distinte? → A: L'import schemi
  (`AggiornaSchemiUseCase`) è single-phase atomico — sovrascrive tutto il catalogo remoto
  senza selezione per-settimana. La conferma utente si applica solo al **refresh del
  programma attivo**: il ViewModel esegue un dry-run, mostra l'anteprima, e applica solo
  dopo conferma. Se l'utente annulla, gli schemi restano aggiornati ma il programma non
  viene modificato.
- Q: SexRule.STESSO_SESSO nella spec 002 era errata? → A: Sì — la descrizione originale
  "stesso sesso OPPURE CONIUGE/GENITORE_FIGLIO" descriveva il comportamento atteso non
  implementato. Il codice attuale tratta STESSO_SESSO come non filtrante (`passaSesso = true`).
  La logica familiare CONIUGE/GENITORE_FIGLIO è un gap intenzionale, non prioritizzato.

### Session 2026-03-03

- Q: L'import schemi è atomico? → A: Sì — `AggiornaSchemiUseCase` valida date upfront
  (fuori transazione, fail-fast), poi tutta la fase di upsert+replace dentro una singola
  transazione `TransactionRunner`.

### Session 2026-03-05

- Q: STESSO_SESSO e auto-assign: comportamento uniforme o diverso? → A: Diverso per
  design: nel suggerimento manuale `sexMismatch` è un'annotazione soft (il candidato
  appare ma evidenziato); in `AutoAssegnaProgrammaUseCase` è un hard filter
  (`firstOrNull { !it.sexMismatch && !it.inCooldown }`). La differenza è intenzionale:
  l'auto-assign è conservativo; l'utente manuale può sovrascrivere. Questo comportamento
  asimmetrico è deliberato ma non ancora documentato nella spec 005 come decisione
  formale.
- Q: `WeekPlan.programId` è tipato come `String?` o come `ProgramMonthId?`? → A:
  Risolto (TYPE-001 completato). `programId` è ora tipato come `ProgramMonthId?`
  (value class). La conversione avviene al boundary SQL: `.value` in input,
  `ProgramMonthId(row.program_id)` in output.

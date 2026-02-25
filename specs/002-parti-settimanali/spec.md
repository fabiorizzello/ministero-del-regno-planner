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
   piano viene salvato con status ACTIVE e nessuna parte.
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

1. **Given** connessione disponibile e nessuno schema locale, **When** si avvia
   l'aggiornamento, **Then** i tipi di parte e gli schemi vengono scaricati e applicati
   integralmente. `ImportResult.weeksNeedingConfirmation` è vuoto.
2. **Given** alcune settimane già presenti localmente, **When** si esegue
   `fetchAndImport`, **Then** le settimane già presenti vengono restituite in
   `weeksNeedingConfirmation` senza essere sovrascritte; l'utente deve confermare
   esplicitamente prima di chiamare `importSchemas`.
3. **Given** l'utente ha confermato le settimane da sovrascrivere, **When** si chiama
   `importSchemas`, **Then** le settimane esistenti vengono eliminate e ricreate con i
   nuovi dati remoti.
4. **Given** nessuna connessione, **When** si avvia l'aggiornamento, **Then** viene
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
- `AggiornaDatiRemotiUseCase` — flusso in due fasi:
  1. `fetchAndImport()`: scarica tipi di parte e schemi; per le settimane già presenti
     localmente restituisce `weeksNeedingConfirmation` invece di sovrascriverle.
  2. `importSchemas(schemas)`: sovrascrive le settimane confermate dall'utente (elimina
     il WeekPlan esistente e ricrea da zero). L'utente deve confermare esplicitamente
     prima della fase 2.

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
- **FR-006**: Il sistema MUST consentire la ricerca dei tipi di parte disponibili per
  nome/codice.
- **FR-007**: Il sistema MUST aggiornare il catalogo dei tipi di parte e gli schemi
  settimanali da sorgente remoto su richiesta, in due fasi:
  (1) `fetchAndImport`: scarica e applica; le settimane già presenti vengono segnalate
  in `weeksNeedingConfirmation` senza sovrascrivere;
  (2) `importSchemas`: sovrascrive le settimane confermate dall'utente dopo conferma
  esplicita. Ogni schema con settimana già presente DEVE essere confermato prima della
  sovrascrittura.
- **FR-008**: Il sistema MUST caricare un WeekPlan per data di inizio settimana, o
  restituire null se non esiste.

### Key Entities

- **WeekPlan**: id (UUID), weekStartDate (sempre lunedì), parts (lista ordinata),
  programId (opzionale, FK a ProgramMonth), status (ACTIVE | SKIPPED).
- **WeeklyPart**: id (UUID), partType (FK), sortOrder. Appartiene a un WeekPlan.
- **PartType**: id, code, label, peopleCount (>= 1), sexRule (UOMO | LIBERO),
  fixed (boolean), sortOrder. Catalogo dei tipi di parte disponibili.
- **SexRule**: UOMO = solo proclamatori maschi per qualsiasi slot; LIBERO = stesso
  sesso OPPURE sesso diverso solo se i proclamatori assegnati agli slot sono in
  relazione familiare (CONIUGE o GENITORE_FIGLIO). `LIBERO` NON significa "qualsiasi
  persona" — vedere spec 001 (RelazioneProclam) e spec 005 (FR-002) per dettaglio
  completo della regola.

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
- Q: AggiornaDatiRemotiUseCase ha fasi distinte? → A: Sì — `fetchAndImport()` è la
  prima fase (skippa settimane esistenti, restituisce `weeksNeedingConfirmation`);
  `importSchemas(schemas)` è la seconda fase (sovrascrive dopo conferma utente).
- Q: SexRule.LIBERO nella spec 002 era errata? → A: Sì — aggiornata a: stesso sesso
  OPPURE sesso diverso solo se CONIUGE/GENITORE_FIGLIO (allineato a spec 001 e 005).

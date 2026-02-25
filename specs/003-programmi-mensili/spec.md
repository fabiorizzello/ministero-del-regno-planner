# Feature Specification: Programmi Mensili

**Feature Branch**: `003-programmi-mensili`
**Created**: 2026-02-25
**Status**: Draft (reverse-engineered from existing code)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Creazione del prossimo programma mensile (Priority: P1)

L'utente vuole creare il programma per il mese successivo. Il sistema determina
automaticamente quale mese è il prossimo disponibile e crea il programma con
le date di inizio/fine corrette (dal primo lunedì alla domenica successiva alla
fine del mese).

**Why this priority**: Il programma mensile è il contenitore di tutte le settimane
e delle relative assegnazioni. Senza di esso non si può lavorare su un mese futuro.

**Independent Test**: Con solo il programma corrente attivo → creare il prossimo →
verificare che esista un programma per il mese seguente con date coerenti.

**Acceptance Scenarios**:

1. **Given** nessun programma futuro, **When** si crea il prossimo programma, **Then**
   viene creato per il mese immediatamente successivo all'ultimo esistente, con
   startDate = primo lunedì del mese ed endDate = domenica successiva alla fine del mese.
2. **Given** esiste già 1 programma futuro, **When** si tenta di crearne un altro,
   **Then** il sistema blocca con errore "puoi avere al massimo un programma futuro".
3. **Given** nessun programma esistente, **When** si crea il primo programma, **Then**
   viene creato per il mese corrente (o il successivo se il mese corrente esiste già).

---

### User Story 2 - Generazione delle settimane del programma (Priority: P1)

L'utente vuole generare le settimane di un programma mensile, applicando i template
degli schemi scaricati per assegnare automaticamente i tipi di parte a ciascuna
settimana. È possibile marcare alcune settimane come saltate.

**Why this priority**: Senza la generazione delle settimane, il programma è vuoto
e le assegnazioni non possono essere fatte.

**Independent Test**: Creare un programma → generare le settimane → verificare che
ogni lunedì del mese abbia un WeekPlan con le parti previste dallo schema.

**Acceptance Scenarios**:

1. **Given** un programma e schemi caricati, **When** si genera il programma, **Then**
   viene creata una WeekPlan per ogni lunedì del periodo (startDate → endDate) con i
   tipi di parte definiti dallo schema di quella settimana.
2. **Given** nessuno schema per una settimana, **When** si genera, **Then** viene
   usata la parte fissa (fixed=true) come fallback; se non esiste neanche quella,
   errore.
3. **Given** alcune settimane marcate come skip, **When** si genera, **Then** quelle
   settimane hanno status SKIPPED.
4. **Given** un programma già con settimane, **When** si rigenera, **Then** le
   settimane precedenti vengono cancellate e ricreate (idempotente con distruzione
   delle assegnazioni esistenti).
5. **Given** il programma viene rigenerato, **Then** `templateAppliedAt` viene
   aggiornato con il timestamp corrente.

---

### User Story 3 - Consultazione programmi attivi (Priority: P1)

L'utente vuole vedere l'elenco dei programmi correnti e futuri per capire lo stato
della pianificazione.

**Why this priority**: La vista d'insieme dei programmi guida la navigazione e
il lavoro di pianificazione.

**Independent Test**: Con 2 programmi attivi → caricare la lista → verificare che
entrambi compaiano con il loro stato (PAST/CURRENT/FUTURE).

**Acceptance Scenarios**:

1. **Given** programmi esistenti, **When** si caricano i programmi attivi, **Then**
   vengono restituiti solo quelli CURRENT e FUTURE (non quelli PAST).
2. **Given** un programma con data corrente nel range startDate-endDate, **Then** il
   suo timelineStatus è CURRENT.

---

### User Story 4 - Eliminazione di un programma futuro (Priority: P2)

L'utente vuole annullare un programma futuro creato per errore o non più necessario.

**Why this priority**: Permette di correggere errori senza restare bloccati con
programmi futuri indesiderati.

**Independent Test**: Creare un programma futuro → eliminarlo → tentare di crearne
un nuovo → il nuovo viene creato correttamente.

**Acceptance Scenarios**:

1. **Given** un programma futuro, **When** si elimina, **Then** il programma non
   appare più nella lista e si può creare un nuovo programma futuro.
2. **Given** si tenta di eliminare il programma CURRENT, **Then** il sistema impedisce
   l'operazione (solo i FUTURE possono essere eliminati).

---

### User Story 5 - Aggiornamento programma da schemi (Priority: P2)

L'utente vuole aggiornare le parti delle settimane di un programma esistente
riallineandole agli schemi correnti, senza perdere le assegnazioni già fatte.

**Why this priority**: Quando gli schemi vengono aggiornati dopo che il programma
è già stato generato, le settimane possono risultare disallineate.

**Independent Test**: Generare un programma → aggiornare gli schemi → eseguire
AggiornaProgrammaDaSchemi → verificare che le parti delle settimane siano quelle
degli schemi nuovi.

**Acceptance Scenarios**:

1. **Given** un programma generato e schemi aggiornati, **When** si aggiorna da schemi,
   **Then** i tipi di parte di ogni settimana vengono riallineati agli schemi attuali.

---

### Edge Cases

- Generazione su un mese senza schemi: usa la parte fissa come fallback per ogni
  settimana; se non esiste neanche la parte fissa, errore di validazione.
- Rigenera settimane: distrugge le settimane esistenti e le assegnazioni. Operazione
  distruttiva che richiede conferma UI.
- Calcolo date: startDate = primo lunedì del mese; endDate = domenica successiva
  all'ultimo giorno del mese (può sforare nel mese successivo).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Il sistema MUST consentire al massimo 1 programma futuro alla volta.
- **FR-002**: Il sistema MUST calcolare automaticamente startDate (primo lunedì del
  mese) ed endDate (domenica successiva alla fine del mese) alla creazione.
- **FR-003**: Il sistema MUST avanzare automaticamente al prossimo mese disponibile
  se il mese corrente esiste già.
- **FR-004**: Il sistema MUST generare un WeekPlan per ogni lunedì nel range
  startDate-endDate del programma.
- **FR-005**: Il sistema MUST applicare gli schemi settimanali ai WeekPlan generati;
  in assenza di schema, usare la parte fissa come fallback.
- **FR-006**: Il sistema MUST supportare la marcatura di settimane come SKIPPED
  durante la generazione.
- **FR-007**: La generazione MUST essere idempotente: rieseguirla ricrea le settimane
  da capo (le assegnazioni esistenti vengono perse).
- **FR-008**: Il sistema MUST aggiornare `templateAppliedAt` dopo ogni generazione.
- **FR-009**: Il sistema MUST impedire l'eliminazione di programmi non-futuri.

### Key Entities

- **ProgramMonth**: id (UUID), year, month, startDate, endDate, templateAppliedAt
  (nullable), createdAt. Metodo: `timelineStatus(referenceDate)` → PAST/CURRENT/FUTURE.
- **ProgramTimelineStatus**: PAST, CURRENT, FUTURE.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: La creazione del prossimo programma avviene in meno di 1 secondo.
- **SC-002**: La generazione delle settimane di un programma mensile (4-5 settimane)
  completa in meno di 2 secondi.
- **SC-003**: Il caricamento dei programmi attivi restituisce risultati in meno di
  200 ms.

## Clarifications

### Session 2026-02-25

- Q: Le spec sono state reverse-engineered dal codice esistente → A: Confermato.
- Q: La generazione è distruttiva per le assegnazioni? → A: Sì, dal codice:
  le settimane vengono cancellate e ricreate in transazione, perdendo le assegnazioni.

# Feature Specification: Gestione Proclamatori

**Feature Branch**: `001-gestione-proclamatori`
**Created**: 2026-02-25
**Status**: Draft (reverse-engineered from existing code)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Aggiunta di un nuovo proclamatore (Priority: P1)

L'utente vuole aggiungere un nuovo proclamatore all'archivio inserendo nome, cognome,
sesso e le impostazioni iniziali di idoneità e sospensione.

**Why this priority**: È la funzione fondamentale senza cui nessun'altra feature
(assegnazioni, programmi) può funzionare.

**Independent Test**: Aprire la schermata proclamatori → aggiungere un proclamatore →
verificare che compaia nell'elenco con i dati corretti.

**Acceptance Scenarios**:

1. **Given** un archivio vuoto o con proclamatori esistenti, **When** l'utente inserisce
   nome, cognome e sesso validi e conferma, **Then** il proclamatore viene salvato come
   attivo e compare nell'elenco.
2. **Given** esiste già un proclamatore "Mario Rossi", **When** l'utente tenta di
   aggiungere un altro "Mario Rossi", **Then** il sistema mostra un errore di duplicato
   e non salva.
3. **Given** l'utente inserisce un nome di oltre 100 caratteri, **When** conferma,
   **Then** il sistema mostra un errore di validazione.
4. **Given** l'utente lascia nome o cognome vuoti, **When** conferma, **Then** il
   sistema mostra un errore di validazione.

---

### User Story 2 - Modifica di un proclamatore esistente (Priority: P1)

L'utente vuole aggiornare i dati anagrafici (nome, cognome, sesso) di un proclamatore
già presente in archivio.

**Why this priority**: I dati cambiano (errori di trascrizione, cambi di nome) e devono
poter essere corretti senza eliminare e ricreare il proclamatore.

**Independent Test**: Selezionare un proclamatore → modificare il cognome → salvare →
verificare che l'elenco mostri il nuovo cognome.

**Acceptance Scenarios**:

1. **Given** un proclamatore esistente, **When** l'utente modifica nome/cognome/sesso
   e salva, **Then** i nuovi dati vengono persistiti e visibili immediatamente.
2. **Given** si sta modificando "Mario Rossi" in "Mario Bianchi", **When** esiste già
   un "Mario Bianchi", **Then** il sistema blocca il salvataggio con errore duplicato.
3. **Given** si sta modificando "Mario Rossi", **When** si cambia solo il nome mantenendo
   il cognome, **Then** la verifica duplicato esclude il proclamatore corrente.

---

### User Story 3 - Gestione stato attivo/sospeso (Priority: P2)

L'utente vuole attivare, disattivare o sospendere temporaneamente un proclamatore
senza eliminarlo dall'archivio, in modo che non venga proposto per le assegnazioni.

**Why this priority**: La sospensione temporanea e la disattivazione permanente sono
workflow distinti rispetto all'eliminazione — il proclamatore viene conservato nello
storico.

**Independent Test**: Impostare un proclamatore come sospeso → aprire la schermata
assegnazioni → verificare che non compaia tra i candidati suggeriti.

**Acceptance Scenarios**:

1. **Given** un proclamatore attivo, **When** l'utente lo imposta come non-attivo,
   **Then** il proclamatore non appare nelle liste di candidati per le assegnazioni.
2. **Given** un proclamatore attivo, **When** l'utente lo imposta come sospeso,
   **Then** il flag sospeso viene persistito.
3. **Given** un proclamatore non-attivo, **When** l'utente lo riattiva, **Then** torna
   disponibile per le assegnazioni.

---

### User Story 4 - Impostazione idoneità conduzione e assistenza (Priority: P2)

L'utente vuole configurare per ogni proclamatore quali tipi di parte è idoneo a
condurre (idoneità specifica per tipo-parte) e se può fare da assistente in generale.

**Why this priority**: L'idoneità determina chi viene proposto per ogni slot — senza
questa configurazione il suggeritore non funziona correttamente.

**Independent Test**: Impostare un proclamatore come idoneo a condurre il tipo-parte
"X" → andare all'assegnazione della settimana → verificare che compaia come candidato
per lo slot 1 della parte "X".

**Acceptance Scenarios**:

1. **Given** un proclamatore, **When** l'utente imposta l'idoneità alla conduzione per
   il tipo-parte "Preghiera pubblica", **Then** il proclamatore viene proposto come
   candidato per lo slot 1 (conduttore) di quella parte.
2. **Given** un proclamatore senza idoneità a un tipo-parte, **When** si visualizzano
   i candidati per quello slot, **Then** il proclamatore non appare.
3. **Given** un proclamatore con `puoAssistere = true`, **When** si cercano candidati
   per uno slot >= 2, **Then** il proclamatore appare tra i candidati assistente.

---

### User Story 5 - Import iniziale da JSON (Priority: P3)

L'utente vuole importare in blocco un archivio proclamatori da un file JSON (operazione
one-shot disponibile solo quando l'archivio è vuoto).

**Why this priority**: Utile solo al primo avvio o dopo un reset. Non è un flusso
ricorrente.

**Independent Test**: Con archivio vuoto → importare un file JSON valido con N
proclamatori → verificare che N proclamatori siano presenti nell'elenco.

**Acceptance Scenarios**:

1. **Given** archivio vuoto, **When** l'utente importa un JSON valido con `version: 1`
   e un array `proclamatori`, **Then** tutti i proclamatori vengono salvati.
2. **Given** archivio già con proclamatori, **When** si tenta l'import, **Then** il
   sistema blocca con errore "archivio non vuoto".
3. **Given** un JSON con un elemento duplicato (stesso nome+cognome nel file), **When**
   si importa, **Then** l'import fallisce con errore che indica la posizione del duplicato.
4. **Given** un JSON con `version != 1`, **When** si importa, **Then** errore "versione
   schema non supportata".
5. **Given** un JSON con campi nome/cognome/sesso mancanti su un elemento, **When** si
   importa, **Then** l'import fallisce mostrando fino a 5 errori di dettaglio.

---

### Edge Cases

- Nome o cognome con solo spazi: deve essere trattato come vuoto e rifiutato.
- Import con array proclamatori vuoto: errore "nessun proclamatore da importare".
- Eliminazione di un proclamatore con assegnazioni storiche: comportamento da definire
  (la spec attuale non specifica; probabile eliminazione logica o blocco).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Il sistema MUST consentire la creazione di un proclamatore con nome,
  cognome, sesso (M/F), sospeso e puoAssistere.
- **FR-002**: Il sistema MUST impedire la creazione di due proclamatori con lo stesso
  nome e cognome (case-insensitive, trim applicato).
- **FR-003**: Il sistema MUST validare: nome e cognome non vuoti, lunghezza massima
  100 caratteri ciascuno.
- **FR-004**: Il sistema MUST consentire la modifica di nome, cognome e sesso, con
  ri-verifica del duplicato escludendo il proclamatore stesso.
- **FR-005**: Il sistema MUST consentire di impostare il flag `attivo` (attivo/non-attivo)
  e il flag `sospeso` indipendentemente.
- **FR-006**: Il sistema MUST consentire di configurare l'idoneità alla conduzione
  per ogni proclamatore su base per-tipo-parte.
- **FR-007**: Il sistema MUST consentire di impostare il flag `puoAssistere` per
  abilitare il proclamatore come candidato assistente.
- **FR-008**: Il sistema MUST consentire l'import bulk da JSON con schema `version: 1`
  solo quando l'archivio è vuoto.
- **FR-009**: Il sistema MUST consentire la ricerca proclamatori per termine testuale
  (nome o cognome); termine null restituisce tutti.
- **FR-010**: Il sistema MUST consentire l'eliminazione di un proclamatore.

### Key Entities

- **Proclamatore**: id (UUID), nome, cognome, sesso (M/F), attivo, sospeso,
  puoAssistere. Invariante: nome e cognome non vuoti.
- **IdoneitaConduzione**: per-proclamatore per-tipo-parte, flag `canLead`. Determina
  se il proclamatore è candidato allo slot 1 di quel tipo di parte.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un nuovo proclamatore può essere creato in meno di 3 interazioni UI.
- **SC-002**: La ricerca per cognome restituisce risultati in meno di 200 ms su un
  archivio di 200 proclamatori.
- **SC-003**: L'import di un file JSON con 200 proclamatori completa senza errori in
  meno di 5 secondi.
- **SC-004**: Tutti i DomainError di validazione vengono mostrati all'utente come
  messaggi comprensibili in italiano.

## Clarifications

### Session 2026-02-25

- Q: Le spec sono state reverse-engineered dal codice esistente → A: Confermato dal
  progetto; questa spec documenta il comportamento attuale del codice.

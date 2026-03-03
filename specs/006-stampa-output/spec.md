# Feature Specification: Stampa e Output

**Feature Branch**: `006-stampa-output`
**Created**: 2026-02-25
**Status**: Draft (reverse-engineered from existing code)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Stampa del programma mensile in PDF (Priority: P1)

L'utente vuole generare un file PDF del programma mensile con settimane, parti e
assegnazioni correnti, pronto per la distribuzione.

**Why this priority**: È il flusso principale esposto in UX (`Stampa programma`) e
produce l'artefatto operativo finale.

**Independent Test**: Con un programma mensile esistente con settimane/assegnazioni →
avviare la stampa → verificare creazione PDF in export con nome e contenuto coerenti.

**Acceptance Scenarios**:

1. **Given** un programma con settimane e assegnazioni, **When** si avvia la stampa,
   **Then** viene generato un PDF in `<exports>/programmi/programma-YYYY-MM.pdf`.
2. **Given** una settimana con slot non assegnati, **When** si stampa il programma,
   **Then** nel PDF gli slot mancanti appaiono come "Non assegnato".
3. **Given** una parte con più slot, **When** si stampa, **Then** slot 1 è etichettato
   "Conducente" e slot successivi "Assistente".
4. **Given** il programma non esiste, **When** si avvia la stampa, **Then** l'operazione
   termina con errore "Programma non trovato".

---

### User Story 2 - Export PDF assegnazioni settimanali (Priority: P2)

L'utente vuole esportare un PDF settimanale delle assegnazioni, includendo tutte o
solo alcune parti selezionate.

**Why this priority**: Permette output rapido focalizzato sulla singola settimana,
utile per condivisione locale o verifica.

**Independent Test**: Selezionare una settimana con assegnazioni → esportare PDF con
insieme parti vuoto (tutte) e con subset parti → verificare contenuto e naming file.

**Acceptance Scenarios**:

1. **Given** una settimana esistente, **When** si esporta PDF con `selectedPartIds`
   vuoto, **Then** il documento include tutte le parti ordinate per `sortOrder`.
2. **Given** un sottoinsieme di parti selezionate, **When** si esporta, **Then**
   il PDF include solo le parti selezionate.
3. **Given** la settimana non esiste, **When** si esporta, **Then** l'operazione
   fallisce con errore "Settimana non trovata per <data>".
4. **Given** l'export completato, **When** il file viene salvato, **Then** il nome
   rispetta il formato `assegnazioni-YYYY-MM-DD-YYYY-MM-DD.pdf`.

---

### User Story 3 - Export immagini per proclamatore (Priority: P3)

L'utente vuole generare una scheda immagine PNG per ogni proclamatore assegnato in
settimana, con l'elenco delle parti assegnate.

**Why this priority**: Utile per distribuzioni individuali rapide senza aprire PDF.

**Independent Test**: Con una settimana con più proclamatori assegnati → avviare export
immagini → verificare che venga prodotto un PNG per ciascun proclamatore coinvolto.

**Acceptance Scenarios**:

1. **Given** assegnazioni presenti in settimana, **When** si genera export immagini,
   **Then** viene creato un file PNG per ogni proclamatore con almeno un'assegnazione.
2. **Given** selezione di parti limitata, **When** si genera export immagini, **Then**
   ogni PNG include solo le assegnazioni appartenenti alle parti selezionate.
3. **Given** un errore durante rendering/conversione, **When** l'operazione fallisce,
   **Then** viene restituito un errore con contesto del proclamatore e path coinvolti.
4. **Given** l'export immagini termina, **When** il file temporaneo PDF non serve più,
   **Then** il sistema tenta la pulizia del temporaneo.

---

### Edge Cases

- Programma non trovato per `programId` in stampa mensile.
- Settimana non trovata per `weekStartDate` negli export settimanali.
- `selectedPartIds` vuoto negli export settimanali: interpretato come "tutte le parti".
- Parti con `peopleCount = 1`: nessuna etichetta ruolo aggiuntiva.
- Nome proclamatore con caratteri speciali: normalizzazione nel nome file PNG.
- Errore di cleanup del PDF temporaneo nella generazione immagini: il fallimento viene
  loggato ma non annulla automaticamente i PNG già prodotti.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Il sistema MUST consentire la stampa di un programma mensile dato il
  `programId`.
- **FR-002**: La stampa mensile MUST produrre un PDF con una sezione per settimana
  ordinata per data.
- **FR-003**: Ogni riga di parte nel PDF mensile MUST mostrare etichetta parte, ruolo
  (quando applicabile) e nominativo assegnato o "Non assegnato".
- **FR-004**: Il PDF mensile MUST essere salvato in
  `<exportsDir>/programmi/programma-YYYY-MM.pdf`.
- **FR-005**: Il sistema MUST restituire il `Path` del PDF mensile generato.
- **FR-006**: Il sistema MUST consentire export PDF settimanale assegnazioni dato
  `weekStartDate` e un set opzionale di `selectedPartIds`.
- **FR-007**: L'export PDF settimanale MUST salvare in `<exportsDir>/assegnazioni/`
  con formato `assegnazioni-<weekStart>-<weekEnd>.pdf`.
- **FR-008**: L'export PDF settimanale MUST includere solo le parti selezionate; se
  nessuna selezione è passata, MUST includere tutte le parti della settimana.
- **FR-009**: Il sistema MUST consentire export immagini PNG per proclamatore a partire
  dalle assegnazioni della settimana (con filtro parti opzionale).
- **FR-010**: L'export immagini MUST produrre file `.png` in `<exportsDir>/assegnazioni/`
  e restituire la lista dei `Path` generati.
- **FR-011**: Le operazioni di output MUST essere eseguite su contesto IO per evitare
  blocchi del thread UI.
- **FR-012**: In caso di dati mancanti (programma/settimana), il sistema MUST fallire
  con errore esplicito e messaggio diagnostico.

### Key Entities

- **ProgramWeekPrintSection**: weekStartDate, statusLabel, lines; sezione logica del PDF
  mensile.
- **RenderedPart**: label + righe assegnazioni già materializzate per PDF settimanale.
- **PersonSheet**: fullName, weekStart, weekEnd, assignments; modello intermedio per
  la scheda individuale esportata in PNG.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: La stampa PDF mensile (4-5 settimane) completa in meno di 5 secondi.
- **SC-002**: L'export PDF settimanale completa in meno di 3 secondi su una settimana
  standard.
- **SC-003**: L'export immagini produce 1 PNG per proclamatore assegnato con tasso di
  successo del 100% su input validi.
- **SC-004**: Durante ogni export, la UI resta responsiva (nessun freeze percepibile).

## Clarifications

### Session 2026-02-25

- Q: Le spec sono reverse-engineered dal codice esistente? → A: Sì.
- Q: Esiste solo la stampa mensile? → A: No. Oltre a `StampaProgrammaUseCase`, il codice
  implementa anche `GeneraPdfAssegnazioni` (settimanale) e `GeneraImmaginiAssegnazioni`
  (schede PNG per proclamatore).

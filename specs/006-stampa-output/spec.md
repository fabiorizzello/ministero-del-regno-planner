# Feature Specification: Stampa e Output

**Feature Branch**: `006-stampa-output`
**Created**: 2026-02-25
**Status**: Draft (reverse-engineered from existing code)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Stampa del programma mensile in PDF (Priority: P1)

L'utente vuole generare un file PDF del programma mensile con tutte le settimane,
le parti e i proclamatori assegnati, da distribuire alla congregazione.

**Why this priority**: Il PDF è il prodotto finale consegnabile dell'applicazione —
il motivo per cui esiste il pianificatore.

**Independent Test**: Con un programma mensile con settimane e assegnazioni → avviare
la stampa → verificare che venga creato un file PDF nella cartella esportazioni con
il nome corretto e contenuto leggibile.

**Acceptance Scenarios**:

1. **Given** un programma con settimane e assegnazioni complete, **When** si avvia
   la stampa, **Then** viene generato un PDF in
   `<exports>/programmi/programma-YYYY-MM.pdf` con tutte le settimane e i nomi
   assegnati.
2. **Given** una settimana con slot non assegnati, **When** si stampa, **Then**
   gli slot non assegnati appaiono come "Non assegnato" nel PDF.
3. **Given** una parte con più slot (peopleCount > 1), **When** si stampa, **Then**
   lo slot 1 è etichettato "Conducente" e gli slot successivi "Assistente".
4. **Given** una parte con un solo slot (peopleCount = 1), **When** si stampa,
   **Then** nessuna etichetta ruolo viene mostrata (solo nome parte e nome assegnato).
5. **Given** il programma non esiste nel DB, **When** si avvia la stampa, **Then**
   viene sollevata un'eccezione con messaggio "Programma non trovato".

---

### Edge Cases

- Il percorso di output include la directory `<exports>/programmi/` che deve essere
  creata se non esiste (gestito da `AppRuntime.paths().exportsDir`).
- Il nome file è fisso: `programma-YYYY-MM.pdf` (es. `programma-2026-03.pdf`).
- La stampa avviene su `Dispatchers.IO` (non blocca il thread UI).
- Settimane con status SKIPPED vengono incluse nel PDF con label "SKIPPED".

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Il sistema MUST generare un PDF per un programma mensile specificato
  per ID.
- **FR-002**: Il PDF MUST contenere una sezione per ogni settimana del programma,
  ordinata per data.
- **FR-003**: Ogni sezione settimanale MUST elencare tutte le parti con: nome parte,
  etichetta ruolo (se peopleCount > 1), nome del proclamatore assegnato o
  "Non assegnato".
- **FR-004**: Il titolo del PDF MUST essere "Programma M/YYYY" (es. "Programma 3/2026").
- **FR-005**: Il file MUST essere salvato in `<exportsDir>/programmi/programma-YYYY-MM.pdf`.
- **FR-006**: La generazione del PDF MUST avvenire su thread IO (non bloccare la UI).
- **FR-007**: Il sistema MUST restituire il `Path` del file generato al chiamante.

### Key Entities

- **ProgramWeekPrintSection**: weekStartDate, statusLabel, lines (lista stringhe).
  Struttura intermedia usata per rendere il PDF.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: La generazione del PDF per un programma mensile (4-5 settimane,
  ~20-30 righe) completa in meno di 5 secondi.
- **SC-002**: Il file PDF generato è leggibile e correttamente formattato su un
  visualizzatore PDF standard.
- **SC-003**: La UI rimane responsiva durante la generazione del PDF (operazione
  su Dispatchers.IO).

## Clarifications

### Session 2026-02-25

- Q: Le spec sono state reverse-engineered dal codice esistente → A: Confermato.
- Q: Il formato del titolo settimanale nel PDF? → A: Dal codice, ogni sezione ha
  `weekStartDate` e `statusLabel`; la formattazione visuale è delegata a
  `PdfProgramRenderer` (infrastruttura).

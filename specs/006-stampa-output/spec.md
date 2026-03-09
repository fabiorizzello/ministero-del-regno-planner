# Feature Specification: Stampa e Output

**Feature Branch**: `006-stampa-output`
**Created**: 2026-02-25
**Status**: Draft (reverse-engineered from existing code)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Stampa del programma mensile in PDF (Priority: P1)

L'utente vuole generare e aprire un PDF del programma mensile con settimane, parti e
assegnazioni correnti, pronto per la distribuzione.

**Why this priority**: E il flusso principale esposto in UX (`Stampa PDF programma`) e
produce l'artefatto operativo finale.

**Independent Test**: Con un programma mensile esistente con settimane/assegnazioni ->
avviare la stampa -> verificare creazione PDF in export, apertura del file e contenuto
coerente con il programma selezionato.

**Acceptance Scenarios**:

1. **Given** un programma con settimane e assegnazioni, **When** si avvia la stampa,
   **Then** viene generato un PDF in `<exports>/programmi/programma-YYYY-MM.pdf`.
2. **Given** una settimana con slot non assegnati, **When** si stampa il programma,
   **Then** nel PDF gli slot mancanti appaiono come `Non assegnato`.
3. **Given** una parte con un solo slot, **When** si stampa, **Then** il ruolo viene
   mostrato come `Studente`.
4. **Given** una parte con piu slot, **When** si stampa, **Then** slot 1 e etichettato
   `Studente` e gli slot successivi `Assistente`.
5. **Given** una settimana marcata come saltata/disattivata, **When** si stampa il
   programma, **Then** la sezione settimana mostra `Settimana non assegnata`.
6. **Given** esistono PDF mensili precedenti in `<exports>/programmi`, **When** si
   genera il PDF del programma corrente, **Then** il sistema mantiene il file corrente e
   tenta di eliminare gli altri `programma-*.pdf`.
7. **Given** il programma non esiste, **When** si avvia la stampa, **Then** l'operazione
   termina con errore `Programma non trovato`.

---

### User Story 2 - Export PDF assegnazioni settimanali (Priority: P2)

L'utente vuole esportare un PDF settimanale delle assegnazioni, includendo tutte o
solo alcune parti selezionate.

**Why this priority**: Permette output rapido focalizzato sulla singola settimana,
utile per condivisione locale o verifica.

**Nota implementativa**: `GeneraPdfAssegnazioni` è stato rimosso come codice morto
(2026-03-09): era registrato in DI ma non esposto da nessun entry point UI o ViewModel.
Solo `StampaProgrammaUseCase` e `GeneraImmaginiAssegnazioni.generateProgramTickets`
hanno wiring UI completo.

**Independent Test**: Selezionare una settimana con assegnazioni -> esportare PDF con
insieme parti vuoto (tutte) e con subset parti -> verificare contenuto e naming file.

**Acceptance Scenarios**:

1. **Given** una settimana esistente, **When** si esporta PDF con `selectedPartIds`
   vuoto, **Then** il documento include tutte le parti ordinate per `sortOrder`.
2. **Given** un sottoinsieme di parti selezionate, **When** si esporta, **Then**
   il PDF include solo le parti selezionate.
3. **Given** la settimana non esiste, **When** si esporta, **Then** l'operazione
   fallisce con errore `Settimana non trovata per <data>`.
4. **Given** l'export completato, **When** il file viene salvato, **Then** il nome
   rispetta il formato `assegnazioni-YYYY-MM-DD-YYYY-MM-DD.pdf`.

---

### User Story 3 - Export immagini per proclamatore (Priority: P3)

L'utente vuole generare una scheda immagine PNG per ogni proclamatore assegnato in
settimana, con l'elenco delle parti assegnate.

**Why this priority**: Utile per distribuzioni individuali rapide senza aprire PDF.

**Independent Test**: Con una settimana con parti complete e parti incomplete -> avviare
export biglietti -> verificare che vengano prodotti PNG solo per proclamatori con slot 1
in parti complete, e card ghost per le parti incomplete.

**Acceptance Scenarios**:

1. **Given** assegnazioni presenti in settimana, **When** si genera export immagini,
   **Then** viene creato un file PNG solo per i proclamatori con ruolo principale (slot 1)
   in parti **completamente coperte** (tutti gli slot assegnati); i proclamatori in parti
   incomplete e i proclamatori esclusivamente slot 2+ non ricevono biglietto.
2. **Given** selezione di parti limitata, **When** si genera export immagini, **Then**
   ogni PNG include solo le assegnazioni appartenenti alle parti selezionate.
3. **Given** un errore durante rendering/conversione, **When** l'operazione fallisce,
   **Then** viene restituito un errore con contesto del proclamatore e path coinvolti.
4. **Given** l'export immagini termina, **When** il file temporaneo PDF non serve piu,
   **Then** il sistema tenta la pulizia del temporaneo.
5. **Given** una parte con slot non tutti compilati, **When** si visualizzano i biglietti,
   **Then** la UI mostra una card ghost "Parte parziale (N/M assegnati)" al posto di
   qualsiasi biglietto per quella parte; nessun PNG viene generato per nessuno dei
   proclamatori assegnati in quella parte, nemmeno per lo slot 1.
6. **Given** una parte completamente priva di assegnazioni, **When** si visualizzano i biglietti,
   **Then** la UI mostra una card ghost "Parte vuota"; nessun PNG viene generato.
7. **Given** biglietti già generati per un mese, **When** si rigenerano i biglietti dello
   stesso mese, **Then** i PNG precedenti del mese vengono eliminati prima di produrre
   i nuovi; i biglietti di altri mesi non vengono toccati.

---

### Edge Cases

- Programma non trovato per `programId` in stampa mensile.
- Settimana non trovata per `weekStartDate` negli export settimanali.
- `selectedPartIds` vuoto negli export settimanali: interpretato come `tutte le parti`.
- Parti con `peopleCount = 1`: il PDF mensile mostra comunque il ruolo `Studente`.
- Settimane `SKIPPED`: nessuna card parte, solo testo `Settimana non assegnata`.
- Proclamatore solo assistente (slot 2+ senza slot 1): escluso dalla generazione biglietti.
- Parte con slot parzialmente compilati: nessun biglietto per nessuno della parte (neanche slot 1); solo card ghost parzialità.
- Parte completamente vuota: solo card ghost assenza; inclusa nelle settimane attive del programma.
- Nome proclamatore con caratteri speciali: normalizzazione nel nome file PNG.
- Errore di cleanup dei vecchi PDF programma: il fallimento viene loggato ma non blocca
  la creazione del nuovo PDF.
- Errore di cleanup del PDF temporaneo nella generazione immagini: il fallimento viene
  loggato ma non annulla automaticamente i PNG gia prodotti.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Il sistema MUST consentire la stampa di un programma mensile dato il
  `programId`.
- **FR-002**: La stampa mensile MUST produrre un PDF con una sezione per settimana
  ordinata per data.
- **FR-003**: Il PDF mensile MUST usare un layout tipografico a griglia, con card parte
  distribuite su righe fino a 3 colonne per settimana.
- **FR-004**: Ogni card parte nel PDF mensile MUST mostrare numero parte, etichetta parte,
  ruolo e nominativo assegnato oppure `Non assegnato`.
- **FR-005**: Le settimane `SKIPPED` MUST essere stampate come sezione senza card, con
  il testo `Settimana non assegnata`.
- **FR-006**: Il PDF mensile MUST essere salvato in
  `<exportsDir>/programmi/programma-YYYY-MM.pdf`.
- **FR-007**: Prima di salvare il PDF mensile corrente, il sistema MUST tentare la
  pulizia degli altri file `programma-*.pdf` presenti nella cartella export mensile.
- **FR-008**: Il sistema MUST restituire il `Path` del PDF mensile generato e MUST
  tentare l'apertura del file tramite integrazione desktop.
- **FR-009**: La UI MUST poter avviare la stampa mensile senza mostrare un banner di
  successo obbligatorio al termine; gli errori restano invece visibili.
- **FR-010**: Il sistema MUST consentire export PDF settimanale assegnazioni dato
  `weekStartDate` e un set opzionale di `selectedPartIds`.
- **FR-011**: L'export PDF settimanale MUST salvare in `<exportsDir>/assegnazioni/`
  con formato `assegnazioni-<weekStart>-<weekEnd>.pdf`.
- **FR-012**: L'export PDF settimanale MUST includere solo le parti selezionate; se
  nessuna selezione e passata, MUST includere tutte le parti della settimana.
- **FR-013**: Il sistema MUST consentire export immagini PNG per proclamatore a partire
  dalle assegnazioni della settimana (con filtro parti opzionale).
- **FR-014**: L'export immagini MUST produrre file `.png` in `<exportsDir>/assegnazioni/`
  e restituire la lista dei `Path` generati.
- **FR-015**: Le operazioni di output MUST essere eseguite su contesto IO per evitare
  blocchi del thread UI.
- **FR-016**: In caso di dati mancanti (programma/settimana), il sistema MUST fallire
  con errore esplicito e messaggio diagnostico.
- **FR-017**: L'export immagini MUST generare un PNG solo per i proclamatori con slot 1
  in parti **completamente coperte** (tutti gli slot assegnati); i proclamatori in parti
  incomplete e i proclamatori esclusivamente slot 2+ MUST essere esclusi.
- **FR-018**: La UI MUST visualizzare una card ghost per ogni parte con slot parzialmente
  o completamente non coperti, posizionata nella settimana di appartenenza. La card MUST
  distinguere "parte parziale" (N/M assegnati) da "parte vuota" (0/M). Le card ghost
  MUST apparire dopo i biglietti normali all'interno della stessa settimana. Le card ghost
  MUST occupare l'intera altezza della riga della griglia (`fillMaxHeight`) per allinearsi
  visivamente ai biglietti normali adiacenti.
- **FR-022**: Prima di generare i biglietti di un mese, il sistema MUST tentare
  la pulizia dei PNG `biglietto-YYYY-MM-*.png` dello stesso mese presenti nella cartella
  export; i file di altri mesi MUST essere preservati; un errore di cleanup MUST essere
  loggato ma non bloccare la generazione.
- **FR-023**: Ogni `AssignmentTicketLine` MUST includere un `partNumber: Int` calcolato
  come `sortOrder + PART_DISPLAY_NUMBER_OFFSET` (costante = 3, poiché le parti fisse
  di apertura occupano i numeri 1 e 2). La costante è definita in
  `feature/output/application/OutputConstants.kt` come `internal const PART_DISPLAY_NUMBER_OFFSET = 3`.
  La UI MUST mostrare il numero come prefisso dell'etichetta parte (es. "3. Studio biblico").
  Il PDF del biglietto MUST includere il numero nel formato "3. Studio biblico (Studente)".
- **FR-024**: I biglietti prodotti da `generateProgramTickets` MUST essere ordinati per
  `weekStart` (crescente), poi per `sortOrder` della parte principale del proclamatore
  (crescente), poi per `fullName` alfabetico. All'interno di `buildPersonTicketSheets`
  (export settimanale) l'ordinamento MUST applicare gli stessi criteri escludendo
  `weekStart`.
- **FR-019**: Il titolo del mese nel PDF mensile MUST essere centrato orizzontalmente
  sull'intera larghezza della pagina.
- **FR-020**: Le intestazioni di ogni sezione settimana nel PDF mensile MUST essere
  centrate orizzontalmente nell'area contenuto della sezione.
- **FR-021**: Il layout verticale del PDF mensile MUST essere ottimizzato per compattezza:
  il margine pagina SHOULD essere mantenuto, mentre spaziature interne (gap titolo,
  gap tra sezioni, altezze header settimana) SHOULD essere minimizzate per contenere
  4-5 settimane in 1 pagina A4 senza overflow.

### Key Entities

- **ProgramWeekPrintSection**: `weekStartDate`, `weekEndDate`, `statusLabel`, `cards`,
  `emptyStateLabel`; sezione logica del PDF mensile.
- **ProgramWeekPrintCard**: `displayNumber`, `partLabel`, `status`, `statusLabel`,
  `slots`; card parte renderizzata nel PDF mensile.
- **ProgramWeekPrintSlot**: `roleLabel`, `assignedTo`, `isAssigned`; riga ruolo/persona
  della card.
- **RenderedPart**: label + righe assegnazioni gia materializzate per PDF settimanale.
- **PersonSheet**: `fullName`, `weekStart`, `weekEnd`, `assignments`; modello intermedio
  per la scheda individuale esportata in PNG.
- **AssignmentTicketLine**: `partLabel`, `roleLabel`, `partNumber`; riga di assegnazione
  nella scheda del proclamatore. `partNumber = sortOrder + 3` (`PART_DISPLAY_NUMBER_OFFSET`
  in `OutputConstants.kt`).
- **AssignmentTicketImage**: `fullName`, `weekStart`, `weekEnd`, `imagePath`, `assignments`;
  biglietto PNG generato per un proclamatore con ruolo principale.
- **PartAssignmentWarning**: `weekStart`, `weekEnd`, `partLabel`, `assignedCount`,
  `expectedCount`; segnalazione di parte parziale (`isPartial`) o vuota (`isEmpty`).
- **TicketGenerationResult**: `tickets: List<AssignmentTicketImage>`, `warnings: List<PartAssignmentWarning>`;
  risultato aggregato di `generateProgramTickets`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: La stampa PDF mensile (4-5 settimane) completa in meno di 5 secondi.
- **SC-002**: Il PDF mensile standard resta contenuto in 1 pagina A4 per dataset mensile
  normale (4-5 settimane, layout attuale a griglia).
- **SC-003**: L'export PDF settimanale completa in meno di 3 secondi su una settimana
  standard.
- **SC-004**: L'export immagini produce esattamente 1 PNG per proclamatore slot 1 in parti
  complete, e 1 card ghost per ogni parte incompleta (le due categorie sono mutuamente
  esclusive per parte), con tasso di successo del 100% su input validi.
- **SC-005**: Durante ogni export, la UI resta responsiva (nessun freeze percepibile).

## Clarifications

### Session 2026-02-25

- Q: Le spec sono reverse-engineered dal codice esistente? -> A: Si.
- Q: Esiste solo la stampa mensile? -> A: No. Oltre a `StampaProgrammaUseCase`, il codice
  implementa anche `GeneraImmaginiAssegnazioni` (schede PNG per proclamatore).
  `GeneraPdfAssegnazioni` (export PDF settimanale) è stato rimosso come codice morto il
  2026-03-09: era registrato in DI ma nessun ViewModel lo invocava.

### Session 2026-03-03

- Q: Qual e il label di ruolo nei PDF? -> A: `Studente` per slot singolo o slot 1,
  `Assistente` per slot >= 2. Uniformato in `StampaProgrammaUseCase`.

### Session 2026-03-08

- Q: Come viene impaginato oggi il PDF mensile? -> A: In 1 pagina A4 con sezioni
  settimana e griglia tipografica di card parte fino a 3 colonne.
- Q: Cosa succede alle settimane disattivate? -> A: Vengono stampate come sezione con il
  solo testo `Settimana non assegnata`.
- Q: Dove viene salvato il PDF mensile? -> A: In
  `<exportsDir>/programmi/programma-YYYY-MM.pdf`.
- Q: I vecchi PDF mensili restano nella cartella export? -> A: Il sistema tenta la
  pulizia degli altri `programma-*.pdf` prima di salvare il file corrente.
- Q: La UI mostra conferma positiva dopo la stampa? -> A: No, il flusso standard apre il
  file senza banner di successo obbligatorio; gli errori restano visibili.

### Session 2026-03-09

- Q: I biglietti vengono generati anche per chi è solo assistente? -> A: No. Solo i
  proclamatori con almeno uno slot 1 (ruolo principale) ricevono un PNG. I proclamatori
  esclusivamente slot 2+ sono esclusi (FR-017).
- Q: Cosa mostra la UI per le parti incomplete o vuote? -> A: Una card di avviso nella
  griglia della settimana, distinta da quelle con PNG. Mostra il nome della parte, il
  tipo di avviso ("Parte parziale" con conteggio N/M, oppure "Parte vuota") e un colore
  semantico (arancio per parziale, rosso per vuota). Nessun PNG viene generato per gli
  slot mancanti (FR-018). Le card ghost si estendono all'altezza piena della riga.
- Q: Il titolo mese e le intestazioni settimana sono allineati a sinistra? -> A: No,
  entrambi devono essere centrati orizzontalmente (FR-019, FR-020).
- Q: Quanto spazio verticale lascia il layout tra titolo e sezioni? -> A: Il layout è
  compatto: font titolo 13pt (non 16pt), gap dopo titolo 16pt, gap tra sezioni 4pt.
  Le proporzioni interne (header settimana, gap, slot row) sono ridotte rispetto
  all'impostazione originale per contenere 4-5 settimane in 1 pagina A4 (FR-021, SC-002).
- Q: In che ordine appaiono i biglietti nella modale? -> A: Ordinati per settimana
  (crescente), poi per sortOrder della parte principale del proclamatore, poi
  alfabeticamente per nome. Stesso criterio per l'export settimanale (escluso weekStart).
  (FR-024).
- Q: Il numero della parte appare sui biglietti? -> A: Sì. Sia nella UI (card dialog)
  che nel PDF del biglietto il numero compare come prefisso: "3. Studio biblico".
  Calcolato come `sortOrder + 3` dove 3 è `PART_DISPLAY_NUMBER_OFFSET` (le parti fisse
  di apertura sono numerate 1 e 2). (FR-023).

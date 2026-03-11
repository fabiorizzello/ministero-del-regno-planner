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
   programma, **Then** la sezione settimana mostra `Settimana saltata`.
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

### User Story 3 - Biglietti S-89 per parte (Priority: P1)

L'utente vuole generare un biglietto S-89 (PNG) per ogni parte completamente assegnata
del programma mensile. Ogni biglietto corrisponde a una parte (studente + assistente
opzionale) e viene prodotto compilando il template ufficiale S-89 con overlay dei dati.

**Why this priority**: È il mezzo principale di distribuzione delle assegnazioni ai
proclamatori. Ogni biglietto viene consegnato individualmente.

**Independent Test**: Con un programma con parti complete, parziali e vuote -> generare
biglietti -> verificare 1 PNG per parte completa (con studente + assistente), card ghost
per parti incomplete, e template S-89 compilato correttamente.

**Acceptance Scenarios**:

1. **Given** assegnazioni presenti in settimana, **When** si generano i biglietti,
   **Then** viene creato un file PNG per ogni parte **completamente coperta** (tutti gli
   slot assegnati) che ha almeno lo slot 1 (studente). Il PNG è prodotto compilando il
   template S-89 con: nome studente, nome assistente (se presente), data settimana,
   numero e etichetta parte, checkbox "Sala principale" sempre selezionato.
2. **Given** una parte con 2 slot entrambi assegnati, **When** si genera il biglietto,
   **Then** il PNG include sia lo studente (slot 1) che l'assistente (slot 2) nel
   template S-89.
3. **Given** un errore durante rendering/conversione, **When** l'operazione fallisce,
   **Then** viene restituito un errore con contesto dello studente e path coinvolti.
4. **Given** l'export immagini termina, **When** il file temporaneo PDF non serve piu,
   **Then** il sistema tenta la pulizia del temporaneo.
5. **Given** una parte con slot non tutti compilati, **When** si visualizzano i biglietti,
   **Then** la UI mostra una card ghost "Parte parziale (N/M assegnati)"; nessun PNG
   viene generato per quella parte.
6. **Given** una parte completamente priva di assegnazioni, **When** si visualizzano i biglietti,
   **Then** la UI mostra una card ghost "Parte vuota"; nessun PNG viene generato.
7. **Given** biglietti già generati per un mese, **When** si rigenerano i biglietti dello
   stesso mese, **Then** i PNG precedenti del mese vengono eliminati prima di produrre
   i nuovi; i biglietti di altri mesi non vengono toccati.

---

### User Story 4 - Tracking consegna biglietti S-89 (Priority: P1)

L'utente genera i biglietti S-89 di un programma mensile e li distribuisce uno alla volta
ai proclamatori (via drag & drop, WhatsApp, stampa, ecc.). Vuole tracciare quali biglietti
ha già consegnato, quali restano da consegnare, e ricevere un avviso se modifica
un'assegnazione per cui il biglietto era già stato consegnato.

**Why this priority**: Senza tracking, l'utente deve ricordare a memoria quali biglietti ha
già distribuito. Con 15-25 biglietti al mese e distribuzione graduale nell'arco di giorni,
il rischio di dimenticanze o doppi invii è alto.

**Independent Test**: Generare biglietti per un programma con parti complete → segnare
alcuni come inviati → verificare separazione nella dialog → cambiare assegnazione di un
biglietto inviato → verificare warning e reset stato.

**Acceptance Scenarios**:

1. **Given** un biglietto generato in dialog, **When** l'utente clicca "Segna come inviato",
   **Then** il sistema registra la consegna con `studentName`, `assistantName`, `sentAt` e
   il biglietto si sposta nella sezione "Inviati" della dialog.
2. **Given** un biglietto già inviato, **When** l'utente trascina o visualizza il biglietto,
   **Then** il biglietto resta utilizzabile normalmente (drag & drop, visualizza); lo stato
   "inviato" non impedisce nessuna azione.
3. **Given** biglietti inviati e non inviati per lo stesso programma, **When** l'utente apre
   la dialog biglietti, **Then** la dialog mostra due sezioni per settimana:
   **"Da inviare"** (prominente, in alto) e **"Inviati"** (visivamente secondaria, sotto).
   I biglietti da inviare hanno il pulsante "Segna come inviato" ben visibile come azione
   successiva suggerita.
4. **Given** un'assegnazione il cui biglietto è stato inviato, **When** l'utente modifica
   l'assegnazione (cambia studente o assistente), **Then** il sistema mostra un dialog di
   conferma: _"Hai già inviato il biglietto a {studentName}. Ricordati di avvisarlo del
   cambio."_ Se l'utente conferma, la consegna precedente viene annullata (`cancelledAt`)
   e il nuovo biglietto appare come "Da inviare".
5. **Given** una consegna annullata per cambio assegnazione, **When** il nuovo biglietto
   viene visualizzato nella sezione "Da inviare", **Then** la card mostra una nota
   informativa: _"Precedente: {vecchio studentName}"_.
6. **Given** un programma con biglietti generati, **When** l'utente visualizza il pulsante
   del programma che apre i biglietti, **Then** il pulsante mostra un badge con i contatori:
   _"N da inviare"_ e, se presenti, _"M bloccati"_ (parti vuote o parziali).
7. **Given** tutti i biglietti di un programma inviati e nessun warning, **When** l'utente
   visualizza il pulsante, **Then** il badge mostra _"Tutti inviati"_ con indicatore
   visivo di completamento.

---

### Edge Cases

- Programma non trovato per `programId` in stampa mensile.
- Settimana non trovata per `weekStartDate` negli export settimanali.
- `selectedPartIds` vuoto negli export settimanali: interpretato come `tutte le parti`.
- Parti con `peopleCount = 1`: il PDF mensile mostra comunque il ruolo `Studente`.
- Settimane `SKIPPED`: nessuna card parte, solo testo `Settimana saltata`.
- Proclamatore solo assistente (slot 2+ senza slot 1): escluso dalla generazione biglietti.
- Parte con slot parzialmente compilati: nessun biglietto per nessuno della parte (neanche slot 1); solo card ghost parzialità.
- Parte completamente vuota: solo card ghost assenza; inclusa nelle settimane attive del programma.
- Nome proclamatore con caratteri speciali: normalizzazione nel nome file PNG.
- Errore di cleanup dei vecchi PDF programma: il fallimento viene loggato ma non blocca
  la creazione del nuovo PDF.
- Errore di cleanup del PDF temporaneo nella generazione immagini: il fallimento viene
  loggato ma non annulla automaticamente i PNG gia prodotti.
- Biglietto inviato e poi rigenerato (nuova generazione biglietti mese): la consegna
  resta valida se l'assegnazione non è cambiata; se l'assegnazione è cambiata, la
  consegna precedente risulta annullata.
- Parte precedentemente completa diventa parziale (rimozione assegnazione): la consegna
  precedente viene annullata; il biglietto sparisce dalla dialog e la parte appare come
  ghost parziale.
- Doppio click su "Segna come inviato": il sistema è idempotente, non crea duplicati
  se esiste già una consegna attiva per la stessa parte/settimana.
- Programma senza biglietti generati: il badge mostra solo i contatori bloccati (warning),
  nessun contatore "da inviare" finché i biglietti non vengono generati.
- Consegna annullata multipla (cambio assegnazione 2+ volte): la nota "Precedente" mostra
  solo l'ultimo assegnatario a cui era stato inviato, non l'intera catena.

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
  il testo `Settimana saltata`.
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
  Il PDF del biglietto MUST includere il numero nel formato "3. Studio biblico".
  Il role label "(Studente)" NON è incluso perché i biglietti sono sempre per lo studente.
- **FR-024**: I biglietti prodotti da `generateProgramTickets` MUST essere ordinati per
  `weekStart` (crescente), poi per `sortOrder` della parte principale del proclamatore
  (crescente), poi per `fullName` alfabetico. All'interno di `buildPersonTicketSheets`
  (export settimanale) l'ordinamento MUST applicare gli stessi criteri escludendo
  `weekStart`.
- **FR-025**: Il sistema MUST tracciare la consegna dei biglietti S-89 in una tabella
  dedicata `slip_delivery(id, weekly_part_id, week_plan_id, student_name, assistant_name,
  sent_at, cancelled_at)`. La chiave logica è `(weekly_part_id, week_plan_id)` con al
  massimo una riga attiva (`cancelled_at IS NULL`) per coppia.
- **FR-026**: La UI MUST fornire un pulsante "Segna come inviato" su ogni biglietto nella
  sezione "Da inviare". Il pulsante MUST essere visivamente prominente (accent color,
  stile filled) per guidare l'utente verso l'azione successiva.
- **FR-027**: La dialog biglietti MUST separare i biglietti in due sezioni per settimana:
  "Da inviare" (in alto, prominente) e "Inviati" (in basso, visivamente secondaria con
  stile attenuato e indicatore di completamento). Le card ghost (parti vuote/parziali)
  MUST apparire nella sezione "Da inviare".
- **FR-028**: Quando l'utente modifica un'assegnazione il cui biglietto è stato inviato,
  il sistema MUST mostrare un dialog di conferma con il messaggio di avviso che include
  il nome del precedente assegnatario. Solo dopo conferma il sistema MUST annullare la
  consegna (impostando `cancelled_at`) e procedere con la modifica.
- **FR-029**: Un biglietto nella sezione "Da inviare" il cui predecessore è stato annullato
  MUST mostrare una nota informativa con il nome del precedente assegnatario
  (es. "Precedente: Mario Rossi").
- **FR-030**: Il pulsante che apre la dialog biglietti MUST mostrare un badge con:
  (a) il numero di biglietti da inviare, (b) il numero di parti bloccate (vuote/parziali).
  Se tutti i biglietti sono inviati e non ci sono warning, il badge MUST mostrare
  "Tutti inviati".
- **FR-031**: Il "Segna come inviato" MUST essere idempotente: se esiste già una consegna
  attiva per la stessa `(weekly_part_id, week_plan_id)`, l'operazione non crea duplicati.
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
- **AssignmentSlip**: `studentName`, `assistantName?`, `weekStart`, `partNumber`,
  `partLabel`; modello per compilare il template S-89 con overlay dati.
- **AssignmentTicketLine**: `partLabel`, `roleLabel`, `partNumber`; riga di assegnazione
  nella scheda del proclamatore. `partNumber = sortOrder + 3` (`PART_DISPLAY_NUMBER_OFFSET`
  in `OutputConstants.kt`).
- **AssignmentTicketImage**: `fullName`, `weekStart`, `weekEnd`, `imagePath`, `assignments`;
  biglietto PNG generato per un proclamatore con ruolo principale.
- **PartAssignmentWarning**: `weekStart`, `weekEnd`, `partLabel`, `assignedCount`,
  `expectedCount`; segnalazione di parte parziale (`isPartial`) o vuota (`isEmpty`).
- **TicketGenerationResult**: `tickets: List<AssignmentTicketImage>`, `warnings: List<PartAssignmentWarning>`;
  risultato aggregato di `generateProgramTickets`.
- **SlipDelivery**: `id`, `weeklyPartId`, `weekPlanId`, `studentName`, `assistantName?`,
  `sentAt`, `cancelledAt?`; record di consegna di un biglietto S-89. Una sola consegna
  attiva per coppia `(weeklyPartId, weekPlanId)`.
- **SlipDeliveryStatus** (derivato): per ogni biglietto, lo stato è `DA_INVIARE` (nessuna
  consegna attiva), `INVIATO` (consegna attiva presente), o `DA_REINVIARE` (consegna
  precedente annullata, nessuna attiva). Nel caso `DA_REINVIARE`, viene esposto il
  `previousStudentName` dell'ultima consegna annullata.

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
- **SC-006**: La marcatura "inviato" è immediata (< 100ms) — scrittura locale su DB.
- **SC-007**: Il badge contatori sul pulsante biglietti si aggiorna in tempo reale dopo
  ogni marcatura o modifica assegnazione, senza necessità di riaprire la dialog.

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
  solo testo `Settimana saltata`.
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

### Session 2026-03-10

- Q: Cosa triggera lo stato "inviato" di un biglietto? -> A: Un pulsante esplicito
  "Segna come inviato" sulla card del biglietto. Non il drag & drop (che non ha callback
  affidabile di completamento su desktop). Il pulsante è l'azione prominente nella
  sezione "Da inviare" per guidare l'utente.
- Q: La granularità dello stato è per persona o per parte? -> A: Per parte
  (`weekly_part_id` + `week_plan_id`). Il biglietto S-89 è uno per parte.
- Q: Cosa succede allo stato "inviato" quando cambio assegnazione? -> A: Reset
  automatico. Il sistema annulla la consegna precedente (`cancelled_at = now`) e il
  nuovo biglietto appare come "Da inviare" con nota "Precedente: {vecchio nome}".
  L'utente riceve un warning prima della modifica.
- Q: Il warning su riassegnazione è bloccante? -> A: È un dialog di conferma. L'utente
  può annullare la modifica. Se conferma, il sistema procede e annulla la consegna.
  È solo un avviso testuale, non una notifica automatica all'assegnatario.
- Q: La tabella `slip_delivery` traccia anche le consegne annullate? -> A: Sì. La riga
  non viene eliminata ma marcata con `cancelled_at`. Questo permette di mostrare
  "Precedente: {nome}" nel biglietto da reinviare.
- Q: Come sono separati "da inviare" e "inviati" nella dialog? -> A: Stessa dialog,
  sezioni diverse. Per ogni settimana: prima "Da inviare" (prominente) poi "Inviati"
  (attenuato). Non due dialog separate.
- Q: Cosa mostra il badge sul pulsante biglietti? -> A: "N da inviare" + "M bloccati"
  (parti vuote/parziali). Se tutto inviato: "Tutti inviati".

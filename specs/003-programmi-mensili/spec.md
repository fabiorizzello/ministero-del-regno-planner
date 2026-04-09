# Feature Specification: Programmi Mensili

**Feature Branch**: `003-programmi-mensili`
**Created**: 2026-02-25
**Status**: Draft (target behavior)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Creazione programma per mese esplicito (Priority: P1)

L'utente vuole creare un programma scegliendo esplicitamente il mese target da
CTA chiare (es. "Crea febbraio", "Crea marzo"), senza essere vincolato a creare
prima il mese corrente.

**Why this priority**: Il programma mensile è il contenitore di tutte le settimane
e delle relative assegnazioni. Senza di esso non si può lavorare su un mese futuro.

**Independent Test**: Con nessun programma corrente e nessun programma futuro →
creare direttamente un mese futuro tramite CTA mese target → verificare che venga
creato solo quel programma con date coerenti.

**Acceptance Scenarios**:

1. **Given** un mese target selezionabile e non ancora presente, **When** l'utente
   crea quel mese, **Then** il programma viene creato con
   startDate = primo lunedì del mese ed endDate = domenica successiva alla fine del mese.
2. **Given** esistono già 2 programmi futuri, **When** l'utente tenta di creare un
   ulteriore mese futuro, **Then** il sistema blocca con errore esplicito.
3. **Given** non esiste alcun programma corrente, **When** l'utente crea un mese
   futuro immediatamente successivo al corrente (`corrente+1`), **Then** il sistema
   accetta lo stato "solo futuro" senza forzare la creazione del mese corrente.
4. **Given** nessuno schema disponibile nel catalogo, **When** l'utente tenta di creare
   un mese target dal workspace, **Then** la creazione viene bloccata con errore
   esplicito che richiede l'aggiornamento degli schemi.
5. **Given** nella finestra corrente..+2 ci sono più mesi mancanti, **When** l'utente
   apre il workspace, **Then** vede una CTA dedicata per ogni mese creabile
   (escludendo i mesi già presenti).
6. **Given** il mese corrente è assente, **When** l'utente tenta di creare `corrente+2`
   senza aver creato `corrente+1`, **Then** il sistema blocca l'azione richiedendo la
   contiguità.
7. **Given** è stato creato `corrente+1` senza mese corrente, **When** il mese corrente
   è ancora nel periodo attuale, **Then** il sistema consente comunque la creazione del
   mese corrente mancante.

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
   viene restituito un `ProgramSelectionSnapshot(current, futures)` dove `current` è
   il programma con timelineStatus CURRENT (o null) e `futures` contiene i programmi
   con status FUTURE ordinati cronologicamente (max 2).
2. **Given** un programma con data corrente nel range startDate-endDate, **Then** il
   suo timelineStatus è CURRENT.
3. **Given** nessun programma corrente né futuro, **When** si carica lo snapshot,
   **Then** entrambi i campi sono null.
4. **Given** esistono programma corrente e futuri, **When** l'utente passa da un mese
   all'altro e poi torna al corrente nella stessa sessione UI, **Then** le informazioni
   del programma corrente (identità, periodo, stato e indicatori) vengono ripristinate
   correttamente senza perdita di contesto.
5. **Given** l'utente ha selezionato un mese specifico, **When** avviene un reload
   dati (creazione mese, aggiornamento schemi, rigenerazione settimane), **Then**
   la selezione resta sul mese scelto se ancora esistente; se non esiste più, fallback
   su `current`, altrimenti sul futuro più vicino.
6. **Given** non esiste `current` e non c'è una selezione precedente valida,
   **When** il workspace viene aperto o ricaricato, **Then** il sistema seleziona
   automaticamente il mese futuro più vicino (primo cronologico).
7. **Given** il mese selezionato contiene settimane già trascorse ma ancora visibili,
   **When** l'utente apre il dettaglio di una settimana passata `ACTIVE`, **Then** la UI
   la mostra come passata ma comunque modificabile per correzioni manuali dello storico.

---

### User Story 4 - Eliminazione programma corrente o futuro (Priority: P2)

L'utente vuole annullare un programma corrente o futuro creato per errore o non più necessario.

**Why this priority**: Permette di correggere errori senza restare bloccati con
programmi futuri indesiderati.

**Independent Test**: Selezionare il programma corrente → eliminarlo → verificare
rimozione completa e ricalcolo coerente della selezione del mese corrente.

**Acceptance Scenarios**:

1. **Given** un programma corrente o futuro, **When** si elimina, **Then** in una transazione
   vengono eliminati prima tutti i WeekPlan (con cascade su parti e assegnazioni), poi
   il programma stesso. Dopo l'operazione il programma non appare più nella lista.
2. **Given** si tenta di eliminare un programma passato, **Then** il sistema impedisce
   l'operazione.
3. **Given** un programma futuro con settimane già assegnate, **When** si elimina,
   **Then** le assegnazioni vengono rimosse in cascata insieme alle settimane.
4. **Given** l'utente avvia l'eliminazione di un programma corrente o futuro, **When**
   il sistema richiede conferma, **Then** il prompt mostra in modo esplicito
   l'impatto atteso (numero settimane e numero assegnazioni da rimuovere).
5. **Given** viene eliminato il programma selezionato, **When** l'operazione termina,
   **Then** il sistema seleziona automaticamente il programma corrente se presente,
   altrimenti il programma futuro più vicino.

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

1. **Given** un programma generato e schemi aggiornati, **When** si aggiorna da schemi
   (dryRun=false), **Then** le parti di ogni settimana futura vengono riallineate agli
   schemi attuali e le assegnazioni corrispondenti per (PartTypeId, sortOrder) vengono
   preservate; le assegnazioni non corrispondenti vengono rimosse.
2. **Given** l'utente aggiorna gli schemi dal workspace, **When** l'operazione termina,
   **Then** l'allineamento del programma viene applicato direttamente e l'UI mostra
   solo il riepilogo finale (senza passaggio preview separato).
3. **Given** una settimana senza schema corrispondente, **When** si aggiorna da schemi,
   **Then** quella settimana viene saltata senza errore.

---

### Edge Cases

- Generazione su un mese senza schemi: usa la parte fissa come fallback per ogni
  settimana; se non esiste neanche la parte fissa, errore di validazione.
- Rigenera settimane: distrugge le settimane esistenti e le assegnazioni. Operazione
  distruttiva che richiede conferma UI.
- Creazione mese target da workspace senza schemi importati: operazione bloccata
  con feedback "Aggiorna schemi prima di creare il programma".
- È ammessa la configurazione senza mese corrente (solo mesi futuri).
- Il numero massimo di programmi futuri contemporanei è 2.
- Regola di contiguità: non è possibile saltare mesi nella creazione; eccezione
  iniziale consentita solo per partire da `corrente+1` quando il mese corrente manca.
- Backfill mese corrente: dopo aver creato `corrente+1` senza corrente, il mese
  corrente resta creabile finché è ancora il mese in corso; non appena diventa passato
  non è più creabile.
- Calcolo date: startDate = primo lunedì del mese; endDate = domenica successiva
  all'ultimo giorno del mese (può sforare nel mese successivo).
- `CaricaProgrammiAttiviUseCase` restituisce `ProgramSelectionSnapshot(current, futures)`:
  al massimo 1 programma CURRENT e fino a 2 FUTURE.
- `AggiornaProgrammaDaSchemiUseCase`: applica solo alle settimane con `weekStartDate >=
  referenceDate` (non rielabora il passato). Usa `dryRun=true` per preview senza
  modifiche al DB. Assegnazioni preservate per chiave `(partTypeId, sortOrder)`.
- Le correzioni manuali del passato sono consentite solo a livello di singola settimana
  visibile nel workspace; operazioni batch e rigenerazioni restano future-only.
- Eliminazione programma corrente/futuro: cascade delete — prima i WeekPlan (e le loro parti e
  assegnazioni), poi il programma stesso, tutto in una transazione.
- Eliminazione programma corrente/futuro: prima della conferma definitiva, il sistema
  deve mostrare il riepilogo dell'impatto distruttivo (settimane/assegnazioni coinvolte).
- Banner "template modificato, verificare": deve comparire solo se l'import schemi
  produce una modifica effettiva dei template rilevanti per il programma futuro.
- Se solo un sottoinsieme dei mesi futuri è impattato dal delta template, il badge
  appare solo sui mesi effettivamente impattati.
- Switch mese con editing in corso: il modello operativo è "on-flight" (nessuna bozza
  locale non salvata), quindi non è previsto prompt di conferma per scartare modifiche.
- Notifiche di successo: se l'operazione ha già un riscontro grafico immediato e non
  aggiunge informazioni utili, non va mostrato alcun toast success.
- Notifiche di errore: devono sempre essere mostrate con messaggio esplicito, anche
  quando i toast di successo sono soppressi.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Il sistema MUST consentire al massimo 2 programmi futuri alla volta.
- **FR-002**: Il sistema MUST calcolare automaticamente startDate (primo lunedì del
  mese) ed endDate (domenica successiva alla fine del mese) alla creazione.
- **FR-003**: Il sistema MUST consentire la creazione diretta del mese target scelto
  dall'utente tramite CTA nominative (es. "Crea febbraio", "Crea marzo"), senza
  obbligo di creare prima il mese corrente.
- **FR-004**: Il sistema MUST generare un WeekPlan per ogni lunedì nel range
  startDate-endDate del programma.
- **FR-005**: Il sistema MUST applicare gli schemi settimanali ai WeekPlan generati;
  in assenza di schema, usare la parte fissa come fallback.
- **FR-006**: Il sistema MUST supportare la marcatura di settimane come SKIPPED
  durante la generazione.
- **FR-007**: La generazione MUST essere idempotente: rieseguirla ricrea le settimane
  da capo (le assegnazioni esistenti vengono perse).
- **FR-008**: Il sistema MUST aggiornare `templateAppliedAt` dopo ogni generazione.
- **FR-009**: Il sistema MUST impedire l'eliminazione dei soli programmi passati.
  L'eliminazione di un programma corrente o futuro MUST avvenire in transazione con
  cascade delete su tutti i WeekPlan, le WeeklyPart e le assegnazioni del programma.
- **FR-010**: `CaricaProgrammiAttiviUseCase` MUST restituire un `ProgramSelectionSnapshot`
  contenente al massimo un programma CURRENT (nullable) e una lista di programmi FUTURE
  ordinata cronologicamente (dimensione 0..2).
- **FR-011**: `AggiornaProgrammaDaSchemiUseCase` MUST supportare modalità `dryRun`
  che calcola `SchemaRefreshReport(weeksUpdated, assignmentsPreserved, assignmentsRemoved)`
  senza modificare il DB. In modalità non-dryRun MUST preservare le assegnazioni le cui
  parti corrispondono per chiave `(partTypeId, sortOrder)` nel nuovo schema.
- **FR-012**: Nel workspace UI, il sistema MUST impedire la creazione del prossimo
  programma se il catalogo schemi è vuoto, mostrando un errore esplicativo.
- **FR-013**: Nel workspace UI, le CTA di creazione mese MUST usare etichette
  esplicite con il nome del mese target.
- **FR-014**: Nel workspace UI, il pulsante di creazione del mese corrente MUST essere
  posizionato sotto i chip/selettori dei mesi.
- **FR-015**: Il sistema MUST consentire uno stato con soli programmi futuri e nessun
  programma corrente.
- **FR-016**: L'indicatore "template modificato, verificare" MUST apparire solo quando
  gli schemi importati comportano un delta reale rispetto ai template già applicati, e
  MUST apparire solo sui mesi futuri effettivamente impattati.
- **FR-017**: Nel workspace UI, il sistema MUST mostrare CTA di creazione per tutti i
  mesi creabili nella finestra "mese corrente .. +2 mesi", escludendo i mesi già
  presenti.
- **FR-018**: Nel workspace UI, l'eliminazione di un programma corrente o futuro MUST
  richiedere una conferma esplicita con riepilogo dell'impatto (almeno conteggio
  settimane e assegnazioni che verranno eliminate).
- **FR-019**: La creazione dei mesi MUST rispettare la contiguità cronologica dei mesi
  mancanti. Se il mese corrente non esiste, il sistema MUST consentire come prima
  creazione diretta solo `corrente+1`; la creazione di `corrente+2` senza `corrente+1`
  MUST essere bloccata.
- **FR-020**: Dopo la creazione di `corrente+1` in assenza del mese corrente, il sistema
  MUST consentire la creazione successiva del mese corrente finché quel mese non è
  passato.
- **FR-021**: Dopo l'eliminazione del programma selezionato, il sistema MUST impostare
  automaticamente la nuova selezione su `current` se disponibile, altrimenti sul
  programma futuro cronologicamente più vicino.
- **FR-022**: Nel workspace UI, l'azione globale `Svuota assegnazioni` MUST essere
  visibile solo quando il programma selezionato contiene almeno una settimana futura.
- **FR-023**: Nel workspace UI non MUST esistere un pulsante di preview separato per
  `Aggiorna programma da schemi`; l'azione `Aggiorna schemi` applica direttamente
  l'aggiornamento e mostra il riepilogo finale.
- **FR-024**: Nel workspace UI, durante lo switch tra mesi, il sistema MUST preservare
  e ripristinare correttamente il contesto informativo del programma corrente quando
  l'utente ritorna alla sua selezione nella stessa sessione UI (senza persistenza
  obbligatoria al riavvio app).
- **FR-025**: Dopo un reload dati del workspace, il sistema MUST mantenere il mese
  precedentemente selezionato se ancora presente. Se il mese selezionato non è più
  disponibile, MUST applicare fallback a `current`, altrimenti al futuro più vicino.
- **FR-026**: Nel workspace UI, le modifiche relative al programma MUST essere applicate
  on-flight senza stato draft locale; durante lo switch mese non MUST essere richiesto
  un prompt di "modifiche non salvate".
- **FR-027**: Se `current` è assente e non esiste una selezione precedente valida,
  il workspace UI MUST selezionare automaticamente il programma futuro più vicino
  (primo cronologico).
- **FR-028**: Le notifiche di successo nel workspace UI MUST comparire solo quando
  l'operazione non produce un riscontro visivo immediato oppure quando il toast porta
  informazioni aggiuntive rilevanti; in caso contrario non MUST essere mostrato un
  toast success.
- **FR-029**: Le notifiche di errore nel workspace UI MUST essere sempre mostrate con
  messaggio esplicito e azionabile, indipendentemente dalla policy di riduzione dei
  toast di successo.
- **FR-030**: Nel workspace UI, una settimana passata con stato `ACTIVE` MUST rimanere
  riconoscibile come passata ma MUST consentire la modifica manuale di parti e
  assegnazioni.
- **FR-031**: Nel workspace UI, le settimane passate modificabili MUST mostrare un
  indicatore visivo aggiuntivo di editabilità storica e i relativi dialog MUST mostrare
  un alert contestuale prima della conferma dell'azione.

## Cross-Feature Dependency

- Prompt di conferma su rimozione singola assegnazione: requisito confermato, da
  allineare nella feature `005-assegnazioni` (non nel perimetro core di questa spec).

### Key Entities

- **ProgramMonth**: id (UUID), year, month, startDate, endDate, templateAppliedAt
  (nullable), createdAt. Metodo: `timelineStatus(referenceDate)` → PAST/CURRENT/FUTURE.
- **ProgramTimelineStatus**: PAST, CURRENT, FUTURE.
- **ProgramSelectionSnapshot**: `current: ProgramMonth?`, `futures: List<ProgramMonth>`.
  Returned da `CaricaProgrammiAttiviUseCase`. Al massimo un current e fino a 2 future.
- **SchemaRefreshReport**: `weeksUpdated: Int`, `assignmentsPreserved: Int`,
  `assignmentsRemoved: Int`. Returned da `AggiornaProgrammaDaSchemiUseCase`
  sia in dryRun che in esecuzione reale.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: La creazione di un programma per il mese target avviene in meno di 1 secondo.
- **SC-002**: La generazione delle settimane di un programma mensile (4-5 settimane)
  completa in meno di 2 secondi.
- **SC-003**: Il caricamento dei programmi attivi restituisce risultati in meno di
  200 ms.

## Clarifications

### Session 2026-02-25

- Q: Questa spec descrive il target prodotto o solo il codice attuale? → A: Descrive
  il comportamento target concordato.
- Q: La generazione è distruttiva per le assegnazioni? → A: Sì, dal codice:
  le settimane vengono cancellate e ricreate in transazione, perdendo le assegnazioni.
- Q: CaricaProgrammiAttiviUseCase restituisce una lista? → A: Dopo la chiarifica su 2
  mesi futuri, deve esporre `ProgramSelectionSnapshot(current, futures)` con `futures`
  ordinato (0..2 elementi).
- Q: L'eliminazione programma mantiene il cascade delete? → A: Sì — prima elimina tutti
  i WeekPlan (con cascade su parti e assegnazioni), poi il programma. In transazione.
- Q: AggiornaProgrammaDaSchemi supporta dry run? → A: Sì — parametro `dryRun: Boolean
  = false`. In dryRun calcola SchemaRefreshReport senza modificare il DB. Applica solo
  alle settimane con weekStartDate >= referenceDate. Preserva assegnazioni per chiave
  `(partTypeId, sortOrder)`.
- Q: Se non ci sono schemi caricati, la creazione del mese target deve essere bloccata?
  → A: Sì, nel workspace l'azione è bloccata finché non vengono aggiornati gli schemi.
- Q: Quanti mesi futuri supportare? → A: Fino a 2 mesi futuri.
- Q: È obbligatorio avere il mese corrente per poter lavorare sul futuro? → A: No, è
  ammesso partire direttamente da un mese futuro.
- Q: La cancellazione deve includere il mese corrente? → A: Sì, corrente e futuri
  eliminabili; passato non eliminabile.
- Q: Il badge "template modificato, verificare" quando deve comparire? → A: Solo su
  modifica reale dei template rilevanti.
- Q: Come va resa più intuitiva la creazione mese in UI? → A: CTA nominate con il mese
  target (es. "Crea febbraio", "Crea marzo"), con pulsante mese corrente sotto i chip.
- Q: Le CTA di creazione mese vanno mostrate solo per il prossimo mese o per tutti i
  mesi creabili? → A: Per tutti i mesi creabili nella finestra corrente..+2, esclusi i
  mesi già presenti.
- Q: Il badge "template modificato, verificare" va mostrato globale o per mese? → A:
  Per mese: solo sui chip dei mesi futuri realmente impattati.
- Q: Cancellazione corrente/futuro: quale guardrail UX adottare? → A: Consentire sempre,
  ma con prompt esplicito che mostra impatto (settimane/assegnazioni eliminate).
- Q: I mesi futuri possono essere creati saltando mesi intermedi? → A: No, contiguità
  obbligatoria; eccezione iniziale: se il corrente manca, è consentito partire da
  `corrente+1` (es. marzo senza febbraio).
- Q: Se esiste `corrente+1` creato senza corrente, il mese corrente resta creabile? →
  A: Sì, finché non è passato.
- Q: Dopo eliminazione del mese selezionato, quale auto-selezione usare? → A:
  Selezionare `corrente` se esiste, altrimenti il futuro più vicino.
- Q: Rimozione singola assegnazione deve chiedere conferma? → A: Sì, con prompt
  esplicito (da applicare nella feature assegnazioni).
- Q: Serve un pulsante UI separato "Preview aggiorna schemi"? → A: No, il workspace
  applica direttamente l'aggiornamento e mostra solo il riepilogo finale.

### Session 2026-02-26

- Q: Cosa deve succedere alle informazioni del programma corrente quando si fa switch
  di mese? → A: Il contesto informativo del programma corrente deve essere memorizzato
  e ripristinato correttamente quando l'utente torna su `current`.
- Q: Persistenza del contesto di switch mese? → A: Solo nella sessione UI corrente
  (Option A), senza persistenza obbligatoria al riavvio app.
- Q: Quale comportamento di selezione adottare dopo un reload dati del workspace?
  → A: Mantenere il mese selezionato se esiste ancora; fallback su `current`,
  altrimenti futuro più vicino (Option A).
- Q: Gestione switch con modifiche non salvate? → A: Non applicabile; operazioni
  on-flight senza bozza locale, quindi nessun prompt di conferma.
- Q: Se `current` manca e non c'è selezione precedente valida, quale fallback usare?
  → A: Selezionare automaticamente il futuro più vicino (Option A).
- Q: Regola per toast success? → A: Mostrarlo solo se aggiunge informazione utile
  o se manca riscontro grafico immediato; altrimenti non mostrarlo.
- Q: Policy notifiche errore rispetto ai toast success ridotti? → A: Errori sempre
  notificati (Option A), anche quando i toast success sono soppressi.

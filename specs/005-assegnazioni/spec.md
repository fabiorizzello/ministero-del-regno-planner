# Feature Specification: Assegnazioni

**Feature Branch**: `005-assegnazioni`
**Created**: 2026-02-25
**Status**: Draft (reverse-engineered from existing code)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Assegnazione manuale di un proclamatore a una parte (Priority: P1)

L'utente vuole assegnare manualmente un proclamatore specifico a uno slot di una
parte settimanale (es. slot 1 = conduttore, slot 2+ = assistente).

**Why this priority**: L'assegnazione manuale è la funzione core dell'applicazione —
il piano settimanale delle assegnazioni è il prodotto finale.

**Independent Test**: Selezionare una parte settimanale → selezionare uno slot →
assegnare un proclamatore → verificare che appaia nell'elenco assegnazioni.

**Acceptance Scenarios**:

1. **Given** una parte settimanale con slot libero, **When** si assegna un proclamatore
   valido, **Then** l'assegnazione viene salvata e lo slot risulta occupato.
2. **Given** uno slot già occupato, **When** si assegna un altro proclamatore allo
   stesso slot, **Then** la vecchia assegnazione viene sostituita.
3. **Given** si tenta di assegnare per uno slot < 1 o > partType.peopleCount, **Then**
   errore di validazione ("Slot non valido").
4. **Given** un proclamatore già assegnato in un'altra parte della stessa settimana,
   **When** si tenta una seconda assegnazione nella stessa settimana, **Then** errore di
   validazione ("Proclamatore già assegnato in questa settimana").
5. **Given** uno slot già assegnato, **When** l'utente clicca rimozione singola,
   **Then** il sistema mostra prima un prompt di conferma esplicita; solo dopo conferma
   esegue la rimozione.

---

### User Story 2 - Suggerimento automatico dei candidati (Priority: P1)

Per ogni slot di ogni parte, il sistema suggerisce una lista ordinata di proclamatori
idonei, tenendo conto di: regola sesso (`UOMO` filtrante, `STESSO_SESSO` non filtrante),
idoneità, stato `sospeso`, cooldown dall'ultima assegnazione globale e specifica
per quel tipo di parte, e parametri configurabili (peso ruolo, settimane cooldown).

**Why this priority**: Il suggerimento è il cuore del valore aggiunto dell'app —
riduce drasticamente il tempo di pianificazione manuale.

**Independent Test**: Con almeno 5 proclamatori idonei → richiedere suggerimenti per
uno slot → verificare che l'ordine rispetti: non-cooldown prima, poi per settimane
dall'ultima assegnazione decrescenti.

**Acceptance Scenarios**:

1. **Given** proclamatori con storie di assegnazione diverse, **When** si richiedono
   suggerimenti per uno slot, **Then** appaiono solo proclamatori idonei (sesso quando
   applicabile, idoneità, non sospesi) ordinati per score
   (globale × peso − penalità cooldown).
2. **Given** un proclamatore in cooldown e `strictCooldown = true`, **When** si
   richiedono suggerimenti, **Then** il proclamatore in cooldown non appare.
3. **Given** un proclamatore in cooldown e `strictCooldown = false`, **When** si
   richiedono suggerimenti, **Then** il proclamatore appare ma con penalità (-10.000)
   e viene posizionato in fondo alla lista.
4. **Given** un proclamatore già assegnato in un'altra parte della stessa settimana,
   **When** si richiedono suggerimenti, **Then** quel proclamatore non appare.
5. **Given** nessun candidato idoneo, **When** si richiedono suggerimenti, **Then**
   viene restituita una lista vuota.

---

### User Story 3 - Auto-assegnazione dell'intero programma (Priority: P2)

L'utente vuole auto-assegnare in blocco tutte le settimane future di un programma
mensile, usando l'algoritmo di suggerimento per ogni slot non ancora assegnato.

**Why this priority**: Automatizza il lavoro più ripetitivo — assegnare decine di
slot su più settimane.

**Independent Test**: Con programma con 4 settimane e tutti gli slot liberi →
avviare auto-assegnazione → verificare che gli slot abbiano almeno un'assegnazione
(o siano in `unresolved` con motivazione).

**Acceptance Scenarios**:

1. **Given** un programma con settimane future con slot liberi, **When** si avvia
   auto-assegnazione, **Then** ogni slot libero viene riempito con il primo candidato
   che non è in cooldown E non ha `sexMismatch = true`.
2. **Given** uno slot senza candidati idonei, **When** si esegue auto-assegnazione,
   **Then** lo slot viene aggiunto alla lista `unresolved` con reason "Nessun candidato
   idoneo".
3. **Given** auto-assegnazione già in esecuzione, **When** si tenta di avviarla di
   nuovo, **Then** la seconda chiamata viene ignorata (mutex).
4. **Given** una settimana con status SKIPPED, **When** si esegue auto-assegnazione,
   **Then** la settimana saltata viene ignorata.
5. **Given** slot già assegnati, **When** si esegue auto-assegnazione, **Then** gli
   slot già assegnati non vengono toccati.

---

### User Story 4 - Gestione impostazioni assegnatore (Priority: P2)

L'utente vuole configurare i parametri dell'algoritmo di suggerimento: settimane
di cooldown per conduttori e assistenti, peso dei ruoli nel punteggio, e se il
cooldown è rigido (esclude completamente) o morbido (penalizza solo).

**Why this priority**: I parametri determinano la qualità delle assegnazioni
suggerite; devono poter essere adattati alle esigenze della congregazione.

**Independent Test**: Impostare cooldownWeeks = 4 per conduttori → assegnare un
proclamatore → verificare che non appaia nei suggerimenti per le 4 settimane
successive per slot 1.

**Acceptance Scenarios**:

1. **Given** impostazioni modificate, **When** si salvano, **Then** vengono persistite
   e usate dal suggeritore alle chiamate successive.
2. **Given** impostazioni non ancora configurate, **When** si caricano, **Then**
   vengono restituite le impostazioni di default.

---

### Edge Cases

- Rimozione assegnazioni di una settimana intera (`RimuoviAssegnazioniSettimana`):
  operazione di reset di tutti gli slot di una settimana.
- Svuotamento assegnazioni di un programma (`SvuotaAssegnazioniProgramma`): accetta
  un parametro `fromDate: LocalDate` — rimuove solo le assegnazioni delle settimane
  a partire da quella data (non necessariamente l'intero programma). Espone anche
  `count(programId, fromDate)` per preview del numero di assegnazioni che sarebbero
  rimosse prima di `execute(programId, fromDate)`.
- Nel workspace UI, il reset di una singola settimana (`Rimuovi assegnazioni`) è
  disponibile solo per settimane future; per la settimana corrente non deve essere
  mostrato.
- Slot = 1 → ruolo "Studente"; slot >= 2 → ruolo "Assistente" (determinato dal
  modello `AssignmentHistoryEntry.role`).
- Un proclamatore può essere assegnato al massimo una volta per settimana, anche se
  ci sono più parti nella stessa settimana (`isPersonAssignedInWeek` cross-parte).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Il sistema MUST consentire l'assegnazione di un proclamatore a uno slot
  specifico (weeklyPartId, slot) di una settimana.
- **FR-002**: Il sistema MUST validare ogni assegnazione rispetto a:
  (a) slot MUST essere nel range `[1, partType.peopleCount]` — valori fuori range sono
  rifiutati con errore "Slot non valido";
  (b) un proclamatore non può essere assegnato due volte nella stessa settimana
  (constraint cross-parte via `isPersonAssignedInWeek`).
- **FR-003**: Il sistema MUST sostituire l'assegnazione esistente se lo slot è già
  occupato.
- **FR-004**: Il sistema MUST suggerire proclamatori idonei ordinati per score
  `(settimane_globali − conteggio_finestra × COUNT_PENALTY_WEIGHT − penalità_cooldown)`.
  `COUNT_PENALTY_WEIGHT = 1`, `COUNT_WINDOW_WEEKS = 26` (finestra di conteggio: 26
  settimane all'indietro rispetto alla data di riferimento).
- **FR-005**: Il sistema MUST filtrare i suggerimenti per: sesso (`SexRule.UOMO`),
  idoneità (slot 1 con lead eligibility, slot >= 2 con `puoAssistere = true`),
  esclusione dei già assegnati nella stessa settimana (set `alreadyAssignedIds`),
  e stato assegnabile (solo proclamatori attivi e non sospesi).
- **FR-006**: Il sistema MUST supportare cooldown rigido (esclusione totale) e morbido
  (penalità) configurabile.
- **FR-007**: Il sistema MUST auto-assegnare tutti gli slot liberi di un programma
  futuro in un'unica operazione, con mutex per evitare esecuzioni parallele.
- **FR-008**: Il sistema MUST restituire la lista `unresolved` degli slot non
  assegnabili con la motivazione.
- **FR-009**: Il sistema MUST consentire la rimozione di una singola assegnazione,
  di tutte le assegnazioni di una settimana, o delle assegnazioni di un programma a
  partire da una data (`SvuotaAssegnazioniProgramma(programId, fromDate)`). Il metodo
  `count(programId, fromDate)` MUST essere disponibile per preview prima di `execute`.
- **FR-011**: Il sistema MUST persistere le impostazioni assegnatore (cooldownWeeks,
  strictCooldown) e applicarle ad ogni esecuzione del suggeritore.
- **FR-012**: Nel workspace UI, la rimozione di una singola assegnazione MUST richiedere
  conferma esplicita dell'utente prima dell'esecuzione.
- **FR-013**: Nel workspace UI, l'azione `Rimuovi assegnazioni` a livello settimana MUST
  essere mostrata solo per settimane future (mai per la settimana corrente o passata).

### Key Entities

- **Assignment**: id, weeklyPartId, personId, slot (>= 1). Slot 1 = studente.
- **AssignmentWithPerson**: join di Assignment con dati anagrafici del proclamatore.
- **SuggestedProclamatore**: proclamatore + lastGlobalWeeks + lastForPartTypeWeeks
  + lastConductorWeeks + lastGlobalBeforeWeeks + lastGlobalAfterWeeks
  + lastForPartTypeBeforeWeeks + lastForPartTypeAfterWeeks
  + inCooldown + cooldownRemainingWeeks + sexMismatch.
  `lastGlobalWeeks` = settimane dall'ultima assegnazione in **qualsiasi ruolo** (absolute,
  anche se l'assegnazione è nel futuro).
  `lastConductorWeeks` = settimane dall'ultima assegnazione come conduttore (slot 1);
  se `lastConductorWeeks == lastGlobalWeeks` l'ultima assegnazione era come conduttore.
  `lastGlobalBeforeWeeks` / `lastGlobalAfterWeeks` = settimane dell'assegnazione globale
  più recente prima e più vicina dopo la data di riferimento (usate per display UI).
  `lastForPartTypeBeforeWeeks` / `lastForPartTypeAfterWeeks` = analogo ma per il tipo-parte.
  `sexMismatch` = true se il sesso del candidato differisce da chi è già assegnato nella
  parte (rilevante solo con SexRule.STESSO_SESSO). Annotazione soft nel suggerimento
  manuale; hard filter in auto-assign.
- **AssignmentSettings**: `strictCooldown: Boolean = true`,
  `leadCooldownWeeks: Int = 4`, `assistCooldownWeeks: Int = 2`.
  Metodo `normalized()` che coerces i valori negativi a 0 (settimane cooldown).
  Le impostazioni di default sono applicate se non ancora configurate.
- **SexRule** (da feature weeklyparts): `UOMO` = filtro hard su soli proclamatori maschi;
  `STESSO_SESSO` = non filtrante nel suggerimento manuale (`passaSesso = true`), ma
  hard filter in `AutoAssegnaProgrammaUseCase` (`!it.sexMismatch`). Il campo
  `sexMismatch` sul `SuggestedProclamatore` segnala la discrepanza di sesso senza
  bloccare l'assegnazione manuale.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: La lista dei suggerimenti per uno slot viene calcolata e restituita in
  meno di 500 ms su un archivio di 200 proclamatori.
- **SC-002**: L'auto-assegnazione di un programma mensile (4-5 settimane, ~20-30 slot)
  completa in meno di 10 secondi.
- **SC-003**: Le assegnazioni sono visibili nella UI immediatamente dopo il salvataggio,
  senza refresh.

## Clarifications

### Session 2026-02-25

- Q: Le spec sono state reverse-engineered dal codice esistente → A: Confermato.
- Q: Lo score è: `settimane_globali × leadWeight + settimane_tipo_parte − cooldown_penalty`?
  → A: Il termine `settimane_tipo_parte` è stato rimosso dalla formula. La formula attuale è:
  `safeGlobalWeeks - cooldownPenalty` dove cooldownPenalty = 10.000 se in cooldown.
- Q: SexRule.STESSO_SESSO significato reale? → A: Nello stato attuale del codice è non
  filtrante nella logica di suggerimento (`passaSesso = true`); il filtro sesso è
  applicato solo su `SexRule.UOMO`.
- Q: Esistono vincoli di validazione slot confermati dal codice? → A: Sì — (1) slot deve
  essere in [1, partType.peopleCount] (da AssegnaPersonaUseCase); (2) un proclamatore
  non può essere assegnato due volte nella stessa settimana, nemmeno su parti diverse
  (isPersonAssignedInWeek cross-parte).
- Q: SvuotaAssegnazioniProgramma è un reset completo? → A: No — accetta `fromDate:
  LocalDate` e cancella solo le assegnazioni da quella data in poi. Espone `count()` e
  `execute()` separati per permettere preview prima dell'operazione.
- Q: AssignmentSettings default confermati dal codice? → A: strictCooldown=true,
  leadCooldownWeeks=4, assistCooldownWeeks=2.
  Metodo `normalized()` coerces valori negativi.

### Session 2026-03-03

- Q: Il cooldown usa le settimane dall'ultima assegnazione in qualsiasi ruolo o per
  ruolo specifico? → A: Il cooldown è basato sull'**ultima assegnazione in qualsiasi
  ruolo** (`lastGlobalWeeks`). La soglia dipende dall'*ultimo ruolo svolto* e dal ruolo
  *target*: se l'ultima era da conduttore E il target è conduttore → `leadCooldownWeeks`;
  altrimenti → `assistCooldownWeeks`. `lastConductorWeeks` in `SuggestedProclamatore`
  traccia l'ultima da conduttore (slot 1); se `lastConductorWeeks == lastGlobalWeeks`
  l'ultima assegnazione era come conduttore. Query SQL: `lastGlobalAssignmentPerPerson`
  (qualsiasi ruolo), `lastSlot1GlobalAssignmentPerPerson` (per determinare l'ultimo ruolo).
- Q: Qual è il label di ruolo per slot 1 e slot >= 2? → A: Slot 1 = "Studente",
  slot >= 2 = "Assistente". Uniformato in tutti gli output (storico, PDF mensile,
  PDF settimanale, PNG). `AssignmentHistoryEntry.role` restituisce "Studente" per
  slot 1. La funzione `slotToRoleLabel(slot: Int): String` nel domain model
  `Assignment.kt` è la fonte canonica.

### Session 2026-03-05

- Q: Il check `slot <= 1` vs `slot == 1` per determinare il ruolo conduttore è
  corretto? → A: Deve essere `slot == 1`. Il dominio garantisce `slot >= 1` per
  invariante (`Assignment.init`), quindi `<= 1` era equivalente ma fuorviante.
  Il codice è stato allineato a `slot == 1` in `SuggerisciProclamatoriUseCase`.
- Q: `cooldownPenalty = 10.000` è un valore hardcoded o una costante? → A: È ora
  una costante named `COOLDOWN_PENALTY = 10_000` in `AssignmentSettings.kt`
  (package `assignments.application`). Il valore è invariato.
- Q: `sexMismatch` in auto-assign vs manuale: il comportamento è intenzionalmente
  diverso? → A: Sì. Nel suggerimento manuale `sexMismatch` è un'annotazione soft
  (candidato visibile, evidenziato in UI). In `AutoAssegnaProgrammaUseCase` è hard:
  `suggestions.firstOrNull { !it.sexMismatch && !it.inCooldown }`. L'auto-assign
  è conservativo per design — preferisce non assegnare piuttosto che violare la
  preferenza di sesso. Questa asimmetria è deliberata.
- Q: I campi `lastGlobalDays`, `lastForPartTypeDays`, `lastGlobalInFuture`,
  `lastForPartTypeInFuture` esistono ancora su `SuggestedProclamatore`? → A: No —
  erano calcolati nell'infrastruttura ma non usati né dalla UI né dal domain scoring.
  Rimossi in sessione di review (branch 001-align-sketch-ui).

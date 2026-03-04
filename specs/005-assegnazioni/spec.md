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
idoneità, stato sospeso/attivo, cooldown dall'ultima assegnazione globale e specifica
per quel tipo di parte, e parametri configurabili (peso ruolo, settimane cooldown).

**Why this priority**: Il suggerimento è il cuore del valore aggiunto dell'app —
riduce drasticamente il tempo di pianificazione manuale.

**Independent Test**: Con almeno 5 proclamatori idonei → richiedere suggerimenti per
uno slot → verificare che l'ordine rispetti: non-cooldown prima, poi per settimane
dall'ultima assegnazione decrescenti.

**Acceptance Scenarios**:

1. **Given** proclamatori con storie di assegnazione diverse, **When** si richiedono
   suggerimenti per uno slot, **Then** appaiono solo proclamatori idonei (sesso quando
   applicabile, idoneità, non sospesi/non inattivi) ordinati per score
   (globale × peso + tipo-parte − penalità cooldown).
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
   auto-assegnazione, **Then** ogni slot libero viene riempito con il miglior
   candidato disponibile.
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

### User Story 5 - Storico assegnazioni di un proclamatore (Priority: P2)

L'utente vuole vedere lo storico delle assegnazioni di un proclamatore specifico,
con il totale per tipo di parte e l'elenco cronologico completo.

**Why this priority**: Permette di verificare manualmente l'equità della distribuzione
degli incarichi.

**Independent Test**: Assegnare un proclamatore 3 volte a tipi diversi → caricare
lo storico → verificare che compaia un totale di 3 assegnazioni con dettaglio.

**Acceptance Scenarios**:

1. **Given** un proclamatore con assegnazioni passate, **When** si carica lo storico,
   **Then** vengono restituite tutte le assegnazioni con tipo-parte, data settimana,
   slot e ruolo (Conduttore/Assistente).
2. **Given** lo storico, **When** si accede al riepilogo, **Then** viene mostrato il
   conteggio per tipo di parte.

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
  (settimane_globali × peso_ruolo + settimane_tipo_parte − penalità_cooldown).
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
- **FR-010**: Il sistema MUST consentire il caricamento dello storico assegnazioni
  per proclamatore con riepilogo per tipo-parte.
- **FR-011**: Il sistema MUST persistere le impostazioni assegnatore (cooldownWeeks,
  weights, strictCooldown) e applicarle ad ogni esecuzione del suggeritore.
- **FR-012**: Nel workspace UI, la rimozione di una singola assegnazione MUST richiedere
  conferma esplicita dell'utente prima dell'esecuzione.
- **FR-013**: Nel workspace UI, l'azione `Rimuovi assegnazioni` a livello settimana MUST
  essere mostrata solo per settimane future (mai per la settimana corrente o passata).

### Key Entities

- **Assignment**: id, weeklyPartId, personId, slot (>= 1). Slot 1 = studente.
- **AssignmentWithPerson**: join di Assignment con dati anagrafici del proclamatore.
- **SuggestedProclamatore**: proclamatore + lastGlobalWeeks + lastForPartTypeWeeks
  + lastConductorWeeks + inCooldown + cooldownRemainingWeeks.
  `lastGlobalWeeks` = settimane dall'ultima assegnazione in **qualsiasi ruolo**.
  `lastConductorWeeks` = settimane dall'ultima assegnazione come conduttore (slot 1);
  se `lastConductorWeeks == lastGlobalWeeks` l'ultima assegnazione era come conduttore.
- **PersonAssignmentHistory**: lista di AssignmentHistoryEntry con summaryByPartType
  e totalAssignments.
- **AssignmentSettings**: `strictCooldown: Boolean = true`, `leadWeight: Int = 2`,
  `assistWeight: Int = 1`, `leadCooldownWeeks: Int = 4`, `assistCooldownWeeks: Int = 2`.
  Metodo `normalized()` che coerces i valori negativi a 0 (settimane cooldown) e 1
  (pesi). Le impostazioni di default sono applicate se non ancora configurate.
- **SexRule** (da feature weeklyparts): `UOMO` = filtro su soli proclamatori maschi;
  `STESSO_SESSO` = nessun filtro di sesso nella logica di suggerimento attuale.

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
  → A: Sì, dal codice: `safeGlobalWeeks * roleWeight + safePartWeeks - cooldownPenalty`
  dove cooldownPenalty = 10.000 se in cooldown.
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
  leadWeight=2, assistWeight=1, leadCooldownWeeks=4, assistCooldownWeeks=2.
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
  slot 1.

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

**Independent Test**: Aprire la schermata proclamatori → cliccare "Aggiungi" → compilare
il dialog → salvare → verificare che il nuovo proclamatore compaia nell'elenco.

**Acceptance Scenarios**:

1. **Given** un archivio vuoto o con proclamatori esistenti, **When** l'utente inserisce
   nome, cognome e sesso validi e conferma, **Then** il proclamatore viene salvato
   (non sospeso, non eliminato) e compare nell'elenco.
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

**Independent Test**: Cliccare su un proclamatore nell'elenco → si apre il dialog →
modificare il cognome → salvare → verificare che l'elenco mostri il nuovo cognome.

**Acceptance Scenarios**:

1. **Given** un proclamatore esistente, **When** l'utente modifica nome/cognome/sesso
   e salva, **Then** i nuovi dati vengono persistiti e visibili immediatamente.
2. **Given** si sta modificando "Mario Rossi" in "Mario Bianchi", **When** esiste già
   un "Mario Bianchi", **Then** il sistema blocca il salvataggio con errore duplicato.
3. **Given** si sta modificando "Mario Rossi", **When** si cambia solo il nome mantenendo
   il cognome, **Then** la verifica duplicato esclude il proclamatore corrente.

---

### User Story 3 - Gestione stato sospeso (Priority: P2)

L'utente vuole gestire lo stato operativo dei proclamatori tramite sospensione
reversibile dal form, senza eliminare i dati anagrafici.

**Why this priority**: Gli stati operativi permettono di governare disponibilità e uso
nei flussi di assegnazione senza ricorrere alla cancellazione definitiva.

**Independent Test**: Dal form impostare `sospeso = true` e verificare che il
proclamatore non venga proposto nei flussi di candidatura; poi impostare
`sospeso = false` e verificare che torni assegnabile.

**Acceptance Scenarios**:

1. **Given** un proclamatore con assegnazioni in settimane future, **When** viene
   applicata la sospensione, **Then** il sistema restituisce la lista delle settimane
   future coinvolte.
2. **Given** un proclamatore sospeso, **When** l'utente rimuove la sospensione dal form,
   **Then** lo stato `sospeso` torna `false` e il proclamatore rientra nei candidati
   assegnabili.
3. **Given** un proclamatore sospeso, **When** si esegue l'auto-assegnazione, **Then**
   il proclamatore viene ignorato.

---

### User Story 4 - Impostazione idoneità conduzione e assistenza (Priority: P2)

L'utente vuole configurare, nel form di creazione/modifica del proclamatore, quali
tipi di parte è idoneo a condurre e se può fare da assistente. Tutti i campi
(anagrafica + idoneità) sono in un unico form.

**Why this priority**: L'idoneità determina chi viene proposto per ogni slot — senza
questa configurazione il suggeritore non funziona correttamente.

**Independent Test**: Nel form del proclamatore → abilitare idoneità al tipo-parte "X"
e `puoAssistere` → salvare → andare all'assegnazione della settimana → verificare che
compaia come candidato per slot 1 della parte "X" e per slot 2.

**Acceptance Scenarios**:

1. **Given** un proclamatore, **When** l'utente imposta l'idoneità alla conduzione per
   il tipo-parte "Preghiera pubblica", **Then** il proclamatore viene proposto come
   candidato per lo slot 1 (conduttore) di quella parte.
2. **Given** un proclamatore senza idoneità a un tipo-parte, **When** si visualizzano
   i candidati per quello slot, **Then** il proclamatore non appare.
3. **Given** un proclamatore con `puoAssistere = true`, **When** si cercano candidati
   per uno slot >= 2, **Then** il proclamatore appare tra i candidati assistente.
4. **Given** il form proclamatore, **When** l'utente usa l'opzione `Seleziona tutto`,
   **Then** vengono impostati insieme `puoAssistere = true` e tutte le idoneità
   conducibili selezionabili; disattivando `Seleziona tutto` vengono deselezionate.

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
- Eliminazione di un proclamatore: il sistema MUST mostrare un dialog di conferma con
  avvertimento esplicito ("questa azione è irreversibile e cancella tutto lo storico")
  prima di procedere. Solo dopo conferma esplicita dell'utente viene eseguita la
  cancellazione definitiva in una transazione: prima vengono rimosse TUTTE le
  assegnazioni storiche del proclamatore (`removeAllForPerson`), poi il proclamatore
  stesso (`store.remove`). L'operazione è irreversibile — non è possibile recuperare
  né il record né lo storico assegnazioni.
- Sospensione con assegnazioni future: l'operazione di aggiornamento del proclamatore
  restituisce `futureWeeksWhereAssigned: List<LocalDate>`. Se la lista non è vuota,
  il chiamante può mostrare un avviso che il proclamatore è già assegnato in quelle
  settimane future (le assegnazioni NON vengono rimosse automaticamente — devono essere
  gestite manualmente).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Il sistema MUST consentire la creazione di un proclamatore con nome,
  cognome, sesso (M/F), `sospeso` e `puoAssistere`.
- **FR-002**: Il sistema MUST impedire la creazione di due proclamatori con lo stesso
  nome e cognome (case-insensitive, trim applicato).
- **FR-003**: Il sistema MUST validare: nome e cognome non vuoti, lunghezza massima
  100 caratteri ciascuno.
- **FR-004**: Il sistema MUST consentire la modifica di nome, cognome e sesso, con
  ri-verifica del duplicato escludendo il proclamatore stesso.
- **FR-005**: Il sistema MUST consentire di sospendere e riattivare un proclamatore
  tramite il campo `sospeso` nel form di modifica (gestito da `AggiornaProclamatoreUseCase`,
  non da un use case separato). Un proclamatore con `sospeso = true` MUST essere escluso
  dai candidati per le assegnazioni (`allAssignableProclaimers`). La sospensione è
  reversibile. Al momento della sospensione (transizione `false → true`) il sistema MUST
  restituire `futureWeeksWhereAssigned` nell'`AggiornamentoOutcome`.
- **FR-006**: Il sistema MUST consentire di configurare l'idoneità alla conduzione
  per ogni proclamatore su base per-tipo-parte. Un nuovo proclamatore NON ha idoneità
  alla conduzione per nessun tipo di parte — deve essere configurata esplicitamente.
- **FR-007**: Il sistema MUST consentire di impostare il flag `puoAssistere` per
  abilitare il proclamatore come candidato assistente.
- **FR-008**: Il sistema MUST consentire l'import bulk da JSON con schema `version: 1`
  solo quando l'archivio è vuoto.
- **FR-009**: Il sistema MUST consentire la ricerca proclamatori per termine testuale
  (nome o cognome); termine null restituisce tutti.
- **FR-010**: Il sistema MUST implementare l'eliminazione come cancellazione definitiva
  (hard delete): prima dell'eliminazione MUST essere mostrato un dialog di conferma con
  avvertimento esplicito ("azione irreversibile, cancella tutto lo storico"). Dopo
  conferma, in una singola transazione vengono rimosse tutte le assegnazioni del
  proclamatore e poi il proclamatore stesso. L'operazione è completamente irreversibile
  — nessun dato viene conservato.
- **FR-011**: Il sistema MUST modellare lo stato operativo solo tramite `sospeso`.
  Il sistema MUST NOT introdurre un secondo flag di stato nel dominio/persistenza.
- **FR-012**: Nel form proclamatori il sistema MUST esporre un controllo `Seleziona tutto`
  che abilita/disabilita in blocco sia `puoAssistere` sia tutte le idoneità di conduzione
  selezionabili.

### Key Entities

- **Proclamatore**: id (UUID), nome, cognome, sesso (M/F), sospeso (boolean, default
  false), puoAssistere (boolean, default false). Lo stato operativo è espresso solo da
  `sospeso`, che governa l'assegnabilità nei flussi di candidatura. Non esiste un flag
  `eliminato` — l'eliminazione è un hard delete che rimuove il record e lo storico
  assegnazioni.
  Invariante (enforced in `ProclamatoreAggregate.create()` e `updateProfile()`): nome e cognome
  non vuoti (isNotBlank) e lunghezza massima 100 caratteri ciascuno (su input trimmed).
  `sospeso = true` esclude dalle candidature per le assegnazioni.
- **AggiornamentoOutcome** (`AggiornaProclamatoreUseCase.AggiornamentoOutcome`):
  `proclamatore: Proclamatore`, `futureWeeksWhereAssigned: List<LocalDate>` — lista
  delle date di inizio settimana in cui il proclamatore ha assegnazioni future al momento
  della sospensione. Popolata solo quando `sospeso` passa da `false` a `true`; altrimenti
  lista vuota. Informativa: le assegnazioni non vengono rimosse automaticamente.
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
- Q: Cosa succede all'eliminazione di un proclamatore con assegnazioni storiche? → A: **Correzione**: il codice implementa un hard delete (`store.remove` + `assignmentStore.removeAllForPerson` in transazione) — NON un soft delete. Il record e tutte le assegnazioni storiche vengono eliminati definitivamente. La spec è stata aggiornata per riflettere il comportamento reale del codice.
- Q: Semantica di `sospeso` vs eliminazione: visibilità in elenco e candidature? → A: Sospeso = stato reversibile usato per escludere dai candidati assegnabili; eliminazione = hard delete del record e dello storico assegnazioni.
- Q: Ruolo di un flag di stato aggiuntivo: serve o va rimosso? → A: Va rimosso dalla spec: il dominio usa solo `sospeso`; non è previsto `ImpostaStatoProclamatoreUseCase`.
- Q: Idoneità conduzione default per nuovo proclamatore? → A: Nessuna — deve essere configurata esplicitamente per ogni tipo di parte.
- Q: Dove si configura idoneità nell'UI: form unico o sezione separata? → A: Form unico — anagrafica, stato sospeso e idoneità nello stesso dialog di creazione/modifica.
- Q: Come accede l'utente ai proclamatori sospesi? → A: Il campo `sospeso` è gestito nel form; la tabella mostra lo stato sospeso senza toggle dedicato aggiuntivo.
- Q: Navigazione UI elenco → form proclamatore? → A: Dialog/modal — click sulla riga apre un dialog con il form completo.
- Q: Conferma prima dell'hard delete? → A: Sì — dialog di conferma con avvertimento "azione irreversibile".

### Session 2026-03-05

- Q: La validazione max-length 100 è solo nei use case o anche nel domain model? → A:
  Ora è nel domain model: `ProclamatoreAggregate.create()` e `updateProfile()` eseguono
  sia `isNotBlank()` che `length <= 100` su input trimmed. `Proclamatore.of()` delega a
  `ProclamatoreAggregate.create()`. Questo garantisce che nessun path di creazione possa
  produrre un `Proclamatore` invalido indipendentemente da quale use case lo costruisce
  (parse-don't-validate).

### Session 2026-03-06

- Q: Esiste `ImpostaSospesoUseCase`? → A: **No.** La sospensione è gestita interamente
  da `AggiornaProclamatoreUseCase` tramite il campo `sospeso` nel `Command`. Il ritorno
  è `AggiornamentoOutcome(proclamatore, futureWeeksWhereAssigned)` — `futureWeeksWhereAssigned`
  è popolata solo quando `sospeso` passa `false → true`, altrimenti lista vuota.
  Non esiste né `ImpostaSospesoUseCase` né un tipo `SospensioneInfo` separato nel codice.
  La spec è stata corretta per allinearsi al codice reale.

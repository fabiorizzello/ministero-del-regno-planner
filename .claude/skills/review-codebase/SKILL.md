---
name: review-codebase
description: >
  Esegue una review architetturale del codebase su DDD, Arrow/Either, TransactionScope,
  qualità test e allineamento spec. Produce findings ordinati per severità.
  Usato sia come review standalone che come verifica post-fix da review-findings-fixer.
user_invocable: true
---

# Review Codebase

Review architetturale del progetto Ministero del Regno Planner.

## Modalità di utilizzo

Questo skill può essere invocato in due modi:

**A) Review completa** — analisi dell'intero codebase o di zone non ancora esplorate.
**B) Review mirata post-fix** — analisi del solo codice modificato da un fixing, per verificare che il fix sia corretto e non abbia introdotto nuovi problemi. In questo caso l'utente o il fixer specifica i file toccati.

---

## Preparazione

1. Leggi `docs/review/review-notes.md` per conoscere i findings già trattati — non ripetere ciò che è già risolto.
2. Verifica i findings aperti: se un finding non è più valido (codice cambiato, problema risolto incidentalmente, finding superato da refactor), rimuovilo dalla lista e spostalo in "Findings risolti" con nota sintetica.
3. **Solo per review completa**: identifica le zone "oscure" del codebase — feature o file poco esplorati nelle iterazioni precedenti, use case non coperti da test, moduli senza nessuna review esistente. Dai priorità a queste zone.
4. **Solo per review post-fix**: concentrati esclusivamente sui file modificati e sui loro caller/dipendenti diretti. Non espandere l'analisi ad aree non toccate.

---

## Criteri di valutazione

### Architettura DDD

- **Vertical slices**: ogni feature è autonoma, senza dipendenze trasversali non dichiarate.
- **Aggregate-root centrico**: le invarianti di dominio sono garantite dall'aggregato, mai dall'esterno; nessun IO dentro il dominio.
- **Use case** (1:1 con azione utente): confine transazionale, orchestrazione IO. Un use case mutante apre esattamente 1 transazione.
- **Application service**: riusabile da più entry point (UI + batch + eventi) se la stessa logica serve contesti multipli.
- **Domain service**: logica pura che attraversa più aggregati — mai IO.
- **Infrastructure service**: implementa contratti dichiarati dal dominio (DB, HTTP, PDF, file system) — non li definisce.

### Modello funzionale

- `Arrow`, `Either`, `DomainError` usati correttamente e in modo consistente.
- Nessun `throw` / `IllegalStateException` / `checkNotNull` non mappato a `DomainError` nel layer domain o application.
- Nessun uso di eccezioni come control flow (es. eccezione privata lanciata e catturata per uscire da un `fold`).
- Valuta se optics, newtypes, ADT/GADT migliorerebbero l'espressività — **solo segnala, non implementare**.

### Pattern TransactionScope (capability token)

- Ogni use case mutante apre esattamente 1 transazione via `TransactionRunner.runInTransaction { }`.
- Il blocco lambda riceve `TransactionScope` come receiver implicito.
- Le funzioni di store dichiarate `context(TransactionScope)` possono essere chiamate **solo** dentro quel blocco — il compilatore lo forza staticamente.
- Conseguenze verificabili:
  - Nessun use case apre transazioni annidate.
  - Nessuna funzione `context(TransactionScope)` viene chiamata fuori da `runInTransaction`.
  - I use case read-only non richiedono transazione.
  - Le interfacce store dichiarano `context(TransactionScope)` sui metodi di mutazione — non solo le implementazioni.

### Test

- **Coverage sulla logica pura**: domain + use case devono avere test unitari.
- **Integration test sui boundary esterni**: HTTP client, DB, PDF rendering.
- **Qualità dei test esistenti**: per ogni test valuta —
  - Testa un comportamento distinto o è ridondante con un altro?
  - Può essere rimosso perché copre solo un dettaglio implementativo fragile?
  - Può essere accorpato con un test simile senza perdere leggibilità?
  - Può essere rafforzato (asserzione più specifica, scenario più rappresentativo)?
  - Può essere ridenominato per chiarire meglio l'intento?
  - Dà falsa sicurezza (passa sempre, non può fallire per regression reali)?

### Qualità

- Assenza di codice orfano, dead code, TODO.
- Nessuna violazione DRY/SOLID/DDD.
- Spec allineate al codice — in caso di disallineamento **segnala senza correggere**.
- Costanti condivise non duplicate tra moduli.
- Dipendenze tra layer nel verso corretto (domain ← application ← infrastructure, mai il contrario).

---

## Contesto produzione

- 1 utente, 1 sessione, no saga, no concorrenza.
- Desktop app JVM, Compose Multiplatform.
- SQLDelight per persistenza, Koin per DI, Arrow per modello funzionale.

---

## Output atteso

### Per review completa
Findings ordinati per severità (High → Medium → Low), ciascuno con:
- Titolo sintetico
- Descrizione del problema e causa strutturale
- Evidenze (file:riga)
- Severità e effort stimato

### Per review post-fix
Rispondi alle seguenti domande per il codice modificato:

1. **Il fix risolve effettivamente il problema documentato nel finding?** Sì / No / Parzialmente — spiega.
2. **Il fix è consistente con i pattern architetturali del codebase?** Verifica DDD, Either, TransactionScope.
3. **Il fix ha introdotto nuovi problemi?** Elenca qualsiasi nuovo finding emerso dai file toccati.
4. **I test aggiornati/aggiunti sono sufficienti?** Valuta qualità e copertura rispetto al comportamento modificato.
5. **Ci sono caller o dipendenti non aggiornati?** Verifica che la modifica non abbia lasciato codice incoerente altrove.

Se il fix supera tutti i punti senza nuovi finding: **"Fix approvato."**
Se emergono problemi: elencali come finding con severità, e indica se bloccano il merge o sono separabili.

---

## Parallelizzazione

Se i task di analisi sono indipendenti (moduli diversi, file diversi), usa agenti paralleli per ridurre i tempi.

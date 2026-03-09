---
name: review-findings-fixer
description: >
  Guida la risoluzione sistematica dei findings da review-notes.md.
  Ogni finding viene lavorato in un worktree isolato, verificato con review-codebase post-fix,
  poi mergiato. Ordina per effort, parallelizza sugli indipendenti, gestisce ambiguità e spec.
user_invocable: true
---

# Review Findings Fixer

Processo sistematico per risolvere i findings aperti in `docs/review/review-notes.md`.

Ogni finding viene:
1. Analizzato prima di toccare codice
2. Lavorato in un **git worktree isolato**
3. Verificato con `review-codebase` post-fix prima di mergiare
4. Committato granularmente e il worktree pulito

---

## FASE 0 — Lettura e convalida finding

Prima di qualsiasi azione:

1. Leggi `docs/review/review-notes.md` nella sua interezza.
2. Leggi anche il **prompt sorgente della review** (sezione "Prompt sorgente") per capire i criteri con cui i finding furono generati — userai gli stessi criteri per convalidarli.
3. Per ogni finding aperto, **convalida che sia ancora attuale** leggendo il file e la riga citata nelle evidenze:
   - Il problema descritto è ancora presente nel codice?
   - È coerente con i criteri del prompt di review (DDD, Either, TransactionScope, test quality)?
   - Se risolto incidentalmente: spostalo in "Findings risolti" con nota prima di procedere.
   - Se basato su un malinteso architetturale: segnalalo all'utente.
4. Elenca i findings **ancora validi** con:
   - ID / titolo
   - Severità (High / Medium / Low)
   - Stima effort (Low / Medium / High) — quanti file toccano, quanti caller, quanti test
   - Dipendenze da altri finding
5. Ordina per **effort crescente a parità di severità**.
6. Mostra la lista ordinata all'utente e attendi conferma prima di procedere.

### Criteri di invalidazione di un finding

Va rimosso/marcato risolto se:
- Il codice citato è stato riscritto e il pattern problematico non esiste più.
- Il comportamento era intenzionale e documentato nella spec.
- Il contesto architetturale è cambiato (es. la classe è stata eliminata).

Rimane aperto se:
- Il problema esiste ancora ma è in un ramo non ancora mergiato.
- Il fix è noto ma non ancora eseguito.

---

## FASE 1 — Analisi per finding

Per ogni finding, prima di scrivere codice:

### 1a. Causa radice
Rispondi: *perché questo problema esiste?* Non ripetere la descrizione — spiega la causa strutturale.

Esempi:
- "La classe fu scritta prima che il pattern `context(TransactionScope)` fosse adottato nel codebase."
- "Il metodo inietta un use case invece di uno store perché originariamente serviva logica business, poi estratta altrove."
- "L'eccezione fu usata come shortcut per uscire da un `fold` annidato, prima che Arrow `Raise` fosse disponibile."

### 1b. Soluzione da applicare
Descrivi *cosa cambia* e *perché è la soluzione corretta* nel contesto del progetto (DDD, Either, TransactionScope, vertical slices). Cita i file e le righe specifiche che saranno toccati.

### 1c. Ambiguità o biforcazioni

Se esistono due o più approcci validi con trade-off significativi, **non procedere**. Invece:
1. Elenca le opzioni con pro/contro sintetici.
2. Indica quale preferiresti e perché.
3. **Chiedi conferma all'utente** prima di scrivere codice.

Esempi di biforcazioni che richiedono conferma:
- Rendere un use case read-only `Either`-aware vs lasciarlo senza Either.
- Aggiungere `context(TransactionScope)` all'interfaccia (breaking su tutti i caller) vs wrappare internamente.
- Estrarre logica in domain service vs tenerla nel use case.

---

## FASE 2 — Pianificazione parallela e worktree

Prima di eseguire, valuta quali finding sono **indipendenti** e possono essere parallelizzati su agenti distinti, ciascuno nel proprio worktree.

### Criteri di indipendenza (tutti devono essere veri):
- Non toccano lo stesso file (nemmeno per import o DI module)
- Non hanno dipendenza logica tra loro (A non introduce un pattern che B deve adottare)
- Producono test in file separati (nessun conflitto di merge)

### Conflitti che forzano sequenzialità:
- Due finding che toccano la stessa interfaccia store
- Un finding che aggiunge un parametro al costruttore già toccato da un altro finding
- Un finding che introduce un'astrazione che un altro finding userebbe

### Output di questa fase:
Presenta all'utente un piano con batch e motivazioni:

```
Batch 1 (parallelo — worktree separati per ogni agente):
  Agent A / worktree fix/finding-X: Finding X + Finding Y  (stesso modulo)
  Agent B / worktree fix/finding-Z: Finding Z              (modulo separato)

Batch 2 (sequenziale dopo merge Batch 1 — dipende da Finding X):
  worktree fix/finding-W: Finding W
```

### Naming dei worktree:
`fix/finding-<ID-o-slug>` — es. `fix/finding-high-2-output-either`, `fix/finding-42-assignment-tx`.

---

## FASE 3 — Setup worktree

Per ogni finding (o sotto-batch parallelo), **prima di toccare codice**:

```bash
# Dalla root del repo
git worktree add ../ministero-del-regno-planner-fix-<slug> -b fix/finding-<slug>
```

Lavora esclusivamente dentro quel worktree. Non modificare il working tree principale durante il fix.

---

## FASE 4 — Esecuzione nel worktree

### Per ogni finding nel suo worktree:

1. **Leggi i test esistenti** che coprono il codice da modificare.
2. **Scrivi/aggiorna i test** se il fix introduce nuovi comportamenti o altera contratti.
3. **Modifica il codice** secondo la soluzione descritta in Fase 1b.
4. **Aggiorna il modulo DI (Koin)** se il costruttore cambia — sempre nello stesso commit, mai separato.
5. **Controlla i caller**: cerca tutti gli usi del simbolo modificato nel codebase.

### Escalation durante il fix

Se scopri che il problema è **più esteso di quanto documentato**:
1. **Fermati.**
2. Riporta all'utente cosa hai scoperto.
3. Aggiorna il finding in `review-notes.md` con le nuove evidenze.
4. Chiedi se procedere con la scope estesa o fermarsi.

### Spec alignment check

Se il fix cambia un comportamento documentato nelle spec (`/specs/NNN-feature-name/spec.md`):
1. **Non aggiornare le spec da solo.**
2. Mostra all'utente: quale spec, quale sezione, cosa cambia, se è correzione o cambio intenzionale.
3. **Aspetta conferma esplicita.**
4. Solo dopo conferma, aggiorna la spec atomicamente con il fix.

---

## FASE 5 — Verifica build nel worktree

Dentro il worktree, dopo il fix:

```bash
./gradlew :composeApp:jvmTest
```

- `BUILD SUCCESSFUL`: procedi alla Fase 6.
- Fallisce: **non procedere**. Analizza, correggi, riverifica. Non uscire dal worktree con test rossi.

---

## FASE 6 — Review post-fix (review-codebase in modalità mirata)

Dopo che i test sono verdi, invoca il skill `review-codebase` in **modalità post-fix** sul codice modificato.

Indica esplicitamente:
- I file toccati dal fix
- Il finding che si intendeva risolvere
- La soluzione applicata

Il skill `review-codebase` risponderà a:
1. Il fix risolve effettivamente il problema?
2. È consistente con i pattern architetturali?
3. Ha introdotto nuovi finding?
4. I test sono sufficienti?
5. Ci sono caller o dipendenti non aggiornati?

### Esiti possibili:

**"Fix approvato"** → procedi alla Fase 7.

**"Fix parziale o nuovi finding emersi"** →

Classifica ogni nuovo finding emerso dalla review:

**Trivial (dead code, import inutilizzato, parametro non usato, nome fuorviante):**
- Correggilo **subito nel worktree corrente**, nello stesso commit o in uno aggiuntivo.
- Non aprire un finding in `review-notes.md` — risolverlo ora costa meno che tracciarlo.

**Importante (nuovo rischio architetturale, bug potenziale, violazione DDD/Either/TransactionScope):**
- **Non fixare nel worktree corrente** — potrebbe allargare la scope incontrollatamente.
- Aggiungilo in `review-notes.md` nella sezione "Findings aperti" con severità e evidenze.
- Procedi al merge del worktree attuale come previsto.

**Bloccante (il fix originale non funziona o introduce regressioni):**
- Correggilo nel worktree corrente prima di procedere al merge.
- Se richiede scope molto più ampia: torna alla Fase 1 con la nuova comprensione.

**Se il fix non risolve il problema originale:**
- Torna alla Fase 1 con la nuova comprensione acquisita.

---

## FASE 7 — Commit nel worktree

Ogni finding ottiene un **commit separato**. Formato:

```
[Finding X] Descrizione sintetica di cosa è stato fatto

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
```

Non raggruppare finding non correlati — rende il log illeggibile e il rollback impossibile.

---

## FASE 8 — Merge e pulizia worktree

```bash
# Dalla root del repo principale
git merge fix/finding-<slug> --no-ff

# Rimozione worktree
git worktree remove ../ministero-del-regno-planner-fix-<slug>
git branch -d fix/finding-<slug>
```

Usa `--no-ff` per preservare la storia del branch nel log.

---

## FASE 9 — Aggiornamento review-notes

Dopo merge completato:

1. Sposta i finding risolti da "Findings aperti" a "Findings risolti" in `review-notes.md`.
2. Per ogni finding risolto, aggiungi nota sintetica con commit reference.
3. Aggiorna la sezione "Verifiche eseguite" con data, comando, totale test e failure count.
4. Se sono emersi nuovi finding in Fase 6, aggiungili in "Findings aperti" con severità e evidenze.

---

## Regole generali

- **Effort prima di severità**: risolvi prima ciò che è veloce — libera spazio mentale.
- **Un worktree per finding** (o per sotto-batch parallelo): mai mescolare fix di finding diversi nello stesso worktree.
- **Spiega sempre la causa, non solo la soluzione**: aiuta a prevenire la ricorrenza del pattern.
- **Mai silent fix**: se durante il fix cambi qualcosa che non era nel finding, segnalalo esplicitamente.
- **Non rimuovere test**: se un test sembra ridondante, segnalarlo — non eliminarlo senza discussione.
- **Context window**: per finding con molti caller, usa subagenti. Non accumulare troppe modifiche non committate.

# Review Notes — Assignment Fairness Refactoring

Prompt sorgente: 5x review-codebase post-refactoring fairness (A→D→E→B), 2026-03-12.

---

## Findings aperti

(nessuno)

---

## Findings risolti

### HIGH-001 — Doc drift: data-model.md riferisce leadWeight/assistWeight rimossi

Risolto in `a129c0a`. Rimosse righe `leadWeight`/`assistWeight` da `AssignmentSettingsBlock`
in `specs/001-align-sketch-ui/data-model.md`.

### HIGH-002 — Doc drift: spec Q&A mostra formula obsoleta

Risolto in `a129c0a`. Aggiornata risposta Q&A in `specs/005-assegnazioni/spec.md:222-224`
con formula attuale `safeGlobalWeeks - countPenalty - slotRepeatPenalty - cooldownPenalty`.

### MEDIUM-001 — DRY: lastWasConductor duplicato in invoke() e weightedScore()

Risolto in `885f966`. Estratto come extension property
`private val SuggestedProclamatore.lastWasConductor: Boolean`.

### MEDIUM-002 — Vestigio: Triple(..., Unit) dopo rimozione roleWeight

Risolto in `885f966`. `Triple(annotated, allowed, Unit)` → `annotated to allowed` (Pair).

### MEDIUM-003 — Inconsistenza: safeGlobalWeeks=999 vs Int.MAX_VALUE

Risolto in `885f966`. `safeGlobalWeeks = suggestion.lastGlobalWeeks ?: Int.MAX_VALUE`,
coerente con il tiebreaker.

### MEDIUM-004 — Test gap: candidato mai assegnato (lastGlobalWeeks=null)

Risolto in `a13ed09`. Test 8: verifica che candidato con `lastGlobalWeeks=null` ottenga
score massimo (Int.MAX_VALUE) e appaia primo.

### MEDIUM-005 — Test gap: interazione count penalty + cooldown

Risolto in `a13ed09`. Test 9: verifica che countPenalty (5) e cooldownPenalty (10000)
si sommino correttamente nello score (-10008).

### MEDIUM-006 — Test gap: assistant-repeating-assistant slot repeat penalty

Risolto in `a13ed09`. Test 10: verifica che assistente→assistente riceva slotRepeatPenalty=4,
mentre conduttore→assistente no.

---

## Findings invalidati

### INV-001 — Fairness doc formula contraddittoria

Il documento `docs/assignment-algorithm-fairness-analysis.md` mostra la vecchia formula in
"Stato attuale" e la nuova in "Formula risultante (implementata)". Non e' una contraddizione —
e' un changelog intenzionale prima/dopo.

### INV-002 — abs() su lastGlobalWeeks crea asimmetria con count window

`abs()` trasforma `lastGlobalWeeks` in "distanza dalla referenceDate" (past + future simmetrici).
Il count window backward-only e' intenzionale: penalizza il carico recente, non quello futuro.
L'asimmetria e' un design corretto, non un bug.

### INV-003 — Count window exclusive end (< windowEnd)

La finestra `[windowStart, windowEnd)` esclude referenceDate. Corretto: l'assegnazione in
corso non e' ancora persistita e non deve influenzare lo scoring.

---

## Verifiche eseguite

| Data | Comando | Test totali | Fallimenti |
|------|---------|-------------|------------|
| 2026-03-12 | `./gradlew :composeApp:jvmTest` | 10 SuggerisciProclamatoriUseCaseTest + full suite | 0 |

# Review Notes — Ministero del Regno Planner

Prompt sorgente: 5x review-codebase su 5 feature slice (people, weeklyparts, programs,
schemas, print+assignments), 2026-03-12.

---

## Findings aperti

(nessuno)

### Debito accettato

- MEDIUM-020 — DiagnosticsViewModel I/O diretto: utility screen senza logica di dominio, debito accettato

---

## Findings risolti

### Fairness refactoring (round 1, 2026-03-12)

- HIGH-001 — Doc drift data-model.md → `a129c0a`
- HIGH-002 — Doc drift spec Q&A formula → `a129c0a`
- MEDIUM-001 — DRY lastWasConductor → `885f966`
- MEDIUM-002 — Triple→Pair → `885f966`
- MEDIUM-003 — 999→Int.MAX_VALUE → `885f966`
- MEDIUM-004 — Test gap: mai assegnato → `a13ed09`
- MEDIUM-005 — Test gap: count+cooldown → `a13ed09`
- MEDIUM-006 — Test gap: assistant-repeat → `a13ed09`

### Full codebase review (round 2, 2026-03-12)

- HIGH-003 — Dual validation Proclamatore.of() vs Aggregate → `f558cbc`
- HIGH-004 — Dead code setSuspended() → `b45c2b8`
- HIGH-005 — Zero test eligibility cleanup → `dd559fb`
- HIGH-006 — N+1 queries generateProgramTickets → `8961736`
- MEDIUM-007 — Spec drift Proclamatore.init → `b0dc797`
- MEDIUM-008 — Missing test max-length → `bf7c96f`
- MEDIUM-009 — Dead code recompactedSortOrders() → `9d4d1ca`
- MEDIUM-010 — Commento OutputConstants errato → `62ae9e1`
- MEDIUM-011 — createWeekWithFixedPart bypassa of() → `bf67a2a`
- MEDIUM-012 — AssegnaPersona double-wraps Either → `b8bbf79`
- MEDIUM-013 — Anomaly ID hashCode() → composite key → `fe3bf68`
- MEDIUM-014 — fileOpener.open() fuori either → `0df4770`
- MEDIUM-015 — Test happy-path RimuoviAssegnazione → `b9150c4`
- MEDIUM-016 — Test ArchivaAnomalieSchema → `b9150c4`
- MEDIUM-017 — Test domain replaceParts() → `b9150c4`

### Codebase review (round 3, 2026-03-12)

- MEDIUM-018 — Settings.putString fuori tx → `c254e17`
- MEDIUM-019 — SchemaCatalogRemoteSource returns Either → `b2e1d15`

### Codebase review (round 4, 2026-03-12)

- MEDIUM-021 — 4 query SQL orfane → `143bf69`
- MEDIUM-022 — Test gap partial failure AutoAssegna → `5030fc8`

---

## Findings invalidati

- INV-001 — Fairness doc formula: changelog intenzionale
- INV-002 — abs() asimmetria: design corretto
- INV-003 — Count window exclusive end: by design
- INV-004 — VACUUM dual connection: non esiste nel codebase
- INV-005 — Dead ImpostaIdoneitaAssistenzaUseCase: attivamente usato
- INV-006 — Orphan ranking queries: tutte referenziate da SqlDelightAssignmentStore
- INV-007 — Missing index slip_delivery.week_plan_id: indice composito presente
- INV-008 — Private DTOs schemas: design corretto
- INV-009 — TOCTOU gap AggiornaSchemiUseCase: nessun gap
- INV-010 — AppBootstrap race: synchronized corretto
- INV-011 — ViewModel error handling: pattern diversi ma tutti corretti nel contesto
- INV-012 — getOrElse{error()} in SqlDelightWeekPlanStore: pattern corretto per stato impossibile (dati DB già validati in write)
- INV-013 — N+1 listByWeek in SuggerisciProclamatori loop: re-query necessario per correttezza (requiredSex da assignment appena scritti)

---

## Verifiche eseguite

| Data | Comando | Test totali | Fallimenti |
|------|---------|-------------|------------|
| 2026-03-12 | `./gradlew :composeApp:jvmTest` | full suite | 0 |
| 2026-03-12 | post-merge batch 1 | full suite | 0 |
| 2026-03-12 | post-merge batch 2 | full suite | 0 |
| 2026-03-12 | post-merge round 3 (MEDIUM-018/019) | full suite | 0 |
| 2026-03-12 | post-merge round 4 (MEDIUM-021/022) | full suite | 0 |

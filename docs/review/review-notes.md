# Review Notes — Ministero del Regno Planner

Prompt sorgente: 5x review-codebase su 5 feature slice (people, weeklyparts, programs,
schemas, print+assignments), 2026-03-12.

---

## Findings aperti

### MEDIUM-011 — createWeekWithFixedPart bypassa WeekPlan.of()

**Severita'**: Medium | **Effort**: Low

Factory method chiama `WeekPlan(...)` direttamente, bypassando la validazione Monday.

**Evidenze**: `WeekPlanAggregate.kt:140`

---

### MEDIUM-012 — AssegnaPersonaUseCase double-wraps Either

**Severita'**: Medium | **Effort**: Low

`Either.catch` attorno a `runInTransaction` che ritorna `Either`. Dovrebbe usare
`runInTransactionEither`.

**Evidenze**: `AssegnaPersonaUseCase.kt:27-37`

---

### MEDIUM-013 — Anomaly ID usa hashCode() con rischio collisione

**Severita'**: Medium | **Effort**: Low

`INSERT OR IGNORE` con ID da `hashCode()`. Collisione = anomalia silenziosamente persa.

**Evidenze**: `SqlDelightSchemaUpdateAnomalyStore.kt:18`

---

### MEDIUM-014 — fileOpener.open() dentro either block

**Severita'**: Medium | **Effort**: Low

Side effect fire-and-forget dentro `either {}`. Fragile se `DesktopFileOpener` cambia.

**Evidenze**: `StampaProgrammaUseCase.kt:139`

---

### MEDIUM-015 — No happy-path test per RimuoviAssegnazioneUseCase

**Severita'**: Medium | **Effort**: Low

---

### MEDIUM-016 — ArchivaAnomalieSchemaUseCase senza test

**Severita'**: Medium | **Effort**: Low

---

### MEDIUM-017 — replaceParts() senza test domain-level

**Severita'**: Medium | **Effort**: Low

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
  Proclamatore.of() delega a ProclamatoreAggregate.create(). Rimosso Konform validator.
- HIGH-004 — Dead code setSuspended() → `b45c2b8`
  Rimosso da interfaccia, implementazione, SQL, 6 test fakes.
- HIGH-005 — Zero test eligibility cleanup → `dd559fb`
  Aggiunto test con RecordingEligibilityStore e RecordingSchemaUpdateAnomalyStore.
- HIGH-006 — N+1 queries generateProgramTickets → `8961736`
  Batch load via assignmentRepository.listByWeekPlanIds(). Aggiornato DI e test.
- MEDIUM-007 — Spec drift Proclamatore.init → `b0dc797`
  Spec aggiornata: validazione in ProclamatoreAggregate, non init block.
- MEDIUM-008 — Missing test max-length → `bf7c96f`
  4 test boundary per nome/cognome 100/101 chars.
- MEDIUM-009 — Dead code recompactedSortOrders() → `9d4d1ca`
- MEDIUM-010 — Commento OutputConstants errato → `62ae9e1`

---

## Findings invalidati

- INV-001 — Fairness doc formula: changelog intenzionale
- INV-002 — abs() asimmetria: design corretto
- INV-003 — Count window exclusive end: by design

---

## Verifiche eseguite

| Data | Comando | Test totali | Fallimenti |
|------|---------|-------------|------------|
| 2026-03-12 | `./gradlew :composeApp:jvmTest` | full suite | 0 |
| 2026-03-12 | `./gradlew :composeApp:jvmTest` (post-merge batch 1) | full suite | 0 |

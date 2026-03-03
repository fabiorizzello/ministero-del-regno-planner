# Tasks: Allineamento UI/UX Sketch Applicazione

**Input**: Design documents from `/specs/001-align-sketch-ui/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: Inclusi perche la spec richiede criteri di verifica indipendenti per ogni user story e la costituzione impone quality gate sui test.

## Format: `[ID] [P?] [Story] Description with file path`

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: predisporre il design-system workspace condiviso e la base testabile comune.

- [X] T001 Create workspace design tokens file in /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmMain/kotlin/org/example/project/ui/theme/WorkspaceTokens.kt
- [X] T002 Create shared workspace primitives in /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmMain/kotlin/org/example/project/ui/components/workspace/WorkspacePrimitives.kt
- [X] T003 [P] Create shared workspace state pane components in /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmMain/kotlin/org/example/project/ui/components/workspace/WorkspaceStatePane.kt
- [X] T004 [P] Create top-bar interaction policy helper in /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmMain/kotlin/org/example/project/ui/components/workspace/TopBarInteractionPolicy.kt

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: vincoli globali che bloccano tutte le user story finche incompleti.

**⚠️ CRITICAL**: nessuna implementazione US1/US2/US3 parte prima del completamento di questa fase.

- [X] T005 Enforce light-only theme policy in /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmMain/kotlin/org/example/project/ui/theme/AppTheme.kt
- [X] T006 Remove dark-mode persistence/bootstrap in /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmMain/kotlin/org/example/project/core/config/WindowSettingsStore.kt and /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmMain/kotlin/org/example/project/main.kt
- [X] T007 Standardize top-bar sections to Programma/Proclamatori/Diagnostica in /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmMain/kotlin/org/example/project/ui/AppScreen.kt
- [X] T008 Integrate workspace primitives into application shell layout in /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmMain/kotlin/org/example/project/ui/AppScreen.kt
- [X] T009 Add reusable screen-state contract adapters in /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmMain/kotlin/org/example/project/ui/proclamatori/ProclamatoriScreen.kt and /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmMain/kotlin/org/example/project/ui/diagnostics/DiagnosticsScreen.kt
- [X] T010 Create UI test semantics helpers in /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmTest/kotlin/org/example/project/ui/UiContractTestHelpers.kt

**Checkpoint**: baseline design-system, shell app e policy globali pronti.

---

## Phase 3: User Story 1 - Programma Allineato allo Sketch (Priority: P1) 🎯 MVP

**Goal**: allineare Programma al reference sketch mantenendo tutte le azioni operative esistenti.

**Independent Test**: Aprire Programma con dataset completo, confrontare con lo sketch e completare flusso base (mese, autoassegna, stampa, parti/assegnazioni) senza regressioni.

### Tests for User Story 1

- [ ] T011 [P] [US1] Add Program workspace layout contract test in /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmTest/kotlin/org/example/project/ui/workspace/ProgramWorkspaceLayoutContractTest.kt
- [ ] T012 [P] [US1] Add Program critical actions regression test in /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmTest/kotlin/org/example/project/ui/workspace/ProgramWorkspaceActionsRegressionTest.kt

### Implementation for User Story 1

- [X] T013 [US1] Refactor 3-column Program workspace structure in /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceScreen.kt
- [X] T014 [US1] Restyle Program months/timeline/parts cards using shared primitives in /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceComponents.kt
- [X] T015 [US1] Restyle Program action block (autoassegna/stampa/copertura/feed) in /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceScreen.kt
- [X] T016 [US1] Keep assignment settings as right-panel inline controls in /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceScreen.kt
- [X] T017 [US1] Align assignment chips and part cards visual language in /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmMain/kotlin/org/example/project/ui/assignments/AssignmentsComponents.kt
- [X] T018 [US1] Implement explicit loading/error/empty/content states for Program workspace in /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceScreen.kt
- [ ] T019 [US1] Validate Program smoke flow coverage notes in /home/fabio/IdeaProjects/efficaci-nel-ministero/specs/001-align-sketch-ui/quickstart.md

**Checkpoint**: User Story 1 completa, testabile e pronta come MVP.

---

## Phase 4: User Story 2 - Linguaggio Visivo Coerente in Tutta l'Applicazione (Priority: P2)

**Goal**: rendere coerenti Programma, Proclamatori e Diagnostica con stesso linguaggio visivo e stessi pattern di stato.

**Independent Test**: Navigare tra Programma/Proclamatori/Diagnostica e verificare coerenza di top bar, componenti, gerarchie e accesso impostazioni assegnazione dal solo Programma.

### Tests for User Story 2

- [ ] T020 [P] [US2] Add top-level navigation contract test in /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmTest/kotlin/org/example/project/ui/AppNavigationContractTest.kt
- [ ] T021 [P] [US2] Add Proclamatori screen-state contract test in /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmTest/kotlin/org/example/project/ui/proclamatori/ProclamatoriScreenStateContractTest.kt
- [ ] T022 [P] [US2] Add Diagnostics screen-state contract test in /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmTest/kotlin/org/example/project/ui/diagnostics/DiagnosticsScreenStateContractTest.kt

### Implementation for User Story 2

- [X] T023 [US2] Apply workspace primitives to Proclamatori container layout in /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmMain/kotlin/org/example/project/ui/proclamatori/ProclamatoriScreen.kt
- [ ] T024 [US2] Restyle Proclamatori table/actions/cards using shared tokens in /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmMain/kotlin/org/example/project/ui/proclamatori/ProclamatoriTableComponents.kt
- [ ] T025 [US2] Restyle Proclamatori form and dialogs with shared primitives in /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmMain/kotlin/org/example/project/ui/proclamatori/ProclamatoriFormComponents.kt and /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmMain/kotlin/org/example/project/ui/proclamatori/ProclamatoriDialogComponents.kt
- [X] T026 [US2] Apply workspace primitives to Diagnostics sections in /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmMain/kotlin/org/example/project/ui/diagnostics/DiagnosticsScreen.kt
- [X] T027 [US2] Normalize loading/error/empty/content rendering in /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmMain/kotlin/org/example/project/ui/proclamatori/ProclamatoriScreen.kt and /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmMain/kotlin/org/example/project/ui/diagnostics/DiagnosticsScreen.kt
- [X] T028 [US2] Remove obsolete settings section entrypoint in /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmMain/kotlin/org/example/project/ui/AppScreen.kt and /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmMain/kotlin/org/example/project/ui/settings/AssignmentEngineSettingsScreen.kt
- [ ] T029 [US2] Align shared typography/spacing/color constants for cross-screen consistency in /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmMain/kotlin/org/example/project/ui/theme/AppTypography.kt, /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmMain/kotlin/org/example/project/ui/theme/AppSpacing.kt, and /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmMain/kotlin/org/example/project/ui/theme/SemanticColors.kt

**Checkpoint**: US1 e US2 complete e indipendentemente verificabili.

---

## Phase 5: User Story 3 - Interazioni Finestra Desktop Naturali (Priority: P3)

**Goal**: rendere la top bar completamente conforme al contratto drag/double-click desktop senza attivazioni accidentali.

**Independent Test**: Trascinare da qualunque area non interattiva top bar e verificare doppio click maximize/restore; controlli interattivi non devono attivare drag/toggle.

### Tests for User Story 3

- [ ] T030 [P] [US3] Add top-bar interaction policy unit tests in /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmTest/kotlin/org/example/project/ui/TopBarInteractionPolicyTest.kt
- [ ] T031 [P] [US3] Add window chrome interaction integration test in /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmTest/kotlin/org/example/project/ui/WindowChromeInteractionContractTest.kt

### Implementation for User Story 3

- [X] T032 [US3] Implement full non-interactive drag surface across top bar in /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmMain/kotlin/org/example/project/ui/AppScreen.kt
- [X] T033 [US3] Implement non-interactive double-click maximize/restore handler in /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmMain/kotlin/org/example/project/ui/AppScreen.kt and /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmMain/kotlin/org/example/project/main.kt
- [X] T034 [US3] Enforce exclusion of interactive controls from drag/toggle in /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmMain/kotlin/org/example/project/ui/AppScreen.kt and /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmMain/kotlin/org/example/project/ui/components/workspace/TopBarInteractionPolicy.kt
- [ ] T035 [US3] Handle maximized drag edge cases and input-stability safeguards in /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmMain/kotlin/org/example/project/ui/AppScreen.kt
- [ ] T036 [US3] Sync contract acceptance matrix with implemented behavior in /home/fabio/IdeaProjects/efficaci-nel-ministero/specs/001-align-sketch-ui/contracts/window-chrome-contract.md

**Checkpoint**: tutte le user story complete con interazioni finestra conformi al contratto.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: consolidare evidenze, quality gates e validazione finale multi-risoluzione.

- [ ] T037 [P] Update final validation steps and command log in /home/fabio/IdeaProjects/efficaci-nel-ministero/specs/001-align-sketch-ui/quickstart.md
- [ ] T038 [P] Record SC-001..SC-006 evidence checklist in /home/fabio/IdeaProjects/efficaci-nel-ministero/specs/001-align-sketch-ui/checklists/ui-alignment-evidence.md
- [ ] T039 Record automated test execution results in /home/fabio/IdeaProjects/efficaci-nel-ministero/specs/001-align-sketch-ui/checklists/test-execution-report.md
- [ ] T040 Record manual desktop validation matrix (1366x768, 1920x1080) in /home/fabio/IdeaProjects/efficaci-nel-ministero/specs/001-align-sketch-ui/checklists/manual-desktop-validation.md

---

## Dependencies & Execution Order

### Phase Dependencies

- Phase 1 (Setup): nessuna dipendenza.
- Phase 2 (Foundational): dipende da Phase 1; blocca tutte le user story.
- Phase 3 (US1): dipende da Phase 2.
- Phase 4 (US2): dipende da Phase 2 e dalla baseline UI prodotta in US1.
- Phase 5 (US3): dipende da Phase 2; da pianificare dopo US2 per evitare conflitti su `AppScreen.kt`.
- Phase 6 (Polish): dipende da completamento US1, US2, US3.

### User Story Dependencies

- US1 (P1): indipendente dopo foundational, definisce MVP.
- US2 (P2): indipendente a livello funzionale ma condivide componenti shell e token introdotti in US1.
- US3 (P3): indipendente a livello di obiettivo, ma tocca gli stessi file di shell e va eseguita dopo convergenza top bar.

### Dependency Graph

- Setup -> Foundational -> US1 -> US2 -> US3 -> Polish

---

## Parallel Execution Examples

### User Story 1

```bash
# Tests in parallel
Task: T011 /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmTest/kotlin/org/example/project/ui/workspace/ProgramWorkspaceLayoutContractTest.kt
Task: T012 /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmTest/kotlin/org/example/project/ui/workspace/ProgramWorkspaceActionsRegressionTest.kt
```

### User Story 2

```bash
# Contract tests in parallel
Task: T020 /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmTest/kotlin/org/example/project/ui/AppNavigationContractTest.kt
Task: T021 /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmTest/kotlin/org/example/project/ui/proclamatori/ProclamatoriScreenStateContractTest.kt
Task: T022 /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmTest/kotlin/org/example/project/ui/diagnostics/DiagnosticsScreenStateContractTest.kt
```

### User Story 3

```bash
# Interaction tests in parallel
Task: T030 /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmTest/kotlin/org/example/project/ui/TopBarInteractionPolicyTest.kt
Task: T031 /home/fabio/IdeaProjects/efficaci-nel-ministero/composeApp/src/jvmTest/kotlin/org/example/project/ui/WindowChromeInteractionContractTest.kt
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Completare Phase 1 e Phase 2.
2. Completare Phase 3 (US1).
3. Validare smoke Programma e test US1.
4. Demo interna del MVP prima di estendere alle altre schermate.

### Incremental Delivery

1. Dopo MVP, implementare US2 per coerenza cross-app.
2. Implementare US3 per interazioni finestra desktop definitive.
3. Chiudere con Phase 6 (evidenze SC + quality gates).

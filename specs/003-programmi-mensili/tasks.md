# Tasks: Programmi Mensili

**Input**: Design documents from `/specs/003-programmi-mensili/`
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: predisporre struttura test e punti comuni per l'evoluzione della feature.

- [X] T001 Create programs feature test package scaffold in composeApp/src/jvmTest/kotlin/org/example/project/feature/programs/
- [X] T002 Create workspace test package scaffold for monthly-program flows in composeApp/src/jvmTest/kotlin/org/example/project/ui/workspace/
- [X] T003 [P] Add reusable program-month fixture builders in composeApp/src/jvmTest/kotlin/org/example/project/feature/programs/ProgramFixtures.kt

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: allineare contratti core e policy trasversali prima delle user story.

**⚠️ CRITICAL**: nessuna user story parte prima del completamento di questa fase.

- [X] T004 Refactor ProgramSelectionSnapshot to `current + futures` in composeApp/src/jvmMain/kotlin/org/example/project/feature/programs/application/CaricaProgrammiAttiviUseCase.kt
- [X] T005 Update active-programs SQL query contract for ordered current/future months in composeApp/src/commonMain/sqldelight/org/example/project/db/MinisteroDatabase.sq
- [X] T006 Implement snapshot mapping changes in composeApp/src/jvmMain/kotlin/org/example/project/feature/programs/infrastructure/SqlDelightProgramStore.kt
- [X] T007 [P] Introduce shared feedback policy helpers (success-if-needed, error-always) in composeApp/src/jvmMain/kotlin/org/example/project/ui/components/AsyncOperationHelper.kt
- [X] T008 [P] Expand lifecycle UI state from single future to future list in composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramLifecycleViewModel.kt
- [X] T009 [P] Expand schema management state from single future to selected-future context in composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/SchemaManagementViewModel.kt

**Checkpoint**: base pronta per implementare storie indipendenti.

---

## Phase 3: User Story 1 - Creazione programma per mese esplicito (Priority: P1) 🎯 MVP

**Goal**: permettere creazione mese target esplicito con regole di contiguità e limite futuri.

**Independent Test**: senza mese corrente, creare direttamente `corrente+1` da CTA mese target; verificare range date corretto e assenza di creazione implicita del corrente.

### Tests for User Story 1

- [X] T010 [P] [US1] Add unit tests for target-month validation rules in composeApp/src/jvmTest/kotlin/org/example/project/feature/programs/CreaProgrammaMeseTargetUseCaseTest.kt
- [X] T011 [P] [US1] Add lifecycle viewmodel tests for creatable-month CTA window in composeApp/src/jvmTest/kotlin/org/example/project/ui/workspace/ProgramLifecycleViewModelCreateMonthTest.kt

### Implementation for User Story 1

- [X] T012 [US1] Replace next-only creation with target-month creation logic in composeApp/src/jvmMain/kotlin/org/example/project/feature/programs/application/CreaProssimoProgrammaUseCase.kt
- [X] T013 [US1] Extend creation contract with month-window and contiguity checks in composeApp/src/jvmMain/kotlin/org/example/project/feature/programs/application/ProgramStore.kt
- [X] T014 [US1] Implement target-month store operations in composeApp/src/jvmMain/kotlin/org/example/project/feature/programs/infrastructure/SqlDelightProgramStore.kt
- [X] T015 [US1] Update use-case DI wiring for target-month creation in composeApp/src/jvmMain/kotlin/org/example/project/feature/programs/di/ProgramsModule.kt
- [X] T016 [US1] Update create-program orchestration and selectable target month state in composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramLifecycleViewModel.kt
- [X] T017 [US1] Render explicit month-target CTA controls in composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceScreen.kt
- [X] T018 [US1] Position "crea mese corrente" action under month chips and hide unavailable create actions in composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceComponents.kt

**Checkpoint**: creazione mese target completa e testabile in autonomia.

---

## Phase 4: User Story 2 - Generazione delle settimane del programma (Priority: P1)

**Goal**: garantire generazione/rigenerazione settimane coerente con template, fallback e skip.

**Independent Test**: creare programma, generare settimane e verificare mapping template/fallback fixed + aggiornamento `templateAppliedAt`.

### Tests for User Story 2

- [X] T019 [P] [US2] Add generation tests for date-range and fixed-part fallback in composeApp/src/jvmTest/kotlin/org/example/project/feature/programs/GeneraSettimaneProgrammaUseCaseTest.kt
- [X] T020 [P] [US2] Add regenerate-destructive-behavior test with timestamp update in composeApp/src/jvmTest/kotlin/org/example/project/feature/programs/GeneraSettimaneProgrammaRegenerateTest.kt

### Implementation for User Story 2

- [X] T021 [US2] Align generation flow with skip/idempotent requirements in composeApp/src/jvmMain/kotlin/org/example/project/feature/programs/application/GeneraSettimaneProgrammaUseCase.kt
- [X] T022 [US2] Ensure week-plan persistence preserves program linkage during regenerate in composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/infrastructure/SqlDelightWeekPlanStore.kt
- [X] T023 [US2] Align week mutation availability (past/current/future) with generation semantics in composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceComponents.kt

**Checkpoint**: generazione settimane robusta e verificabile senza dipendere da altre story P2.

---

## Phase 5: User Story 3 - Consultazione programmi attivi (Priority: P1)

**Goal**: consultare e selezionare `current + futures` con fallback/memoria di sessione coerenti.

**Independent Test**: con due futuri disponibili, cambiare mese e fare reload dati; la selezione resta sul mese valido o applica fallback corretto.

### Tests for User Story 3

- [X] T024 [P] [US3] Add snapshot selection tests for current-plus-futures ordering in composeApp/src/jvmTest/kotlin/org/example/project/feature/programs/CaricaProgrammiAttiviUseCaseTest.kt
- [X] T025 [P] [US3] Add reload/switch selection-memory tests in composeApp/src/jvmTest/kotlin/org/example/project/ui/workspace/ProgramLifecycleViewModelSelectionTest.kt

### Implementation for User Story 3

- [X] T026 [US3] Refactor lifecycle loading fallback logic to support `futures` list and selected-id retention in composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramLifecycleViewModel.kt
- [X] T027 [US3] Refactor schema selection binding to selected future month (not singleton) in composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/SchemaManagementViewModel.kt
- [X] T028 [US3] Update month switch UI rendering for multiple future chips in composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceScreen.kt
- [X] T029 [US3] Preserve and restore current-program context on month switch in composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceComponents.kt
- [X] T030 [US3] Apply success-toast suppression and error-always feedback rules in composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramLifecycleViewModel.kt

**Checkpoint**: consultazione e switch mesi completi con comportamento session-safe.

---

## Phase 6: User Story 4 - Eliminazione programma corrente o futuro (Priority: P2)

**Goal**: consentire delete di corrente/futuro con prompt impatto e fallback selezione.

**Independent Test**: eliminare un corrente con settimane/assegnazioni e verificare prompt, transazione, rimozione e nuova selezione automatica.

### Tests for User Story 4

- [X] T031 [P] [US4] Add delete-eligibility tests (current/future allowed, past denied) in composeApp/src/jvmTest/kotlin/org/example/project/feature/programs/EliminaProgrammaUseCaseTest.kt
- [X] T032 [P] [US4] Add delete-confirmation impact dialog tests in composeApp/src/jvmTest/kotlin/org/example/project/ui/workspace/ProgramWorkspaceDeleteDialogTest.kt

### Implementation for User Story 4

- [X] T033 [US4] Generalize delete use case from future-only to current-or-future in composeApp/src/jvmMain/kotlin/org/example/project/feature/programs/application/EliminaProgrammaFuturoUseCase.kt
- [X] T034 [US4] Add SQLDelight counters for delete impact (weeks and assignments) in composeApp/src/commonMain/sqldelight/org/example/project/db/MinisteroDatabase.sq
- [X] T035 [US4] Expose delete-impact counting from store adapter in composeApp/src/jvmMain/kotlin/org/example/project/feature/programs/infrastructure/SqlDelightProgramStore.kt
- [X] T036 [US4] Update delete flow and post-delete reselection fallback in composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramLifecycleViewModel.kt
- [X] T037 [US4] Render destructive confirmation prompt with impact summary in composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceScreen.kt

**Checkpoint**: delete corrente/futuro completo e difeso da guardrail UX.

---

## Phase 7: User Story 5 - Aggiornamento programma da schemi (Priority: P2)

**Goal**: aggiornare programma da schemi senza preview separata e badge solo su delta reale.

**Independent Test**: aggiornare schemi con/ senza variazioni template e verificare badge solo su mesi impattati + refresh diretto programma.

### Tests for User Story 5

- [X] T038 [P] [US5] Add refresh tests for assignment preservation/removal report in composeApp/src/jvmTest/kotlin/org/example/project/feature/programs/AggiornaProgrammaDaSchemiUseCaseTest.kt
- [X] T039 [P] [US5] Extend schema management tests for per-month real-delta badge behavior in composeApp/src/jvmTest/kotlin/org/example/project/ui/workspace/SchemaManagementViewModelTest.kt

### Implementation for User Story 5

- [X] T040 [US5] Remove preview-style branching and keep direct refresh execution in composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/SchemaManagementViewModel.kt
- [X] T041 [US5] Implement real-delta detection for impacted months using schema/program timestamps and hashes in composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/SchemaManagementViewModel.kt
- [X] T042 [US5] Render "template modificato" badge only on impacted future month chips in composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceComponents.kt
- [X] T043 [US5] Ensure schema refresh action respects selected month and keeps selection after reload in composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceScreen.kt

**Checkpoint**: refresh schemi allineato a spec con segnalazione delta non rumorosa.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: consolidamento finale multi-story.

- [X] T044 [P] Align final quickstart verification steps with implemented UI actions in specs/003-programmi-mensili/quickstart.md
- [X] T045 [P] Add regression tests for feedback policy (silent success vs visible errors) in composeApp/src/jvmTest/kotlin/org/example/project/ui/workspace/FeedbackPolicyTest.kt
- [X] T046 Normalize remaining month-action labels and Italian copy consistency in composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceScreen.kt
- [X] T047 Validate DI wiring consistency for updated program use cases in composeApp/src/jvmMain/kotlin/org/example/project/ui/di/ViewModelsModule.kt

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: nessuna dipendenza.
- **Phase 2 (Foundational)**: dipende da Phase 1, blocca tutte le user story.
- **Phases 3-7 (User Stories)**: dipendono dal completamento di Phase 2.
- **Phase 8 (Polish)**: dipende dal completamento delle story selezionate.

### User Story Dependencies

- **US1 (P1)**: parte subito dopo Foundational.
- **US2 (P1)**: parte dopo Foundational; usa programma esistente ma resta testabile in autonomia.
- **US3 (P1)**: parte dopo Foundational; raccomandato completare dopo T004-T009.
- **US4 (P2)**: dipende dai flussi di selezione consolidati in US3.
- **US5 (P2)**: dipende da snapshot/selection multi-mese stabilizzati in US3.

### Dependency Graph (story level)

- `US1 -> US3 -> (US4, US5)`
- `US2` può procedere in parallelo a `US1/US3` dopo Foundational.

---

## Parallel Execution Examples

### User Story 1

- Eseguibili in parallelo: `T010`, `T011`.
- Dopo test red: in parallelo `T013` e `T015`; poi `T012 -> T014 -> T016 -> T017 -> T018`.

### User Story 2

- Eseguibili in parallelo: `T019`, `T020`.
- Implementazione sequenza: `T021 -> T022 -> T023`.

### User Story 3

- Eseguibili in parallelo: `T024`, `T025`.
- Implementazione consigliata: `T026` e `T027` in parallelo, poi `T028 -> T029 -> T030`.

### User Story 4

- Eseguibili in parallelo: `T031`, `T032`.
- Implementazione consigliata: `T034` e `T035` in parallelo, poi `T033 -> T036 -> T037`.

### User Story 5

- Eseguibili in parallelo: `T038`, `T039`.
- Implementazione sequenza: `T040 -> T041 -> T042 -> T043`.

---

## Implementation Strategy

### MVP First

1. Completare Phase 1 e Phase 2.
2. Completare US1 (Phase 3).
3. Validare end-to-end creazione mese target.
4. Completare US3 per esperienza mese completa minima.

### Incremental Delivery

1. Rilasciare US1 + US3 come baseline operativa programmi multi-mese.
2. Aggiungere US2 per solidità generazione settimane.
3. Aggiungere US4 per gestione distruttiva controllata.
4. Aggiungere US5 per riallineamento schemi senza rumore UX.
5. Chiudere con Phase 8 (polish).

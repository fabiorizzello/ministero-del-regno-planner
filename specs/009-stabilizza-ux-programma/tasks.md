# Tasks: Stabilizzazione UX Programma e Studenti

**Input**: Design documents from `/specs/009-stabilizza-ux-programma/`  
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`

**Tests**: Inclusi. La specifica richiede scenari testabili per refresh modale, preservazione assegnazioni, fuzzy search, disponibilita' di `Salta settimana`, reset scroll e stabilita' delle card studenti.

**Organization**: Tasks grouped by user story to enable independent implementation and independent validation.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (`[US1]`, `[US2]`, `[US3]`)
- Every task includes an exact file path

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: preparare gli agganci condivisi per ricerca locale, contratti UI e copertura test prima dei fix di story.

- [X] T001 Create shared local-search package scaffold for UI-facing ranking helpers in `composeApp/src/jvmMain/kotlin/org/example/project/ui/search/`
- [X] T002 Create matching test package scaffold for shared local-search helpers in `composeApp/src/jvmTest/kotlin/org/example/project/ui/search/`
- [X] T003 Review feature quickstart and keep validation steps aligned with the planned fixes in `specs/009-stabilizza-ux-programma/quickstart.md`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: costruire le utility condivise che bloccano le user story di refresh/ranking e i test UI di regressione.

**CRITICAL**: No user story work can begin until this phase is complete

- [X] T004 [P] Implement deterministic fuzzy ranking and normalization helpers shared by picker and studenti list in `composeApp/src/jvmMain/kotlin/org/example/project/ui/search/FuzzyPersonSearch.kt`
- [X] T005 [P] Add unit tests for typo tolerance, stable tie-break and blank-query fallbacks in `composeApp/src/jvmTest/kotlin/org/example/project/ui/search/FuzzyPersonSearchTest.kt`
- [X] T006 Extend reusable UI contract tags needed by picker, skip-week action and studenti pagination tests in `composeApp/src/jvmTest/kotlin/org/example/project/ui/UiContractTestHelpers.kt`

**Checkpoint**: Foundation ready - user stories can now begin

---

## Phase 3: User Story 1 - Assegnare proclamatori con dati aggiornati (Priority: P1) MVP

**Goal**: aggiornare in modo coerente la modale di assegnazione quando cambiano riposo e ricerca, con copy italiana comprensibile.

**Independent Test**: aprire la modale di assegnazione, cambiare il filtro di riposo con e senza ricerca attiva e verificare refresh immediato, ordinamento coerente e terminologia "riposo".

### Tests for User Story 1

- [X] T007 [P] [US1] Add ViewModel tests for reload-on-rest-toggle, last-change-wins and search-state preservation in `composeApp/src/jvmTest/kotlin/org/example/project/ui/workspace/PersonPickerViewModelTest.kt`
- [X] T008 [P] [US1] Add suggestion ranking and cooldown-label regression tests for the assignment picker in `composeApp/src/jvmTest/kotlin/org/example/project/feature/assignments/SuggerisciProclamatoriUseCaseTest.kt`

### Implementation for User Story 1

- [X] T009 [P] [US1] Apply shared fuzzy ranking to assignment-picker search results in `composeApp/src/jvmMain/kotlin/org/example/project/ui/assignments/AssignmentsComponents.kt`
- [X] T010 [US1] Update picker state transitions and guarded reload flow for rest-filter changes in `composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/PersonPickerViewModel.kt`
- [X] T011 [US1] Wire rest-toggle changes to explicit picker refresh without stale UI leakage in `composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceScreen.kt`
- [X] T012 [US1] Replace residual "cooldown" user-facing copy with "riposo" terminology in `composeApp/src/jvmMain/kotlin/org/example/project/ui/assignments/AssignmentsComponents.kt`
- [X] T013 [US1] Align recency/rest copy formatting with Italian terminology in `composeApp/src/jvmMain/kotlin/org/example/project/ui/assignments/RecencyFormatter.kt`

**Checkpoint**: User Story 1 should be functional and independently testable

---

## Phase 4: User Story 2 - Modificare settimane assegnate senza perdere lavoro valido (Priority: P1)

**Goal**: preservare selettivamente le assegnazioni ancora valide quando si aggiorna la composizione di una settimana gia' assegnata.

**Independent Test**: partire da una settimana con assegnazioni esistenti, aggiungere o rimuovere singole parti e verificare che vengano rimosse solo le assegnazioni non piu' compatibili.

### Tests for User Story 2

- [X] T014 [P] [US2] Add use-case regressions for preserving assignments on add/remove/reorder of weekly parts in `composeApp/src/jvmTest/kotlin/org/example/project/feature/weeklyparts/AggiornaPartiSettimanaUseCaseTest.kt`
- [X] T015 [P] [US2] Add aggregate/domain tests for logical continuity keys across unchanged weekly parts in `composeApp/src/jvmTest/kotlin/org/example/project/feature/weeklyparts/domain/WeekPlanAggregateTest.kt`

### Implementation for User Story 2

- [X] T016 [P] [US2] Extract reusable assignment continuity key support from program regeneration logic into `composeApp/src/jvmMain/kotlin/org/example/project/feature/programs/application/AssignmentRestoreSupport.kt`
- [X] T017 [US2] Apply continuity-key based preservation when updating week parts in `composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/application/AggiornaPartiSettimanaUseCase.kt`
- [X] T018 [US2] Persist selectively preserved assignments while saving edited week aggregates in `composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/infrastructure/SqlDelightWeekPlanStore.kt`

**Checkpoint**: User Story 2 should be functional and independently testable

---

## Phase 5: User Story 3 - Navigare programma e studenti con ricerca e layout piu' solidi (Priority: P2)

**Goal**: rendere piu' affidabili ricerca, navigazione settimana, paginazione e layout studenti mantenendo il design desktop esistente.

**Independent Test**: verificare fuzzy search coerente in studenti, disponibilita' di `Salta settimana` su programmi passati, reset scroll al cambio pagina, etichette italiane e action bar stabile in vista card.

### Tests for User Story 3

- [X] T019 [P] [US3] Add list ViewModel tests for fuzzy ranking, page-reset token and search pagination behavior in `composeApp/src/jvmTest/kotlin/org/example/project/ui/proclamatori/ProclamatoriListViewModelTest.kt`
- [X] T020 [P] [US3] Add workspace UI tests that keep `Salta settimana` available for past programs with coherent state feedback in `composeApp/src/jvmTest/kotlin/org/example/project/ui/workspace/ProgramLifecycleViewModelTest.kt`
- [X] T021 [P] [US3] Add UI regression tests for studenti table scroll reset and stable card action bar in `composeApp/src/jvmTest/kotlin/org/example/project/ui/proclamatori/ProclamatoriScreenTest.kt`

### Implementation for User Story 3

- [X] T022 [P] [US3] Apply shared fuzzy ranking and scroll-reset state to the studenti list model in `composeApp/src/jvmMain/kotlin/org/example/project/ui/proclamatori/ProclamatoriListViewModel.kt`
- [X] T023 [US3] Update studenti table and card rendering for Italian labels, explicit empty states and fixed-height action bars in `composeApp/src/jvmMain/kotlin/org/example/project/ui/proclamatori/ProclamatoriTableComponents.kt`
- [X] T024 [US3] Refine studenti screen orchestration for search-first focus and pagination reset handling in `composeApp/src/jvmMain/kotlin/org/example/project/ui/proclamatori/ProclamatoriScreen.kt`
- [X] T025 [US3] Keep skip-week action visible and actionable for past programs in `composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceScreen.kt`
- [X] T026 [US3] Align week-skip command state and feedback flows with always-available UI affordance in `composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/PartEditorViewModel.kt`

**Checkpoint**: All user stories should now be independently functional

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: validare il feature slice end-to-end e rifinire i dettagli trasversali.

- [ ] T027 [P] Run manual quickstart validation scenarios and update any clarified wording in `specs/009-stabilizza-ux-programma/quickstart.md`
- [X] T028 [P] Review AGENTS context and keep only relevant stabilization notes in `AGENTS.md`
- [X] T029 Run automated verification for this feature with `./gradlew :composeApp:jvmTest` from `C:/Users/fabio/dev_windows/ministero-del-regno-planner`
- [ ] T030 Run extended verification with `./gradlew :composeApp:build` from `C:/Users/fabio/dev_windows/ministero-del-regno-planner`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1: Setup**: no dependencies
- **Phase 2: Foundational**: depends on Phase 1 and blocks all user stories
- **Phase 3: US1**: depends on Phase 2
- **Phase 4: US2**: depends on Phase 2
- **Phase 5: US3**: depends on Phase 2
- **Phase 6: Polish**: depends on all desired user stories being complete

### User Story Dependencies

- **US1**: can start after Foundational; no dependency on other stories
- **US2**: can start after Foundational; no dependency on other stories
- **US3**: can start after Foundational; reuses the shared fuzzy search helper but remains independently testable from US1 and US2

### Within Each User Story

- Tests should be written first and fail before implementation
- Shared projections/helpers before ViewModel or use case rewiring
- State handling before UI rendering refinements
- Domain preservation logic before infrastructure persistence updates

### Parallel Opportunities

- `T004` and `T005` can run in parallel once the shared package scaffold exists
- `T007` and `T008` can run in parallel
- `T009` can proceed in parallel with `T010`, then `T011` and `T012` follow serially
- `T014` and `T015` can run in parallel
- `T016` can run in parallel with regression test authoring, then `T017` and `T018` follow serially
- `T019`, `T020` and `T021` can run in parallel
- `T022` and `T025` can start in parallel, then `T023`, `T024` and `T026` close the UI flows
- `T027` and `T028` can run in parallel during polish

---

## Parallel Example: User Story 1

```bash
# Parallel test work for US1
Task: "Add ViewModel tests for reload-on-rest-toggle, last-change-wins and search-state preservation in composeApp/src/jvmTest/kotlin/org/example/project/ui/workspace/PersonPickerViewModelTest.kt"
Task: "Add suggestion ranking and cooldown-label regression tests for the assignment picker in composeApp/src/jvmTest/kotlin/org/example/project/feature/assignments/SuggerisciProclamatoriUseCaseTest.kt"

# Parallel implementation prep for US1
Task: "Apply shared fuzzy ranking to assignment-picker search results in composeApp/src/jvmMain/kotlin/org/example/project/ui/assignments/AssignmentsComponents.kt"
Task: "Update picker state transitions and guarded reload flow for rest-filter changes in composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/PersonPickerViewModel.kt"
```

---

## Parallel Example: User Story 2

```bash
# Parallel test work for US2
Task: "Add use-case regressions for preserving assignments on add/remove/reorder of weekly parts in composeApp/src/jvmTest/kotlin/org/example/project/feature/weeklyparts/AggiornaPartiSettimanaUseCaseTest.kt"
Task: "Add aggregate/domain tests for logical continuity keys across unchanged weekly parts in composeApp/src/jvmTest/kotlin/org/example/project/feature/weeklyparts/domain/WeekPlanAggregateTest.kt"

# Parallel implementation prep for US2
Task: "Extract reusable assignment continuity key support from program regeneration logic into composeApp/src/jvmMain/kotlin/org/example/project/feature/programs/application/AssignmentRestoreSupport.kt"
```

---

## Parallel Example: User Story 3

```bash
# Parallel test work for US3
Task: "Add list ViewModel tests for fuzzy ranking, page-reset token and search pagination behavior in composeApp/src/jvmTest/kotlin/org/example/project/ui/proclamatori/ProclamatoriListViewModelTest.kt"
Task: "Add workspace UI tests that keep Salta settimana available for past programs with coherent state feedback in composeApp/src/jvmTest/kotlin/org/example/project/ui/workspace/ProgramLifecycleViewModelTest.kt"
Task: "Add UI regression tests for studenti table scroll reset and stable card action bar in composeApp/src/jvmTest/kotlin/org/example/project/ui/proclamatori/ProclamatoriScreenTest.kt"

# Parallel implementation prep for US3
Task: "Apply shared fuzzy ranking and scroll-reset state to the studenti list model in composeApp/src/jvmMain/kotlin/org/example/project/ui/proclamatori/ProclamatoriListViewModel.kt"
Task: "Keep skip-week action visible and actionable for past programs in composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceScreen.kt"
```

---

## Implementation Strategy

### MVP First (User Stories 1 and 2)

1. Complete Setup
2. Complete Foundational
3. Complete US1 to remove stale assignment suggestions
4. Complete US2 to stop destructive assignment resets on week edits
5. Validate both P1 flows before moving to UX polish

### Incremental Delivery

1. Deliver shared fuzzy search + picker refresh
2. Deliver selective assignment preservation on week edits
3. Deliver studenti/program workspace UX refinements
4. Finish with cross-cutting validation and full build

### Parallel Team Strategy

1. One developer completes Setup + Foundational
2. Then:
   - Developer A: US1
   - Developer B: US2
   - Developer C: US3 test authoring or UI polish prep
3. Final pass covers polish and automated verification

---

## Notes

- All tasks follow the required checklist format
- Exact file paths are included in every task
- Tests are explicitly included because the specification marks testing as mandatory
- `ui-ux-pro-max` guidance was applied as restrained desktop refinements: flat/light visual language, search-first interactions, explicit empty states and stable action bars

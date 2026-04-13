# Tasks: Catalogo Admin Tipi Parte e Schemi

**Input**: Design documents from `/specs/008-admin-part-catalog/`  
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`

**Tests**: Inclusi. Il piano richiede test ViewModel e UI non banali per stati, selezione e navigazione admin secondaria.

**Organization**: Tasks grouped by user story to enable independent implementation and independent validation.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (`[US1]`, `[US2]`, `[US3]`)
- Every task includes an exact file path

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: preparare lo spazio UI/admin e i punti di aggancio minimi senza ancora implementare le singole storie.

- [X] T001 Create feature package scaffold for admin catalog screens in `composeApp/src/jvmMain/kotlin/org/example/project/ui/admincatalog/`
- [X] T002 Create matching test package scaffold in `composeApp/src/jvmTest/kotlin/org/example/project/ui/admincatalog/`
- [X] T003 Review and align naming/comments for admin catalog feature in `specs/008-admin-part-catalog/plan.md`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: costruire la base condivisa che blocca tutte le user story successive.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T004 Implement shared admin section/navigation models for secondary admin areas in `composeApp/src/jvmMain/kotlin/org/example/project/ui/admincatalog/AdminCatalogNavigation.kt`
- [X] T005 [P] Implement shared read-only state models and selection helpers in `composeApp/src/jvmMain/kotlin/org/example/project/ui/admincatalog/AdminCatalogUiState.kt`
- [X] T006 [P] Implement shared list-detail shell and read-only state pane composables in `composeApp/src/jvmMain/kotlin/org/example/project/ui/admincatalog/AdminCatalogComponents.kt`
- [X] T007 Wire the admin secondary navigation entry point into `composeApp/src/jvmMain/kotlin/org/example/project/ui/AppScreen.kt`
- [X] T008 Add navigation-focused UI contract helpers or tags for the new admin areas in `composeApp/src/jvmTest/kotlin/org/example/project/ui/UiContractTestHelpers.kt`

**Checkpoint**: Foundation ready - user stories can now begin

---

## Phase 3: User Story 1 - Consultare il catalogo tipi di parte (Priority: P1) 🎯 MVP

**Goal**: permettere all'amministratore di aprire una schermata read-only con elenco tipi parte, stato attivo/disattivo e dettaglio contestuale.

**Independent Test**: aprire `Tipi parte`, verificare elenco completo, selezionare un elemento e vedere il dettaglio aggiornarsi senza flussi di modifica.

### Tests for User Story 1

- [X] T009 [P] [US1] Add ViewModel tests for loading/content/empty/error states of the part type catalog in `composeApp/src/jvmTest/kotlin/org/example/project/ui/admincatalog/PartTypeCatalogViewModelTest.kt`
- [X] T010 [P] [US1] Add UI tests for part type selection and visible read-only detail panel in `composeApp/src/jvmTest/kotlin/org/example/project/ui/admincatalog/PartTypeCatalogScreenTest.kt`

### Implementation for User Story 1

- [X] T011 [P] [US1] Create part type catalog projections and mappers in `composeApp/src/jvmMain/kotlin/org/example/project/ui/admincatalog/PartTypeCatalogModels.kt`
- [X] T012 [US1] Implement the part type catalog ViewModel using `PartTypeStore.allWithStatus()` in `composeApp/src/jvmMain/kotlin/org/example/project/ui/admincatalog/PartTypeCatalogViewModel.kt`
- [X] T013 [US1] Implement the read-only part type catalog screen in `composeApp/src/jvmMain/kotlin/org/example/project/ui/admincatalog/PartTypeCatalogScreen.kt`
- [X] T014 [US1] Connect the `Tipi parte` admin destination to the secondary navigator in `composeApp/src/jvmMain/kotlin/org/example/project/ui/AppScreen.kt`

**Checkpoint**: User Story 1 should be functional and independently testable

---

## Phase 4: User Story 2 - Consultare lo schema settimanale delle parti (Priority: P1)

**Goal**: permettere all'amministratore di consultare gli schemi settimanali del catalogo con indice settimane e dettaglio ordinato delle parti.

**Independent Test**: aprire `Schemi settimanali`, selezionare una settimana e verificare che il dettaglio mostri le parti previste in ordine corretto.

### Tests for User Story 2

- [X] T015 [P] [US2] Add ViewModel tests for weekly schema loading, selection, empty and error states in `composeApp/src/jvmTest/kotlin/org/example/project/ui/admincatalog/WeeklySchemaCatalogViewModelTest.kt`
- [X] T016 [P] [US2] Add UI tests for weekly schema selection and ordered detail rendering in `composeApp/src/jvmTest/kotlin/org/example/project/ui/admincatalog/WeeklySchemaCatalogScreenTest.kt`

### Implementation for User Story 2

- [X] T017 [P] [US2] Create weekly schema projections that join schema rows with part type metadata in `composeApp/src/jvmMain/kotlin/org/example/project/ui/admincatalog/WeeklySchemaCatalogModels.kt`
- [X] T018 [US2] Implement the weekly schema catalog ViewModel using `SchemaTemplateStore.listAll()` and `PartTypeStore.allWithStatus()` in `composeApp/src/jvmMain/kotlin/org/example/project/ui/admincatalog/WeeklySchemaCatalogViewModel.kt`
- [X] T019 [US2] Implement the read-only weekly schema screen in `composeApp/src/jvmMain/kotlin/org/example/project/ui/admincatalog/WeeklySchemaCatalogScreen.kt`
- [X] T020 [US2] Connect the `Schemi settimanali` admin destination to the secondary navigator in `composeApp/src/jvmMain/kotlin/org/example/project/ui/AppScreen.kt`

**Checkpoint**: User Story 2 should be functional and independently testable

---

## Phase 5: User Story 3 - Accedere a strumenti admin secondari senza appesantire la navigazione principale (Priority: P2)

**Goal**: consolidare l'esperienza di navigazione admin secondaria affinche' `Diagnostica`, `Tipi parte` e `Schemi settimanali` convivano fuori dai tab top-level.

**Independent Test**: partendo dall'app aperta, raggiungere entrambe le nuove schermate admin in non piu' di 2 passaggi senza introdurre nuovi tab principali e con sezione attiva evidente.

### Tests for User Story 3

- [X] T021 [P] [US3] Add UI tests that verify no new top-level tabs are introduced and admin navigation remains secondary in `composeApp/src/jvmTest/kotlin/org/example/project/ui/AdminSecondaryNavigationTest.kt`
- [X] T022 [P] [US3] Add UI tests for switching between `Diagnostica`, `Tipi parte` and `Schemi settimanali` with correct active state in `composeApp/src/jvmTest/kotlin/org/example/project/ui/AdminSecondaryNavigationStateTest.kt`

### Implementation for User Story 3

- [X] T023 [US3] Refine admin secondary navigation labels, active-state styling and entry interaction in `composeApp/src/jvmMain/kotlin/org/example/project/ui/AppScreen.kt`
- [X] T024 [US3] Integrate diagnostics and new admin catalog screens into a coherent secondary navigation shell in `composeApp/src/jvmMain/kotlin/org/example/project/ui/admincatalog/AdminToolsScreen.kt`
- [X] T025 [US3] Add read-only guidance text and section-level empty/error/loading copy across admin tools in `composeApp/src/jvmMain/kotlin/org/example/project/ui/admincatalog/AdminCatalogComponents.kt`

**Checkpoint**: All user stories should now be independently functional

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: rifinire l'esperienza complessiva e validare il feature slice end-to-end.

- [X] T026 [P] Run quickstart validation scenarios and update any mismatches in `specs/008-admin-part-catalog/quickstart.md`
- [X] T027 [P] Review AGENTS context and keep only relevant admin catalog notes in `AGENTS.md`
- [ ] T028 Run full automated verification for the feature with `./gradlew test` from repository root in `C:/Users/fabio/dev_windows/ministero-del-regno-planner`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1: Setup**: no dependencies
- **Phase 2: Foundational**: depends on Phase 1 and blocks all user stories
- **Phase 3: US1**: depends on Phase 2
- **Phase 4: US2**: depends on Phase 2
- **Phase 5: US3**: depends on completion of US1 and US2 because it consolidates the shared secondary navigation around both new screens
- **Phase 6: Polish**: depends on all desired user stories being complete

### User Story Dependencies

- **US1**: can start after Foundational; no dependency on other stories
- **US2**: can start after Foundational; no dependency on US1
- **US3**: depends on the existence of both new admin destinations and therefore follows US1 + US2

### Within Each User Story

- Tests should be written first and fail before implementation
- Projection/state models before ViewModel
- ViewModel before screen wiring
- Screen implementation before global navigation hookup

### Parallel Opportunities

- `T005` and `T006` can run in parallel after `T004`
- `T009` and `T010` can run in parallel
- `T011` can run in parallel with test authoring, then `T012` and `T013` proceed serially
- `T015` and `T016` can run in parallel
- `T017` can run in parallel with test authoring, then `T018` and `T019` proceed serially
- `T021` and `T022` can run in parallel once US1 and US2 are implemented
- `T026` and `T027` can run in parallel in polish phase

---

## Parallel Example: User Story 1

```bash
# Parallel test work for US1
Task: "Add ViewModel tests for loading/content/empty/error states of the part type catalog in composeApp/src/jvmTest/kotlin/org/example/project/ui/admincatalog/PartTypeCatalogViewModelTest.kt"
Task: "Add UI tests for part type selection and visible read-only detail panel in composeApp/src/jvmTest/kotlin/org/example/project/ui/admincatalog/PartTypeCatalogScreenTest.kt"

# Parallel prep for US1
Task: "Create part type catalog projections and mappers in composeApp/src/jvmMain/kotlin/org/example/project/ui/admincatalog/PartTypeCatalogModels.kt"
```

---

## Parallel Example: User Story 2

```bash
# Parallel test work for US2
Task: "Add ViewModel tests for weekly schema loading, selection, empty and error states in composeApp/src/jvmTest/kotlin/org/example/project/ui/admincatalog/WeeklySchemaCatalogViewModelTest.kt"
Task: "Add UI tests for weekly schema selection and ordered detail rendering in composeApp/src/jvmTest/kotlin/org/example/project/ui/admincatalog/WeeklySchemaCatalogScreenTest.kt"

# Parallel prep for US2
Task: "Create weekly schema projections that join schema rows with part type metadata in composeApp/src/jvmMain/kotlin/org/example/project/ui/admincatalog/WeeklySchemaCatalogModels.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Setup
2. Complete Foundational
3. Complete US1
4. Validate `Tipi parte` as standalone MVP

### Incremental Delivery

1. Deliver `Tipi parte`
2. Deliver `Schemi settimanali`
3. Consolidate admin secondary navigation with `Diagnostica`

### Parallel Team Strategy

1. One developer completes Setup + Foundational
2. Then:
   - Developer A: US1
   - Developer B: US2
3. After both complete, one developer finalizes US3 and polish

---

## Notes

- All tasks follow the required checklist format
- Exact file paths are included in every task
- US1 and US2 are independently testable increments
- US3 is intentionally last because it consolidates the shared admin navigation around the two delivered screens

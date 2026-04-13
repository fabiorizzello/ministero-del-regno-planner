# Implementation Plan: Catalogo Admin Tipi Parte e Schemi

**Branch**: `main` | **Date**: 2026-04-11 | **Spec**: [spec.md](C:/Users/fabio/dev_windows/ministero-del-regno-planner/specs/008-admin-part-catalog/spec.md)
**Input**: Feature specification from `/specs/008-admin-part-catalog/spec.md`

## Summary

Introdurre una navigazione amministrativa secondaria, separata dai tab top-level, che permetta di consultare in sola lettura due nuovi strumenti desktop: il catalogo dei tipi di parte e gli schemi settimanali del catalogo. L'approccio UX adotta un pattern data-dense con selezione attiva evidente e pannello di dettaglio contestuale, coerente con la Diagnostica ma senza assorbire queste schermate nella navigazione primaria.

## Technical Context

**Language/Version**: Kotlin 2.3.0 (KMP JVM, toolchain 17)  
**Primary Dependencies**: Compose Multiplatform 1.10.0, Material3, Coroutines 1.10.2, Voyager Navigator 1.0.0, Koin 3.5.6  
**Storage**: SQLite via SQLDelight con store esistenti `PartTypeStore` e `SchemaTemplateStore`  
**Testing**: Kotlin Test `jvmTest`, Compose UI test APIs, test ViewModel con store fake/mock  
**Target Platform**: Desktop JVM (Windows-first, Compose Desktop)  
**Project Type**: Desktop application in repository singolo con modulo `composeApp`  
**Performance Goals**: rendering fluido a 60 fps, letture locali < 100 ms, navigazione admin in massimo 2 passaggi  
**Constraints**: sola lettura admin, nessun nuovo tab top-level, stati loading/error/empty espliciti, italiano hardcoded, tema unico chiaro, riuso design system esistente  
**Scale/Scope**: 2 nuove schermate admin secondarie, 1 navigatore admin secondario, integrazione con dati catalogo esistenti

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Pre-Phase 0 Gate Review

- **I. Vertical Slices + DDD**: PASS
  - La feature resta nel perimetro UI/application e legge store esistenti senza spostare logica di dominio fuori dalle feature catalogo.
- **II. Test-Driven Quality**: PASS
  - Il piano include test ViewModel e test UI non banali per stati, selezione e navigazione admin secondaria.
- **III. UX Consistency**: PASS
  - Gli output di design impongono pattern coerente con Diagnostica, stati espliciti e indicazione visiva della sezione attiva.
- **IV. Performance by Design**: PASS
  - Tutte le letture sono locali e a bassa cardinalita'; il piano evita fetch ridondanti e predilige stato UI esplicito.
- **V. Beautiful UI**: PASS
  - La soluzione riusa Material 3, tema light esistente e un layout data-dense intenzionale, senza introdurre tab primari o UI placeholder.

## Project Structure

### Documentation (this feature)

```text
specs/008-admin-part-catalog/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── admin-navigation-contract.md
│   └── admin-readonly-catalog-contract.md
└── tasks.md
```

### Source Code (repository root)

```text
composeApp/
└── src/
    ├── jvmMain/
    │   └── kotlin/org/example/project/
    │       ├── feature/
    │       │   ├── weeklyparts/
    │       │   │   └── application/
    │       │   └── schemas/
    │       │       └── application/
    │       └── ui/
    │           ├── AppScreen.kt
    │           ├── diagnostics/
    │           └── [new admin catalog package]
    └── jvmTest/
        └── kotlin/org/example/project/
            ├── ui/
            └── feature/

specs/
└── 008-admin-part-catalog/
```

**Structure Decision**: mantenere l'architettura desktop esistente. La lettura dei dati resta appoggiata a `PartTypeStore` e `SchemaTemplateStore`; la nuova responsabilita' viene introdotta in un package UI/admin dedicato e in un piccolo boundary di navigazione secondaria all'interno di `AppScreen.kt`.

## Phase 0: Research Output

- Consolidato in: [research.md](C:/Users/fabio/dev_windows/ministero-del-regno-planner/specs/008-admin-part-catalog/research.md)
- Tutti i punti di chiarimento del contesto tecnico e UX sono risolti senza `NEEDS CLARIFICATION`.

## Phase 1: Design Output

- Data model: [data-model.md](C:/Users/fabio/dev_windows/ministero-del-regno-planner/specs/008-admin-part-catalog/data-model.md)
- Contracts: [contracts/](C:/Users/fabio/dev_windows/ministero-del-regno-planner/specs/008-admin-part-catalog/contracts)
- Quickstart: [quickstart.md](C:/Users/fabio/dev_windows/ministero-del-regno-planner/specs/008-admin-part-catalog/quickstart.md)
- Agent context update: `.specify/scripts/powershell/update-agent-context.ps1 -AgentType codex`

### Post-Phase 1 Gate Re-Check

- **I. Vertical Slices + DDD**: PASS
  - Il design usa projection UI read-only e navigator dedicato; nessuna logica business nuova nel layer UI.
- **II. Test-Driven Quality**: PASS
  - Contratti e quickstart richiedono test su navigazione, stati di caricamento e rendering dei dettagli.
- **III. UX Consistency**: PASS
  - I contratti definiscono stessa grammatica visuale per selection state, empty/error/loading e accesso admin secondario.
- **IV. Performance by Design**: PASS
  - Il design prevede fetch locali singoli per schermata e stato immutable nel ViewModel.
- **V. Beautiful UI**: PASS
  - Il design resta light-only, Material 3, con pattern data-dense + drill-down coerente con la richiesta e con l'app.

## Complexity Tracking

Nessuna violazione costituzionale da giustificare. Nota operativa: l'implementazione deve avvenire su feature branch anche se questo planning e' stato prodotto partendo da `main`.

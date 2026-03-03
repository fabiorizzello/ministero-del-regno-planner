# Implementation Plan: Allineamento UI/UX Sketch Applicazione

**Branch**: `[001-align-sketch-ui]` | **Date**: 2026-02-27 | **Spec**: [/home/fabio/IdeaProjects/efficaci-nel-ministero/specs/001-align-sketch-ui/spec.md](/home/fabio/IdeaProjects/efficaci-nel-ministero/specs/001-align-sketch-ui/spec.md)
**Input**: Feature specification from `/specs/001-align-sketch-ui/spec.md`

## Summary

Allineare l'intera applicazione desktop al reference `docs/sketches/workspace-reference-board-modes.html`, mantenendo invariata la logica funzionale esistente. Il piano adotta un design system condiviso (token + componenti semantici), integra le impostazioni assegnatore dentro Programma, standardizza la top bar (drag su aree non interattive + doppio click maximize/restore), elimina la sezione Impostazioni dalla top bar e applica coerenza visiva su Programma, Proclamatori e Diagnostica.

## Technical Context

**Language/Version**: Kotlin 2.3.0 (KMP JVM, toolchain 17)  
**Primary Dependencies**: Compose Multiplatform 1.10.0, Material3, Coroutines 1.10.2, SQLDelight 2.1.0, Koin 3.5.6, Voyager Navigator 1.0.0  
**Storage**: SQLite via SQLDelight (`MinisteroDatabase.sq`)  
**Testing**: Kotlin Test (`commonTest`/`jvmTest`), Compose UI test APIs, desktop integration/manual validation for window behavior  
**Target Platform**: Desktop JVM (Compose Desktop, Windows distribution)  
**Project Type**: Desktop application (single repository, `composeApp` module)  
**Performance Goals**: Startup < 3s; UI fluida 60 fps; read DB < 100ms; write DB < 200ms; UI non bloccata durante operazioni asincrone  
**Constraints**: Shared design system obbligatorio; gestione esplicita loading/error/empty su ogni schermata; top bar con drag/double-click solo su aree non interattive; accessibilita minima 48dp e contentDescription; app in italiano; tema singolo chiaro (dark mode fuori scope)  
**Scale/Scope**: 3 sezioni top-level (`Programma`, `Proclamatori`, `Diagnostica`), restyle cross-app + chrome finestra + preservazione completa delle operazioni esistenti di Programma

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Pre-Phase 0 Gate Review

- **I. Vertical Slices + DDD**: PASS
  - Feature concentrata su UI/chrome; nessuna nuova logica dominio prevista fuori slice.
- **II. Test-Driven Quality**: PASS
  - Piano include test interazione/UI e regressione funzionale con strategia red-green-refactor.
- **III. UX Consistency**: PASS
  - Piano impone token/componenti condivisi e stati loading/error/empty espliciti su tutte le schermate principali.
- **IV. Performance by Design**: PASS
  - Obiettivi performance esplicitati e verifiche di non-regressione incluse.
- **V. Beautiful UI**: PASS
  - Material3 mantenuto con personalizzazione via design system; enforcement tema singolo chiaro.

### Post-Phase 1 Gate Re-Check

- **I. Vertical Slices + DDD**: PASS (artefatti di design non introducono coupling cross-feature)
- **II. Test-Driven Quality**: PASS (quickstart e research definiscono strategia testabile e misurabile)
- **III. UX Consistency**: PASS (contratti UI e data model formalizzano stati e componenti condivisi)
- **IV. Performance by Design**: PASS (niente nuove dipendenze pesanti; criteri di verifica espliciti)
- **V. Beautiful UI**: PASS (design allineato allo sketch reference con policy light-only)

## Project Structure

### Documentation (this feature)

```text
specs/001-align-sketch-ui/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── ui-navigation-contract.md
│   └── window-chrome-contract.md
└── tasks.md
```

### Source Code (repository root)

```text
composeApp/
├── src/
│   ├── commonMain/
│   │   ├── kotlin/org/example/project/
│   │   └── sqldelight/org/example/project/db/
│   ├── jvmMain/
│   │   ├── kotlin/org/example/project/
│   │   │   ├── core/
│   │   │   └── ui/
│   │   └── composeResources/
│   └── jvmTest/kotlin/org/example/project/
└── build.gradle.kts

docs/
└── sketches/

specs/
└── 001-align-sketch-ui/
```

**Structure Decision**: mantenere la struttura KMP/Compose esistente; introdurre componenti design-system e contratti UI dentro l'area `ui/` senza creare nuovi moduli.

## Phase 0: Research Output

- Consolidato in: [/home/fabio/IdeaProjects/efficaci-nel-ministero/specs/001-align-sketch-ui/research.md](/home/fabio/IdeaProjects/efficaci-nel-ministero/specs/001-align-sketch-ui/research.md)
- Tutti i punti di chiarimento tecnici del piano sono risolti.

## Phase 1: Design Output

- Data model: [/home/fabio/IdeaProjects/efficaci-nel-ministero/specs/001-align-sketch-ui/data-model.md](/home/fabio/IdeaProjects/efficaci-nel-ministero/specs/001-align-sketch-ui/data-model.md)
- Contracts: `/specs/001-align-sketch-ui/contracts/`
- Quickstart: [/home/fabio/IdeaProjects/efficaci-nel-ministero/specs/001-align-sketch-ui/quickstart.md](/home/fabio/IdeaProjects/efficaci-nel-ministero/specs/001-align-sketch-ui/quickstart.md)
- Agent context update: `.specify/scripts/bash/update-agent-context.sh codex`

## Complexity Tracking

Nessuna violazione costituzionale da giustificare in planning.

# Implementation Plan: Stabilizzazione UX Programma e Studenti

**Branch**: `main` | **Date**: 2026-04-18 | **Spec**: [spec.md](C:/Users/fabio/dev_windows/ministero-del-regno-planner/specs/009-stabilizza-ux-programma/spec.md)
**Input**: Feature specification from `/specs/009-stabilizza-ux-programma/spec.md`

## Summary

Correggere tre aree ad alto attrito dell'app desktop: aggiornamento coerente dei suggerimenti nella modale di assegnazione quando cambia il filtro di riposo, preservazione delle assegnazioni ancora valide durante le modifiche alle parti di settimana, e rifiniture UX/testuali nella sezione studenti e nel workspace programma. L'approccio mantiene l'architettura a vertical slice esistente, rafforza i boundary di dominio gia' presenti per settimana/assegnazioni e allinea le superfici UI a pattern desktop stabili di ricerca, paginazione, toolbar e stati espliciti.

## Technical Context

**Language/Version**: Kotlin 2.3.10 su JVM desktop con toolchain Java 21  
**Primary Dependencies**: Compose Multiplatform 1.10.1, Material3, Coroutines 1.10.2, Arrow 2.2.1.1, SQLDelight 2.2.1, Voyager 1.0.0, Koin 4.1.1  
**Storage**: SQLite via SQLDelight (`MinisteroDatabase.sq`) con store/query esistenti per persone, assegnazioni, programmi e settimane  
**Testing**: Kotlin Test + MockK + kotlinx-coroutines-test in `composeApp/src/jvmTest`, con test ViewModel/domain/use case e test UI Compose dove il comportamento visuale e' non banale  
**Target Platform**: Desktop JVM Compose, con uso principale su Windows  
**Project Type**: Applicazione desktop monorepo con modulo `composeApp` e vertical slice feature-based  
**Performance Goals**: refresh suggerimenti e ricerca percepiti come immediati, nessun blocco UI, scorrimento/paginazione fluidi, nessuna regressione rispetto ai target costituzionali di letture locali <100 ms e interazioni a 60 fps  
**Constraints**: italiano hardcoded, light theme unico, riuso design system esistente `workspaceSketch`, no logica business in infrastructure/UI, stati loading/error/empty espliciti, accessibilita' semantica minima e target interattivi coerenti  
**Scale/Scope**: fix mirati su `ui/workspace`, `ui/proclamatori`, feature `assignments`, `programs`, `weeklyparts`, piu' test mirati su regressioni di refresh, preservazione assegnazioni e comportamento UI

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Pre-Phase 0 Gate Review

- **I. Vertical Slices + DDD**: PASS
  - I fix ricadono nei boundary gia' esistenti: refresh suggestions in `assignments`, preservazione assegnazioni in `programs/weeklyparts`, affinamenti visuali in `ui/workspace` e `ui/proclamatori`, senza spostare regole di dominio in Composable o store SQL.
- **II. Test-Driven Quality**: PASS
  - Il piano prevede test su ViewModel/use case e regressioni UI; le parti sensibili sono tutte verificabili con `jvmTest`.
- **III. UX Consistency**: PASS
  - Tutti gli interventi mantengono design system condiviso, terminologia italiana, stati espliciti e comportamenti coerenti fra modale assegnazione e schermata studenti.
- **IV. Performance by Design**: PASS
  - Il piano evita recomputazioni inconsapevoli e refresh ridondanti; la fuzzy search sara' locale e deterministica su dataset limitato.
- **V. Beautiful UI**: PASS
  - Nessun re-skin arbitrario; solo raffinamento di layout, etichette e micro-interazioni gia' presenti nel linguaggio visuale corrente.

## Project Structure

### Documentation (this feature)

```text
specs/009-stabilizza-ux-programma/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── students-list-ui-contract.md
│   └── workspace-assignment-ui-contract.md
└── tasks.md
```

### Source Code (repository root)

```text
composeApp/
└── src/
    ├── commonMain/
    │   └── sqldelight/org/example/project/db/
    │       └── MinisteroDatabase.sq
    ├── jvmMain/
    │   └── kotlin/org/example/project/
    │       ├── feature/
    │       │   ├── assignments/
    │       │   │   ├── application/
    │       │   │   ├── domain/
    │       │   │   └── infrastructure/
    │       │   ├── people/
    │       │   │   ├── application/
    │       │   │   └── infrastructure/
    │       │   ├── programs/
    │       │   │   ├── application/
    │       │   │   └── infrastructure/
    │       │   └── weeklyparts/
    │       │       ├── application/
    │       │       └── domain/
    │       └── ui/
    │           ├── proclamatori/
    │           └── workspace/
    └── jvmTest/
        └── kotlin/org/example/project/
            ├── feature/
            └── ui/

specs/
└── 009-stabilizza-ux-programma/
```

**Structure Decision**: mantenere i fix dentro le slice esistenti. Le regole di preservazione assegnazioni restano in `feature/programs` e `feature/weeklyparts`; ranking/refresh suggerimenti restano in `feature/assignments` e `ui/workspace`; la fuzzy search e i fix di tabella/card restano in `feature/people` e `ui/proclamatori`.

## Phase 0: Research Output

- Consolidato in: [research.md](C:/Users/fabio/dev_windows/ministero-del-regno-planner/specs/009-stabilizza-ux-programma/research.md)
- Nessun `NEEDS CLARIFICATION` residuo nel contesto tecnico o UX.

## Phase 1: Design Output

- Data model: [data-model.md](C:/Users/fabio/dev_windows/ministero-del-regno-planner/specs/009-stabilizza-ux-programma/data-model.md)
- Contracts: [contracts/](C:/Users/fabio/dev_windows/ministero-del-regno-planner/specs/009-stabilizza-ux-programma/contracts)
- Quickstart: [quickstart.md](C:/Users/fabio/dev_windows/ministero-del-regno-planner/specs/009-stabilizza-ux-programma/quickstart.md)
- Agent context update: `.specify/scripts/powershell/update-agent-context.ps1 -AgentType codex`

### Post-Phase 1 Gate Re-Check

- **I. Vertical Slices + DDD**: PASS
  - Il design introduce projection e stati UI dedicati, ma lascia le decisioni di compatibilita' assegnazioni dentro il dominio/use case.
- **II. Test-Driven Quality**: PASS
  - I contratti includono scenari testabili per refresh modale, ricerca fuzzy, scroll reset e preservazione selettiva delle assegnazioni.
- **III. UX Consistency**: PASS
  - I contratti fissano terminologia italiana, empty state espliciti e toolbar/card stabili nel linguaggio UI esistente.
- **IV. Performance by Design**: PASS
  - Il ranking fuzzy e' limitato a liste locali gia' disponibili in memoria o caricate una sola volta; nessuna nuova dipendenza remota.
- **V. Beautiful UI**: PASS
  - Le raccomandazioni `ui-ux-pro-max` vengono applicate come micro-interazioni sobrie, lista-detail/search-first e stabilita' visiva, senza deviare dal design system attuale.

## Complexity Tracking

Nessuna violazione costituzionale da giustificare. Nota operativa: il planning e' stato generato partendo da `main`; l'implementazione dovra' comunque avvenire su feature branch.

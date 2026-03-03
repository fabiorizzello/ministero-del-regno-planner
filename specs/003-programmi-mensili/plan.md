# Implementation Plan: Programmi Mensili

**Branch**: `003-programmi-mensili` | **Date**: 2026-02-26 | **Spec**: [/home/fabio/IdeaProjects/efficaci-nel-ministero/specs/003-programmi-mensili/spec.md](/home/fabio/IdeaProjects/efficaci-nel-ministero/specs/003-programmi-mensili/spec.md)
**Input**: Feature specification from `/specs/003-programmi-mensili/spec.md`

## Summary

Allineare la feature Programma Mensile al comportamento target già chiarito:
- gestione mesi `current + futures` (max 2 futuri) con creazione mese target esplicita,
- snapshot di selezione con fallback robusto e memoria di sessione,
- eliminazione consentita su corrente/futuro (mai passato) con conferma impatto,
- aggiornamento schemi senza preview separata e badge solo su delta reale,
- policy notifiche: success solo quando utile, error sempre visibili.

Approccio tecnico: estendere i contratti applicativi esistenti nella vertical slice `feature/programs` e riflettere i cambiamenti in `ui/workspace` mantenendo SQLDelight, Coroutines, Compose e Koin già presenti.

## Technical Context

**Language/Version**: Kotlin 2.3.0 (KMP target JVM, toolchain 17)  
**Primary Dependencies**: Compose Multiplatform 1.10.0, Material3, SQLDelight 2.1.0, Coroutines 1.10.2, Arrow 2.1.2, Koin 3.5.6, Multiplatform Settings 1.1.1  
**Storage**: SQLite via SQLDelight (`composeApp/src/commonMain/sqldelight/org/example/project/db/MinisteroDatabase.sq`)  
**Testing**: Kotlin Test (`jvmTest`), Compose UI tests dove necessario  
**Target Platform**: Desktop JVM (Compose Multiplatform)  
**Project Type**: Single desktop application (feature-based vertical slices)  
**Performance Goals**: startup < 3s, UI fluida 60fps, query DB read < 100ms / write < 200ms  
**Constraints**: App italiana; single-user operativo; max 2 mesi futuri; regole contiguità mesi; operazioni on-flight senza draft locale; success toast ridotti, error toast sempre presenti  
**Scale/Scope**: Pianificazione mensile per un singolo utente locale, dati su pochi mesi attivi e settimane correlate

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Pre-Phase 0 Gate

- **I. Vertical Slices + DDD**: PASS  
  Cambi previsti confinati a `feature/programs`, `feature/assignments`, `feature/schemas`, `ui/workspace` con adapter infrastrutturali sottili.
- **II. Test-Driven Quality**: PASS (vincolato a implementazione)  
  Pianificati test su use case e comportamento UI workspace per regressioni su selezione mesi, delete e notifiche.
- **III. UX Consistency**: PASS  
  Regole uniformi su feedback, stati empty/error/loading e coerenza chip/azioni mese.
- **IV. Performance by Design**: PASS  
  Nessuna operazione bloccante nuova; query e aggiornamenti restano su percorsi SQLDelight esistenti.
- **V. Beautiful UI**: PASS  
  Evoluzione visuale nel perimetro design system già in uso (Material3 + semantic colors).

**Gate Result (pre-research)**: PASS

## Project Structure

### Documentation (this feature)

```text
specs/003-programmi-mensili/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── program-workspace-contract.md
└── tasks.md
```

### Source Code (repository root)

```text
composeApp/
├── src/commonMain/sqldelight/org/example/project/db/MinisteroDatabase.sq
├── src/jvmMain/kotlin/org/example/project/feature/programs/
│   ├── application/
│   ├── domain/
│   ├── infrastructure/
│   └── di/
├── src/jvmMain/kotlin/org/example/project/feature/assignments/application/
├── src/jvmMain/kotlin/org/example/project/feature/schemas/application/
├── src/jvmMain/kotlin/org/example/project/ui/workspace/
└── src/jvmTest/kotlin/org/example/project/
    ├── ui/workspace/
    └── ui/components/
```

**Structure Decision**: Mantenere la struttura esistente a vertical slices feature-first. Le modifiche saranno concentrate su contratti applicativi programmi e orchestrazione UI workspace, evitando nuovi moduli. Dove necessario verranno aggiunti nuovi test package in `src/jvmTest` mantenendo il naming per feature/schermata.

## Phase 0: Research Plan

1. Verificare pattern migliori per snapshot selezione multi-mese (`current + futures`) mantenendo compatibilità con stato UI.
2. Definire strategia per creazione mese target con vincoli contiguità + max future senza duplicare logica in UI.
3. Definire criterio affidabile di “template modificato” per evitare falsi positivi del badge.
4. Formalizzare policy notifiche (`success` condizionale, `error` sempre) coerente con feedback già presenti.

Output: `/home/fabio/IdeaProjects/efficaci-nel-ministero/specs/003-programmi-mensili/research.md`

## Phase 1: Design Plan

1. Modellare entità/relazioni e transizioni aggiornate (`ProgramSelectionSnapshot`, creazione mese target, policy feedback).
2. Definire contratti applicativi/UI per i casi d'uso principali del workspace programmi.
3. Redigere quickstart con scenari di verifica end-to-end allineati alle user story P1/P2.
4. Aggiornare contesto agente con stack realmente usato.

Output:
- `/home/fabio/IdeaProjects/efficaci-nel-ministero/specs/003-programmi-mensili/data-model.md`
- `/home/fabio/IdeaProjects/efficaci-nel-ministero/specs/003-programmi-mensili/contracts/program-workspace-contract.md`
- `/home/fabio/IdeaProjects/efficaci-nel-ministero/specs/003-programmi-mensili/quickstart.md`

## Post-Design Constitution Check

- **I. Vertical Slices + DDD**: PASS (design confinato alle slice esistenti, nessuna logica business in adapter SQL/UI helper)
- **II. Test-Driven Quality**: PASS (quickstart e contratti includono scenari testabili per use case/UI)
- **III. UX Consistency**: PASS (contratto feedback unico per toast/banner e selezione mesi)
- **IV. Performance by Design**: PASS (nessun requisito che impone query extra non necessarie o polling)
- **V. Beautiful UI**: PASS (linee guida di coerenza visiva incluse nei contratti UI)

**Gate Result (post-design)**: PASS

## Complexity Tracking

Nessuna violazione costituzionale prevista; se emergono compromessi in implementazione verranno tracciati in questa sezione con rationale esplicito.

# Research: Allineamento UI/UX Sketch Applicazione

## Decision 1: Contratto top bar per drag e doppio click

- **Decision**:
  - Drag finestra (`primary press + move`) attivo su tutta la superficie non interattiva della top bar.
  - Doppio click (`primary double-click`) su area non interattiva: toggle `Maximized <-> Floating`.
  - Controlli interattivi (tab, pulsanti, icone, menu, trigger) esclusi da drag/toggle.
  - Implementazione in `jvmMain` con area drag dedicata separata dai controlli interattivi.

- **Rationale**:
  - `undecorated = true` richiede comportamento finestra custom e prevedibile.
  - `WindowDraggableArea` su contenitori con controlli embedded e fragile; separare drag-surface e controlli riduce conflitti input.
  - Allinea FR-005/006/007/007a e gli edge case della spec.

- **Alternatives considered**:
  - Unica `WindowDraggableArea` che include anche controlli: scartata per click/drag accidentali.
  - Drag solo su blocco brand: scartata, non copre la superficie non interattiva richiesta.
  - Gestione AWT low-level completa: scartata per complessita/manutenibilita.

## Decision 2: Strategia restyle cross-app con design system condiviso

- **Decision**:
  - Restyle unico per Programma, Proclamatori, Diagnostica tramite token centrali e componenti semantici condivisi (`WorkspacePanel`, `WorkspaceHeader`, `WorkspaceActionButton`, `WorkspaceStatePane`).
  - Material3 mantenuto come base; personalizzazione solo tramite wrapper condivisi (no styling ad-hoc per schermata).
  - Introduzione modello stato uniforme per ogni schermata: `Loading`, `Error`, `Empty`, `Content`.

- **Rationale**:
  - Riduce drift visivo e soddisfa requirement di coerenza cross-screen.
  - Soddisfa costituzione su design system condiviso e gestione esplicita degli stati UI.
  - Mantiene basso il rischio regressione comportamentale preservando la logica esistente.

- **Alternatives considered**:
  - Restyle per singola schermata senza componenti comuni: scartata, crea inconsistenze.
  - Rimpiazzare completamente Material3 con toolkit custom: scartata, costo elevato e non necessario.
  - Restyle solo Programma: scartata, non soddisfa scope richiesto.

## Decision 3: Policy tema e conformita costituzionale

- **Decision**:
  - Applicare policy **light-theme only** per questa feature, rimuovendo toggle/persistenza dark mode dal chrome applicativo.

- **Rationale**:
  - La costituzione vigente definisce tema singolo chiaro come vincolo non negoziabile.
  - Evita conflitti tra spec esecutiva e governance tecnica durante implementazione/QA.

- **Alternatives considered**:
  - Mantenere dark mode in parallelo: scartata, in conflitto con costituzione.
  - Posticipare decisione al coding: scartata, aumenta rework e ambiguita test.

## Decision 4: Strategia test e validazione qualità

- **Decision**:
  - Strategia a 4 livelli:
    - unit test (regole pure di mapping/interazione);
    - UI tests Compose (`jvmTest`) su navigazione/top bar/stati;
    - integrazione desktop headful per interazioni finestra reali;
    - validazione manuale tracciata (rubrica visuale + smoke + survey interna).
  - Mapping esplicito degli SC-001..SC-006 in check misurabili.

- **Rationale**:
  - Feature ad alto rischio UX/interazione: solo test funzionali tradizionali non bastano.
  - Copre sia regressioni deterministiche che comportamento window-manager reale.
  - Allinea i quality gate alla costituzione (`./gradlew test`, coverage minima dominio/use case).

- **Alternatives considered**:
  - Solo validazione manuale: scartata, troppo soggettiva.
  - Solo screenshot/golden: scartata, insufficiente su drag/double-click.
  - Solo end-to-end desktop: scartata, piu fragile e meno diagnostica.

# Data Model: Allineamento UI/UX Sketch Applicazione

## Overview

Questa feature non introduce nuove entita di dominio persistente. Definisce un modello contrattuale UI/chrome per garantire coerenza visuale, interazioni finestra e accessibilita delle azioni esistenti.

## Entities

### 1) AppChromeBar

| Field | Type | Description | Validation |
|-------|------|-------------|------------|
| title | String | Titolo applicazione mostrato nella top bar | Non vuoto, italiano |
| sections | List<ChromeSection> | Sezioni di navigazione | Esattamente `Programma`, `Proclamatori`, `Diagnostica`; nessuna `Impostazioni` |
| interactiveTargets | List<InteractiveTarget> | Elementi cliccabili in top bar | Devono essere esclusi da drag/toggle finestra |
| draggableZones | List<DragZone> | Aree non interattive della top bar | Copertura uniforme della superficie non interattiva |
| windowActions | WindowActions | Minimize, maximize/restore, close | Doppio click non esegue minimize |

**State transitions**

- `WindowPlacement.Floating` --(double click zona non interattiva)--> `WindowPlacement.Maximized`
- `WindowPlacement.Maximized` --(double click zona non interattiva)--> `WindowPlacement.Floating`

### 2) WorkspacePanel

| Field | Type | Description | Validation |
|-------|------|-------------|------------|
| panelId | Enum (`Months`, `WeeksBoard`, `ActionsFeed`) | Identificativo pannello area Programma | Unico per layout a tre colonne |
| title | String | Header pannello | Non vuoto |
| state | UiState | Stato visuale | Uno tra `Loading`, `Error`, `Empty`, `Content` |
| actions | List<PanelAction> | Azioni disponibili nel pannello | Azioni principali sempre raggiungibili nelle risoluzioni target |

**State transitions**

- `Loading -> Content` quando dati disponibili
- `Loading -> Empty` quando nessun dato
- `Loading -> Error` su errore caricamento
- `Error -> Loading` su retry

### 3) ProgramActionBlock

| Field | Type | Description | Validation |
|-------|------|-------------|------------|
| autoAssignEnabled | Boolean | Disponibilita azione autoassegna | Disabilitata durante operazione in corso |
| printEnabled | Boolean | Disponibilita azione stampa | Disabilitata durante operazione in corso |
| coverageAssignedSlots | Int | Slot assegnati | `>= 0` |
| coverageTotalSlots | Int | Slot totali | `>= 0` e `>= coverageAssignedSlots` |
| unresolvedSlots | Int | Slot non assegnati | `>= 0` |
| feedEntries | List<ActivityFeedEntry> | Eventi recenti | Ordinamento temporale decrescente |
| assignmentSettings | AssignmentSettingsBlock | Impostazioni assegnatore inline | Sempre accessibile da Programma |

### 4) AssignmentSettingsBlock

| Field | Type | Description | Validation |
|-------|------|-------------|------------|
| strictCooldown | Boolean | Applica cooldown rigido | Default conforme settings correnti |
| leadCooldownWeeks | Int | Cooldown conduzione in settimane | Intero `>= 0` |
| assistCooldownWeeks | Int | Cooldown assistenza in settimane | Intero `>= 0` |
| isSaving | Boolean | Stato salvataggio | Se `true`, comandi mutazione disabilitati |

**State transitions**

- `Idle -> Dirty` su modifica campo
- `Dirty -> Saving` su conferma salvataggio
- `Saving -> Idle` su successo
- `Saving -> Dirty` su errore salvataggio

### 5) ActivityFeedEntry

| Field | Type | Description | Validation |
|-------|------|-------------|------------|
| id | String | Identificativo evento | Unico nel feed |
| timestamp | Instant | Data/ora evento | Sempre valorizzato |
| severity | Enum (`Info`, `Warning`, `Error`) | Severita evento | Deve guidare stile visuale coerente |
| message | String | Testo principale evento | Italiano, non vuoto |
| details | String? | Dettagli opzionali | Facoltativo |

## Relationships

- `AppChromeBar` governa la navigazione verso le schermate top-level.
- `WorkspacePanel(WeeksBoard)` e `WorkspacePanel(ActionsFeed)` partecipano al layout centrale Programma.
- `ProgramActionBlock` contiene `AssignmentSettingsBlock` e una collezione `ActivityFeedEntry`.

## Invariants

- La top bar non espone tab `Impostazioni` finche esistono solo impostazioni assegnazione.
- Il punto unico di accesso alle impostazioni assegnatore e il pannello destro di Programma.
- Le aree top bar interattive non devono mai attivare drag/toggle finestra.
- Le tre schermate top-level devono condividere token e componenti visuali comuni.

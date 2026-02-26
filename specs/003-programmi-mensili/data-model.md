# Data Model: Programmi Mensili

## Overview

Il modello mantiene la pianificazione mensile con settimane, parti e assegnazioni.
La feature estende il comportamento da un singolo futuro a una lista di futuri (max 2)
nel contratto applicativo/UI, senza cambiare il vincolo DB `UNIQUE(year, month)`.

## Entities

### ProgramMonth

- **Purpose**: rappresenta un programma mensile pianificabile.
- **Fields**:
  - `id: ProgramMonthId` (UUID)
  - `year: Int`
  - `month: Int` (1..12)
  - `startDate: LocalDate` (primo lunedì del mese)
  - `endDate: LocalDate` (domenica successiva a fine mese)
  - `templateAppliedAt: LocalDateTime?`
  - `createdAt: LocalDateTime`
- **Persistence**: tabella `program_monthly`.
- **Constraints**:
  - `UNIQUE(year, month)`
  - timeline derivata tramite `timelineStatus(referenceDate): PAST|CURRENT|FUTURE`

### ProgramSelectionSnapshot

- **Purpose**: contratto di lettura per il workspace programmi.
- **Fields**:
  - `current: ProgramMonth?`
  - `futures: List<ProgramMonth>` (ordinati cronologicamente, max 2)
- **Persistence**: nessuna (DTO applicativo).

### WeekPlan

- **Purpose**: settimana pianificata appartenente a un programma.
- **Fields**:
  - `id: WeekPlanId`
  - `weekStartDate: LocalDate` (lunedì)
  - `programId: ProgramMonthId?`
  - `status: WeekPlanStatus` (`ACTIVE|SKIPPED`)
  - `parts: List<WeeklyPart>`
- **Persistence**: tabella `week_plan`.
- **Constraints**:
  - `week_start_date` univoco
  - FK `program_id -> program_monthly(id)`

### WeeklyPart

- **Purpose**: parte settimanale concreta su cui fare assegnazioni.
- **Fields**:
  - `id: WeeklyPartId`
  - `weekPlanId: WeekPlanId`
  - `partType: PartType`
  - `sortOrder: Int`
- **Persistence**: tabella `weekly_part`.

### Assignment

- **Purpose**: assegnazione persona-slot per una parte.
- **Fields**:
  - `id: AssignmentId`
  - `weeklyPartId: WeeklyPartId`
  - `personId: PersonId`
  - `slot: Int`
- **Persistence**: tabella `assignment`.
- **Constraints**:
  - `UNIQUE(weekly_part_id, slot)`
  - cascade delete su `weekly_part`

### SchemaRefreshReport

- **Purpose**: riepilogo aggiornamento programma da schemi.
- **Fields**:
  - `weeksUpdated: Int`
  - `assignmentsPreserved: Int`
  - `assignmentsRemoved: Int`
- **Persistence**: nessuna (DTO applicativo).

## Relationships

- `ProgramMonth (1) -> (N) WeekPlan`
- `WeekPlan (1) -> (N) WeeklyPart`
- `WeeklyPart (1) -> (N) Assignment`
- `PartType` e `schema_week_part` guidano la rigenerazione/refresh delle `WeeklyPart`

## Domain Rules & Validation

1. **Cardinalità futuri**: massimo 2 `ProgramMonth` con status `FUTURE`.
2. **Contiguità creazione**:
   - non si possono saltare mesi;
   - eccezione iniziale: se il corrente manca, prima creazione ammessa solo `corrente+1`;
   - dopo `corrente+1`, il corrente resta creabile finché è ancora in corso.
3. **Delete policy**:
   - consentito su `CURRENT`/`FUTURE`;
   - vietato su `PAST`.
4. **Refresh policy**:
   - aggiornare solo settimane `weekStartDate >= referenceDate`;
   - preservare assegnazioni su chiave `(partTypeId, sortOrder)`.
5. **Selection fallback**:
   - mantenere mese selezionato se esiste;
   - fallback a `current`, altrimenti futuro più vicino;
   - se manca `current` e non c’è selezione valida, scegliere futuro più vicino.
6. **Feedback policy**:
   - success feedback solo se necessario;
   - error feedback sempre visibile.

## State Transitions

### Program timeline

- `FUTURE -> CURRENT -> PAST` (derivata dal calendario, non mutazione persistita).

### WeekPlan status

- `ACTIVE <-> SKIPPED` (azione utente su settimana non passata).

### Workspace selection

- `selectedProgramId = null` (inizio)  
  -> `current.id` se presente  
  -> altrimenti `futures.first().id`.

## Scale Assumptions

- Utente operativo singolo.
- Dataset piccolo/medio: pochi mesi attivi e settimane correlate.
- Operazioni transazionali locali su SQLite (nessun coordinamento distribuito).

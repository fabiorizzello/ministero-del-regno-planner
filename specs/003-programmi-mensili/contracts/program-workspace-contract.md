# Contract: Program Workspace Application Interfaces

## Scope

Contratti applicativi tra UI workspace e layer use case della feature `programs`.
Il progetto non espone API HTTP pubbliche: il contratto è interno all'app desktop.

## 1) Load Active Programs

### Use case
`CaricaProgrammiAttiviUseCase.invoke(referenceDate: LocalDate): ProgramSelectionSnapshot`

### Input contract

| Field | Type | Required | Rules |
|---|---|---|---|
| `referenceDate` | `LocalDate` | Yes | Data di riferimento per timeline `PAST/CURRENT/FUTURE` |

### Output contract

| Field | Type | Required | Rules |
|---|---|---|---|
| `current` | `ProgramMonth?` | Yes | Al massimo un programma `CURRENT` |
| `futures` | `List<ProgramMonth>` | Yes | Ordinati cronologicamente, cardinalità `0..2` |

### Invariants

- Mai più di un `current`.
- Nessun `future` fuori ordinamento cronologico.

## 2) Create Program For Target Month

### Use case
`CreaProgrammaMeseTargetUseCase.invoke(targetYear: Int, targetMonth: Int, referenceDate: LocalDate): Either<DomainError, ProgramMonth>`

### Input contract

| Field | Type | Required | Rules |
|---|---|---|---|
| `targetYear` | `Int` | Yes | Mese target esplicito |
| `targetMonth` | `Int` | Yes | Valori 1..12 |
| `referenceDate` | `LocalDate` | Yes | Regole contiguità/finestra basate su data corrente |

### Error contract (`DomainError.Validation`)

- mese già esistente,
- superato limite di 2 futuri,
- violazione contiguità (incluso tentativo `corrente+2` senza `corrente+1` quando manca current),
- creazione mese corrente non più consentita se ormai passato.

## 3) Delete Current/Future Program

### Use case
`EliminaProgrammaUseCase.invoke(programId: ProgramMonthId, referenceDate: LocalDate): Either<DomainError, Unit>`

### Guardrails

- `PAST` non eliminabile.
- `CURRENT` e `FUTURE` eliminabili in transazione.
- rimozione in cascata: `week_plan -> weekly_part -> assignment`.

### UI precondition contract

Prompt di conferma obbligatorio con almeno:
- numero settimane coinvolte,
- numero assegnazioni coinvolte.

## 4) Refresh Program From Schemas

### Use case
`AggiornaProgrammaDaSchemiUseCase.invoke(programId: String, referenceDate: LocalDate, dryRun: Boolean): Either<DomainError, SchemaRefreshReport>`

### Contract rules

- Workspace usa solo `dryRun=false` (nessun pulsante preview separato).
- `dryRun=true` resta supportato per diagnosi/test.
- Aggiornamento solo su settimane `weekStartDate >= referenceDate`.
- Preservazione assegnazioni per chiave `(partTypeId, sortOrder)`.

### Output contract

| Field | Type | Meaning |
|---|---|---|
| `weeksUpdated` | `Int` | Settimane rielaborate |
| `assignmentsPreserved` | `Int` | Assegnazioni rimappate su nuove parti |
| `assignmentsRemoved` | `Int` | Assegnazioni non più compatibili |

## 5) UI Feedback Contract

### Success feedback

- Mostrare notifica success **solo** se:
  - non c'è riscontro grafico immediato, oppure
  - il messaggio aggiunge informazioni rilevanti.

### Error feedback

- Mostrare sempre feedback errore esplicito (toast/banner equivalente).

## 6) Selection Memory Contract (Session)

- Durante switch e reload dati, mantenere `selectedProgramId` se ancora valido.
- Se non valido: fallback a `current`, altrimenti futuro più vicino.
- Se `current` assente e nessuna selezione valida: autoselezionare il futuro più vicino.
- Persistenza richiesta: solo sessione UI corrente (non cross-restart obbligatoria).

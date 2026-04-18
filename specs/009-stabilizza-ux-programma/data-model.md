# Data Model: Stabilizzazione UX Programma e Studenti

## Overview

La feature non introduce nuove entita' persistite. Definisce stati UI, projection e criteri derivati per:
- suggerimenti nella modale di assegnazione;
- continuita' logica delle parti settimanali quando una settimana viene modificata;
- ranking fuzzy e presentazione della lista studenti.

## Entities

### AssignmentSuggestionCriteria

**Purpose**: rappresenta i criteri correnti che determinano i suggerimenti visibili nella modale di assegnazione.

**Fields**:
- `weekStartDate`
- `weeklyPartId`
- `slot`
- `searchTerm`
- `strictRestEnabled`

**Relationships**:
- Alimenta la costruzione della `SuggestionListState`

**Validation Rules**:
- `weekStartDate`, `weeklyPartId` e `slot` devono essere sempre valorizzati quando la modale e' aperta
- `searchTerm` puo' essere vuoto ma non null

### SuggestionListState

**Purpose**: stato derivato della modale di assegnazione durante caricamento e selezione proclamatori.

**Fields**:
- `criteria: AssignmentSuggestionCriteria`
- `suggestions: List<SuggestedPersonView>`
- `isLoading`
- `notice`
- `emptyStateVisible`

**State Transitions**:
- `Closed -> Loading`
- `Loading -> Content`
- `Loading -> Empty`
- `Loading -> Error`
- `Content -> Loading` quando cambia un criterio rilevante

### SuggestedPersonView

**Purpose**: projection UI di un proclamatore suggerito per assegnazione.

**Fields**:
- `personId`
- `fullName`
- `distanceScore` (derivato per ranking ricerca quando la ricerca e' attiva)
- `assignmentScore` (score dominio gia' usato dal ranking suggerimenti)
- `inRestPeriod`
- `restWeeksRemaining`
- `sexMismatch`
- `canAssist`

**Relationships**:
- Deriva da `SuggestedProclamatore`

**Validation Rules**:
- L'ordinamento finale deve essere stabile e deterministico
- L'etichetta utente deve usare "riposo" invece di "cooldown"

### WeekPartContinuityKey

**Purpose**: chiave logica che identifica una parte di settimana al di la' dell'ID tecnico rigenerato.

**Fields**:
- `partTypeId`
- `occurrenceIndex`
- `slot`

**Relationships**:
- Collega `ExistingWeekAssignment` a `UpdatedWeekPart`

**Validation Rules**:
- `occurrenceIndex` deve essere calcolato nell'ordine visibile della settimana
- La chiave deve essere identica tra snapshot pre-modifica e struttura post-modifica per preservare l'assegnazione

### ExistingWeekAssignment

**Purpose**: rappresenta un'assegnazione gia' presente prima della modifica della settimana.

**Fields**:
- `assignmentId`
- `weeklyPartId`
- `personId`
- `slot`
- `continuityKey: WeekPartContinuityKey`

**Relationships**:
- Puo' essere preservata o rimossa confrontandola con le `UpdatedWeekPart`

### UpdatedWeekPart

**Purpose**: rappresenta una parte nella nuova configurazione della settimana.

**Fields**:
- `weeklyPartId`
- `partTypeId`
- `sortOrder`
- `peopleCount`
- `continuityKeys: List<WeekPartContinuityKey>`

**Relationships**:
- Riceve zero o piu' assegnazioni preservate

**Validation Rules**:
- Ogni slot della parte genera una continuita' distinta
- Le parti nuove non possono ricevere assegnazioni preservate se non trovano un match di chiave

### FuzzySearchMatch

**Purpose**: rappresenta il risultato intermedio del ranking fuzzy per persone/studenti.

**Fields**:
- `personId`
- `normalizedQuery`
- `normalizedCandidate`
- `distance`
- `matchedOn` (`firstName`, `lastName`, `fullName`)
- `exactPrefix`

**Relationships**:
- Alimenta la costruzione della lista ordinata in studenti e modale assegnazione

**Validation Rules**:
- Distanza minore = risultato migliore
- A parita' di distanza, il ranking deve usare fallback alfabetico stabile

### StudentsListViewState

**Purpose**: stato UI della sezione studenti nelle modalita' tabella/card.

**Fields**:
- `searchTerm`
- `filteredItems`
- `pageIndex`
- `pageSize`
- `visiblePageItems`
- `scrollResetToken`
- `capabilitySummaryById`
- `viewMode`

**State Transitions**:
- `Loading -> Content`
- `Content -> Content(search changed)`
- `Content -> Content(page changed + scrollResetToken incremented)`
- `Content -> Empty`

### StudentCardActionBar

**Purpose**: contratto visuale della toolbar azioni nelle card studenti.

**Fields**:
- `minHeight`
- `isVisible`
- `actions: [edit, delete]`
- `alignment`

**Validation Rules**:
- L'altezza della toolbar non dipende dal numero di capability
- I pulsanti restano sempre cliccabili e leggibili

## Derived Rules

- Il cambio del filtro di riposo e il cambio della ricerca nella modale di assegnazione invalidano entrambi la stessa lista suggerimenti.
- La fuzzy search deve poter essere condivisa da studenti e modale senza duplicare regole di ranking divergenti.
- Le assegnazioni vengono rimosse solo quando la `WeekPartContinuityKey` non trova piu' una destinazione valida nella nuova settimana.
- Il cambio pagina nella schermata studenti deve produrre un segnale esplicito di reset scroll, non dipendere da effetti collaterali del rerender.

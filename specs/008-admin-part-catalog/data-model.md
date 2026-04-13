# Data Model: Catalogo Admin Tipi Parte e Schemi

## Overview

La feature non introduce nuove entita' persistite. Definisce projection e stato UI read-only per rappresentare dati gia' disponibili nel catalogo tipi parte e negli schemi settimanali.

## Entities

### AdminCatalogSection

**Purpose**: rappresenta la destinazione attiva nel navigatore admin secondario.

**Fields**:
- `id`: identificatore logico della sezione (`diagnostica`, `tipi-parte`, `schemi-settimanali`)
- `label`: etichetta italiana mostrata nella navigazione secondaria
- `isActive`: indicatore derivato per styling e accessibilita'

**Relationships**:
- Seleziona una tra piu' schermate admin secondarie

**Validation Rules**:
- Deve esistere una sola sezione attiva per volta

### PartTypeCatalogItem

**Purpose**: proiezione read-only di un tipo di parte nell'elenco catalogo.

**Fields**:
- `id`
- `code`
- `label`
- `peopleCount`
- `sexRule`
- `fixed`
- `active`
- `revisionId` (opzionale, solo se disponibile dal boundary)

**Relationships**:
- Puo' essere selezionato per mostrare un `PartTypeCatalogDetail`

**Validation Rules**:
- `code` e `label` devono essere renderizzabili senza perdita di significato
- `active` deve essere sempre esplicitamente visibile in UI

### PartTypeCatalogDetail

**Purpose**: dettaglio contestuale del tipo di parte selezionato.

**Fields**:
- tutti i campi di `PartTypeCatalogItem`
- `selectionContextLabel` (testo UI per confermare l'elemento attivo)
- `readonlyNotice` (testo UI che esplicita l'assenza di azioni mutanti)

**Relationships**:
- Deriva da un singolo `PartTypeCatalogItem`

### WeeklySchemaListItem

**Purpose**: rappresentazione sintetica di una settimana di schema nel pannello di navigazione.

**Fields**:
- `weekStartDate`
- `partsCount`
- `summaryLabel`
- `isSelected`

**Relationships**:
- Puo' essere selezionato per mostrare un `WeeklySchemaDetail`

**Validation Rules**:
- L'ordinamento e' cronologico stabile
- La selezione e' unica

### WeeklySchemaDetail

**Purpose**: dettaglio della settimana selezionata con elenco ordinato delle parti previste.

**Fields**:
- `weekStartDate`
- `rows: List<WeeklySchemaRow>`
- `emptyReason` (opzionale, quando non ci sono parti)
- `readonlyNotice`

**Relationships**:
- Contiene una lista ordinata di `WeeklySchemaRow`

### WeeklySchemaRow

**Purpose**: singola riga del dettaglio schema settimanale.

**Fields**:
- `position`
- `partTypeId`
- `partTypeCode`
- `partTypeLabel`
- `peopleCount`
- `sexRule`
- `isFixed`

**Relationships**:
- Referenzia un `PartTypeCatalogItem` esistente

**Validation Rules**:
- `position` preserva l'ordine ufficiale dello schema
- Ogni riga deve identificare chiaramente il tipo di parte corrispondente

## UI State Aggregates

### PartTypeCatalogUiState

**Fields**:
- `isLoading`
- `items: List<PartTypeCatalogItem>`
- `selectedId`
- `selectedDetail`
- `notice`
- `emptyStateVisible`

**State Transitions**:
- `Loading -> Content`
- `Loading -> Error`
- `Content -> Content(selected item changed)`
- `Content -> Empty`

### WeeklySchemaCatalogUiState

**Fields**:
- `isLoading`
- `weeks: List<WeeklySchemaListItem>`
- `selectedWeekStartDate`
- `selectedDetail`
- `notice`
- `emptyStateVisible`

**State Transitions**:
- `Loading -> Content`
- `Loading -> Error`
- `Content -> Content(selected week changed)`
- `Content -> Empty`

## Derived Rules

- Le schermate sono sempre in modalita' read-only: nessuna transizione di stato include editing o persistenza.
- La sezione attiva nel navigatore admin secondario e l'elemento selezionato nell'elenco devono avere segnali visivi distinti.
- Gli stati `empty` ed `error` sono mutuamente esclusivi e sostituiscono il pannello contenutistico, non si sommano ad esso.

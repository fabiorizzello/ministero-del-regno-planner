# UI Contract: Cataloghi Admin Read-Only

## Purpose

Definire il comportamento condiviso delle schermate `Tipi parte` e `Schemi settimanali` come strumenti di consultazione amministrativa in sola lettura.

## Shared Contract

### Layout

- Ogni schermata usa un pattern lista + dettaglio oppure drill-down leggero equivalente.
- L'area di selezione deve permettere scansione rapida di piu' record.
- Il dettaglio deve aggiornarsi rispetto all'elemento selezionato senza dialog obbligatori.

### State Handling

- Stati obbligatori:
  - `loading`
  - `empty`
  - `error`
  - `content`
- Gli stati devono essere espliciti e in italiano.

### Read-Only Rules

- Nessuna CTA di:
  - creazione
  - modifica
  - eliminazione
  - riordino
  - salvataggio
- L'interfaccia deve comunicare chiaramente che si tratta di consultazione.

### Selection Rules

- L'item selezionato e' sempre visivamente evidente.
- La selezione e' unica.
- In assenza di dati non viene mostrato un dettaglio ambiguo.

### Content Minimums

#### Tipi parte

- codice
- etichetta
- persone richieste
- regola di composizione
- stato attivo/disattivo

#### Schemi settimanali

- settimana selezionata
- elenco ordinato delle parti
- riferimento leggibile al tipo di parte per ogni riga

## Validation Points

- Nessuna affordance di editing presente
- Empty/error/loading corretti in entrambe le schermate
- Dettaglio coerente con l'item selezionato

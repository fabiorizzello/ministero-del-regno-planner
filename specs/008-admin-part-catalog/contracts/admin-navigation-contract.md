# UI Contract: Navigazione Admin Secondaria

## Purpose

Definire il comportamento della navigazione amministrativa secondaria che raggruppa strumenti non top-level come `Diagnostica`, `Tipi parte` e `Schemi settimanali`.

## Contract

### Entry Point

- L'accesso avviene da un punto di ingresso amministrativo secondario gia' coerente con la toolbar utility esistente.
- L'ingresso non crea nuovi tab top-level nella barra centrale principale.

### Navigation Items

- Le destinazioni minime supportate sono:
  - `Diagnostica`
  - `Tipi parte`
  - `Schemi settimanali`
- L'item attivo deve essere distinguibile con segnale visivo persistente.
- Il cambio destinazione deve essere immediato e non distruggere la comprensione del contesto.

### Behaviour Rules

- Deve esistere una sola sezione admin attiva alla volta.
- Il passaggio tra sezioni admin non deve alterare i tab top-level attivi.
- Ogni sezione deve esporre titolo coerente, contenuto principale e propri stati loading/error/empty.

### Non-Goals

- Nessun supporto a create/edit/delete da questa navigazione.
- Nessuna promozione di strumenti admin a navigazione primaria.

## Validation Points

- Sezione attiva sempre evidente
- Nessun tab top-level aggiunto
- Passaggio prevedibile tra le tre destinazioni admin

---
name: compose-table-standard
description: Standardizzare viste tabellari in Kotlin Compose Desktop per questo progetto. Usare questa skill quando si crea o rifattorizza una schermata con elenco entita', ricerca, righe con azioni, paginazione o stato vuoto, per mantenere griglia righe/colonne, spaziature compatte e comportamento coerente.
---

# Compose Table Standard

## Obiettivo

Applicare una resa tabellare coerente e compatta tramite i componenti condivisi:
- `composeApp/src/jvmMain/kotlin/org/example/project/ui/components/TableStandard.kt`
- `composeApp/src/jvmMain/kotlin/org/example/project/ui/proclamatori/ProclamatoriScreen.kt` come riferimento pratico.

## Workflow

1. Definire colonne con `TableColumnSpec(title, weight)` in un elenco stabile (preferibilmente top-level `private val`).
2. Renderizzare intestazione con `StandardTableHeader(columns, lineColor)`.
3. Racchiudere area scrollabile in `StandardTableViewport(...)` con bordo esterno.
4. Renderizzare celle riga con `Modifier.standardTableCell(lineColor)` evitando `border + padding` duplicati.
5. Gestire stato vuoto con `StandardTableEmptyRow(message, totalWeight, lineColor)`.
6. Mantenere spaziatura compatta: padding cella default del componente standard.
7. Tenere scrollbar verticale visibile quando il contenuto supera l'altezza disponibile.

## Regole

- Usare un unico colore linee derivato dal tema (`MaterialTheme.colorScheme.outline`) per header, righe e bordo tabella.
- Mostrare sempre la tabella anche se vuota.
- Usare pesi colonna costanti per mantenere allineamento tra header e righe.
- Non introdurre varianti locali di tabella se i componenti standard coprono il caso.
- Applicare questo standard a nuove feature UI prima di introdurre componenti custom.

## Checklist Finale

- Header e righe perfettamente allineate.
- Griglia righe/colonne sempre visibile.
- Stato vuoto presente nella stessa struttura tabellare.
- Nessuna duplicazione locale di modifier cella o header table.

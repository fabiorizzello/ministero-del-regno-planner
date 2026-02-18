# Proclamatori Table UI Modernization — Design

**Data:** 2026-02-18

## Obiettivo

Modernizzare lo stile visivo della tabella Proclamatori per uniformarlo al nuovo look di WeeklyPartsScreen, mantenendo la struttura tabellare (sorting, paginazione, selezione batch).

## Cosa cambia

| Elemento | Prima | Dopo |
|---|---|---|
| Bordi cella | `standardTableCell` con bordo 0.6dp | Niente bordi — zebra striping + padding |
| Header tabella | `StandardTableHeader` con bordi | Row custom con sfondo `surfaceVariant.copy(alpha = 0.5f)`, clickable per sort |
| Viewport | `StandardTableViewport` con bordo esterno 0.8dp | Card con `RoundedCornerShape(12.dp)`, elevazione 2dp |
| Righe dati | Tutte bianche, bordi per cella | Zebra striping: righe alterne `surfaceVariant.copy(alpha = 0.3f)` |
| Empty row | `StandardTableEmptyRow` con bordo | Messaggio centrato senza bordo |
| Toolbar selezione | Card senza shape/elevation espliciti | Card con `RoundedCornerShape(12.dp)`, elevazione 1dp |

## Cosa NON cambia

- Sorting cliccando le intestazioni (indicatori `^`/`v`)
- Checkbox selezione riga + selezione pagina
- Batch actions (attiva/disattiva/rimuovi)
- Switch attivo/disattivo per riga
- Pulsanti Modifica/Rimuovi per riga
- Paginazione (Prec/Succ)
- Scrollbar verticale
- Form nuovo/modifica proclamatore
- ViewModel e logica applicativa

## File coinvolti

| File | Modifica |
|---|---|
| `ProclamatoriComponents.kt` | Riscrivere `ProclamatoriElencoContent` e `TableDataRow` senza `StandardTable*` |
| `ProclamatoriUiSupport.kt` | Rimuovere `proclamatoriTableColumns`, `proclamatoriTableTotalWeight`, `tableScrollbarPadding` (non piu' necessari) |

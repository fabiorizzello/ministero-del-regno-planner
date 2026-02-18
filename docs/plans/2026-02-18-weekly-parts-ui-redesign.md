# WeeklyPartsScreen UI Redesign — Design

**Data:** 2026-02-18

## Obiettivo

Migliorare l'estetica di WeeklyPartsScreen e sostituire le frecce su/giu' con drag & drop per il riordinamento delle parti.

## Scelte approvate

| Decisione | Scelta |
|---|---|
| D&D library | `sh.calvin.reorderable:reorderable:3.0.0` (Compose Multiplatform) |
| Stile visivo | Look moderno/distinto (non allineato a Proclamatori) |
| Colonna azioni | Rimuovere frecce su/giu', tenere icona X per rimuovere |

## Design visivo

### Layout

```
┌─────────────────────────────────────────────────┐
│  [Chip settimana]   ◀  15 Dic - 21 Dic 2025  ▶ │
│                                       [Aggiorna] │
├─────────────────────────────────────────────────┤
│  ⚠ Feedback Banner (se presente)                │
├─────────────────────────────────────────────────┤
│                                                 │
│  ≡  3. Lettura della Bibbia      👤1  M    ✕   │
│  ≡  4. Facciamo un video         👤2  MF   ✕   │  ← zebra striping
│  ≡  5. Primo discorso            👤1  M    ✕   │
│  ≡  6. Studio biblico            👤2  MF   ✕   │
│                                                 │
│  [+ Aggiungi parte ▾]                           │
└─────────────────────────────────────────────────┘
```

### Elementi stilistici

1. **Card con elevazione leggera** attorno alla tabella — `Card(elevation = 2.dp)` con `shape = RoundedCornerShape(12.dp)`.
2. **Zebra striping** — righe pari con `surfaceVariant.copy(alpha = 0.3f)` per leggibilita'.
3. **Niente bordi cella** — usare solo padding e sfondi alternati. Piu' moderno della griglia a bordi.
4. **Drag handle** (`Icons.Rounded.DragHandle`) a sinistra di ogni riga non-fixed. Appare con alpha ridotta, piena al hover/drag.
5. **Icona X** per rimuovere — a destra della riga, solo parti non-fixed.
6. **Elevazione durante drag** — la riga "sollevata" con `shadowElevation = 4.dp` animata.
7. **Righe fixed** (es. cantico, preghiera) — nessun drag handle, nessuna X. Sfondo leggermente diverso per distinguerle.

### Chip indicatore settimana

Stessi colori attuali:
- CORRENTE: `Color(0xFF4CAF50)` (verde)
- FUTURA: `Color(0xFF2196F3)` (blu)
- PASSATA: `Color(0xFF9E9E9E)` (grigio)

Chip con `AssistChip`, `containerColor` al 15% alpha.

## Libreria D&D

**`sh.calvin.reorderable` v3.0.0**

Dependency in `libs.versions.toml`:
```toml
[versions]
reorderable = "3.0.0"

[libraries]
reorderable = { module = "sh.calvin.reorderable:reorderable", version.ref = "reorderable" }
```

API pattern:
```kotlin
val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
    // update list in-memory, then persist via movePart()
}

ReorderableItem(reorderableState, key = part.id.value) { isDragging ->
    // row content with Modifier.draggableHandle() on the drag icon
}
```

## Cosa cambia

| File | Modifica |
|---|---|
| `libs.versions.toml` | Aggiungere `reorderable` version + library |
| `build.gradle.kts` | Aggiungere `implementation(libs.reorderable)` |
| `WeeklyPartsScreen.kt` | Nuovo layout: Card wrapper, zebra rows, D&D, drag handle, rimuovi frecce |
| `TableStandard.kt` | Nessuna modifica (WeeklyParts non usera' piu' StandardTableHeader/Viewport) |

## Cosa NON cambia

- `WeeklyPartsViewModel.kt` — `movePart(from, to)`, `addPart()`, `removePart()` restano identici
- `OverwriteConfirmDialog` — invariato
- Navigazione frecce avanti/indietro — controllata da `isLoading`
- Logica `enabled = !state.isImporting` — gia' implementata
- `ProclamatoriScreen` e altri — fuori scope

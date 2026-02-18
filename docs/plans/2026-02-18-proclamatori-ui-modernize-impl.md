# Proclamatori Table UI Modernization — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace StandardTable grid in ProclamatoriComponents with modern card+zebra style matching WeeklyPartsScreen.

**Architecture:** Replace `StandardTableHeader`/`StandardTableViewport`/`standardTableCell` usage with custom composables: a header Row with surfaceVariant background (clickable for sort), and data rows with zebra striping inside a rounded Card. Remove unused table column definitions from support file.

**Tech Stack:** Compose Multiplatform 1.10.0, Material3.

**Design doc:** `docs/plans/2026-02-18-proclamatori-ui-modernize-design.md`

---

### Task 1: Modernize ProclamatoriElencoContent and TableDataRow

**Files:**
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/proclamatori/ProclamatoriComponents.kt`

**Changes:**

1. Remove imports: `StandardTableEmptyRow`, `StandardTableHeader`, `StandardTableViewport`, `standardTableCell`, `TableColumnSpec`
2. Add imports: `background`, `RoundedCornerShape`, `CardDefaults`, `clickable`, `PointerIcon`, `pointerHoverIcon`
3. In `ProclamatoriElencoContent`:
   - Remove `tableLineColor` variable
   - Add `RoundedCornerShape(12.dp)` and `elevation = 1.dp` to selection toolbar Card
   - Replace `StandardTableHeader` + `StandardTableViewport` + `StandardTableEmptyRow` with custom header Row and LazyColumn inside a Card with `RoundedCornerShape(12.dp)`, elevation 2dp
   - Header: Row with `surfaceVariant.copy(alpha = 0.5f)` background, weighted columns matching current weights, clickable for sort, sort indicators
   - Data rows: zebra striping via index, no cell borders
   - Empty state: centered Text without borders
4. Rewrite `TableDataRow`:
   - Remove `lineColor` parameter
   - Add `backgroundColor: Color` parameter
   - Remove `standardTableCell` from all cells
   - Use `background(backgroundColor)` on the Row
   - Keep same layout weights and content

**Verify:** `./gradlew compileKotlinJvm`

**Commit:** `feat(ui): modernize Proclamatori table with zebra striping and rounded cards`

---

### Task 2: Clean up ProclamatoriUiSupport

**Files:**
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/proclamatori/ProclamatoriUiSupport.kt`

**Changes:**
- Remove `proclamatoriTableColumns` (no longer used — header is now custom)
- Remove `proclamatoriTableTotalWeight` (no longer used — no StandardTableEmptyRow)
- Remove `tableScrollbarPadding` (replaced by inline value)
- Remove `TableColumnSpec` import

**Verify:** `./gradlew compileKotlinJvm`

**Commit:** `chore: remove unused table column definitions from ProclamatoriUiSupport`

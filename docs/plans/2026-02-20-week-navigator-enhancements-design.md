# Week Navigator Enhancements — Design

## Goal

Two enhancements to the shared `WeekNavigator` component:
1. Colored dots on prev/next arrows showing adjacent week completion status
2. "Go to current week" button visible when not on current week

## Feature 1: Adjacent Week Completion Dots

A small dot (8dp) next to each navigation arrow indicates the completion status of the adjacent week.

**Colors (reuse existing `SemanticColors`):**
- Green — all slots assigned (`assigned == total && total > 0`)
- Amber — partially assigned (`0 < assigned < total`)
- Grey — empty or not configured (`assigned == 0` or `weekPlan == null`)

Always shown for all adjacent weeks (past, current, future). No exceptions.

**Data flow:**
- `AssignmentsViewModel` loads completion data for `monday - 7` and `monday + 7` on every week change, using existing `CaricaSettimanaUseCase` + `CaricaAssegnazioniUseCase`
- New enum: `WeekCompletionStatus { COMPLETE, PARTIAL, EMPTY }`
- `WeekNavigator` receives optional `prevWeekStatus: WeekCompletionStatus?` and `nextWeekStatus: WeekCompletionStatus?` (null = don't show dot)

**Scope:** Dots shown in Assignments screen. WeeklyParts screen passes null (no dots) unless we decide otherwise later.

## Feature 2: "Go to Current Week" Button

An `IconButton` with `Icons.Filled.Today` in the center of `WeekNavigator`, between the date text and the week indicator chip.

- Visible only when `weekIndicator != CORRENTE`
- Calls new `SharedWeekState.navigateToCurrentWeek()` method
- Since `WeekNavigator` is shared, the button appears in both Assignments and WeeklyParts screens

## Files Touched

| File | Change |
|---|---|
| `WeekNavigator.kt` | Add dot rendering, Today button, new params |
| `SharedWeekState.kt` | Add `navigateToCurrentWeek()` |
| `AssignmentsViewModel.kt` | Load adjacent week completion status |
| `AssignmentsScreen.kt` | Pass new params to `WeekNavigator` |
| `WeeklyPartsScreen.kt` | Pass `onNavigateToCurrentWeek` + null for dots |

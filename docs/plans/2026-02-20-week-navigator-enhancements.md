# Week Navigator Enhancements — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add adjacent-week completion dots and a "go to current week" button to the shared WeekNavigator component.

**Files touched:** 5 modified, 0 new

---

## Context

The `WeekNavigator` component is shared between Assignments and WeeklyParts screens. Both screens navigate weeks via `SharedWeekState`. The assignments screen already computes `assignedSlotCount`/`totalSlotCount` for the current week — we reuse the same use cases for adjacent weeks.

Design doc: `docs/plans/2026-02-20-week-navigator-enhancements-design.md`

---

## Batch 1: SharedWeekState + WeekNavigator UI

### Step 1: Add `navigateToCurrentWeek()` to SharedWeekState

**File:** `composeApp/src/jvmMain/kotlin/org/example/project/core/application/SharedWeekState.kt`

Add method after `navigateToNextWeek()`:

```kotlin
fun navigateToCurrentWeek() {
    _currentMonday.value = currentMonday()
}
```

### Step 2: Add `WeekCompletionStatus` enum and update `WeekNavigator` signature

**File:** `composeApp/src/jvmMain/kotlin/org/example/project/ui/components/WeekNavigator.kt`

Add enum before the `WeekNavigator` composable:

```kotlin
enum class WeekCompletionStatus { COMPLETE, PARTIAL, EMPTY }
```

Update `WeekNavigator` signature — add 3 new optional parameters with defaults so existing call sites don't break:

```kotlin
@Composable
fun WeekNavigator(
    monday: LocalDate,
    sunday: LocalDate,
    indicator: WeekTimeIndicator,
    enabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    prevWeekStatus: WeekCompletionStatus? = null,
    nextWeekStatus: WeekCompletionStatus? = null,
    onNavigateToCurrentWeek: (() -> Unit)? = null,
)
```

### Step 3: Add dot rendering helper and Today button inside WeekNavigator

**File:** `composeApp/src/jvmMain/kotlin/org/example/project/ui/components/WeekNavigator.kt`

Add private composable before `WeekNavigator`:

```kotlin
@Composable
private fun CompletionDot(status: WeekCompletionStatus) {
    val color = when (status) {
        WeekCompletionStatus.COMPLETE -> SemanticColors.green
        WeekCompletionStatus.PARTIAL -> SemanticColors.amber
        WeekCompletionStatus.EMPTY -> SemanticColors.grey
    }
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(color, CircleShape),
    )
}
```

New imports needed: `Box`, `background`, `size`, `CircleShape` from `androidx.compose.foundation.shape`, and `Icons.Filled.Today` from material icons.

Update the `WeekNavigator` body to:

1. Wrap the previous-arrow `IconButton` in a `Column` that stacks the dot below:

```kotlin
Column(horizontalAlignment = Alignment.CenterHorizontally) {
    IconButton(onClick = onPrevious, enabled = enabled, modifier = Modifier.handCursorOnHover()) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Settimana precedente")
    }
    if (prevWeekStatus != null) {
        CompletionDot(prevWeekStatus)
    }
}
```

2. After the `AssistChip`, conditionally show the Today button:

```kotlin
if (onNavigateToCurrentWeek != null && indicator != WeekTimeIndicator.CORRENTE) {
    Spacer(Modifier.width(spacing.sm))
    IconButton(
        onClick = onNavigateToCurrentWeek,
        modifier = Modifier.handCursorOnHover().size(32.dp),
    ) {
        Icon(
            Icons.Filled.Today,
            contentDescription = "Vai a settimana corrente",
            modifier = Modifier.size(18.dp),
        )
    }
}
```

3. Same Column wrapping for the next-arrow:

```kotlin
Column(horizontalAlignment = Alignment.CenterHorizontally) {
    IconButton(onClick = onNext, enabled = enabled, modifier = Modifier.handCursorOnHover()) {
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Settimana successiva")
    }
    if (nextWeekStatus != null) {
        CompletionDot(nextWeekStatus)
    }
}
```

### Step 4: Update WeeklyPartsScreen call site

**File:** `composeApp/src/jvmMain/kotlin/org/example/project/ui/weeklyparts/WeeklyPartsScreen.kt`

The existing call uses only the 6 original params, so with defaults it still compiles. But we want the Today button here too. Update:

```kotlin
WeekNavigator(
    monday = state.currentMonday,
    sunday = state.sundayDate,
    indicator = state.weekIndicator,
    enabled = !state.isLoading,
    onPrevious = { viewModel.navigateToPreviousWeek() },
    onNext = { viewModel.navigateToNextWeek() },
    onNavigateToCurrentWeek = { viewModel.navigateToCurrentWeek() },
)
```

Also add `navigateToCurrentWeek()` to `WeeklyPartsViewModel`:

**File:** `composeApp/src/jvmMain/kotlin/org/example/project/ui/weeklyparts/WeeklyPartsViewModel.kt`

Add after `navigateToNextWeek()`:

```kotlin
fun navigateToCurrentWeek() {
    sharedWeekState.navigateToCurrentWeek()
}
```

**Verify:** `./gradlew composeApp:compileKotlinJvm`

---

## Batch 2: Adjacent week status in AssignmentsViewModel + Screen

### Step 1: Add state fields and navigation method to AssignmentsViewModel

**File:** `composeApp/src/jvmMain/kotlin/org/example/project/ui/assignments/AssignmentsViewModel.kt`

Add to `AssignmentsUiState`:

```kotlin
val prevWeekStatus: WeekCompletionStatus? = null,
val nextWeekStatus: WeekCompletionStatus? = null,
```

Add import for `WeekCompletionStatus` from `org.example.project.ui.components.WeekCompletionStatus`.

Add method to `AssignmentsViewModel` after `navigateToNextWeek()`:

```kotlin
fun navigateToCurrentWeek() {
    sharedWeekState.navigateToCurrentWeek()
}
```

### Step 2: Compute adjacent week status in `loadWeekData()`

**File:** `composeApp/src/jvmMain/kotlin/org/example/project/ui/assignments/AssignmentsViewModel.kt`

Add private helper:

```kotlin
private fun computeCompletionStatus(weekPlan: WeekPlan?, assignments: List<AssignmentWithPerson>): WeekCompletionStatus {
    val total = weekPlan?.parts?.sumOf { it.partType.peopleCount } ?: 0
    val assigned = assignments.size
    return when {
        total == 0 || weekPlan == null -> WeekCompletionStatus.EMPTY
        assigned >= total -> WeekCompletionStatus.COMPLETE
        assigned > 0 -> WeekCompletionStatus.PARTIAL
        else -> WeekCompletionStatus.EMPTY
    }
}
```

Update `loadWeekData()` — after loading the current week's data, also load adjacent weeks:

```kotlin
private fun loadWeekData() {
    loadJob?.cancel()
    loadJob = scope.launch {
        _state.update { it.copy(isLoading = true) }
        try {
            val monday = _state.value.currentMonday
            val weekPlan = caricaSettimana(monday)
            val assignments = caricaAssegnazioni(monday)

            // Load adjacent week completion status
            val prevMonday = monday.minusWeeks(1)
            val prevPlan = caricaSettimana(prevMonday)
            val prevAssignments = caricaAssegnazioni(prevMonday)
            val prevStatus = computeCompletionStatus(prevPlan, prevAssignments)

            val nextMonday = monday.plusWeeks(1)
            val nextPlan = caricaSettimana(nextMonday)
            val nextAssignments = caricaAssegnazioni(nextMonday)
            val nextStatus = computeCompletionStatus(nextPlan, nextAssignments)

            _state.update {
                it.copy(
                    isLoading = false,
                    weekPlan = weekPlan,
                    assignments = assignments,
                    prevWeekStatus = prevStatus,
                    nextWeekStatus = nextStatus,
                )
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    isLoading = false,
                    notice = FeedbackBannerModel("Errore nel caricamento: ${e.message}", FeedbackBannerKind.ERROR),
                )
            }
        }
    }
}
```

### Step 3: Pass new params from AssignmentsScreen

**File:** `composeApp/src/jvmMain/kotlin/org/example/project/ui/assignments/AssignmentsScreen.kt`

Update `WeekNavigator` call:

```kotlin
WeekNavigator(
    monday = state.currentMonday,
    sunday = state.sundayDate,
    indicator = state.weekIndicator,
    enabled = !state.isLoading,
    onPrevious = { viewModel.navigateToPreviousWeek() },
    onNext = { viewModel.navigateToNextWeek() },
    prevWeekStatus = state.prevWeekStatus,
    nextWeekStatus = state.nextWeekStatus,
    onNavigateToCurrentWeek = { viewModel.navigateToCurrentWeek() },
)
```

**Verify:** `./gradlew composeApp:compileKotlinJvm`

---

## Verification

- `./gradlew composeApp:compileKotlinJvm` must pass after each batch
- WeekNavigator shows dots under prev/next arrows in Assignments screen
- WeekNavigator shows Today button when not on current week (both screens)
- WeeklyParts screen has no dots (default null)
- Clicking Today navigates to current week

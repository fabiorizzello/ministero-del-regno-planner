# Stability & Destructive Operations Fixes

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix 2 critical bugs, 8 important issues, and harden all destructive operations with proper confirmation dialogs and error handling.

**Architecture:** Defensive approach — UI disables controls for past weeks, use cases validate invariants, all destructive ops require confirmation, all async loads are wrapped in try/catch.

**Tech Stack:** Kotlin, Compose Desktop (Material3), SQLDelight, Arrow Either, Koin DI

---

## Task 1: Fix NPE on `assignment!!` in SlotRow lambdas

**Files:**
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/assignments/AssignmentsComponents.kt:89,100`

**Step 1: Fix the two `!!` assertions**

Replace line 89 (single-slot branch):
```kotlin
// BEFORE
onRemove = { onRemoveAssignment(assignment!!.id.value) },
// AFTER
onRemove = { assignment?.let { onRemoveAssignment(it.id.value) } },
```

Same for line 100 (multi-slot loop):
```kotlin
// BEFORE
onRemove = { onRemoveAssignment(assignment!!.id.value) },
// AFTER
onRemove = { assignment?.let { onRemoveAssignment(it.id.value) } },
```

**Step 2: Verify**

Run: `./gradlew compileKotlinJvm`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git commit -m "fix: replace assignment!! with safe call in SlotRow lambdas"
```

---

## Task 2: Add try/catch to all async load functions

**Files:**
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/assignments/AssignmentsViewModel.kt:160-168`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/weeklyparts/WeeklyPartsViewModel.kt:188-201`

**Step 1: Wrap `loadWeekData` in AssignmentsViewModel**

```kotlin
private fun loadWeekData() {
    loadJob?.cancel()
    loadJob = scope.launch {
        _state.update { it.copy(isLoading = true) }
        try {
            val weekPlan = caricaSettimana(_state.value.currentMonday)
            val assignments = caricaAssegnazioni(_state.value.currentMonday)
            _state.update { it.copy(isLoading = false, weekPlan = weekPlan, assignments = assignments) }
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

**Step 2: Wrap `loadWeek` in WeeklyPartsViewModel**

```kotlin
private fun loadWeek() {
    scope.launch {
        _state.update { it.copy(isLoading = true) }
        try {
            val weekPlan = caricaSettimana(_state.value.currentMonday)
            _state.update { it.copy(isLoading = false, weekPlan = weekPlan) }
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

**Step 3: Wrap `loadPartTypes` in WeeklyPartsViewModel**

```kotlin
private fun loadPartTypes() {
    scope.launch {
        try {
            val types = cercaTipiParte()
            _state.update { it.copy(partTypes = types) }
        } catch (_: Exception) {
            // partTypes remains empty — "Aggiungi parte" button stays hidden
        }
    }
}
```

**Step 4: Verify**

Run: `./gradlew compileKotlinJvm`

**Step 5: Commit**

```bash
git commit -m "fix: add try/catch to all async load functions to prevent infinite spinner"
```

---

## Task 3: Add SharedWeekState atomic updates

**Files:**
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/core/application/SharedWeekState.kt:16-22`

**Step 1: Use `update {}` instead of direct assignment**

```kotlin
fun navigateToPreviousWeek() {
    _currentMonday.update { it.minusWeeks(1) }
}

fun navigateToNextWeek() {
    _currentMonday.update { it.plusWeeks(1) }
}
```

Add import: `import kotlinx.coroutines.flow.update`

**Step 2: Verify**

Run: `./gradlew compileKotlinJvm`

**Step 3: Commit**

```bash
git commit -m "fix: use atomic update for SharedWeekState navigation"
```

---

## Task 4: Block mutations on past weeks

**Files:**
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/assignments/AssignmentsScreen.kt:126-140`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/weeklyparts/WeeklyPartsScreen.kt` (DraggablePartRow, add part button)
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/assignments/AssignmentsComponents.kt` (PartAssignmentCard, SlotRow)

**Step 1: Pass `readOnly` flag to PartAssignmentCard**

In `AssignmentsScreen.kt`, compute `readOnly`:
```kotlin
val readOnly = state.weekIndicator == WeekTimeIndicator.PASSATA
```

Pass to `PartAssignmentCard`:
```kotlin
PartAssignmentCard(
    part = part,
    assignments = partAssignments,
    displayNumber = part.sortOrder + 1,
    readOnly = readOnly,
    onAssignSlot = { slot -> viewModel.openPersonPicker(part.id, slot) },
    onRemoveAssignment = { id -> viewModel.removeAssignment(id) },
)
```

**Step 2: Update PartAssignmentCard signature and propagate**

In `AssignmentsComponents.kt`, add `readOnly: Boolean` parameter to `PartAssignmentCard`. Pass it to `SlotRow`.

In `SlotRow`, when `readOnly = true`:
- Don't show the "Assegna" `OutlinedButton` (empty slots show "Non assegnato" text instead)
- Don't show the close `IconButton` (assigned names are NOT clickable)
- Assigned names still visible but not interactive

```kotlin
@Composable
private fun SlotRow(
    label: String?,
    assignment: AssignmentWithPerson?,
    readOnly: Boolean,
    onAssign: () -> Unit,
    onRemove: () -> Unit,
) {
    // ... existing label code ...

    if (assignment != null) {
        Text(
            text = assignment.fullName,
            modifier = Modifier.weight(1f).then(
                if (!readOnly) Modifier.handCursorOnHover().clickable { onAssign() }
                else Modifier
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!readOnly) {
            IconButton(onClick = onRemove, modifier = Modifier.handCursorOnHover()) {
                Icon(Icons.Filled.Close, contentDescription = "Rimuovi", tint = MaterialTheme.colorScheme.error)
            }
        }
    } else {
        Spacer(Modifier.weight(1f))
        if (!readOnly) {
            OutlinedButton(onClick = onAssign, modifier = Modifier.handCursorOnHover()) {
                Text("Assegna")
            }
        } else {
            Text(
                text = "Non assegnato",
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

**Step 3: Disable schema mutations for past weeks in WeeklyPartsScreen**

In `WeeklyPartsScreen.kt`, wherever the "Aggiungi parte" dropdown and the X remove button are rendered, add `enabled = state.weekIndicator != WeekTimeIndicator.PASSATA`. The `DraggablePartRow` already has an `enabled` parameter — make sure it's `false` for past weeks. Disable drag-and-drop reorder too.

Look for:
- The `IconButton` for adding parts (filter dropdown trigger)
- `DraggablePartRow`'s `enabled` parameter
- The reorderable list's drag gesture

**Step 4: Verify**

Run: `./gradlew compileKotlinJvm`

**Step 5: Commit**

```bash
git commit -m "feat: block mutations on past weeks in both Assignments and Schema screens"
```

---

## Task 5: Strict no-duplicate person per week

**Files:**
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/feature/assignments/application/AssegnaPersonaUseCase.kt:34-36`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/feature/assignments/application/AssignmentStore.kt`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/feature/assignments/infrastructure/SqlDelightAssignmentStore.kt`
- Modify: `composeApp/src/commonMain/sqldelight/org/example/project/db/MinisteroDatabase.sq`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/feature/assignments/application/SuggerisciProclamatoriUseCase.kt:29-40`

**Step 1: Add SQL query to check person assigned in week**

In `MinisteroDatabase.sq`, add:
```sql
personAlreadyAssignedInWeek:
SELECT COUNT(*) FROM assignment a
JOIN weekly_part wp ON a.weekly_part_id = wp.id
WHERE wp.week_plan_id = ? AND a.person_id = ?;
```

**Step 2: Add method to AssignmentStore interface**

```kotlin
suspend fun isPersonAssignedInWeek(weekPlanId: WeekPlanId, personId: ProclamatoreId): Boolean
```

**Step 3: Implement in SqlDelightAssignmentStore**

```kotlin
override suspend fun isPersonAssignedInWeek(
    weekPlanId: WeekPlanId,
    personId: ProclamatoreId,
): Boolean {
    val count = database.ministeroDatabaseQueries
        .personAlreadyAssignedInWeek(weekPlanId.value, personId.value)
        .executeAsOne()
    return count > 0L
}
```

**Step 4: Update AssegnaPersonaUseCase — replace part-level check with week-level**

Replace the existing `isPersonAssignedToPart` check:
```kotlin
// BEFORE
if (assignmentStore.isPersonAssignedToPart(weeklyPartId, personId)) {
    raise(DomainError.Validation("Proclamatore gia' assegnato a questa parte"))
}

// AFTER
if (assignmentStore.isPersonAssignedInWeek(plan.id, personId)) {
    raise(DomainError.Validation("Proclamatore gia' assegnato in questa settimana"))
}
```

**Step 5: Update SuggerisciProclamatoriUseCase — filter by whole week**

Change the filter from part-level to week-level:
```kotlin
// BEFORE
val existingForPart = assignmentStore.listByWeek(plan.id)
    .filter { it.weeklyPartId == weeklyPartId }
    .map { it.personId }
    .toSet()

// AFTER
val existingForWeek = assignmentStore.listByWeek(plan.id)
    .map { it.personId }
    .toSet()
```

And update the filter:
```kotlin
passaSesso && p.id !in existingForWeek
```

**Step 6: Verify**

Run: `./gradlew compileKotlinJvm`

**Step 7: Commit**

```bash
git commit -m "feat: enforce strict one-person-per-week constraint in assignments"
```

---

## Task 6: Double-click protection on confirmAssignment

**Files:**
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/assignments/AssignmentsViewModel.kt:42,126-145`

**Step 1: Add `isAssigning` flag to UiState**

In `AssignmentsUiState`, add:
```kotlin
val isAssigning: Boolean = false,
```

**Step 2: Guard and set flag in confirmAssignment**

```kotlin
fun confirmAssignment(personId: ProclamatoreId) {
    if (_state.value.isAssigning) return
    val s = _state.value
    val weeklyPartId = s.pickerWeeklyPartId ?: return
    val slot = s.pickerSlot ?: return

    _state.update { it.copy(isAssigning = true) }
    scope.launch {
        assegnaPersona(
            weekStartDate = s.currentMonday,
            weeklyPartId = weeklyPartId,
            personId = personId,
            slot = slot,
        ).fold(
            ifLeft = { error ->
                _state.update { it.copy(isAssigning = false) }
                showError(error)
            },
            ifRight = {
                _state.update { it.copy(isAssigning = false) }
                closePersonPicker()
                loadWeekData()
            },
        )
    }
}
```

**Step 3: Disable "Assegna" buttons in picker while isAssigning**

In `PersonPickerDialog` call site (`AssignmentsScreen.kt`), the `onAssign` callback already calls `confirmAssignment`. The buttons in `SuggestionRow` should be disabled when `isAssigning`. Pass `isAssigning` to `PersonPickerDialog` and then to `SuggestionRow`:

In `SuggestionRow`, change the Button:
```kotlin
Button(
    onClick = onAssign,
    enabled = !isAssigning,
    modifier = Modifier.width(110.dp).handCursorOnHover(),
) { ... }
```

**Step 4: Verify**

Run: `./gradlew compileKotlinJvm`

**Step 5: Commit**

```bash
git commit -m "fix: add double-click protection on assignment confirmation"
```

---

## Task 7: Confirmation dialog for removing a part with assignments

**Files:**
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/weeklyparts/WeeklyPartsViewModel.kt:30-50,103-110`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/weeklyparts/WeeklyPartsScreen.kt` (add dialog, wire up)

**Step 1: Add confirmation state to WeeklyPartsUiState**

```kotlin
val removePartCandidate: WeeklyPartId? = null,
val removePartAssignmentCount: Int = 0,
```

**Step 2: Add request/confirm/dismiss functions in ViewModel**

```kotlin
fun requestRemovePart(weeklyPartId: WeeklyPartId) {
    val assignments = _state.value.weekPlan?.let { plan ->
        // We don't have assignment data in this ViewModel,
        // so we proceed directly for now.
        // The confirmation is about the fact that cascade will happen.
    }
    _state.update {
        it.copy(removePartCandidate = weeklyPartId)
    }
}

fun confirmRemovePart() {
    val partId = _state.value.removePartCandidate ?: return
    _state.update { it.copy(removePartCandidate = null) }
    scope.launch {
        rimuoviParte(_state.value.currentMonday, partId).fold(
            ifLeft = { error -> showError(error) },
            ifRight = { weekPlan -> _state.update { it.copy(weekPlan = weekPlan) } },
        )
    }
}

fun dismissRemovePart() {
    _state.update { it.copy(removePartCandidate = null) }
}
```

**Step 3: Add AlertDialog in WeeklyPartsScreen**

```kotlin
// After the OverwriteConfirmDialog block
state.removePartCandidate?.let { partId ->
    val partLabel = state.weekPlan?.parts?.find { it.id == partId }?.partType?.label ?: ""
    AlertDialog(
        onDismissRequest = { viewModel.dismissRemovePart() },
        title = { Text("Rimuovi parte") },
        text = {
            Text("Rimuovere \"$partLabel\"? Tutte le assegnazioni associate verranno cancellate.")
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.confirmRemovePart() },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) { Text("Rimuovi") }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.dismissRemovePart() }) { Text("Annulla") }
        },
    )
}
```

**Step 4: Wire the X button to requestRemovePart instead of removePart**

In `PartsCard` where `onRemove` is called (line 231):
```kotlin
// BEFORE
onRemove = { onRemove(part.id) },
// The onRemove callback should now call viewModel.requestRemovePart instead of viewModel.removePart
```

**Step 5: Verify**

Run: `./gradlew compileKotlinJvm`

**Step 6: Commit**

```bash
git commit -m "feat: add confirmation dialog when removing a part with cascade warning"
```

---

## Task 8: Delete proclamatore — handle assignments with prompt

**Files:**
- Modify: `composeApp/src/commonMain/sqldelight/org/example/project/db/MinisteroDatabase.sq` (add query + change FK)
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/feature/assignments/application/AssignmentStore.kt`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/feature/assignments/infrastructure/SqlDelightAssignmentStore.kt`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/feature/people/application/EliminaProclamatoreUseCase.kt`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/proclamatori/ProclamatoriViewModel.kt:304-336`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/proclamatori/ProclamatoriComponents.kt:88-119`

**Step 1: Add SQL queries**

In `MinisteroDatabase.sq`:
```sql
countAssignmentsForPerson:
SELECT COUNT(*) FROM assignment WHERE person_id = ?;

deleteAssignmentsForPerson:
DELETE FROM assignment WHERE person_id = ?;
```

**Step 2: Add methods to AssignmentStore interface**

```kotlin
suspend fun countAssignmentsForPerson(personId: ProclamatoreId): Int
suspend fun removeAllForPerson(personId: ProclamatoreId)
```

**Step 3: Implement in SqlDelightAssignmentStore**

```kotlin
override suspend fun countAssignmentsForPerson(personId: ProclamatoreId): Int {
    return database.ministeroDatabaseQueries
        .countAssignmentsForPerson(personId.value)
        .executeAsOne()
        .toInt()
}

override suspend fun removeAllForPerson(personId: ProclamatoreId) {
    database.ministeroDatabaseQueries.deleteAssignmentsForPerson(personId.value)
}
```

**Step 4: Update EliminaProclamatoreUseCase**

Inject `AssignmentStore` and delete assignments before person:
```kotlin
class EliminaProclamatoreUseCase(
    private val store: ProclamatoriAggregateStore,
    private val assignmentStore: AssignmentStore,
) {
    suspend operator fun invoke(id: ProclamatoreId): Either<DomainError, Unit> = either {
        store.load(id) ?: raise(DomainError.Validation("Proclamatore non trovato"))
        try {
            assignmentStore.removeAllForPerson(id)
            store.remove(id)
        } catch (e: Exception) {
            raise(DomainError.Validation("Errore nell'eliminazione: ${e.message}"))
        }
    }
}
```

**Step 5: Update Koin DI registration for EliminaProclamatoreUseCase**

In `AppModules.kt`, update the factory to inject `AssignmentStore`:
```kotlin
factory { EliminaProclamatoreUseCase(get(), get()) }
```

**Step 6: Add assignment count to delete dialog flow**

In `ProclamatoriViewModel`:
- `requestDeleteCandidate` should query assignment count and store it in state
- Add `deleteAssignmentCount: Int = 0` to UiState

```kotlin
fun requestDeleteCandidate(candidate: Proclamatore) {
    scope.launch {
        val count = assignmentStore.countAssignmentsForPerson(candidate.id)
        _uiState.update { it.copy(deleteCandidate = candidate, deleteAssignmentCount = count) }
    }
}
```

Inject `assignmentStore` into `ProclamatoriViewModel`.

**Step 7: Update ProclamatoreDeleteDialog**

Add `assignmentCount: Int` parameter. When > 0, show warning:
```kotlin
text = {
    Column {
        Text("Confermi rimozione di ${candidate.nome} ${candidate.cognome}?")
        if (assignmentCount > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Attenzione: $assignmentCount assegnazion${if (assignmentCount == 1) "e verra' cancellata" else "i verranno cancellate"}.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
```

**Step 8: Verify**

Run: `./gradlew compileKotlinJvm`

**Step 9: Commit**

```bash
git commit -m "feat: warn about assignment loss when deleting proclamatore, delete assignments first"
```

---

## Task 9: Improve overwrite dialog to warn about assignment loss

**Files:**
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/weeklyparts/WeeklyPartsScreen.kt:423-455`

**Step 1: Update OverwriteConfirmDialog text**

```kotlin
text = {
    Column {
        Text("Le seguenti settimane esistono gia':")
        Spacer(Modifier.height(8.dp))
        weeks.forEach { date ->
            Text("- ${date.format(dateFormatter)}")
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Sovrascrivendo, tutte le parti e le assegnazioni esistenti verranno cancellate definitivamente.",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
},
```

**Step 2: Verify**

Run: `./gradlew compileKotlinJvm`

**Step 3: Commit**

```bash
git commit -m "fix: overwrite dialog now warns about assignment loss"
```

---

## Task 10: Fix negative ranking for past weeks + success feedback on remove

**Files:**
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/feature/assignments/infrastructure/SqlDelightAssignmentStore.kt:106-111`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/assignments/AssignmentsComponents.kt` (formatWeeksAgo)
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/assignments/AssignmentsViewModel.kt:147-154`

**Step 1: Clamp negative weeks to 0**

In `SqlDelightAssignmentStore.suggestedProclamatori`:
```kotlin
lastGlobalWeeks = lastGlobalDate?.let {
    ChronoUnit.WEEKS.between(LocalDate.parse(it), referenceDate).toInt().coerceAtLeast(0)
},
lastForPartTypeWeeks = lastPartDate?.let {
    ChronoUnit.WEEKS.between(LocalDate.parse(it), referenceDate).toInt().coerceAtLeast(0)
},
```

**Step 2: Add success feedback for removeAssignment**

In `AssignmentsViewModel.removeAssignment`:
```kotlin
fun removeAssignment(assignmentId: String) {
    scope.launch {
        rimuoviAssegnazione(assignmentId).fold(
            ifLeft = { error -> showError(error) },
            ifRight = {
                _state.update {
                    it.copy(notice = FeedbackBannerModel("Assegnazione rimossa", FeedbackBannerKind.SUCCESS))
                }
                loadWeekData()
            },
        )
    }
}
```

**Step 3: Verify**

Run: `./gradlew compileKotlinJvm`

**Step 4: Commit**

```bash
git commit -m "fix: clamp negative ranking weeks, add success feedback on assignment removal"
```

---

## Verification Checklist

After all tasks are complete:

1. `./gradlew compileKotlinJvm` — clean build
2. Manual test: navigate to a past week in Assignments — buttons should be disabled
3. Manual test: navigate to a past week in Schema — add/remove/reorder should be disabled
4. Manual test: try to assign same person to 2 parts — should get error "gia' assegnato in questa settimana"
5. Manual test: remove a part that has assignments — confirmation dialog should appear
6. Manual test: delete a proclamatore that has assignments — dialog shows assignment count warning
7. Manual test: overwrite week from GitHub — dialog warns about assignment loss
8. Manual test: double-click "Assegna" rapidly — only one assignment should be created
9. Manual test: assignments screen for past week — shows "Non assegnato" instead of "Assegna" button

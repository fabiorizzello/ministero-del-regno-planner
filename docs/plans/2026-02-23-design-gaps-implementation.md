# Design Gaps Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement 6 missing features from the monthly program design doc to complete the UX.

**Architecture:** Each feature is a vertical slice (domain → infrastructure → UI). No tests per user decision. Verification via `./gradlew composeApp:compileKotlinJvm`.

**Tech Stack:** Kotlin, Compose Desktop, SqlDelight, Arrow Either, Koin DI, StateFlow ViewModels.

---

## Task 1: Recency format for suggestions

Add design-compliant recency formatting: `< 14 days → "X giorni fa"`, `>= 14 days → "X settimane fa"`.

**Files:**
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/feature/assignments/domain/SuggestedProclamatore.kt`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/feature/assignments/infrastructure/SqlDelightAssignmentStore.kt:97-105`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/assignments/AssignmentsComponents.kt:539-544`

**Step 1: Add days fields to SuggestedProclamatore**

```kotlin
data class SuggestedProclamatore(
    val proclamatore: Proclamatore,
    val lastGlobalWeeks: Int?,
    val lastForPartTypeWeeks: Int?,
    val lastGlobalDays: Int?,         // NEW
    val lastForPartTypeDays: Int?,    // NEW
    val inCooldown: Boolean = false,
    val cooldownRemainingWeeks: Int = 0,
)
```

**Step 2: Compute days in SqlDelightAssignmentStore**

At lines 97-105, add `ChronoUnit.DAYS.between()` alongside existing `WEEKS`:

```kotlin
SuggestedProclamatore(
    proclamatore = p,
    lastGlobalWeeks = lastGlobalDate?.let {
        ChronoUnit.WEEKS.between(LocalDate.parse(it), referenceDate).toInt().coerceAtLeast(0)
    },
    lastForPartTypeWeeks = lastPartDate?.let {
        ChronoUnit.WEEKS.between(LocalDate.parse(it), referenceDate).toInt().coerceAtLeast(0)
    },
    lastGlobalDays = lastGlobalDate?.let {
        ChronoUnit.DAYS.between(LocalDate.parse(it), referenceDate).toInt().coerceAtLeast(0)
    },
    lastForPartTypeDays = lastPartDate?.let {
        ChronoUnit.DAYS.between(LocalDate.parse(it), referenceDate).toInt().coerceAtLeast(0)
    },
)
```

**Step 3: Update formatWeeksAgo to use days-based recency**

Replace `formatWeeksAgo` in `AssignmentsComponents.kt:539-544` with a new function that takes both days and weeks:

```kotlin
private fun formatRecency(days: Int?, weeks: Int?): String = when {
    days == null -> "Mai assegnato"
    days == 0 -> "Oggi"
    days < 14 -> "$days giorni fa"
    else -> "${weeks ?: (days / 7)} settimane fa"
}
```

Update the two call sites in `SuggestionRow` to pass both values:
- `formatRecency(suggestion.lastGlobalDays, suggestion.lastGlobalWeeks)`
- `formatRecency(suggestion.lastForPartTypeDays, suggestion.lastForPartTypeWeeks)`

**Step 4: Verify compilation**

```bash
./gradlew composeApp:compileKotlinJvm
```

---

## Task 2: Svuota assegnazioni programma

Clear all assignments for current+future weeks of a program. Past week assignments are preserved.

**Files:**
- Modify: `composeApp/src/commonMain/sqldelight/org/example/project/db/MinisteroDatabase.sq` (new query)
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/feature/assignments/application/AssignmentStore.kt` (new method on `AssignmentRepository`)
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/feature/assignments/infrastructure/SqlDelightAssignmentStore.kt` (implement)
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/assignments/application/SvuotaAssegnazioniProgrammaUseCase.kt`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/core/di/AppModules.kt` (Koin registration)
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceViewModel.kt` (new method + state)
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceScreen.kt` (button + confirmation dialog)

**Step 1: Add SQL query**

In `MinisteroDatabase.sq`, add:

```sql
deleteAssignmentsByProgramFromDate:
DELETE FROM assignment
WHERE weekly_part_id IN (
    SELECT wp.id FROM weekly_part wp
    JOIN week_plan w ON wp.week_plan_id = w.id
    WHERE w.program_id = ?
    AND w.week_start_date >= ?
    AND w.status = 'ACTIVE'
);

countAssignmentsByProgramFromDate:
SELECT COUNT(*) AS cnt FROM assignment
WHERE weekly_part_id IN (
    SELECT wp.id FROM weekly_part wp
    JOIN week_plan w ON wp.week_plan_id = w.id
    WHERE w.program_id = ?
    AND w.week_start_date >= ?
    AND w.status = 'ACTIVE'
);
```

**Step 2: Add method to AssignmentRepository**

In `AssignmentStore.kt`, add to `AssignmentRepository`:

```kotlin
suspend fun deleteByProgramFromDate(programId: String, fromDate: LocalDate): Int
suspend fun countByProgramFromDate(programId: String, fromDate: LocalDate): Int
```

**Step 3: Implement in SqlDelightAssignmentStore**

```kotlin
override suspend fun deleteByProgramFromDate(programId: String, fromDate: LocalDate): Int {
    val count = database.ministeroDatabaseQueries
        .countAssignmentsByProgramFromDate(programId, fromDate.toString())
        .executeAsOne().cnt.toInt()
    database.ministeroDatabaseQueries
        .deleteAssignmentsByProgramFromDate(programId, fromDate.toString())
    return count
}

override suspend fun countByProgramFromDate(programId: String, fromDate: LocalDate): Int {
    return database.ministeroDatabaseQueries
        .countAssignmentsByProgramFromDate(programId, fromDate.toString())
        .executeAsOne().cnt.toInt()
}
```

**Step 4: Create SvuotaAssegnazioniProgrammaUseCase**

```kotlin
package org.example.project.feature.assignments.application

import java.time.LocalDate

class SvuotaAssegnazioniProgrammaUseCase(
    private val assignmentRepository: AssignmentRepository,
) {
    suspend fun count(programId: String, fromDate: LocalDate): Int =
        assignmentRepository.countByProgramFromDate(programId, fromDate)

    suspend fun execute(programId: String, fromDate: LocalDate): Int =
        assignmentRepository.deleteByProgramFromDate(programId, fromDate)
}
```

**Step 5: Register in Koin**

In `AppModules.kt`, add:

```kotlin
single { SvuotaAssegnazioniProgrammaUseCase(get()) }
```

And add the dependency to `ProgramWorkspaceViewModel` constructor.

**Step 6: Wire in ViewModel**

In `ProgramWorkspaceViewModel`, add:
- Constructor param: `svuotaAssegnazioni: SvuotaAssegnazioniProgrammaUseCase`
- State fields: `isClearingAssignments: Boolean = false`, `clearAssignmentsConfirm: Int? = null` (null = no dialog, Int = count to confirm)
- Methods: `requestClearAssignments()` (loads count, shows confirm), `confirmClearAssignments()` (executes delete), `dismissClearAssignments()`

**Step 7: Add button + confirmation dialog to ProgramWorkspaceScreen**

Add `OutlinedButton` "Svuota assegnazioni" in the toolbar row. When clicked, show confirmation `AlertDialog` with assignment count. On confirm, execute clear.

**Step 8: Verify compilation**

```bash
./gradlew composeApp:compileKotlinJvm
```

---

## Task 3: SKIPPED week UX

Conditional card rendering for SKIPPED weeks: badge styling + "Riattiva" button only (no "Vai"/"Assegna"). Past SKIPPED weeks show only badge (no actions).

**Files:**
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceScreen.kt:133-168`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceViewModel.kt`

**Step 1: Add riattiva method to ViewModel**

```kotlin
fun reactivateWeek(week: WeekPlan) {
    scope.launch {
        runCatching {
            weekPlanStore.updateWeekStatus(week.id, WeekPlanStatus.ACTIVE)
        }.onSuccess {
            _state.update {
                it.copy(notice = FeedbackBannerModel("Settimana ${week.weekStartDate} riattivata", FeedbackBannerKind.SUCCESS))
            }
            loadWeeksForSelectedProgram()
        }.onFailure { error ->
            _state.update {
                it.copy(notice = FeedbackBannerModel("Errore riattivazione: ${error.message}", FeedbackBannerKind.ERROR))
            }
        }
    }
}
```

**Step 2: Modify week card rendering**

Replace the current card body at lines 134-168 with conditional rendering:

```kotlin
items(state.selectedProgramWeeks, key = { it.id.value }) { week ->
    val isSkipped = week.status == WeekPlanStatus.SKIPPED
    val isPast = week.weekStartDate < state.today

    Card(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            if (isSkipped) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSkipped) 0.dp else 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSkipped)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Settimana ${week.weekStartDate}", style = MaterialTheme.typography.titleMedium)
                    if (isSkipped) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                "SALTATA",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
                Text("Parti: ${week.parts.size}", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                if (isSkipped) {
                    if (!isPast) {
                        OutlinedButton(
                            onClick = { viewModel.reactivateWeek(week) },
                            modifier = Modifier.handCursorOnHover(),
                        ) { Text("Riattiva") }
                    }
                } else {
                    OutlinedButton(
                        onClick = { viewModel.navigateToWeek(week) },
                        modifier = Modifier.handCursorOnHover(),
                    ) { Text("Vai") }
                    Button(
                        onClick = { navigateToSection(AppSection.ASSIGNMENTS) },
                        modifier = Modifier.handCursorOnHover(),
                    ) { Text("Assegna") }
                }
            }
        }
    }
}
```

**Step 3: Add imports**

Add `import org.example.project.feature.weeklyparts.domain.WeekPlanStatus` and `import androidx.compose.material3.Surface` to `ProgramWorkspaceScreen.kt`.

**Step 4: Verify compilation**

```bash
./gradlew composeApp:compileKotlinJvm
```

---

## Task 4: Badge template aggiornato

Show a badge "Template aggiornato" on future programs when schemas were imported after `templateAppliedAt`.

**Files:**
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/feature/schemas/application/AggiornaSchemiUseCase.kt` (record timestamp)
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceViewModel.kt` (load + compare)
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceScreen.kt` (badge in ProgramHeader)

**Approach:** Store `last_schema_import_at` in the existing `Settings` (Java Preferences) store. Compare with `program.templateAppliedAt`.

**Step 1: Store timestamp on schema import**

In `AggiornaSchemiUseCase`, add `Settings` as constructor dependency. After the transaction succeeds (before return), store:

```kotlin
settings.putString("last_schema_import_at", LocalDateTime.now().toString())
```

Update Koin registration to pass `get()` for `Settings`.

**Step 2: Load and compare in ViewModel**

Add `Settings` to `ProgramWorkspaceViewModel` constructor. In `loadProgramsAndWeeks()`, compute badge flag:

```kotlin
val lastSchemaImport = settings.getStringOrNull("last_schema_import_at")
    ?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
```

Add to `ProgramWorkspaceUiState`:

```kotlin
val futureNeedsSchemaRefresh: Boolean = false,
```

Set it in `loadProgramsAndWeeks`:

```kotlin
val futureNeedsRefresh = snapshot.future != null && lastSchemaImport != null &&
    (snapshot.future.templateAppliedAt == null || snapshot.future.templateAppliedAt < lastSchemaImport)
```

**Step 3: Show badge in ProgramHeader**

In `ProgramHeader`, next to the future program button, add a badge:

```kotlin
if (state.futureNeedsSchemaRefresh) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Text(
            "Template aggiornato, verificare",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}
```

**Step 4: Update Koin wiring**

Add `settings = get()` to `AggiornaSchemiUseCase` and `ProgramWorkspaceViewModel` constructors.

**Step 5: Verify compilation**

```bash
./gradlew composeApp:compileKotlinJvm
```

---

## Task 5: Conferma impatti schema refresh

Show a preview dialog with impact counts BEFORE executing "Aggiorna programma da schemi".

**Files:**
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/feature/programs/application/AggiornaProgrammaDaSchemiUseCase.kt` (add `dryRun` param)
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceViewModel.kt` (two-step flow)
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceScreen.kt` (confirmation dialog)

**Step 1: Add dryRun parameter to use case**

In `AggiornaProgrammaDaSchemiUseCase`, add `dryRun: Boolean = false` parameter to `invoke()`. When `dryRun == true`:
- Compute the report (counting what would change) but skip all writes
- Move the `transactionRunner.runInTransaction` block inside an `if (!dryRun)` guard
- The counting logic runs regardless, the writes only when `!dryRun`

```kotlin
suspend operator fun invoke(
    programId: String,
    referenceDate: LocalDate = LocalDate.now(),
    dryRun: Boolean = false,
): Either<DomainError, SchemaRefreshReport> = either {
    // ... existing validation and loading ...

    var weeksUpdated = 0
    var assignmentsPreserved = 0
    var assignmentsRemoved = 0

    // Analyze all weeks (dry run or real)
    for (week in weeks) {
        val template = schemaTemplateStore.findByWeekStartDate(week.weekStartDate)
            ?: continue
        val newPartTypeIds = template.partTypeIds
        if (newPartTypeIds.isEmpty()) continue

        val currentAssignments = assignmentRepository.listByWeek(week.id)
        val assignmentsByKey = mutableMapOf<Pair<PartTypeId, Int>, MutableList<Assignment>>()
        // ... same key-building logic ...

        // Count matches vs removed
        val newParts = newPartTypeIds.mapIndexed { index, ptId -> ptId to index }
        for ((ptId, index) in newParts) {
            val key = ptId to index
            val matched = assignmentsByKey.remove(key)
            if (matched != null) assignmentsPreserved += matched.size
        }
        assignmentsRemoved += assignmentsByKey.values.sumOf { it.size }
        weeksUpdated++
    }

    if (!dryRun) {
        transactionRunner.runInTransaction {
            // ... existing write logic (same as current) ...
        }
    }

    SchemaRefreshReport(weeksUpdated, assignmentsPreserved, assignmentsRemoved)
}
```

**Step 2: Two-step ViewModel flow**

In `ProgramWorkspaceViewModel`, add state:

```kotlin
val schemaRefreshPreview: SchemaRefreshReport? = null,
```

Change `refreshProgramFromSchemas()` to first do dry run:

```kotlin
fun refreshProgramFromSchemas() {
    val programId = _state.value.selectedProgramId ?: return
    if (_state.value.isRefreshingProgramFromSchemas) return
    scope.launch {
        _state.update { it.copy(isRefreshingProgramFromSchemas = true) }
        aggiornaProgrammaDaSchemi(programId, _state.value.today, dryRun = true).fold(
            ifLeft = { error ->
                _state.update {
                    it.copy(isRefreshingProgramFromSchemas = false,
                        notice = FeedbackBannerModel(error.toMessage(), FeedbackBannerKind.ERROR))
                }
            },
            ifRight = { preview ->
                _state.update {
                    it.copy(isRefreshingProgramFromSchemas = false, schemaRefreshPreview = preview)
                }
            },
        )
    }
}

fun confirmSchemaRefresh() {
    val programId = _state.value.selectedProgramId ?: return
    scope.launch {
        _state.update { it.copy(schemaRefreshPreview = null, isRefreshingProgramFromSchemas = true) }
        aggiornaProgrammaDaSchemi(programId, _state.value.today, dryRun = false).fold(
            ifLeft = { error ->
                _state.update {
                    it.copy(isRefreshingProgramFromSchemas = false,
                        notice = FeedbackBannerModel(error.toMessage(), FeedbackBannerKind.ERROR))
                }
            },
            ifRight = { report ->
                _state.update {
                    it.copy(isRefreshingProgramFromSchemas = false,
                        notice = FeedbackBannerModel(
                            "Programma aggiornato: ${report.weeksUpdated} settimane, ${report.assignmentsPreserved} preservate, ${report.assignmentsRemoved} rimosse",
                            FeedbackBannerKind.SUCCESS))
                }
                loadWeeksForSelectedProgram()
            },
        )
    }
}

fun dismissSchemaRefresh() {
    _state.update { it.copy(schemaRefreshPreview = null) }
}
```

**Step 3: Add confirmation dialog to Screen**

In `ProgramWorkspaceScreen`, add an `AlertDialog` controlled by `state.schemaRefreshPreview`:

```kotlin
state.schemaRefreshPreview?.let { preview ->
    AlertDialog(
        onDismissRequest = { viewModel.dismissSchemaRefresh() },
        title = { Text("Conferma aggiornamento da schemi") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text("Settimane da aggiornare: ${preview.weeksUpdated}")
                Text("Assegnazioni preservate: ${preview.assignmentsPreserved}")
                if (preview.assignmentsRemoved > 0) {
                    Text(
                        "Assegnazioni da rimuovere: ${preview.assignmentsRemoved}",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.confirmSchemaRefresh() }) { Text("Aggiorna") }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.dismissSchemaRefresh() }) { Text("Annulla") }
        },
    )
}
```

**Step 4: Verify compilation**

```bash
./gradlew composeApp:compileKotlinJvm
```

---

## Task 6: Dirty prompt for week editing

Buffer schema changes (add/remove/reorder parts) locally. Save commits all; Discard reverts. Confirmation on navigating away while dirty.

**Files:**
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/weeklyparts/WeeklyPartsViewModel.kt`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/weeklyparts/WeeklyPartsScreen.kt`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/application/WeekPlanStore.kt` (if `replaceAllParts` needs adjustments)

**Step 1: Add dirty state to ViewModel UiState**

```kotlin
internal data class WeeklyPartsUiState(
    // ... existing fields ...
    val editingParts: List<WeeklyPart>? = null,  // null = not dirty, non-null = local buffer
    val isDirty: Boolean = false,
    val showDirtyPrompt: Boolean = false,         // Salva/Scarta/Annulla dialog
    val pendingNavigation: (() -> Unit)? = null,  // deferred nav action
    val isSaving: Boolean = false,
)
```

**Step 2: Refactor ViewModel editing methods**

Change `addPart`, `confirmRemovePart`, `movePart` to operate on the local buffer instead of calling use cases:

```kotlin
fun addPart(partTypeId: PartTypeId) {
    val weekPlan = _state.value.weekPlan ?: return
    val partType = _state.value.partTypes.find { it.id == partTypeId } ?: return
    val currentParts = _state.value.editingParts ?: weekPlan.parts
    val newPart = WeeklyPart(
        id = WeeklyPartId(java.util.UUID.randomUUID().toString()),
        partType = partType,
        sortOrder = currentParts.size,
    )
    val updated = currentParts + newPart
    _state.update { it.copy(editingParts = updated, isDirty = true) }
}

fun confirmRemovePart() {
    val partId = _state.value.removePartCandidate ?: return
    val weekPlan = _state.value.weekPlan ?: return
    val currentParts = _state.value.editingParts ?: weekPlan.parts
    val updated = currentParts
        .filter { it.id != partId }
        .mapIndexed { i, p -> p.copy(sortOrder = i) }
    _state.update { it.copy(removePartCandidate = null, editingParts = updated, isDirty = true) }
}

fun movePart(fromIndex: Int, toIndex: Int) {
    val weekPlan = _state.value.weekPlan ?: return
    val parts = (_state.value.editingParts ?: weekPlan.parts).toMutableList()
    if (fromIndex !in parts.indices || toIndex !in parts.indices) return
    val moved = parts.removeAt(fromIndex)
    parts.add(toIndex, moved)
    val reordered = parts.mapIndexed { i, p -> p.copy(sortOrder = i) }
    _state.update { it.copy(editingParts = reordered, isDirty = true) }
}
```

**Step 3: Add Save / Discard / navigation guard methods**

```kotlin
fun saveChanges() {
    val weekPlan = _state.value.weekPlan ?: return
    val editingParts = _state.value.editingParts ?: return
    scope.launch {
        _state.update { it.copy(isSaving = true) }
        runCatching {
            weekPlanStore.replaceAllParts(
                weekPlan.id,
                editingParts.sortedBy { it.sortOrder }.map { it.partType.id },
            )
        }.onSuccess {
            _state.update { it.copy(editingParts = null, isDirty = false, isSaving = false) }
            loadWeek()
        }.onFailure { error ->
            _state.update {
                it.copy(
                    isSaving = false,
                    notice = FeedbackBannerModel("Errore salvataggio: ${error.message}", FeedbackBannerKind.ERROR),
                )
            }
        }
    }
}

fun discardChanges() {
    _state.update { it.copy(editingParts = null, isDirty = false, showDirtyPrompt = false) }
    _state.value.pendingNavigation?.invoke()
    _state.update { it.copy(pendingNavigation = null) }
}

fun cancelNavigation() {
    _state.update { it.copy(showDirtyPrompt = false, pendingNavigation = null) }
}

fun saveAndNavigate() {
    saveChanges()
    // After save completes, pending navigation will execute
}
```

**Step 4: Guard navigation methods**

Wrap `navigateToPreviousWeek`, `navigateToNextWeek`, `navigateToCurrentWeek` with dirty check:

```kotlin
fun navigateToPreviousWeek() {
    if (_state.value.isDirty) {
        _state.update { it.copy(showDirtyPrompt = true, pendingNavigation = { sharedWeekState.navigateToPreviousWeek() }) }
        return
    }
    sharedWeekState.navigateToPreviousWeek()
}
// Same pattern for navigateToNextWeek, navigateToCurrentWeek
```

**Step 5: Add WeekPlanStore to ViewModel constructor (if not already there)**

Check: `WeekPlanStore` is not a current dependency of `WeeklyPartsViewModel`. Add it as a constructor parameter. The individual use cases (`aggiungiParte`, `rimuoviParte`, `riordinaParti`) are no longer called during editing — only `weekPlanStore.replaceAllParts` on Save. Keep the use cases in the constructor for `createWeek` which still needs `creaSettimana`.

Actually, `replaceAllParts` needs `WeekPlanStore` directly. Add:

```kotlin
private val weekPlanStore: WeekPlanStore,
```

Update Koin wiring in `AppModules.kt` to pass `weekPlanStore = get()`.

**Step 6: Update WeeklyPartsScreen**

Add the dirty prompt dialog:

```kotlin
if (state.showDirtyPrompt) {
    AlertDialog(
        onDismissRequest = { viewModel.cancelNavigation() },
        title = { Text("Modifiche non salvate") },
        text = { Text("Hai modifiche non salvate. Cosa vuoi fare?") },
        confirmButton = {
            TextButton(onClick = { viewModel.saveChanges(); viewModel.discardChanges() }) { Text("Salva") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                TextButton(onClick = { viewModel.discardChanges() }) { Text("Scarta") }
                TextButton(onClick = { viewModel.cancelNavigation() }) { Text("Annulla") }
            }
        },
    )
}
```

Add Save/Discard buttons visible when dirty:

```kotlin
if (state.isDirty) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Button(
            onClick = { viewModel.saveChanges() },
            enabled = !state.isSaving,
            modifier = Modifier.handCursorOnHover(),
        ) { Text(if (state.isSaving) "Salvataggio..." else "Salva modifiche") }
        OutlinedButton(
            onClick = { viewModel.discardChanges() },
            enabled = !state.isSaving,
            modifier = Modifier.handCursorOnHover(),
        ) { Text("Scarta") }
    }
}
```

Update the `PartsCard` call to use `state.editingParts ?: state.weekPlan?.parts ?: emptyList()` as the parts source.

**Step 7: Verify compilation**

```bash
./gradlew composeApp:compileKotlinJvm
```

---

## Execution Order & Dependencies

Tasks 1-4 are fully independent and can be parallelized.

Task 5 modifies `AggiornaProgrammaDaSchemiUseCase` — independent of others.

Task 6 is the most complex refactor — should go last.

Recommended batch execution:
- **Batch 1:** Tasks 1, 2, 3, 4 (independent)
- **Batch 2:** Tasks 5, 6

## Verification

After all tasks:

```bash
./gradlew composeApp:compileKotlinJvm
```

Smoke test: launch app, navigate to Cruscotto, verify all buttons present, SKIPPED card styling, template badge, recency format in suggestion picker.

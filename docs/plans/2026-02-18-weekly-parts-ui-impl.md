# WeeklyPartsScreen UI Redesign — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Redesign WeeklyPartsScreen with modern card-based layout, zebra-striped rows, and drag-and-drop reordering via `sh.calvin.reorderable`.

**Architecture:** Replace the current `StandardTableHeader`/`StandardTableViewport` grid with a `Card`-wrapped `LazyColumn` using `ReorderableItem` for drag-and-drop. Non-fixed parts get a drag handle (left) and remove button (right). Fixed parts render without either. The ViewModel's `movePart(from, to)` API is unchanged — D&D calls it on drop.

**Tech Stack:** Compose Multiplatform 1.10.0, Material3, `sh.calvin.reorderable:reorderable:3.0.0`, Koin, Kotlin 2.3.0.

**Design doc:** `docs/plans/2026-02-18-weekly-parts-ui-redesign.md`

---

### Task 1: Add reorderable dependency

**Files:**
- Modify: `gradle/libs.versions.toml:16` (add version + library entry)
- Modify: `composeApp/build.gradle.kts:15-28` (add to commonMain.dependencies)

**Step 1: Add version and library to version catalog**

In `gradle/libs.versions.toml`, add at end of `[versions]`:
```toml
reorderable = "3.0.0"
```

In `[libraries]`, add:
```toml
reorderable = { module = "sh.calvin.reorderable:reorderable", version.ref = "reorderable" }
```

**Step 2: Add dependency to build.gradle.kts**

In `composeApp/build.gradle.kts`, inside `commonMain.dependencies`, add after `libs.voyager.navigator`:
```kotlin
implementation(libs.reorderable)
```

**Step 3: Sync and verify**

Run: `./gradlew compileKotlinJvm`
Expected: BUILD SUCCESSFUL (dependency resolves, no compilation errors)

**Step 4: Commit**

```bash
git add gradle/libs.versions.toml composeApp/build.gradle.kts
git commit -m "chore: add sh.calvin.reorderable dependency for drag-and-drop"
```

---

### Task 2: Rewrite WeeklyPartsScreen with new layout

This is the main task. Replace the entire content of `WeeklyPartsScreen.kt` with the new design.

**Files:**
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/weeklyparts/WeeklyPartsScreen.kt`

**What changes:**
- Remove `StandardTableHeader`, `StandardTableViewport`, `TableColumnSpec`, `standardTableCell` imports and usage
- Remove `columns` list
- Remove `PartRow` composable (replaced by inline `ReorderableItem` content)
- Remove arrow icons (`KeyboardArrowUp`, `KeyboardArrowDown`) — no longer needed
- Add `Card` wrapper around parts list
- Add `ReorderableItem` + `draggableHandle()` for D&D
- Add zebra striping on rows
- Keep `WeekNavigator`, `AddPartDropdown`, `OverwriteConfirmDialog` (mostly unchanged)

**Step 1: Rewrite imports**

Replace import block (lines 1-56) with:
```kotlin
package org.example.project.ui.weeklyparts

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.WeeklyPart
import org.example.project.ui.components.FeedbackBanner
import org.example.project.ui.components.handCursorOnHover
import org.koin.core.context.GlobalContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
```

**Step 2: Replace `columns` and `WeeklyPartsScreen` composable**

Remove the `columns` list (lines 60-66) and the old `WeeklyPartsScreen()` function (lines 67-177). Replace with:

```kotlin
private val dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ITALIAN)

@Composable
fun WeeklyPartsScreen() {
    val viewModel = remember { GlobalContext.get().get<WeeklyPartsViewModel>() }
    val state by viewModel.state.collectAsState()

    // Overwrite confirmation dialog
    if (state.weeksNeedingConfirmation.isNotEmpty()) {
        OverwriteConfirmDialog(
            weeks = state.weeksNeedingConfirmation.map { LocalDate.parse(it.weekStartDate) },
            onConfirmAll = { viewModel.confirmOverwrite() },
            onSkip = { viewModel.dismissConfirmation() },
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Top bar: sync button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            OutlinedButton(
                onClick = { viewModel.syncRemoteData() },
                enabled = !state.isImporting,
                modifier = Modifier.handCursorOnHover(),
            ) {
                if (state.isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Aggiorna dati")
            }
        }

        // Feedback banner
        FeedbackBanner(
            model = state.notice,
            onDismissRequest = { viewModel.dismissNotice() },
        )

        // Week navigator
        WeekNavigator(
            monday = state.currentMonday,
            sunday = state.sundayDate,
            indicator = state.weekIndicator,
            enabled = !state.isLoading,
            onPrevious = { viewModel.navigateToPreviousWeek() },
            onNext = { viewModel.navigateToNextWeek() },
        )

        // Content
        if (state.isLoading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.weekPlan == null) {
            EmptyWeekContent(
                isImporting = state.isImporting,
                onCreate = { viewModel.createWeek() },
            )
        } else {
            PartsCard(
                parts = state.weekPlan!!.parts,
                isImporting = state.isImporting,
                partTypes = state.partTypes,
                onMove = { from, to -> viewModel.movePart(from, to) },
                onRemove = { viewModel.removePart(it) },
                onAddPart = { viewModel.addPart(it) },
            )
        }
    }
}
```

**Step 3: Write `EmptyWeekContent`**

```kotlin
@Composable
private fun EmptyWeekContent(isImporting: Boolean, onCreate: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Settimana non configurata", style = MaterialTheme.typography.bodyLarge)
            Button(
                onClick = onCreate,
                enabled = !isImporting,
                modifier = Modifier.handCursorOnHover(),
            ) {
                Text("Crea settimana")
            }
        }
    }
}
```

**Step 4: Write `PartsCard` with D&D**

This is the core new composable. It wraps the parts list in a Card with a reorderable LazyColumn.

```kotlin
@Composable
private fun PartsCard(
    parts: List<WeeklyPart>,
    isImporting: Boolean,
    partTypes: List<PartType>,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onRemove: (org.example.project.feature.weeklyparts.domain.WeeklyPartId) -> Unit,
    onAddPart: (org.example.project.feature.weeklyparts.domain.PartTypeId) -> Unit,
) {
    val lazyListState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onMove(from.index, to.index)
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            // Column headers
            PartsHeader()

            // Parts list
            LazyColumn(state = lazyListState) {
                items(parts, key = { it.id.value }) { part ->
                    val index = parts.indexOf(part)
                    val isFixed = part.partType.fixed
                    val zebraColor = if (index % 2 == 0) {
                        Color.Transparent
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    }

                    if (isFixed) {
                        // Fixed parts: no drag, no remove, slightly different style
                        FixedPartRow(
                            part = part,
                            displayNumber = part.sortOrder + 3,
                            backgroundColor = zebraColor,
                        )
                    } else {
                        ReorderableItem(
                            reorderableLazyListState,
                            key = part.id.value,
                            enabled = !isImporting,
                        ) { isDragging ->
                            val elevation by animateDpAsState(
                                targetValue = if (isDragging) 4.dp else 0.dp,
                            )
                            DraggablePartRow(
                                part = part,
                                displayNumber = part.sortOrder + 3,
                                backgroundColor = zebraColor,
                                elevation = elevation,
                                enabled = !isImporting,
                                onRemove = { onRemove(part.id) },
                                dragModifier = Modifier.draggableHandle(),
                            )
                        }
                    }
                }
            }
        }
    }

    // Add part button (outside card)
    if (partTypes.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        AddPartDropdown(
            partTypes = partTypes,
            onSelect = { onAddPart(it.id) },
            enabled = !isImporting,
        )
    }
}
```

**Step 5: Write `PartsHeader`**

```kotlin
@Composable
private fun PartsHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(40.dp)) // drag handle space
        Text("N.", modifier = Modifier.width(36.dp), style = MaterialTheme.typography.labelMedium)
        Text("Tipo", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
        Text("Persone", modifier = Modifier.width(64.dp), style = MaterialTheme.typography.labelMedium)
        Text("Regola", modifier = Modifier.width(56.dp), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(40.dp)) // remove button space
    }
}
```

**Step 6: Write `DraggablePartRow`**

```kotlin
@Composable
private fun DraggablePartRow(
    part: WeeklyPart,
    displayNumber: Int,
    backgroundColor: Color,
    elevation: androidx.compose.ui.unit.Dp,
    enabled: Boolean,
    onRemove: () -> Unit,
    dragModifier: Modifier,
) {
    Surface(shadowElevation = elevation) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Drag handle
            Icon(
                Icons.Rounded.DragHandle,
                contentDescription = "Trascina per riordinare",
                modifier = dragModifier
                    .handCursorOnHover()
                    .size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Spacer(Modifier.width(8.dp))

            // Number
            Text(
                "$displayNumber",
                modifier = Modifier.width(36.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            // Type label
            Text(
                part.partType.label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            // People count
            Text(
                "${part.partType.peopleCount}",
                modifier = Modifier.width(64.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            // Sex rule
            Text(
                part.partType.sexRule.name,
                modifier = Modifier.width(56.dp),
                style = MaterialTheme.typography.bodyMedium,
            )

            // Remove button
            IconButton(
                onClick = onRemove,
                enabled = enabled,
                modifier = Modifier.size(32.dp).handCursorOnHover(),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Rimuovi parte",
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
```

**Step 7: Write `FixedPartRow`**

```kotlin
@Composable
private fun FixedPartRow(
    part: WeeklyPart,
    displayNumber: Int,
    backgroundColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Empty space where drag handle would be
        Spacer(Modifier.width(28.dp))

        Text(
            "$displayNumber",
            modifier = Modifier.width(36.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            part.partType.label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "${part.partType.peopleCount}",
            modifier = Modifier.width(64.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            part.partType.sexRule.name,
            modifier = Modifier.width(56.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Empty space where remove button would be
        Spacer(Modifier.width(40.dp))
    }
}
```

**Step 8: Keep `WeekNavigator` unchanged** (lines 179-223 of current file)

Copy as-is, no changes needed.

**Step 9: Update `AddPartDropdown`**

Keep identical to current version (lines 277-309). No changes.

**Step 10: Keep `OverwriteConfirmDialog` unchanged** (lines 311-343)

Copy as-is.

**Step 11: Verify build**

Run: `./gradlew compileKotlinJvm`
Expected: BUILD SUCCESSFUL

**Step 12: Manual visual test**

Run the app, navigate to "Schemi" tab:
1. Verify Card-wrapped list with zebra stripes
2. Drag a non-fixed part via handle — verify it moves and persists
3. Click X to remove a part — verify removal
4. Navigate weeks — verify loading state
5. Click "Aggiorna dati" — verify buttons disable during import
6. Switch tabs during import — return — verify import still running

**Step 13: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/ui/weeklyparts/WeeklyPartsScreen.kt
git commit -m "feat(ui): redesign WeeklyPartsScreen with card layout and drag-and-drop reordering"
```

---

### Task 3: Clean up unused imports from StandardTable

**Files:**
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/weeklyparts/WeeklyPartsScreen.kt` (verify no `StandardTable*` imports remain)

**Step 1: Verify no dead imports**

Grep the file for `StandardTable`, `TableColumnSpec`, `standardTableCell`, `KeyboardArrowUp`, `KeyboardArrowDown`, `itemsIndexed`. None should remain.

**Step 2: Verify build**

Run: `./gradlew compileKotlinJvm`
Expected: BUILD SUCCESSFUL

If Task 2 was done correctly, this is a no-op verification. If stale imports remain, remove them and commit:

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/ui/weeklyparts/WeeklyPartsScreen.kt
git commit -m "chore: remove unused StandardTable imports from WeeklyPartsScreen"
```

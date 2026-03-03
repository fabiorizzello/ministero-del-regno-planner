# Program Workspace v2 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Port the validated `ProgrammaSketch.kt` design to the real `ProgramWorkspaceScreen.kt`, replacing the "LazyColumn of all weeks" layout with a sidebar week-list + master-detail center panel.

**Architecture:** Add `selectedWeekId: String?` local state at the screen level (defaulting to the current week). Left sidebar navigates months and weeks with dot/mini-bar indicators. Center shows the selected week's detail. Right panel shows coverage card, collapsible settings, issues, and a pinned danger zone. A 24dp accent status bar runs across the bottom.

**Tech Stack:** Kotlin Multiplatform Desktop, Compose Multiplatform, existing ViewModels (ProgramLifecycleViewModel, AssignmentManagementViewModel, etc.), `WorkspaceSketchPalette` dark theme, `formatWeekRangeLabel` / `formatMonthYearLabel` for Italian date formatting.

---

## Key domain knowledge

- `WeekPlan.status`: only `ACTIVE | SKIPPED` — "past" and "current" are derived from dates.
- Current week: `week.weekStartDate == currentMonday` where `currentMonday = today.with(previousOrSame(MONDAY))`.
- Past week: `week.weekStartDate < currentMonday && week.status != SKIPPED`.
- Complete: `ACTIVE && assignedSlots == totalSlots && totalSlots > 0`.
- Partial: `ACTIVE && assignedSlots > 0 && assignedSlots < totalSlots`.
- Empty: `ACTIVE && assignedSlots == 0`.
- Week slot counts come from `lifecycleState.selectedProgramAssignments[week.id.value]?.size`.
- `formatWeekRangeLabel(monday, sunday)` → Italian short label e.g. "3–9 Marzo 2026".
- `formatMonthYearLabel(month, year)` → "Marzo 2026".
- Keep all existing dialogs (AlertDialog for delete/clear/assignment removal) and ViewModel wiring untouched.

---

## Task 1 — selectedWeekId state + sidebar week list

**Files:**
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceScreen.kt` (lines 336–449 — left panel section of the Row)
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceComponents.kt` (add new composable at end)

### Step 1: Derive sidebar week status helper

Add a private enum and helper at the top of `ProgramWorkspaceScreen.kt` (after the `ProgramActivityFeedEntry` data class, around line 88):

```kotlin
private enum class WeekSidebarStatus { CURRENT, PAST, COMPLETE, PARTIAL, EMPTY, SKIPPED }

private fun WeekPlan.sidebarStatus(
    currentMonday: java.time.LocalDate,
    assignedSlots: Int,
    totalSlots: Int,
): WeekSidebarStatus = when {
    status == WeekPlanStatus.SKIPPED -> WeekSidebarStatus.SKIPPED
    weekStartDate == currentMonday -> WeekSidebarStatus.CURRENT
    weekStartDate < currentMonday -> WeekSidebarStatus.PAST
    assignedSlots == totalSlots && totalSlots > 0 -> WeekSidebarStatus.COMPLETE
    assignedSlots > 0 -> WeekSidebarStatus.PARTIAL
    else -> WeekSidebarStatus.EMPTY
}
```

### Step 2: Add selectedWeekId state

Inside `ProgramWorkspaceScreen`, in the `else ->` branch after the `val weekListState = rememberLazyListState()` line (around line 378), add:

```kotlin
// Auto-select current week (or first available) when weeks change
var selectedWeekId by remember { mutableStateOf<String?>(null) }
val effectiveSelectedWeekId = remember(selectedWeekId, lifecycleState.selectedProgramWeeks, currentMonday) {
    val id = selectedWeekId
    val weeks = lifecycleState.selectedProgramWeeks
    when {
        id != null && weeks.any { it.id.value == id } -> id
        else -> weeks.firstOrNull { it.weekStartDate == currentMonday }?.id?.value
            ?: weeks.firstOrNull()?.id?.value
    }
}
val selectedWeek = lifecycleState.selectedProgramWeeks.firstOrNull { it.id.value == effectiveSelectedWeekId }
```

Also remove `val weekListState = rememberLazyListState()` — it's no longer needed.

### Step 3: Replace the left WorkspacePanel with a plain sidebar Column

Replace the left `WorkspacePanel(modifier = Modifier.width(280.dp)...)` block (lines 387–449) with:

```kotlin
// ── Left sidebar ──────────────────────────────────────────────────────
Column(
    modifier = Modifier
        .width(210.dp)
        .fillMaxHeight()
        .background(sketch.panelLeft),
) {
    // Programs (months) section
    Column(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            "PROGRAMMI",
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 10.sp,
                letterSpacing = 0.7.sp,
            ),
            color = sketch.inkMuted,
        )
        lifecycleState.currentProgram?.let { program ->
            val selected = lifecycleState.selectedProgramId == program.id.value
            ProgramMonthSelectorButton(
                label = formatMonthYearLabel(program.month, program.year),
                selected = selected,
                accent = sketch.accent,
                onClick = {
                    lifecycleVM.selectProgram(program.id.value)
                    selectedWeekId = null // reset to current week of new month
                },
            )
        }
        lifecycleState.futurePrograms.forEach { program ->
            val selected = lifecycleState.selectedProgramId == program.id.value
            ProgramMonthSelectorButton(
                label = formatMonthYearLabel(program.month, program.year),
                selected = selected,
                accent = sketch.accent,
                onClick = {
                    lifecycleVM.selectProgram(program.id.value)
                    selectedWeekId = null
                },
            )
        }
        lifecycleState.creatableTargets.forEach { target ->
            val label = formatMonthYearLabel(target.monthValue, target.year)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .handCursorOnHover()
                    .clip(RoundedCornerShape(5.dp))
                    .clickable { lifecycleVM.createProgramForTarget(target.year, target.monthValue) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    tint = sketch.accent.copy(alpha = 0.7f),
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    if (lifecycleState.isCreatingProgram) "Creazione..." else "Crea $label",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = sketch.accent.copy(alpha = 0.7f),
                )
            }
        }
    }

    // Divider
    Box(Modifier.fillMaxWidth().height(1.dp).background(sketch.lineSoft.copy(alpha = 0.6f)))

    // Week list (scrollable, for selected month)
    val weeksForMonth = lifecycleState.selectedProgramWeeks
    LazyColumn(
        modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            Text(
                selectedProgram?.let {
                    formatMonthYearLabel(it.month, it.year).uppercase()
                } ?: "SETTIMANE",
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp,
                    letterSpacing = 0.6.sp,
                ),
                color = sketch.inkMuted,
            )
        }
        items(weeksForMonth, key = { it.id.value }) { week ->
            val weekAssignedSlots = lifecycleState.selectedProgramAssignments[week.id.value]?.size ?: 0
            val weekTotalSlots = week.parts.sumOf { it.partType.peopleCount }
            val status = week.sidebarStatus(currentMonday, weekAssignedSlots, weekTotalSlots)
            val fraction = if (weekTotalSlots > 0) weekAssignedSlots.toFloat() / weekTotalSlots else 0f
            WeekSidebarItem(
                label = formatWeekRangeLabel(week.weekStartDate, week.weekStartDate.plusDays(6)),
                status = status,
                fraction = fraction,
                selected = effectiveSelectedWeekId == week.id.value,
                onClick = { selectedWeekId = week.id.value },
            )
        }
    }

    // Divider + footer
    Box(Modifier.fillMaxWidth().height(1.dp).background(sketch.lineSoft.copy(alpha = 0.6f)))
    Column(modifier = Modifier.padding(8.dp)) {
        SidebarFooterButton(
            label = if (schemaState.isRefreshingSchemas || schemaState.isRefreshingProgramFromSchemas)
                "Aggiornamento..." else "Aggiorna schemi",
            icon = Icons.Filled.Refresh,
            onClick = { schemaVM.refreshSchemasAndProgram(onProgramRefreshComplete = reloadData) },
            enabled = !schemaState.isRefreshingSchemas && !schemaState.isRefreshingProgramFromSchemas,
        )
    }
}
// Sidebar / center divider
Box(Modifier.fillMaxHeight().width(1.dp).background(sketch.lineSoft))
```

### Step 4: Add `WeekSidebarItem` and `SidebarFooterButton` to ProgramWorkspaceComponents.kt

Append to end of `ProgramWorkspaceComponents.kt`:

```kotlin
@Composable
internal fun WeekSidebarItem(
    label: String,
    status: WeekSidebarStatus, // passed from screen
    fraction: Float,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val sketch = MaterialTheme.workspaceSketch
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    val dotColor = when (status) {
        WeekSidebarStatus.CURRENT -> sketch.accent
        WeekSidebarStatus.COMPLETE -> sketch.ok
        WeekSidebarStatus.PARTIAL -> sketch.warn
        WeekSidebarStatus.PAST -> sketch.inkMuted
        WeekSidebarStatus.SKIPPED, WeekSidebarStatus.EMPTY -> sketch.lineSoft
    }
    val fillColor = when (status) {
        WeekSidebarStatus.CURRENT -> sketch.accent
        WeekSidebarStatus.COMPLETE -> sketch.ok
        WeekSidebarStatus.PARTIAL -> sketch.warn
        WeekSidebarStatus.PAST -> sketch.inkMuted
        else -> Color.Transparent
    }
    val bgColor = when {
        selected -> sketch.accentSoft
        hovered -> sketch.surface
        else -> Color.Transparent
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interactionSource)
            .handCursorOnHover()
            .clip(RoundedCornerShape(9.dp))
            .background(bgColor)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(dotColor))
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = if (selected) sketch.accent else sketch.ink,
                maxLines = 1,
            )
            when (status) {
                WeekSidebarStatus.CURRENT -> WeekSidebarTag("CORRENTE", sketch.accent)
                WeekSidebarStatus.SKIPPED -> WeekSidebarTag("SALTATA", sketch.inkMuted)
                else -> {}
            }
        }
        if (status != WeekSidebarStatus.SKIPPED) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .padding(bottom = 6.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(sketch.lineSoft.copy(alpha = 0.5f)),
            ) {
                if (fraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction.coerceAtMost(1f))
                            .background(fillColor, RoundedCornerShape(999.dp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekSidebarTag(label: String, color: Color) {
    Surface(shape = RoundedCornerShape(3.dp), color = color.copy(alpha = 0.14f)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 10.sp,
                letterSpacing = 0.3.sp,
            ),
            color = color,
        )
    }
}

@Composable
internal fun SidebarFooterButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val sketch = MaterialTheme.workspaceSketch
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .handCursorOnHover(enabled)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(5.dp),
        color = sketch.surface,
        border = BorderStroke(1.dp, sketch.lineSoft),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = sketch.inkSoft, modifier = Modifier.size(12.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = sketch.inkSoft,
            )
        }
    }
}
```

Note: `WeekSidebarStatus` enum is private in `ProgramWorkspaceScreen.kt`. Either make it `internal` or duplicate it. **Make it `internal`** so `WeekSidebarItem` can accept it.

Also add needed imports to `ProgramWorkspaceComponents.kt`:
```kotlin
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
```

And to `ProgramWorkspaceScreen.kt`:
```kotlin
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
```

### Step 5: Build and verify

```bash
./gradlew :composeApp:compileKotlinJvm --quiet
```

Expected: BUILD SUCCESSFUL (no errors).

### Step 6: Commit

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceScreen.kt \
        composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceComponents.kt
git commit -m "feat(ui): add selectedWeekId state + v2 sidebar week list"
```

---

## Task 2 — Center master-detail panel

**Files:**
- Modify: `ProgramWorkspaceScreen.kt` (replace center WorkspacePanel, ~lines 451–560)
- Modify: `ProgramWorkspaceComponents.kt` (add week detail composables)

### Step 1: Replace center WorkspacePanel with master-detail Column

Replace the center `WorkspacePanel(modifier = Modifier.weight(1f)...)` block with:

```kotlin
// ── Center — week detail ──────────────────────────────────────────────────────
Column(
    modifier = Modifier
        .weight(1f)
        .fillMaxHeight()
        .background(sketch.panelMid),
) {
    if (selectedWeek == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Seleziona una settimana dalla sidebar",
                style = MaterialTheme.typography.bodySmall,
                color = sketch.inkMuted,
            )
        }
    } else {
        val weekAssignments = lifecycleState.selectedProgramAssignments[selectedWeek.id.value] ?: emptyList()
        val weekTotalSlots = selectedWeek.parts.sumOf { it.partType.peopleCount }
        val weekAssignedSlots = weekAssignments.size
        val fraction = if (weekTotalSlots > 0) weekAssignedSlots.toFloat() / weekTotalSlots else 0f
        val isPast = selectedWeek.weekStartDate < currentMonday
        val isSkipped = selectedWeek.status == WeekPlanStatus.SKIPPED
        val isCurrent = selectedWeek.weekStartDate == currentMonday
        val canMutate = !isPast && !isSkipped
        val weekLabel = formatWeekRangeLabel(selectedWeek.weekStartDate, selectedWeek.weekStartDate.plusDays(6))
        val monthLabel = selectedProgram?.let { formatMonthYearLabel(it.month, it.year) } ?: ""

        // Sticky detail header
        WeekDetailHeader(
            weekLabel = weekLabel,
            monthLabel = monthLabel,
            isCurrent = isCurrent,
            isSkipped = isSkipped,
            canMutate = canMutate,
            onOpenPartEditor = { partEditorVM.openPartEditor(selectedWeek) },
            onSkipWeek = { partEditorVM.skipWeek(selectedWeek, onSuccess = reloadData) },
            onReactivate = { partEditorVM.reactivateWeek(selectedWeek, onSuccess = reloadData) },
        )

        // Coverage strip (not for skipped)
        if (!isSkipped && weekTotalSlots > 0) {
            WeekCoverageStrip(
                assigned = weekAssignedSlots,
                total = weekTotalSlots,
                fraction = fraction,
            )
        }

        // Scrollable parts body
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            when {
                isSkipped -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Settimana saltata", style = MaterialTheme.typography.titleSmall, color = sketch.inkSoft)
                        Text(
                            "Questa settimana è stata esclusa dal programma.\nClicca 'Riattiva' per ripristinarla.",
                            style = MaterialTheme.typography.bodySmall,
                            color = sketch.inkMuted,
                        )
                    }
                }
                selectedWeek.parts.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Nessuna parte configurata per questa settimana",
                            style = MaterialTheme.typography.bodySmall,
                            color = sketch.inkMuted,
                        )
                    }
                }
                else -> {
                    val assignmentsByPart = remember(weekAssignments) {
                        weekAssignments.groupBy { it.weeklyPartId }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        selectedWeek.parts.chunked(2).forEach { rowParts ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                rowParts.forEach { part ->
                                    PartAssignmentCard(
                                        part = part,
                                        assignments = assignmentsByPart[part.id] ?: emptyList(),
                                        readOnly = !canMutate,
                                        onAssignSlot = { slot ->
                                            personPickerVM.openPersonPicker(
                                                weekStartDate = selectedWeek.weekStartDate,
                                                weeklyPartId = part.id,
                                                slot = slot,
                                                selectedProgramWeeks = lifecycleState.selectedProgramWeeks,
                                                selectedProgramAssignments = lifecycleState.selectedProgramAssignments,
                                            )
                                        },
                                        onRemoveAssignment = { assignmentId ->
                                            val assignment = assignmentsById[assignmentId.value]
                                            if (assignment != null) pendingAssignmentRemoval = assignment
                                            else personPickerVM.removeAssignment(assignmentId, onSuccess = reloadData)
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (rowParts.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}
// Center / right divider
Box(Modifier.fillMaxHeight().width(1.dp).background(sketch.lineSoft))
```

Add new imports to `ProgramWorkspaceScreen.kt`:
```kotlin
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
```

### Step 2: Add WeekDetailHeader and WeekCoverageStrip to ProgramWorkspaceComponents.kt

Append to `ProgramWorkspaceComponents.kt`:

```kotlin
@Composable
internal fun WeekDetailHeader(
    weekLabel: String,
    monthLabel: String,
    isCurrent: Boolean,
    isSkipped: Boolean,
    canMutate: Boolean,
    onOpenPartEditor: () -> Unit,
    onSkipWeek: () -> Unit,
    onReactivate: () -> Unit,
) {
    val sketch = MaterialTheme.workspaceSketch
    Column(modifier = Modifier.background(sketch.panelLeft)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        weekLabel,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            letterSpacing = (-0.3).sp,
                        ),
                        color = sketch.ink,
                    )
                    when {
                        isCurrent -> WeekDetailBadge("CORRENTE", sketch.accent)
                        isSkipped -> WeekDetailBadge("SALTATA", sketch.inkMuted)
                    }
                }
                Text(monthLabel, style = MaterialTheme.typography.bodySmall, color = sketch.inkMuted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    isSkipped -> WeekHdrButton("Riattiva", Icons.Filled.PlayCircle, sketch.ok, sketch.ok.copy(alpha = 0.45f), onClick = onReactivate)
                    canMutate -> {
                        WeekHdrButton("Modifica parti", Icons.Filled.Edit, sketch.inkSoft, sketch.lineSoft, onClick = onOpenPartEditor)
                        WeekHdrButton("Salta settimana", Icons.Filled.Block, sketch.warn, sketch.warn.copy(alpha = 0.45f), onClick = onSkipWeek)
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(sketch.lineSoft))
    }
}

@Composable
private fun WeekDetailBadge(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp,
                letterSpacing = 0.2.sp,
            ),
            color = color,
        )
    }
}

@Composable
private fun WeekHdrButton(
    label: String,
    icon: ImageVector,
    fg: Color,
    border: Color,
    onClick: () -> Unit,
) {
    val sketch = MaterialTheme.workspaceSketch
    Surface(
        modifier = Modifier.handCursorOnHover().clickable(onClick = onClick),
        shape = RoundedCornerShape(5.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(13.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = fg,
            )
        }
    }
}

@Composable
internal fun WeekCoverageStrip(assigned: Int, total: Int, fraction: Float) {
    val sketch = MaterialTheme.workspaceSketch
    val fillColor = when {
        fraction == 1f -> sketch.ok
        fraction > 0f -> sketch.accent
        else -> Color.Transparent
    }
    val pctColor = when {
        fraction == 1f -> sketch.ok
        fraction > 0f -> sketch.accent
        else -> sketch.inkMuted
    }
    Column(modifier = Modifier.background(sketch.panelLeft)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$assigned / $total slot",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = sketch.inkSoft,
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(5.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(sketch.lineSoft),
            ) {
                if (fraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction.coerceAtMost(1f))
                            .background(fillColor, RoundedCornerShape(999.dp)),
                    )
                }
            }
            Text(
                "${(fraction * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = pctColor,
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(sketch.lineSoft.copy(alpha = 0.5f)))
    }
}
```

Add needed imports to `ProgramWorkspaceComponents.kt`:
```kotlin
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.ui.unit.sp
```

Check that `PartAssignmentCard` in the screen now accepts a `modifier: Modifier` param — look at its signature in `ui/assignments/PartAssignmentCard.kt`. If it doesn't have `modifier`, pass it via a wrapping `Box(modifier = Modifier.weight(1f)) { PartAssignmentCard(...) }`.

### Step 3: Build

```bash
./gradlew :composeApp:compileKotlinJvm --quiet
```

Expected: BUILD SUCCESSFUL.

### Step 4: Commit

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceScreen.kt \
        composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceComponents.kt
git commit -m "feat(ui): center master-detail week view + WeekDetailHeader + WeekCoverageStrip"
```

---

## Task 3 — Right panel redesign

**Files:**
- Modify: `ProgramWorkspaceScreen.kt` (replace right WorkspacePanel, ~lines 562–730)
- Modify: `ProgramWorkspaceComponents.kt` (add ProgramCoverageCard, ProgramIssuesPanel)

### Step 1: Replace right WorkspacePanel

Replace the right `WorkspacePanel(modifier = Modifier.width(360.dp)...)` block with:

```kotlin
// ── Right panel ────────────────────────────────────────────────────────────────
Column(
    modifier = Modifier
        .width(248.dp)
        .fillMaxHeight()
        .background(sketch.panelRight),
) {
    // Scrollable content
    Column(
        modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Quick actions (compact row at top)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ProgramRightPanelButton(
                label = if (assignmentState.isAutoAssigning) "..." else "Autoassegna",
                icon = Icons.Filled.PlayArrow,
                isPrimary = false,
                enabled = lifecycleState.selectedProgramId != null && !assignmentState.isAutoAssigning,
                onClick = {
                    lifecycleState.selectedProgramId?.let { programId ->
                        assignmentVM.autoAssignSelectedProgram(programId, fromFutureDate, onSuccess = reloadData)
                    }
                },
                modifier = Modifier.weight(1f),
            )
            ProgramRightPanelButton(
                label = if (assignmentState.isPrintingProgram) "..." else "Stampa",
                icon = Icons.Filled.Print,
                isPrimary = true,
                enabled = lifecycleState.selectedProgramId != null && !assignmentState.isPrintingProgram,
                onClick = {
                    lifecycleState.selectedProgramId?.let { programId ->
                        assignmentVM.printSelectedProgram(programId)
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }

        // Coverage card (month level)
        if (selectedProgram != null) {
            ProgramCoverageCard(
                programLabel = formatMonthYearLabel(selectedProgram.month, selectedProgram.year),
                assigned = totalAssignments,
                total = totalSlots,
            )
        }

        // Settings (collapsible)
        var settingsOpen by remember { mutableStateOf(false) }
        RightPanelCollapsibleSection(title = "IMPOSTAZIONI", open = settingsOpen, onToggle = { settingsOpen = !settingsOpen }) {
            Box(modifier = Modifier.testTag("program-assignment-settings")) {
                ProgramInlineAssignmentSettings(
                    state = assignmentState.assignmentSettings,
                    isSaving = assignmentState.isSavingAssignmentSettings,
                    onStrictCooldownChange = assignmentVM::setStrictCooldown,
                    onLeadWeightChange = assignmentVM::setLeadWeight,
                    onAssistWeightChange = assignmentVM::setAssistWeight,
                    onLeadCooldownChange = assignmentVM::setLeadCooldownWeeks,
                    onAssistCooldownChange = assignmentVM::setAssistCooldownWeeks,
                    onSave = assignmentVM::saveAssignmentSettings,
                )
            }
        }

        // Issues (auto-assign unresolved)
        if (assignmentState.autoAssignUnresolved.isNotEmpty()) {
            ProgramIssuesPanel(issues = assignmentState.autoAssignUnresolved)
        }
    }

    // Danger zone (pinned to bottom)
    Box(Modifier.fillMaxWidth().height(1.dp).background(sketch.lineSoft.copy(alpha = 0.5f)))
    Column(
        modifier = Modifier.padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (hasFutureWeeks) {
            ProgramDangerButton(
                label = if (assignmentState.isClearingAssignments) "Svuotamento..." else "Svuota assegnazioni future",
                icon = Icons.Filled.ClearAll,
                enabled = lifecycleState.selectedProgramId != null && !assignmentState.isClearingAssignments,
                onClick = {
                    lifecycleState.selectedProgramId?.let { programId ->
                        assignmentVM.requestClearAssignments(programId, fromFutureDate)
                    }
                },
            )
        }
        if (lifecycleState.canDeleteSelectedProgram) {
            ProgramDangerButton(
                label = if (lifecycleState.isDeletingSelectedProgram) "Eliminazione..." else "Elimina mese",
                icon = Icons.Filled.Delete,
                enabled = !lifecycleState.isDeletingSelectedProgram,
                onClick = { lifecycleVM.requestDeleteSelectedProgram() },
            )
        }
    }
}
```

### Step 2: Add new right panel composables to ProgramWorkspaceComponents.kt

```kotlin
@Composable
internal fun ProgramRightPanelButton(
    label: String,
    icon: ImageVector,
    isPrimary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sketch = MaterialTheme.workspaceSketch
    val alpha = if (enabled) 1f else 0.45f
    Surface(
        modifier = modifier
            .handCursorOnHover(enabled)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(5.dp),
        color = if (isPrimary) sketch.accent.copy(alpha = alpha) else sketch.surfaceMuted.copy(alpha = alpha),
        border = BorderStroke(1.dp, if (isPrimary) sketch.accent.copy(alpha = 0.7f * alpha) else sketch.lineSoft.copy(alpha = alpha)),
    ) {
        Row(
            modifier = Modifier.height(30.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(icon, contentDescription = null, tint = if (isPrimary) Color.White.copy(alpha = alpha) else sketch.inkSoft.copy(alpha = alpha), modifier = Modifier.size(13.dp))
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium), color = if (isPrimary) Color.White.copy(alpha = alpha) else sketch.inkSoft.copy(alpha = alpha))
        }
    }
}

@Composable
internal fun ProgramCoverageCard(
    programLabel: String,
    assigned: Int,
    total: Int,
) {
    val sketch = MaterialTheme.workspaceSketch
    val fraction = if (total > 0) assigned.toFloat() / total else 0f
    val empty = (total - assigned).coerceAtLeast(0)
    val fillColor = if (fraction == 1f) sketch.ok else sketch.accent

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "COPERTURA",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, letterSpacing = 0.7.sp),
            color = sketch.inkMuted,
        )
        Surface(
            shape = RoundedCornerShape(9.dp),
            color = sketch.surfaceMuted,
            border = BorderStroke(1.dp, sketch.lineSoft),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "$assigned",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.5).sp),
                        color = sketch.ink,
                    )
                    Text("/ $total", style = MaterialTheme.typography.bodyMedium, color = sketch.inkMuted, modifier = Modifier.padding(bottom = 3.dp))
                }
                Text("slot assegnati · $programLabel", style = MaterialTheme.typography.labelSmall, color = sketch.inkMuted)
                Spacer(Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(999.dp)).background(sketch.lineSoft)) {
                    if (fraction > 0f) {
                        Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceAtMost(1f)).background(fillColor, RoundedCornerShape(999.dp)))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CovPill("$assigned assegnati", sketch.ok)
                    if (empty > 0) CovPill("$empty vuoti", sketch.warn)
                }
            }
        }
    }
}

@Composable
private fun CovPill(label: String, color: Color) {
    val sketch = MaterialTheme.workspaceSketch
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = color)
    }
}

@Composable
internal fun RightPanelCollapsibleSection(
    title: String,
    open: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val sketch = MaterialTheme.workspaceSketch
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .handCursorOnHover()
                .clickable(onClick = onToggle)
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, letterSpacing = 0.7.sp),
                color = sketch.inkMuted,
            )
            Icon(
                imageVector = if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = sketch.inkMuted,
                modifier = Modifier.size(14.dp),
            )
        }
        AnimatedVisibility(visible = open) {
            content()
        }
    }
}

@Composable
internal fun ProgramIssuesPanel(issues: List<AutoAssignUnresolvedSlot>) {
    val sketch = MaterialTheme.workspaceSketch
    var open by remember { mutableStateOf(true) }
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = sketch.warn.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, sketch.warn.copy(alpha = 0.4f)),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .handCursorOnHover()
                    .clickable { open = !open }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = sketch.warn, modifier = Modifier.size(12.dp))
                Text(
                    "PROBLEMI",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 0.5.sp),
                    color = sketch.warn,
                )
                Box(modifier = Modifier.size(17.dp).clip(CircleShape).background(sketch.warn), contentAlignment = Alignment.Center) {
                    Text("${issues.size}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 10.sp), color = Color.White)
                }
                Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = sketch.warn, modifier = Modifier.size(12.dp))
            }
            AnimatedVisibility(visible = open) {
                Column {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(sketch.warn.copy(alpha = 0.4f)))
                    issues.take(6).forEach { issue ->
                        val weekLabel = formatWeekRangeLabel(issue.weekStartDate, issue.weekStartDate.plusDays(6))
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                "${issue.partLabel.uppercase()} · $weekLabel",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 10.5.sp, letterSpacing = 0.4.sp),
                                color = sketch.warn,
                            )
                            Text(issue.reason, style = MaterialTheme.typography.labelSmall, color = sketch.inkSoft)
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(sketch.warn.copy(alpha = 0.15f)))
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProgramDangerButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val sketch = MaterialTheme.workspaceSketch
    val alpha = if (enabled) 1f else 0.45f
    Surface(
        modifier = Modifier.fillMaxWidth().handCursorOnHover(enabled).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(5.dp),
        color = sketch.surface,
        border = BorderStroke(1.dp, sketch.bad.copy(alpha = 0.4f * alpha)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = sketch.bad.copy(alpha = alpha), modifier = Modifier.size(12.dp))
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = sketch.bad.copy(alpha = alpha))
        }
    }
}
```

`AutoAssignUnresolvedSlot` — check its fields with:
```bash
grep -n "data class AutoAssignUnresolvedSlot\|class AutoAssignUnresolvedSlot" \
  composeApp/src/jvmMain/kotlin/org/example/project/ -r
```
Expected fields: `weekStartDate: LocalDate`, `partLabel: String`, `reason: String`.

Add needed imports to `ProgramWorkspaceComponents.kt`:
```kotlin
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
```

Also add import for `AutoAssignUnresolvedSlot` from its package.

### Step 3: Build

```bash
./gradlew :composeApp:compileKotlinJvm --quiet
```

### Step 4: Commit

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceScreen.kt \
        composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceComponents.kt
git commit -m "feat(ui): right panel v2 — coverage card, collapsible settings, issues, danger zone"
```

---

## Task 4 — Status bar + swap AppScreen + cleanup

**Files:**
- Modify: `ProgramWorkspaceScreen.kt` (add status bar + remove unused helpers)
- Modify: `AppScreen.kt` (swap `ProgrammaSketchScreen()` → `ProgramWorkspaceScreen()`)
- Modify: `ProgramWorkspaceComponents.kt` (delete `ProgramWeekStickyHeader`, `ProgramWeekCard`)

### Step 1: Add status bar to ProgramWorkspaceScreen

After the `Row(modifier = Modifier.weight(1f)) { ... }` (the 3-panel Row), add a status bar Row:

```kotlin
// Status bar
if (selectedProgram != null) {
    val sb = MaterialTheme.workspaceSketch
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(sb.accent)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            formatMonthYearLabel(selectedProgram.month, selectedProgram.year),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp),
            color = Color.White.copy(alpha = 0.88f),
        )
        Box(Modifier.height(12.dp).width(1.dp).background(Color.White.copy(alpha = 0.25f)))
        if (selectedWeek != null) {
            Text(
                "Settimana ${formatWeekRangeLabel(selectedWeek.weekStartDate, selectedWeek.weekStartDate.plusDays(6))}",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp),
                color = Color.White.copy(alpha = 0.88f),
            )
            Box(Modifier.height(12.dp).width(1.dp).background(Color.White.copy(alpha = 0.25f)))
        }
        Text(
            "$totalAssignments/$totalSlots slot",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp),
            color = Color.White.copy(alpha = 0.88f),
        )
        Spacer(Modifier.weight(1f))
        Text(
            lifecycleState.today.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ITALIAN)),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp),
            color = Color.White.copy(alpha = 0.75f),
        )
    }
}
```

Note: the status bar goes **inside** the `else ->` branch (the main content block) and **after** the closing `}` of the 3-panel Row, but before the end of the `else ->` block.

### Step 2: Swap AppScreen to use real ProgramWorkspaceScreen

In `AppScreen.kt`, find:
```kotlin
private data object PlanningDashboardSectionScreen : Screen {
    @Composable
    override fun Content() {
        // SKETCH: swap with ProgramWorkspaceScreen() when done validating
        ProgrammaSketchScreen()
    }
}
```

Replace with:
```kotlin
private data object PlanningDashboardSectionScreen : Screen {
    @Composable
    override fun Content() {
        ProgramWorkspaceScreen()
    }
}
```

Remove the now-unused import:
```kotlin
import org.example.project.ui.workspace.ProgrammaSketchScreen
```

### Step 3: Remove unused composables from ProgramWorkspaceScreen.kt

Delete these private functions (they're now replaced):
- `ProgramMonthSelectorButton` — keep, still used
- `ProgramStatusPill` — **delete** (moved to status bar)
- `InspectorSectionLabel` — **delete**
- `ProgramQuickAction` — **delete**
- `ProgramActivityFeedPanel` — **delete**, also remove `activityFeed` state and the `LaunchedEffect(noticeSignature)` block that adds to it (or keep notices without the feed — simplest: just keep dismissing notices, no accumulation)
- Remove `ProgramActivityFeedEntry` data class
- Remove `mutableStateListOf` and its import

### Step 4: Remove unused composables from ProgramWorkspaceComponents.kt

Delete:
- `ProgramWeekStickyHeader` (lines 82–175)
- `ProgramWeekCard` (lines 177–440) — this is a large composable, delete entirely

Keep:
- `PartEditorDialog` (lines 441–end)

### Step 5: Build clean

```bash
./gradlew :composeApp:compileKotlinJvm --quiet
```

Fix any remaining "unresolved reference" or "unused import" errors.

### Step 6: Screenshot validation

```bash
xvfb-run -a --server-args="-screen 0 3840x2160x24 -ac" bash -c '
  SKIKO_RENDER_API=SOFTWARE ./gradlew :composeApp:run --quiet &
  APP_PID=$!
  sleep 14
  WIN_ID=$(xwininfo -root -tree 2>/dev/null | grep "Scuola di ministero" | head -1 | awk "{print \$1}")
  if [ -n "$WIN_ID" ]; then
    xwd -id "$WIN_ID" -silent -out /tmp/workspace_v2.xwd
  fi
  kill $APP_PID 2>/dev/null
' 2>/dev/null

python3 -c "
import struct
from PIL import Image
raw = open('/tmp/workspace_v2.xwd', 'rb').read()
fields = struct.unpack('>22I', raw[:88])
header_size = fields[0]; ncolors = fields[19]; w = fields[4]; h = fields[5]; bpl = fields[12]
offset = header_size + ncolors * 12
img = Image.frombuffer('RGBA', (w, h), raw[offset:], 'raw', 'BGRA', bpl, 1)
img.save('/tmp/workspace_v2.png')
print(f'{w}x{h}')
"
```

Visually verify: sidebar week list, master-detail center, right panel coverage card, status bar.

### Step 7: Commit

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceScreen.kt \
        composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceComponents.kt \
        composeApp/src/jvmMain/kotlin/org/example/project/ui/AppScreen.kt
git commit -m "feat(ui): port v2 workspace layout — status bar, swap AppScreen, cleanup"
```

---

## Notes

- The `WeekSidebarStatus` enum must be `internal` (not `private`) because `WeekSidebarItem` in `ProgramWorkspaceComponents.kt` needs to accept it. Alternatively, derive the status inside `WeekSidebarItem` by passing the raw booleans — but passing the enum is cleaner.
- `PartAssignmentCard` signature: check if it already accepts `modifier: Modifier`. If not, wrap in `Box(modifier = Modifier.weight(1f))`.
- The `settingsOpen` var in the right panel is declared inside the composable body — this is intentional (resets on screen re-enter, consistent with sketch behavior).
- The `activityFeed` notice accumulation can be simplified: after removal of `ProgramActivityFeedPanel`, the `LaunchedEffect(noticeSignature)` block should be kept but only do the dismiss calls (no accumulation to a list). The feed state variable and data class can be deleted.
- `selectedWeek` variable for the status bar: inside the `else ->` branch, after `val selectedWeek = ...` is computed, it's accessible.

# Delivery Badge Pre-caricato — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Show a pre-loaded delivery status badge (pending/blocked/all-sent) under "Biglietti assegnazioni" on program selection, and exclude past weeks from both badge counts and the ticket dialog.

**Architecture:** A new read-only use case `CaricaRiepilogoConsegneProgrammaUseCase` queries week plans + assignments + delivery status to produce a `ProgramDeliverySnapshot(pending, blocked)`. The `completePartIds()` logic is extracted from `GeneraImmaginiAssegnazioni` into a shared domain function. The badge is computed by the ViewModel on program selection and after state-changing actions. The ticket dialog filters out past weeks at the ViewModel level.

**Tech Stack:** Kotlin, Compose Multiplatform, Arrow Either, SQLDelight, Koin DI, Material Design 3

---

### Task 1: Extract `completePartIds` to domain function

**Files:**
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/output/domain/PartCompleteness.kt`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/feature/output/application/GeneraImmaginiAssegnazioni.kt:188-196`
- Create: `composeApp/src/jvmTest/kotlin/org/example/project/feature/output/domain/PartCompletenessTest.kt`

This task extracts the private `completePartIds()` function from `GeneraImmaginiAssegnazioni` into a reusable domain function so it can be shared with the new use case.

**Step 1: Write the failing test**

Create `composeApp/src/jvmTest/kotlin/org/example/project/feature/output/domain/PartCompletenessTest.kt`:

```kotlin
package org.example.project.feature.output.domain

import org.example.project.feature.assignments.domain.AssignmentWithPerson
import org.example.project.feature.weeklyparts.domain.WeeklyPart
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.SexRule
import kotlin.test.Test
import kotlin.test.assertEquals

class PartCompletenessTest {

    private val partType2People = PartType.unsafeOf(
        id = PartTypeId("pt-1"),
        code = "BS",
        label = "Studio biblico",
        peopleCount = 2,
        sexRule = SexRule.STESSO_SESSO,
        fixed = false,
        sortOrder = 0,
    )
    private val partType1Person = PartType.unsafeOf(
        id = PartTypeId("pt-2"),
        code = "LT",
        label = "Lettura",
        peopleCount = 1,
        sexRule = SexRule.UOMO,
        fixed = false,
        sortOrder = 1,
    )

    private val partA = WeeklyPart(
        id = WeeklyPartId("part-a"),
        partType = partType2People,
        sortOrder = 0,
    )
    private val partB = WeeklyPart(
        id = WeeklyPartId("part-b"),
        partType = partType1Person,
        sortOrder = 1,
    )

    private fun assignment(weeklyPartId: WeeklyPartId, slot: Int) = AssignmentWithPerson(
        weeklyPartId = weeklyPartId,
        slot = slot,
        fullName = "Test",
        spipiledId = null,
        personId = null,
    )

    @Test
    fun `part with enough assignments is complete`() {
        val assignments = listOf(
            assignment(partA.id, 1),
            assignment(partA.id, 2),
            assignment(partB.id, 1),
        )
        val result = completePartIds(listOf(partA, partB), assignments)
        assertEquals(setOf(partA.id, partB.id), result)
    }

    @Test
    fun `part with fewer assignments than needed is not complete`() {
        val assignments = listOf(
            assignment(partA.id, 1), // only 1 of 2
            assignment(partB.id, 1),
        )
        val result = completePartIds(listOf(partA, partB), assignments)
        assertEquals(setOf(partB.id), result)
    }

    @Test
    fun `no assignments yields empty set`() {
        val result = completePartIds(listOf(partA, partB), emptyList())
        assertEquals(emptySet(), result)
    }

    @Test
    fun `empty parts list yields empty set`() {
        val result = completePartIds(emptyList(), listOf(assignment(partA.id, 1)))
        assertEquals(emptySet(), result)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :composeApp:jvmTest --tests "org.example.project.feature.output.domain.PartCompletenessTest" --no-build-cache`
Expected: FAIL — `completePartIds` is not defined yet.

**Step 3: Write the implementation**

Create `composeApp/src/jvmMain/kotlin/org/example/project/feature/output/domain/PartCompleteness.kt`:

```kotlin
package org.example.project.feature.output.domain

import org.example.project.feature.assignments.domain.AssignmentWithPerson
import org.example.project.feature.weeklyparts.domain.WeeklyPart
import org.example.project.feature.weeklyparts.domain.WeeklyPartId

/**
 * Determina quali parti sono "complete" — hanno un numero di assegnazioni >= peopleCount.
 * Usata sia da GeneraImmaginiAssegnazioni sia da CaricaRiepilogoConsegneProgrammaUseCase.
 */
fun completePartIds(
    parts: List<WeeklyPart>,
    assignments: List<AssignmentWithPerson>,
): Set<WeeklyPartId> {
    val assignedCountByPart = assignments.groupBy { it.weeklyPartId }.mapValues { it.value.size }
    return parts
        .filter { part -> (assignedCountByPart[part.id] ?: 0) >= part.partType.peopleCount }
        .mapTo(mutableSetOf()) { it.id }
}
```

**Step 4: Update `GeneraImmaginiAssegnazioni` to use the extracted function**

In `GeneraImmaginiAssegnazioni.kt`, replace the private `completePartIds` method (lines 188-196) with a call to the domain function:

Replace:
```kotlin
    private fun completePartIds(
        weekPlan: WeekPlan,
        assignments: List<AssignmentWithPerson>,
    ): Set<WeeklyPartId> {
        val assignedCountByPart = assignments.groupBy { it.weeklyPartId }.mapValues { it.value.size }
        return weekPlan.parts
            .filter { part -> (assignedCountByPart[part.id] ?: 0) >= part.partType.peopleCount }
            .mapTo(mutableSetOf()) { it.id }
    }
```

With:
```kotlin
    private fun completePartIds(
        weekPlan: WeekPlan,
        assignments: List<AssignmentWithPerson>,
    ): Set<WeeklyPartId> = org.example.project.feature.output.domain.completePartIds(weekPlan.parts, assignments)
```

Add the import at the top of the file:
```kotlin
import org.example.project.feature.output.domain.completePartIds
```

And then simplify the private wrapper — since it just delegates, inline it. Replace usages of `completePartIds(week, weekAssignments)` with `completePartIds(week.parts, weekAssignments)` and remove the private method entirely. There are 2 call sites:
- Line 140: `val completePartIds = completePartIds(week, weekAssignments)` → `val completeIds = completePartIds(week.parts, weekAssignments)`
- The `buildPartWarnings` method does NOT call `completePartIds` — it's independent.

**Step 5: Run tests to verify nothing broke**

Run: `./gradlew :composeApp:jvmTest --no-build-cache`
Expected: All tests PASS.

**Step 6: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/feature/output/domain/PartCompleteness.kt \
       composeApp/src/jvmTest/kotlin/org/example/project/feature/output/domain/PartCompletenessTest.kt \
       composeApp/src/jvmMain/kotlin/org/example/project/feature/output/application/GeneraImmaginiAssegnazioni.kt
git commit -m "Extract completePartIds to shared domain function"
```

---

### Task 2: Create `ProgramDeliverySnapshot` domain model

**Files:**
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/output/domain/ProgramDeliverySnapshot.kt`

This is a trivial value object — no test needed (only computed property `allDelivered` which will be covered by use case tests).

**Step 1: Write the file**

Create `composeApp/src/jvmMain/kotlin/org/example/project/feature/output/domain/ProgramDeliverySnapshot.kt`:

```kotlin
package org.example.project.feature.output.domain

data class ProgramDeliverySnapshot(
    val pending: Int,
    val blocked: Int,
) {
    val allDelivered: Boolean get() = pending == 0 && blocked == 0
}
```

**Step 2: Run build to verify**

Run: `./gradlew :composeApp:jvmTest --tests "org.example.project.feature.output.domain.PartCompletenessTest" --no-build-cache`
Expected: PASS (smoke check that compilation works).

**Step 3: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/feature/output/domain/ProgramDeliverySnapshot.kt
git commit -m "Add ProgramDeliverySnapshot domain value object"
```

---

### Task 3: Create `CaricaRiepilogoConsegneProgrammaUseCase`

**Files:**
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/output/application/CaricaRiepilogoConsegneProgrammaUseCase.kt`
- Create: `composeApp/src/jvmTest/kotlin/org/example/project/feature/output/application/CaricaRiepilogoConsegneProgrammaUseCaseTest.kt`

This read-only use case loads week plans, assignments, and delivery status to produce a `ProgramDeliverySnapshot`.

**Step 1: Write the failing test**

Create `composeApp/src/jvmTest/kotlin/org/example/project/feature/output/application/CaricaRiepilogoConsegneProgrammaUseCaseTest.kt`:

```kotlin
package org.example.project.feature.output.application

import arrow.core.right
import kotlinx.coroutines.test.runTest
import org.example.project.feature.assignments.domain.AssignmentWithPerson
import org.example.project.feature.output.domain.ProgramDeliverySnapshot
import org.example.project.feature.output.domain.SlipDelivery
import org.example.project.feature.output.domain.SlipDeliveryId
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.weeklyparts.domain.*
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class CaricaRiepilogoConsegneProgrammaUseCaseTest {

    private val programId = ProgramMonthId("prog-1")
    private val today = LocalDate.of(2026, 3, 9) // Monday

    private val partType2 = PartType.unsafeOf(
        id = PartTypeId("pt-1"), code = "BS", label = "Studio biblico",
        peopleCount = 2, sexRule = SexRule.STESSO_SESSO, fixed = false, sortOrder = 0,
    )
    private val partType1 = PartType.unsafeOf(
        id = PartTypeId("pt-2"), code = "LT", label = "Lettura",
        peopleCount = 1, sexRule = SexRule.UOMO, fixed = false, sortOrder = 1,
    )

    private val futureWeek = WeekPlan.of(
        id = WeekPlanId("w-future"),
        weekStartDate = LocalDate.of(2026, 3, 9), // today, included
        parts = listOf(
            WeeklyPart(id = WeeklyPartId("p1"), partType = partType2, sortOrder = 0),
            WeeklyPart(id = WeeklyPartId("p2"), partType = partType1, sortOrder = 1),
        ),
        programId = programId,
    ).getOrNull()!!

    private val pastWeek = WeekPlan.of(
        id = WeekPlanId("w-past"),
        weekStartDate = LocalDate.of(2026, 3, 2), // before today
        parts = listOf(
            WeeklyPart(id = WeeklyPartId("p3"), partType = partType1, sortOrder = 0),
        ),
        programId = programId,
    ).getOrNull()!!

    private fun assignment(partId: WeeklyPartId, slot: Int) = AssignmentWithPerson(
        weeklyPartId = partId,
        slot = slot,
        fullName = "Test Person",
        spipiledId = null,
        personId = null,
    )

    private fun delivery(partId: WeeklyPartId, weekPlanId: WeekPlanId) = SlipDelivery(
        id = SlipDeliveryId("d-${partId.value}"),
        weeklyPartId = partId,
        weekPlanId = weekPlanId,
        studentName = "Test",
        assistantName = null,
        sentAt = Instant.now(),
        cancelledAt = null,
    )

    @Test
    fun `complete parts without delivery are pending`() = runTest {
        val weekPlanQueries = FakeWeekPlanQueries(
            weeksByProgram = mapOf(programId to listOf(futureWeek)),
        )
        val assignmentRepo = FakeAssignmentRepo(
            byWeekPlanIds = mapOf(
                futureWeek.id to listOf(
                    assignment(WeeklyPartId("p1"), 1),
                    assignment(WeeklyPartId("p1"), 2),
                    assignment(WeeklyPartId("p2"), 1),
                ),
            ),
        )
        val deliveryStore = FakeSlipDeliveryStore()

        val useCase = CaricaRiepilogoConsegneProgrammaUseCase(weekPlanQueries, assignmentRepo, deliveryStore)
        val result = useCase(programId, today)
        assertEquals(ProgramDeliverySnapshot(pending = 2, blocked = 0).right(), result)
    }

    @Test
    fun `incomplete parts are blocked`() = runTest {
        val weekPlanQueries = FakeWeekPlanQueries(
            weeksByProgram = mapOf(programId to listOf(futureWeek)),
        )
        val assignmentRepo = FakeAssignmentRepo(
            byWeekPlanIds = mapOf(
                futureWeek.id to listOf(
                    assignment(WeeklyPartId("p1"), 1), // only 1 of 2 → blocked
                    assignment(WeeklyPartId("p2"), 1), // 1 of 1 → complete, pending
                ),
            ),
        )
        val deliveryStore = FakeSlipDeliveryStore()

        val useCase = CaricaRiepilogoConsegneProgrammaUseCase(weekPlanQueries, assignmentRepo, deliveryStore)
        val result = useCase(programId, today)
        assertEquals(ProgramDeliverySnapshot(pending = 1, blocked = 1).right(), result)
    }

    @Test
    fun `delivered parts are not pending`() = runTest {
        val weekPlanQueries = FakeWeekPlanQueries(
            weeksByProgram = mapOf(programId to listOf(futureWeek)),
        )
        val assignmentRepo = FakeAssignmentRepo(
            byWeekPlanIds = mapOf(
                futureWeek.id to listOf(
                    assignment(WeeklyPartId("p1"), 1),
                    assignment(WeeklyPartId("p1"), 2),
                    assignment(WeeklyPartId("p2"), 1),
                ),
            ),
        )
        val deliveryStore = FakeSlipDeliveryStore().apply {
            // both parts delivered
            activeDeliveries[WeeklyPartId("p1") to futureWeek.id] = delivery(WeeklyPartId("p1"), futureWeek.id)
            activeDeliveries[WeeklyPartId("p2") to futureWeek.id] = delivery(WeeklyPartId("p2"), futureWeek.id)
        }

        val useCase = CaricaRiepilogoConsegneProgrammaUseCase(weekPlanQueries, assignmentRepo, deliveryStore)
        val result = useCase(programId, today)
        assertEquals(ProgramDeliverySnapshot(pending = 0, blocked = 0).right(), result)
    }

    @Test
    fun `past weeks are excluded`() = runTest {
        val weekPlanQueries = FakeWeekPlanQueries(
            weeksByProgram = mapOf(programId to listOf(pastWeek, futureWeek)),
        )
        val assignmentRepo = FakeAssignmentRepo(
            byWeekPlanIds = mapOf(
                // past week has a complete assignment — should be ignored
                pastWeek.id to listOf(assignment(WeeklyPartId("p3"), 1)),
                futureWeek.id to listOf(
                    assignment(WeeklyPartId("p2"), 1),
                ),
            ),
        )
        val deliveryStore = FakeSlipDeliveryStore()

        val useCase = CaricaRiepilogoConsegneProgrammaUseCase(weekPlanQueries, assignmentRepo, deliveryStore)
        val result = useCase(programId, today)
        // Only futureWeek counts: p1 incomplete (blocked), p2 complete (pending)
        assertEquals(ProgramDeliverySnapshot(pending = 1, blocked = 1).right(), result)
    }

    @Test
    fun `skipped weeks are excluded`() = runTest {
        val skippedWeek = WeekPlan.of(
            id = WeekPlanId("w-skipped"),
            weekStartDate = LocalDate.of(2026, 3, 16),
            parts = listOf(WeeklyPart(id = WeeklyPartId("p4"), partType = partType1, sortOrder = 0)),
            programId = programId,
            status = WeekPlanStatus.SKIPPED,
        ).getOrNull()!!
        val weekPlanQueries = FakeWeekPlanQueries(
            weeksByProgram = mapOf(programId to listOf(futureWeek, skippedWeek)),
        )
        val assignmentRepo = FakeAssignmentRepo(byWeekPlanIds = emptyMap())
        val deliveryStore = FakeSlipDeliveryStore()

        val useCase = CaricaRiepilogoConsegneProgrammaUseCase(weekPlanQueries, assignmentRepo, deliveryStore)
        val result = useCase(programId, today)
        // futureWeek: p1 0/2 blocked, p2 0/1 blocked. skippedWeek excluded.
        assertEquals(ProgramDeliverySnapshot(pending = 0, blocked = 2).right(), result)
    }

    @Test
    fun `no weeks returns zero snapshot`() = runTest {
        val weekPlanQueries = FakeWeekPlanQueries(weeksByProgram = emptyMap())
        val assignmentRepo = FakeAssignmentRepo(byWeekPlanIds = emptyMap())
        val deliveryStore = FakeSlipDeliveryStore()

        val useCase = CaricaRiepilogoConsegneProgrammaUseCase(weekPlanQueries, assignmentRepo, deliveryStore)
        val result = useCase(programId, today)
        assertEquals(ProgramDeliverySnapshot(pending = 0, blocked = 0).right(), result)
    }
}

// --- Test fakes ---

private class FakeWeekPlanQueries(
    private val weeksByProgram: Map<ProgramMonthId, List<WeekPlan>> = emptyMap(),
) : WeekPlanQueries {
    override suspend fun findByDate(weekStartDate: LocalDate): WeekPlan? = null
    override suspend fun listInRange(startDate: LocalDate, endDate: LocalDate): List<WeekPlanSummary> = emptyList()
    override suspend fun totalSlotsByWeekInRange(startDate: LocalDate, endDate: LocalDate): Map<WeekPlanId, Int> = emptyMap()
    override suspend fun findByDateAndProgram(weekStartDate: LocalDate, programId: ProgramMonthId): WeekPlan? = null
    override suspend fun listByProgram(programId: ProgramMonthId): List<WeekPlan> =
        weeksByProgram[programId] ?: emptyList()
}

private class FakeAssignmentRepo(
    private val byWeekPlanIds: Map<WeekPlanId, List<AssignmentWithPerson>> = emptyMap(),
) : org.example.project.feature.assignments.application.AssignmentRepository {
    override suspend fun listByWeek(weekPlanId: WeekPlanId): List<AssignmentWithPerson> =
        byWeekPlanIds[weekPlanId] ?: emptyList()
    override suspend fun listByWeekPlanIds(weekPlanIds: Set<WeekPlanId>): Map<WeekPlanId, List<AssignmentWithPerson>> =
        weekPlanIds.associateWith { byWeekPlanIds[it] ?: emptyList() }
    context(tx: org.example.project.core.persistence.TransactionScope)
    override suspend fun save(assignment: org.example.project.feature.assignments.domain.Assignment) {}
    context(tx: org.example.project.core.persistence.TransactionScope)
    override suspend fun removeAllForPerson(personId: org.example.project.feature.proclamatori.domain.ProclamatoreId) {}
    context(tx: org.example.project.core.persistence.TransactionScope)
    override suspend fun deleteByProgramFromDate(programId: ProgramMonthId, fromDate: LocalDate) {}
    override suspend fun countByProgramFromDate(programId: ProgramMonthId, fromDate: LocalDate): Int = 0
    override suspend fun isPersonAssignedInWeek(planId: WeekPlanId, personId: org.example.project.feature.proclamatori.domain.ProclamatoreId): Boolean = false
}
```

> **Note to implementer:** The `AssignmentWithPerson` constructor and `AssignmentRepository` interface may have slightly different fields — check the actual imports. The `spipiledId` field name was seen in test fixtures; it might be a typo for `spiritualId` or similar. Copy the exact field names from existing test fixtures in `AssignmentTestFixtures.kt`.

**Step 2: Run test to verify it fails**

Run: `./gradlew :composeApp:jvmTest --tests "org.example.project.feature.output.application.CaricaRiepilogoConsegneProgrammaUseCaseTest" --no-build-cache`
Expected: FAIL — `CaricaRiepilogoConsegneProgrammaUseCase` does not exist yet.

**Step 3: Write the implementation**

Create `composeApp/src/jvmMain/kotlin/org/example/project/feature/output/application/CaricaRiepilogoConsegneProgrammaUseCase.kt`:

```kotlin
package org.example.project.feature.output.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.feature.assignments.application.AssignmentRepository
import org.example.project.feature.output.domain.ProgramDeliverySnapshot
import org.example.project.feature.output.domain.SlipDeliveryStatus
import org.example.project.feature.output.domain.completePartIds
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.weeklyparts.application.WeekPlanQueries
import org.example.project.feature.weeklyparts.domain.WeekPlanStatus
import java.time.LocalDate

class CaricaRiepilogoConsegneProgrammaUseCase(
    private val weekPlanQueries: WeekPlanQueries,
    private val assignmentRepository: AssignmentRepository,
    private val slipDeliveryStore: SlipDeliveryStore,
) {
    suspend operator fun invoke(
        programId: ProgramMonthId,
        referenceDate: LocalDate,
    ): Either<DomainError, ProgramDeliverySnapshot> = either {
        val weeks = weekPlanQueries.listByProgram(programId)
            .filter { it.weekStartDate >= referenceDate && it.status == WeekPlanStatus.ACTIVE }

        if (weeks.isEmpty()) return@either ProgramDeliverySnapshot(pending = 0, blocked = 0)

        val weekPlanIds = weeks.map { it.id }
        val assignmentsByWeek = assignmentRepository.listByWeekPlanIds(weekPlanIds.toSet())

        // Determine which (weeklyPartId, weekPlanId) pairs are complete
        val completeKeys = weeks.flatMap { week ->
            val weekAssignments = assignmentsByWeek[week.id] ?: emptyList()
            val completeIds = completePartIds(week.parts, weekAssignments)
            completeIds.map { partId -> partId to week.id }
        }.toSet()

        // Count total parts across all active future weeks
        val totalParts = weeks.sumOf { it.parts.size }
        val completeParts = completeKeys.size
        val blocked = totalParts - completeParts

        // For complete parts, check delivery status
        val activeDeliveries = slipDeliveryStore.listActiveDeliveries(weekPlanIds)
        val deliveredKeys = activeDeliveries.map { it.weeklyPartId to it.weekPlanId }.toSet()
        val pending = completeKeys.count { it !in deliveredKeys }

        ProgramDeliverySnapshot(pending = pending, blocked = blocked)
    }
}
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew :composeApp:jvmTest --tests "org.example.project.feature.output.application.CaricaRiepilogoConsegneProgrammaUseCaseTest" --no-build-cache`
Expected: All PASS.

**Step 5: Run full test suite**

Run: `./gradlew :composeApp:jvmTest --no-build-cache`
Expected: All PASS.

**Step 6: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/feature/output/application/CaricaRiepilogoConsegneProgrammaUseCase.kt \
       composeApp/src/jvmTest/kotlin/org/example/project/feature/output/application/CaricaRiepilogoConsegneProgrammaUseCaseTest.kt
git commit -m "Add CaricaRiepilogoConsegneProgrammaUseCase with tests"
```

---

### Task 4: Wire `CaricaRiepilogoConsegneProgrammaUseCase` in Koin DI

**Files:**
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/feature/output/di/OutputModule.kt`

**Step 1: Add the use case registration**

In `OutputModule.kt`, after line 30 (`single { VerificaConsegnaPreAssegnazioneUseCase(get()) }`), add:

```kotlin
    single { CaricaRiepilogoConsegneProgrammaUseCase(get(), get(), get()) }
```

Add the import at the top:
```kotlin
import org.example.project.feature.output.application.CaricaRiepilogoConsegneProgrammaUseCase
```

**Step 2: Run build to verify**

Run: `./gradlew :composeApp:jvmTest --no-build-cache`
Expected: PASS.

**Step 3: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/feature/output/di/OutputModule.kt
git commit -m "Wire CaricaRiepilogoConsegneProgrammaUseCase in Koin DI"
```

---

### Task 5: Add `deliverySnapshot` to ViewModel and load on program selection

**Files:**
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/AssignmentManagementViewModel.kt`

This task adds the pre-loaded badge state to `AssignmentManagementUiState` and a `loadDeliverySummary()` method.

**Step 1: Add state field and load method**

In `AssignmentManagementUiState` (line 50), add a new field:

```kotlin
    val deliverySnapshot: ProgramDeliverySnapshot? = null,
    val isLoadingDeliverySnapshot: Boolean = false,
```

Add the import:
```kotlin
import org.example.project.feature.output.domain.ProgramDeliverySnapshot
```

In `AssignmentManagementViewModel` (line 91), add `CaricaRiepilogoConsegneProgrammaUseCase` as a constructor dependency:

```kotlin
    private val caricaRiepilogo: CaricaRiepilogoConsegneProgrammaUseCase,
```

Add a new method:

```kotlin
    fun loadDeliverySummary(programId: ProgramMonthId, referenceDate: LocalDate) {
        scope.launch {
            _uiState.update { it.copy(isLoadingDeliverySnapshot = true) }
            caricaRiepilogo(programId, referenceDate).fold(
                ifLeft = { _uiState.update { it.copy(isLoadingDeliverySnapshot = false) } },
                ifRight = { snapshot ->
                    _uiState.update { it.copy(deliverySnapshot = snapshot, isLoadingDeliverySnapshot = false) }
                },
            )
        }
    }
```

Add the import:
```kotlin
import org.example.project.feature.output.application.CaricaRiepilogoConsegneProgrammaUseCase
```

**Step 2: Remove `ticketBadgeText` computed property**

The old `ticketBadgeText` (lines 71-88) computed from in-dialog state is no longer needed for the pre-loaded badge. However, it's still useful inside the dialog. Instead of removing it, keep it but also add a new computed property for the pre-loaded badge:

In `AssignmentManagementUiState`, add after `ticketBadgeText`:

```kotlin
    val preloadedBadge: ProgramDeliverySnapshot? get() = deliverySnapshot
```

Actually, the `deliverySnapshot` field is already enough. The UI will read `deliverySnapshot` directly.

**Step 3: Refresh badge after state-changing actions**

Add a private field to remember the current program context:

```kotlin
    private var currentProgramId: ProgramMonthId? = null
    private var currentReferenceDate: LocalDate? = null
```

Update `loadDeliverySummary` to also save the context:

```kotlin
    fun loadDeliverySummary(programId: ProgramMonthId, referenceDate: LocalDate) {
        currentProgramId = programId
        currentReferenceDate = referenceDate
        scope.launch {
            _uiState.update { it.copy(isLoadingDeliverySnapshot = true) }
            caricaRiepilogo(programId, referenceDate).fold(
                ifLeft = { _uiState.update { it.copy(isLoadingDeliverySnapshot = false) } },
                ifRight = { snapshot ->
                    _uiState.update { it.copy(deliverySnapshot = snapshot, isLoadingDeliverySnapshot = false) }
                },
            )
        }
    }

    private fun refreshDeliverySummary() {
        val pid = currentProgramId ?: return
        val ref = currentReferenceDate ?: return
        loadDeliverySummary(pid, ref)
    }
```

Call `refreshDeliverySummary()` at the end of each state-changing action's success path:
- `markAsDelivered()` — after `loadDeliveryStatus`, add `refreshDeliverySummary()`
- `autoAssignSelectedProgram()` — inside `if (shouldReload)`, add `refreshDeliverySummary()`
- `confirmClearAssignments()` — inside `if (shouldReload)`, add `refreshDeliverySummary()`
- `confirmClearWeekAssignments()` — inside `if (succeeded)`, add `refreshDeliverySummary()`

**Step 4: Run build**

Run: `./gradlew :composeApp:jvmTest --no-build-cache`
Expected: Compilation may fail if the Koin module or the ViewModel instantiation in tests doesn't pass the new dependency. Fix by adding the dependency where `AssignmentManagementViewModel` is constructed.

Check where the ViewModel is created:

Search for `AssignmentManagementViewModel(` in the codebase. It's likely in `ProgramWorkspaceScreen.kt` via Koin `get()`. The DI is set up in `WorkspaceModule.kt` or similar — find and add `get()` for the new parameter.

**Step 5: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/AssignmentManagementViewModel.kt
git commit -m "Add delivery snapshot pre-load to AssignmentManagementViewModel"
```

---

### Task 6: Wire ViewModel DI and trigger badge load on program selection

**Files:**
- Modify: DI module where `AssignmentManagementViewModel` is instantiated (search for `AssignmentManagementViewModel(` in DI/screen code)
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceScreen.kt`

**Step 1: Update DI wiring**

Find where `AssignmentManagementViewModel` is constructed (likely Koin module or `ProgramWorkspaceScreen`). Add `get()` for the new `caricaRiepilogo` parameter.

**Step 2: Call `loadDeliverySummary` on program selection**

In `ProgramWorkspaceScreen.kt`, find where `lifecycleVM.selectProgram()` is called or where the selected program changes. After that call, also invoke:

```kotlin
assignmentVM.loadDeliverySummary(programId, currentMonday)
```

Also call it on initial screen load when a program is already selected:

```kotlin
LaunchedEffect(lifecycleState.selectedProgramId) {
    lifecycleState.selectedProgramId?.let { programId ->
        assignmentVM.loadDeliverySummary(programId, currentMonday)
    }
}
```

> **Important:** `currentMonday` is the reference date already used in the screen for other operations. Use the same value.

**Step 3: Run full tests**

Run: `./gradlew :composeApp:jvmTest --no-build-cache`
Expected: PASS.

**Step 4: Commit**

```bash
git add -A  # DI module + ProgramWorkspaceScreen.kt
git commit -m "Wire delivery summary load on program selection"
```

---

### Task 7: Filter past weeks from ticket dialog

**Files:**
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/AssignmentManagementViewModel.kt:242-277`

**Step 1: Add date filter to `openAssignmentTickets`**

In `openAssignmentTickets()`, the `successUpdate` lambda receives `result.tickets` and `result.warnings`. Filter them before storing:

Change the `successUpdate` (lines 254-262) to:

```kotlin
                successUpdate = { state, result ->
                    val today = LocalDate.now()
                    val futureTickets = result.tickets.filter { it.weekStart >= today }
                    val futureWarnings = result.warnings.filter { it.weekStart >= today }
                    scope.launch { loadDeliveryStatus(futureTickets) }
                    state.copy(
                        isAssignmentTicketsDialogOpen = true,
                        isLoadingAssignmentTickets = false,
                        assignmentTickets = futureTickets,
                        assignmentPartWarnings = futureWarnings,
                        assignmentTicketsError = null,
                    )
                },
```

> **Design note:** We use `LocalDate.now()` here rather than injecting a clock because the ViewModel already uses `LocalDate.now()` elsewhere. Consistency is more important than testability for this UI-level filter — the use case already handles the `referenceDate` parameter correctly.

**Step 2: Run tests**

Run: `./gradlew :composeApp:jvmTest --no-build-cache`
Expected: PASS.

**Step 3: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/AssignmentManagementViewModel.kt
git commit -m "Filter past weeks from ticket dialog"
```

---

### Task 8: Create `DeliveryBadge` composable

**Files:**
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/ui/components/workspace/DeliveryBadge.kt`

This task creates the visually styled badge composable using Material Design 3 colors.

**Step 1: Write the composable**

Create `composeApp/src/jvmMain/kotlin/org/example/project/ui/components/workspace/DeliveryBadge.kt`:

```kotlin
package org.example.project.ui.components.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.example.project.feature.output.domain.ProgramDeliverySnapshot

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeliveryBadge(
    snapshot: ProgramDeliverySnapshot,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when {
            snapshot.allDelivered -> {
                DeliveryChip(
                    indicator = "\u2713", // ✓
                    label = "Tutti inviati",
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            else -> {
                if (snapshot.pending > 0) {
                    DeliveryChip(
                        indicator = "\u25CF", // ●
                        label = "${snapshot.pending} da inviare",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        indicatorColor = MaterialTheme.colorScheme.primary,
                    )
                }
                if (snapshot.blocked > 0) {
                    DeliveryChip(
                        indicator = "\u25B2", // ▲
                        label = "${snapshot.blocked} bloccati",
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        indicatorColor = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun DeliveryChip(
    indicator: String,
    label: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    indicatorColor: androidx.compose.ui.graphics.Color = contentColor,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = indicator,
                style = MaterialTheme.typography.labelSmall,
                color = indicatorColor,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
```

**Step 2: Run build**

Run: `./gradlew :composeApp:jvmTest --no-build-cache`
Expected: PASS (compilation check; no unit tests needed for a pure UI composable).

**Step 3: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/ui/components/workspace/DeliveryBadge.kt
git commit -m "Add DeliveryBadge composable with M3 chip styling"
```

---

### Task 9: Replace plain text badge with `DeliveryBadge` in screen

**Files:**
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceScreen.kt:793-801`

**Step 1: Replace the old badge text**

Replace lines 793-801 (the `assignmentState.ticketBadgeText?.let { ... }` block) with:

```kotlin
                                assignmentState.deliverySnapshot?.let { snapshot ->
                                    if (snapshot.pending > 0 || snapshot.blocked > 0 || snapshot.allDelivered) {
                                        DeliveryBadge(
                                            snapshot = snapshot,
                                            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp),
                                        )
                                    }
                                }
```

Add the import:
```kotlin
import org.example.project.ui.components.workspace.DeliveryBadge
```

> **Design note:** Show nothing if `deliverySnapshot` is null (still loading or no program selected). Show the badge chips as soon as data is available. The `allDelivered` case shows the green "Tutti inviati" chip.

**Step 2: Run build**

Run: `./gradlew :composeApp:jvmTest --no-build-cache`
Expected: PASS.

**Step 3: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceScreen.kt
git commit -m "Replace plain text badge with DeliveryBadge composable"
```

---

### Task 10: Update `AssignmentManagementViewModelTest` for new constructor parameter

**Files:**
- Modify: `composeApp/src/jvmTest/kotlin/org/example/project/ui/workspace/AssignmentManagementViewModelTest.kt`

**Step 1: Find all places where `AssignmentManagementViewModel` is constructed in tests**

Search for `AssignmentManagementViewModel(` in the test file. Add the new `caricaRiepilogo` parameter using a fake or a no-op implementation.

Create a minimal fake:

```kotlin
private val fakeCaricaRiepilogo = CaricaRiepilogoConsegneProgrammaUseCase(
    weekPlanQueries = /* fake */,
    assignmentRepository = /* fake */,
    slipDeliveryStore = /* fake */,
)
```

Or simpler — since the tests don't exercise the badge, create a stub:

```kotlin
private val noopRiepilogo = object {
    suspend operator fun invoke(programId: ProgramMonthId, referenceDate: LocalDate) =
        ProgramDeliverySnapshot(0, 0).right()
}
```

Actually, since `CaricaRiepilogoConsegneProgrammaUseCase` is a concrete class (not an interface), you need to construct it with real fakes. Use the same `FakeWeekPlanQueries` and `FakeSlipDeliveryStore` patterns from OutputTestFixtures.

The simplest approach: construct with empty fakes that return no data. The badge will just compute `ProgramDeliverySnapshot(0, 0)`.

**Step 2: Run tests**

Run: `./gradlew :composeApp:jvmTest --no-build-cache`
Expected: PASS.

**Step 3: Commit**

```bash
git add composeApp/src/jvmTest/kotlin/org/example/project/ui/workspace/AssignmentManagementViewModelTest.kt
git commit -m "Update AssignmentManagementViewModelTest for new constructor parameter"
```

---

### Task 11: Clean up old `ticketBadgeText` if no longer needed

**Files:**
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/AssignmentManagementViewModel.kt`

**Step 1: Check if `ticketBadgeText` is still used anywhere**

Search for `ticketBadgeText` in the codebase. If it's only used in the old badge location that was replaced in Task 9, and the dialog doesn't use it either, remove it from `AssignmentManagementUiState`.

If the dialog still uses it for its own section headers — keep it.

Looking at the dialog code: the dialog separates tickets into "Da inviare" / "Inviati" sections based on `deliveryStatus` map, NOT `ticketBadgeText`. So `ticketBadgeText` was only used for the external badge.

**Step 2: Remove if unused**

If `ticketBadgeText` is only referenced in ProgramWorkspaceScreen.kt (which now uses `deliverySnapshot`), remove the computed property from `AssignmentManagementUiState`.

**Step 3: Run tests**

Run: `./gradlew :composeApp:jvmTest --no-build-cache`
Expected: PASS.

**Step 4: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/AssignmentManagementViewModel.kt
git commit -m "Remove unused ticketBadgeText computed property"
```

---

### Task 12: Final verification

**Step 1: Run full test suite**

Run: `./gradlew :composeApp:jvmTest --no-build-cache`
Expected: All PASS.

**Step 2: Build the uber jar and visually verify**

Run: `./gradlew clean && ./gradlew :composeApp:packageUberJarForCurrentOS`

Test manually:
1. Open the app
2. Select a program
3. Verify the badge appears under "Biglietti assegnazioni" without clicking the button
4. Verify past weeks don't appear in the ticket dialog
5. Assign/unassign someone → verify badge updates
6. Mark as delivered → verify badge updates

**Step 3: Commit any final fixes**

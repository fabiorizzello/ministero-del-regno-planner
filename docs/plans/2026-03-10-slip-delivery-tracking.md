# Slip Delivery Tracking — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Track which S-89 assignment slips have been delivered, separate them in the dialog, show a badge on the program button, and warn when reassigning a delivered slip.

**Architecture:** New `SlipDelivery` entity in `feature/output` with its own store interface + SQLDelight implementation. Use cases follow the project's `Either<DomainError, T>` + `TransactionRunner.runInTransaction` pattern. UI changes are in `ProgramWorkspaceComponents.kt` (dialog) and `ProgramWorkspaceScreen.kt` (badge). The reassignment warning hooks into `PersonPickerViewModel.confirmAssignment()`.

**Tech Stack:** Kotlin, SQLDelight (single `.sq` file), Arrow Either, Koin DI, Compose Multiplatform (Material Design 3, light theme).

**Codebase patterns to follow** (from `codebase-patterns` skill):
- Store mutation methods MUST have `context(tx: TransactionScope)` on the **interface** (not just implementation).
- Read-only store methods do NOT have the context.
- Use cases that mutate open exactly 1 transaction via `transactionRunner.runInTransaction { either { ... } }`.
- No `throw`/`IllegalStateException` for domain errors — use `raise(DomainError.xxx)`.
- No private exception classes as control flow.
- Test fakes MUST repeat `context(tx: TransactionScope)` on overrides.
- All Koin DI bindings updated in the same commit as constructor changes.

---

## Task 1: Add `slip_delivery` table to SQLDelight schema

**Files:**
- Modify: `composeApp/src/commonMain/sqldelight/org/example/project/db/MinisteroDatabase.sq`

**Step 1: Add DDL + queries to the `.sq` file**

After the `assignment` table and its indexes (around line 153), add:

```sql
CREATE TABLE slip_delivery (
    id TEXT NOT NULL PRIMARY KEY,
    weekly_part_id TEXT NOT NULL,
    week_plan_id TEXT NOT NULL,
    student_name TEXT NOT NULL,
    assistant_name TEXT,
    sent_at TEXT NOT NULL,
    cancelled_at TEXT
);

CREATE INDEX IF NOT EXISTS slip_delivery_part_week_idx
ON slip_delivery(weekly_part_id, week_plan_id);

-- Slip delivery queries

findActiveDelivery:
SELECT id, weekly_part_id, week_plan_id, student_name, assistant_name, sent_at, cancelled_at
FROM slip_delivery
WHERE weekly_part_id = ? AND week_plan_id = ? AND cancelled_at IS NULL
LIMIT 1;

findLastCancelledDelivery:
SELECT id, weekly_part_id, week_plan_id, student_name, assistant_name, sent_at, cancelled_at
FROM slip_delivery
WHERE weekly_part_id = ? AND week_plan_id = ? AND cancelled_at IS NOT NULL
ORDER BY cancelled_at DESC
LIMIT 1;

listActiveDeliveriesByWeekPlanIds:
SELECT id, weekly_part_id, week_plan_id, student_name, assistant_name, sent_at, cancelled_at
FROM slip_delivery
WHERE week_plan_id IN ?
AND cancelled_at IS NULL;

listCancelledDeliveriesByWeekPlanIds:
SELECT id, weekly_part_id, week_plan_id, student_name, assistant_name, sent_at, cancelled_at
FROM slip_delivery
WHERE week_plan_id IN ?
AND cancelled_at IS NOT NULL;

insertDelivery:
INSERT INTO slip_delivery(id, weekly_part_id, week_plan_id, student_name, assistant_name, sent_at, cancelled_at)
VALUES (?, ?, ?, ?, ?, ?, NULL);

cancelDelivery:
UPDATE slip_delivery
SET cancelled_at = ?
WHERE id = ?;
```

**Step 2: Verify the schema compiles**

Run: `./gradlew :composeApp:generateCommonMainMinisteroDatabaseInterface`
Expected: BUILD SUCCESSFUL (SQLDelight generates the query interface)

**Step 3: Commit**

```bash
git add composeApp/src/commonMain/sqldelight/org/example/project/db/MinisteroDatabase.sq
git commit -m "[US4] Add slip_delivery table and queries to SQLDelight schema"
```

---

## Task 2: Create `SlipDelivery` domain model and `SlipDeliveryStore` interface

**Files:**
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/output/domain/SlipDelivery.kt`
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/output/application/SlipDeliveryStore.kt`

**Step 1: Write the domain model**

```kotlin
package org.example.project.feature.output.domain

import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.Instant

@JvmInline
value class SlipDeliveryId(val value: String)

data class SlipDelivery(
    val id: SlipDeliveryId,
    val weeklyPartId: WeeklyPartId,
    val weekPlanId: String,
    val studentName: String,
    val assistantName: String?,
    val sentAt: Instant,
    val cancelledAt: Instant?,
) {
    val isActive: Boolean get() = cancelledAt == null
}

/**
 * Stato derivato per ogni biglietto nella dialog.
 * - DA_INVIARE: nessuna consegna attiva.
 * - INVIATO: consegna attiva presente.
 * - DA_REINVIARE: consegna precedente annullata, nessuna attiva.
 */
enum class SlipDeliveryStatus {
    DA_INVIARE,
    INVIATO,
    DA_REINVIARE,
}

data class SlipDeliveryInfo(
    val status: SlipDeliveryStatus,
    val activeDelivery: SlipDelivery?,
    val previousStudentName: String?,
)
```

**Step 2: Write the store interface**

```kotlin
package org.example.project.feature.output.application

import org.example.project.core.persistence.TransactionScope
import org.example.project.feature.output.domain.SlipDelivery
import org.example.project.feature.output.domain.SlipDeliveryId
import org.example.project.feature.weeklyparts.domain.WeeklyPartId

interface SlipDeliveryStore {
    /** Read: no TransactionScope needed. */
    suspend fun findActiveDelivery(weeklyPartId: WeeklyPartId, weekPlanId: String): SlipDelivery?
    suspend fun findLastCancelledDelivery(weeklyPartId: WeeklyPartId, weekPlanId: String): SlipDelivery?
    suspend fun listActiveDeliveries(weekPlanIds: List<String>): List<SlipDelivery>
    suspend fun listCancelledDeliveries(weekPlanIds: List<String>): List<SlipDelivery>

    /** Mutation: requires TransactionScope. */
    context(tx: TransactionScope) suspend fun insert(delivery: SlipDelivery)
    context(tx: TransactionScope) suspend fun cancel(id: SlipDeliveryId, cancelledAt: java.time.Instant)
}
```

**Step 3: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/feature/output/domain/SlipDelivery.kt \
       composeApp/src/jvmMain/kotlin/org/example/project/feature/output/application/SlipDeliveryStore.kt
git commit -m "[US4] Add SlipDelivery domain model and SlipDeliveryStore interface"
```

---

## Task 3: Implement `SqlDelightSlipDeliveryStore`

**Files:**
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/output/infrastructure/SqlDelightSlipDeliveryStore.kt`
- Create: `composeApp/src/jvmTest/kotlin/org/example/project/feature/output/infrastructure/SqlDelightSlipDeliveryStoreTest.kt`

**Step 1: Write the failing test**

```kotlin
package org.example.project.feature.output.infrastructure

import kotlinx.coroutines.runBlocking
import org.example.project.core.persistence.TransactionScope
import org.example.project.feature.output.domain.SlipDelivery
import org.example.project.feature.output.domain.SlipDeliveryId
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// Uses the real DB; follow existing integration test patterns (see SqlDelightAssignmentStoreTest)

class SqlDelightSlipDeliveryStoreTest {
    // TODO: setup real DB (copy pattern from existing store tests)

    @Test
    fun `insert and findActive returns delivery`() = runBlocking {
        val delivery = SlipDelivery(
            id = SlipDeliveryId("d1"),
            weeklyPartId = WeeklyPartId("wp1"),
            weekPlanId = "plan1",
            studentName = "Mario Rossi",
            assistantName = "Luigi Bianchi",
            sentAt = Instant.parse("2026-03-10T10:00:00Z"),
            cancelledAt = null,
        )
        // insert within transaction, then findActive
        // assert: found == delivery
    }

    @Test
    fun `cancel sets cancelledAt and findActive returns null`() = runBlocking {
        // insert, cancel, findActive -> null
        // findLastCancelled -> has cancelledAt set
    }

    @Test
    fun `listActiveDeliveries returns only active`() = runBlocking {
        // insert 2 deliveries for different parts, cancel 1
        // listActive -> only the non-cancelled one
    }
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew :composeApp:jvmTest --tests "*SqlDelightSlipDeliveryStoreTest*" --rerun`
Expected: FAIL (class not found / not implemented)

**Step 3: Write the implementation**

```kotlin
package org.example.project.feature.output.infrastructure

import org.example.project.core.persistence.TransactionScope
import org.example.project.db.MinisteroDatabase
import org.example.project.feature.output.application.SlipDeliveryStore
import org.example.project.feature.output.domain.SlipDelivery
import org.example.project.feature.output.domain.SlipDeliveryId
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.Instant

class SqlDelightSlipDeliveryStore(
    private val database: MinisteroDatabase,
) : SlipDeliveryStore {

    override suspend fun findActiveDelivery(weeklyPartId: WeeklyPartId, weekPlanId: String): SlipDelivery? =
        database.ministeroDatabaseQueries.findActiveDelivery(weeklyPartId.value, weekPlanId)
            .executeAsOneOrNull()
            ?.let(::mapRow)

    override suspend fun findLastCancelledDelivery(weeklyPartId: WeeklyPartId, weekPlanId: String): SlipDelivery? =
        database.ministeroDatabaseQueries.findLastCancelledDelivery(weeklyPartId.value, weekPlanId)
            .executeAsOneOrNull()
            ?.let(::mapRow)

    override suspend fun listActiveDeliveries(weekPlanIds: List<String>): List<SlipDelivery> {
        if (weekPlanIds.isEmpty()) return emptyList()
        return database.ministeroDatabaseQueries.listActiveDeliveriesByWeekPlanIds(weekPlanIds)
            .executeAsList()
            .map(::mapRow)
    }

    override suspend fun listCancelledDeliveries(weekPlanIds: List<String>): List<SlipDelivery> {
        if (weekPlanIds.isEmpty()) return emptyList()
        return database.ministeroDatabaseQueries.listCancelledDeliveriesByWeekPlanIds(weekPlanIds)
            .executeAsList()
            .map(::mapRow)
    }

    context(tx: TransactionScope)
    override suspend fun insert(delivery: SlipDelivery) {
        database.ministeroDatabaseQueries.insertDelivery(
            id = delivery.id.value,
            weekly_part_id = delivery.weeklyPartId.value,
            week_plan_id = delivery.weekPlanId,
            student_name = delivery.studentName,
            assistant_name = delivery.assistantName,
            sent_at = delivery.sentAt.toString(),
        )
    }

    context(tx: TransactionScope)
    override suspend fun cancel(id: SlipDeliveryId, cancelledAt: Instant) {
        database.ministeroDatabaseQueries.cancelDelivery(
            cancelled_at = cancelledAt.toString(),
            id = id.value,
        )
    }

    private fun mapRow(row: /* generated type */): SlipDelivery =
        SlipDelivery(
            id = SlipDeliveryId(row.id),
            weeklyPartId = WeeklyPartId(row.weekly_part_id),
            weekPlanId = row.week_plan_id,
            studentName = row.student_name,
            assistantName = row.assistant_name,
            sentAt = Instant.parse(row.sent_at),
            cancelledAt = row.cancelled_at?.let(Instant::parse),
        )
}
```

Note: the `mapRow` parameter type will be the SQLDelight-generated row type. Use the same naming pattern as existing store implementations (e.g., `SqlDelightAssignmentStore`).

**Step 4: Complete the tests with real DB setup and run**

Run: `./gradlew :composeApp:jvmTest --tests "*SqlDelightSlipDeliveryStoreTest*" --rerun`
Expected: PASS

**Step 5: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/feature/output/infrastructure/SqlDelightSlipDeliveryStore.kt \
       composeApp/src/jvmTest/kotlin/org/example/project/feature/output/infrastructure/SqlDelightSlipDeliveryStoreTest.kt
git commit -m "[US4] Implement SqlDelightSlipDeliveryStore with integration tests"
```

---

## Task 4: Add `weeklyPartId` to `AssignmentTicketImage`

The delivery tracking needs to link tickets to `(weeklyPartId, weekPlanId)`. Currently `AssignmentTicketImage` has no `weeklyPartId` field.

**Files:**
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/feature/output/application/GeneraImmaginiAssegnazioni.kt`
  - Add `weeklyPartId: WeeklyPartId` and `weekPlanId: String` fields to `AssignmentTicketImage`
  - Add `weeklyPartId: WeeklyPartId` to `AssignmentSlipWithOrder`
  - Pass `weeklyPartId` through `buildAssignmentSlips()` → `generateProgramTickets()`
- Modify: `composeApp/src/jvmTest/kotlin/org/example/project/feature/output/application/GeneraImmaginiAssegnazioniTest.kt`
  - Update assertions to check new fields

**Step 1: Update `AssignmentTicketImage` data class**

In `GeneraImmaginiAssegnazioni.kt`, line 39-45:

```kotlin
data class AssignmentTicketImage(
    val fullName: String,
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val imagePath: Path,
    val assignments: List<AssignmentTicketLine>,
    val weeklyPartId: WeeklyPartId,
    val weekPlanId: String,
)
```

**Step 2: Update `AssignmentSlipWithOrder` to carry `weeklyPartId` and `weekPlanId`**

In `GeneraImmaginiAssegnazioni.kt`, line 63-68:

```kotlin
private data class AssignmentSlipWithOrder(
    val slip: PdfAssignmentsRenderer.AssignmentSlip,
    val sortOrder: Int,
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val weeklyPartId: WeeklyPartId,
    val weekPlanId: String,
)
```

**Step 3: Update `buildAssignmentSlips()` to populate the new fields**

In the `mapNotNull` block (~line 232), add:
```kotlin
weeklyPartId = part.id,
weekPlanId = weekPlan.id.value,
```

**Step 4: Update `generateProgramTickets()` to pass the new fields**

In the `.map` block (~line 156-168), add:
```kotlin
weeklyPartId = slipWithOrder.weeklyPartId,
weekPlanId = slipWithOrder.weekPlanId,
```

**Step 5: Update tests**

Run: `./gradlew :composeApp:jvmTest --tests "*GeneraImmaginiAssegnazioniTest*" --rerun`
Expected: compilation failure (new required fields). Fix test assertions to include the new fields.

**Step 6: Run tests**

Run: `./gradlew :composeApp:jvmTest --tests "*GeneraImmaginiAssegnazioniTest*" --rerun`
Expected: PASS

**Step 7: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/feature/output/application/GeneraImmaginiAssegnazioni.kt \
       composeApp/src/jvmTest/kotlin/org/example/project/feature/output/application/GeneraImmaginiAssegnazioniTest.kt
git commit -m "[US4] Add weeklyPartId and weekPlanId to AssignmentTicketImage for delivery tracking"
```

---

## Task 5: Create `SegnaComInviatoUseCase`

**Files:**
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/output/application/SegnaComInviatoUseCase.kt`
- Create: `composeApp/src/jvmTest/kotlin/org/example/project/feature/output/application/SegnaComInviatoUseCaseTest.kt`

**Step 1: Write the failing test**

```kotlin
package org.example.project.feature.output.application

import arrow.core.Either
import kotlinx.coroutines.runBlocking
import org.example.project.feature.output.domain.SlipDelivery
import org.example.project.feature.output.domain.SlipDeliveryId
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertEquals

class SegnaComInviatoUseCaseTest {

    @Test
    fun `marks slip as delivered creates new delivery record`() = runBlocking {
        val store = FakeSlipDeliveryStore()
        val useCase = SegnaComInviatoUseCase(store, FakeTransactionRunner())

        val result = useCase(
            weeklyPartId = WeeklyPartId("wp1"),
            weekPlanId = "plan1",
            studentName = "Mario Rossi",
            assistantName = "Luigi Bianchi",
        )

        assertIs<Either.Right<Unit>>(result)
        assertEquals(1, store.inserted.size)
        assertEquals("Mario Rossi", store.inserted[0].studentName)
        Unit
    }

    @Test
    fun `idempotent - does not create duplicate if active delivery exists`() = runBlocking {
        val store = FakeSlipDeliveryStore()
        store.activeDeliveries[WeeklyPartId("wp1") to "plan1"] = SlipDelivery(
            id = SlipDeliveryId("existing"),
            weeklyPartId = WeeklyPartId("wp1"),
            weekPlanId = "plan1",
            studentName = "Mario Rossi",
            assistantName = null,
            sentAt = Instant.now(),
            cancelledAt = null,
        )
        val useCase = SegnaComInviatoUseCase(store, FakeTransactionRunner())

        val result = useCase(
            weeklyPartId = WeeklyPartId("wp1"),
            weekPlanId = "plan1",
            studentName = "Mario Rossi",
            assistantName = null,
        )

        assertIs<Either.Right<Unit>>(result)
        assertEquals(0, store.inserted.size) // no new insert
        Unit
    }
}
```

Note: `FakeSlipDeliveryStore` and `FakeTransactionRunner` test fakes are needed. `FakeTransactionRunner` already exists in test fixtures (see review-notes Medium 62 — multiple copies exist; use whichever is in scope or import from centralized location). Create `FakeSlipDeliveryStore` in a test fixtures file for the output feature.

**Step 2: Run tests to verify they fail**

Run: `./gradlew :composeApp:jvmTest --tests "*SegnaComInviatoUseCaseTest*" --rerun`
Expected: FAIL

**Step 3: Write the implementation**

```kotlin
package org.example.project.feature.output.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionRunner
import org.example.project.feature.output.domain.SlipDelivery
import org.example.project.feature.output.domain.SlipDeliveryId
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.Instant
import java.util.UUID

class SegnaComInviatoUseCase(
    private val store: SlipDeliveryStore,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(
        weeklyPartId: WeeklyPartId,
        weekPlanId: String,
        studentName: String,
        assistantName: String?,
    ): Either<DomainError, Unit> = transactionRunner.runInTransaction {
        either {
            // Idempotent: skip if already active
            val existing = store.findActiveDelivery(weeklyPartId, weekPlanId)
            if (existing != null) return@either

            store.insert(
                SlipDelivery(
                    id = SlipDeliveryId(UUID.randomUUID().toString()),
                    weeklyPartId = weeklyPartId,
                    weekPlanId = weekPlanId,
                    studentName = studentName,
                    assistantName = assistantName,
                    sentAt = Instant.now(),
                    cancelledAt = null,
                )
            )
        }
    }
}
```

**Codebase-patterns check:** `findActiveDelivery` is read-only (no `context(TransactionScope)`) — but here it's called inside `runInTransaction`. That's fine: read-only methods work both inside and outside transactions. Only mutation methods are gate-locked by `context(TransactionScope)`. The `store.insert(...)` call compiles because we're inside `runInTransaction` which provides `TransactionScope`.

**Step 4: Run tests**

Run: `./gradlew :composeApp:jvmTest --tests "*SegnaComInviatoUseCaseTest*" --rerun`
Expected: PASS

**Step 5: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/feature/output/application/SegnaComInviatoUseCase.kt \
       composeApp/src/jvmTest/kotlin/org/example/project/feature/output/application/SegnaComInviatoUseCaseTest.kt
git commit -m "[US4] Add SegnaComInviatoUseCase with idempotent delivery tracking"
```

---

## Task 6: Create `AnnullaConsegnaUseCase`

**Files:**
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/output/application/AnnullaConsegnaUseCase.kt`
- Create: `composeApp/src/jvmTest/kotlin/org/example/project/feature/output/application/AnnullaConsegnaUseCaseTest.kt`

**Step 1: Write the failing test**

```kotlin
package org.example.project.feature.output.application

import arrow.core.Either
import kotlinx.coroutines.runBlocking
import org.example.project.feature.output.domain.SlipDelivery
import org.example.project.feature.output.domain.SlipDeliveryId
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class AnnullaConsegnaUseCaseTest {

    @Test
    fun `cancels active delivery for the given part and week`() = runBlocking {
        val store = FakeSlipDeliveryStore()
        store.activeDeliveries[WeeklyPartId("wp1") to "plan1"] = SlipDelivery(
            id = SlipDeliveryId("d1"),
            weeklyPartId = WeeklyPartId("wp1"),
            weekPlanId = "plan1",
            studentName = "Mario Rossi",
            assistantName = null,
            sentAt = Instant.now(),
            cancelledAt = null,
        )
        val useCase = AnnullaConsegnaUseCase(store, FakeTransactionRunner())

        val result = useCase(weeklyPartId = WeeklyPartId("wp1"), weekPlanId = "plan1")

        assertIs<Either.Right<Unit>>(result)
        assertNotNull(store.cancelledIds.find { it == SlipDeliveryId("d1") })
        Unit
    }

    @Test
    fun `noop if no active delivery exists`() = runBlocking {
        val store = FakeSlipDeliveryStore()
        val useCase = AnnullaConsegnaUseCase(store, FakeTransactionRunner())

        val result = useCase(weeklyPartId = WeeklyPartId("wp1"), weekPlanId = "plan1")

        assertIs<Either.Right<Unit>>(result)
        assertEquals(0, store.cancelledIds.size)
        Unit
    }
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew :composeApp:jvmTest --tests "*AnnullaConsegnaUseCaseTest*" --rerun`
Expected: FAIL

**Step 3: Write the implementation**

```kotlin
package org.example.project.feature.output.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionRunner
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.Instant

class AnnullaConsegnaUseCase(
    private val store: SlipDeliveryStore,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(
        weeklyPartId: WeeklyPartId,
        weekPlanId: String,
    ): Either<DomainError, Unit> = transactionRunner.runInTransaction {
        either {
            val active = store.findActiveDelivery(weeklyPartId, weekPlanId)
                ?: return@either // nothing to cancel
            store.cancel(active.id, Instant.now())
        }
    }
}
```

**Step 4: Run tests**

Run: `./gradlew :composeApp:jvmTest --tests "*AnnullaConsegnaUseCaseTest*" --rerun`
Expected: PASS

**Step 5: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/feature/output/application/AnnullaConsegnaUseCase.kt \
       composeApp/src/jvmTest/kotlin/org/example/project/feature/output/application/AnnullaConsegnaUseCaseTest.kt
git commit -m "[US4] Add AnnullaConsegnaUseCase for cancelling delivered slips"
```

---

## Task 7: Create `CaricaStatoConsegneUseCase`

Loads delivery status for all tickets of a program. Returns a map from `(weeklyPartId, weekPlanId)` to `SlipDeliveryInfo`.

**Files:**
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/output/application/CaricaStatoConsegneUseCase.kt`
- Create: `composeApp/src/jvmTest/kotlin/org/example/project/feature/output/application/CaricaStatoConsegneUseCaseTest.kt`

**Step 1: Write the failing test**

```kotlin
package org.example.project.feature.output.application

import kotlinx.coroutines.runBlocking
import org.example.project.feature.output.domain.SlipDelivery
import org.example.project.feature.output.domain.SlipDeliveryId
import org.example.project.feature.output.domain.SlipDeliveryStatus
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class CaricaStatoConsegneUseCaseTest {

    @Test
    fun `returns DA_INVIARE when no delivery exists`() = runBlocking {
        val store = FakeSlipDeliveryStore()
        val useCase = CaricaStatoConsegneUseCase(store)

        val result = useCase(listOf("plan1"))

        // No deliveries at all -> empty map (tickets without entries are DA_INVIARE by default)
        assertEquals(0, result.size)
    }

    @Test
    fun `returns INVIATO for active delivery`() = runBlocking {
        val store = FakeSlipDeliveryStore()
        store.activeDeliveries[WeeklyPartId("wp1") to "plan1"] = SlipDelivery(
            id = SlipDeliveryId("d1"),
            weeklyPartId = WeeklyPartId("wp1"),
            weekPlanId = "plan1",
            studentName = "Mario Rossi",
            assistantName = null,
            sentAt = Instant.now(),
            cancelledAt = null,
        )
        val useCase = CaricaStatoConsegneUseCase(store)

        val result = useCase(listOf("plan1"))

        val info = result[WeeklyPartId("wp1") to "plan1"]!!
        assertEquals(SlipDeliveryStatus.INVIATO, info.status)
    }

    @Test
    fun `returns DA_REINVIARE with previousStudentName when cancelled exists but no active`() = runBlocking {
        val store = FakeSlipDeliveryStore()
        store.cancelledDeliveries += SlipDelivery(
            id = SlipDeliveryId("d1"),
            weeklyPartId = WeeklyPartId("wp1"),
            weekPlanId = "plan1",
            studentName = "Mario Rossi",
            assistantName = null,
            sentAt = Instant.now(),
            cancelledAt = Instant.now(),
        )
        val useCase = CaricaStatoConsegneUseCase(store)

        val result = useCase(listOf("plan1"))

        val info = result[WeeklyPartId("wp1") to "plan1"]!!
        assertEquals(SlipDeliveryStatus.DA_REINVIARE, info.status)
        assertEquals("Mario Rossi", info.previousStudentName)
    }
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew :composeApp:jvmTest --tests "*CaricaStatoConsegneUseCaseTest*" --rerun`
Expected: FAIL

**Step 3: Write the implementation**

```kotlin
package org.example.project.feature.output.application

import org.example.project.feature.output.domain.SlipDeliveryInfo
import org.example.project.feature.output.domain.SlipDeliveryStatus
import org.example.project.feature.weeklyparts.domain.WeeklyPartId

class CaricaStatoConsegneUseCase(
    private val store: SlipDeliveryStore,
) {
    /**
     * Returns delivery info keyed by (weeklyPartId, weekPlanId).
     * Tickets not present in the map are DA_INVIARE by default.
     */
    suspend operator fun invoke(
        weekPlanIds: List<String>,
    ): Map<Pair<WeeklyPartId, String>, SlipDeliveryInfo> {
        if (weekPlanIds.isEmpty()) return emptyMap()

        val active = store.listActiveDeliveries(weekPlanIds)
        val cancelled = store.listCancelledDeliveries(weekPlanIds)

        val activeByKey = active.associateBy { it.weeklyPartId to it.weekPlanId }
        // For cancelled: group by key, pick most recent (by cancelledAt DESC)
        val cancelledByKey = cancelled
            .groupBy { it.weeklyPartId to it.weekPlanId }
            .mapValues { (_, list) -> list.maxByOrNull { it.cancelledAt!! } }

        val allKeys = activeByKey.keys + cancelledByKey.keys
        return allKeys.associateWith { key ->
            val activeDelivery = activeByKey[key]
            val lastCancelled = cancelledByKey[key]

            when {
                activeDelivery != null -> SlipDeliveryInfo(
                    status = SlipDeliveryStatus.INVIATO,
                    activeDelivery = activeDelivery,
                    previousStudentName = null,
                )
                lastCancelled != null -> SlipDeliveryInfo(
                    status = SlipDeliveryStatus.DA_REINVIARE,
                    activeDelivery = null,
                    previousStudentName = lastCancelled.studentName,
                )
                else -> SlipDeliveryInfo(
                    status = SlipDeliveryStatus.DA_INVIARE,
                    activeDelivery = null,
                    previousStudentName = null,
                )
            }
        }
    }
}
```

Note: this use case is **read-only** — no `TransactionRunner` needed, no `Either` needed (no domain errors possible).

**Step 4: Run tests**

Run: `./gradlew :composeApp:jvmTest --tests "*CaricaStatoConsegneUseCaseTest*" --rerun`
Expected: PASS

**Step 5: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/feature/output/application/CaricaStatoConsegneUseCase.kt \
       composeApp/src/jvmTest/kotlin/org/example/project/feature/output/application/CaricaStatoConsegneUseCaseTest.kt
git commit -m "[US4] Add CaricaStatoConsegneUseCase for loading delivery status"
```

---

## Task 8: Create `VerificaConsegnaPreAssegnazioneUseCase`

This use case is called **before** reassigning a person to a slot where a slip was already delivered. Returns `Either.Right(previousStudentName)` if a delivery exists, `Either.Right(null)` otherwise.

**Files:**
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/output/application/VerificaConsegnaPreAssegnazioneUseCase.kt`
- Create: `composeApp/src/jvmTest/kotlin/org/example/project/feature/output/application/VerificaConsegnaPreAssegnazioneUseCaseTest.kt`

**Step 1: Write the failing test**

```kotlin
package org.example.project.feature.output.application

import kotlinx.coroutines.runBlocking
import org.example.project.feature.output.domain.SlipDelivery
import org.example.project.feature.output.domain.SlipDeliveryId
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VerificaConsegnaPreAssegnazioneUseCaseTest {

    @Test
    fun `returns null when no active delivery`() = runBlocking {
        val store = FakeSlipDeliveryStore()
        val useCase = VerificaConsegnaPreAssegnazioneUseCase(store)

        val result = useCase(WeeklyPartId("wp1"), "plan1")
        assertNull(result)
    }

    @Test
    fun `returns student name when active delivery exists`() = runBlocking {
        val store = FakeSlipDeliveryStore()
        store.activeDeliveries[WeeklyPartId("wp1") to "plan1"] = SlipDelivery(
            id = SlipDeliveryId("d1"),
            weeklyPartId = WeeklyPartId("wp1"),
            weekPlanId = "plan1",
            studentName = "Mario Rossi",
            assistantName = null,
            sentAt = Instant.now(),
            cancelledAt = null,
        )
        val useCase = VerificaConsegnaPreAssegnazioneUseCase(store)

        val result = useCase(WeeklyPartId("wp1"), "plan1")
        assertEquals("Mario Rossi", result)
    }
}
```

**Step 2: Write the implementation**

```kotlin
package org.example.project.feature.output.application

import org.example.project.feature.weeklyparts.domain.WeeklyPartId

class VerificaConsegnaPreAssegnazioneUseCase(
    private val store: SlipDeliveryStore,
) {
    /** Returns the student name of the active delivery, or null if none. */
    suspend operator fun invoke(weeklyPartId: WeeklyPartId, weekPlanId: String): String? =
        store.findActiveDelivery(weeklyPartId, weekPlanId)?.studentName
}
```

**Step 3: Run tests**

Run: `./gradlew :composeApp:jvmTest --tests "*VerificaConsegnaPreAssegnazioneUseCaseTest*" --rerun`
Expected: PASS

**Step 4: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/feature/output/application/VerificaConsegnaPreAssegnazioneUseCase.kt \
       composeApp/src/jvmTest/kotlin/org/example/project/feature/output/application/VerificaConsegnaPreAssegnazioneUseCaseTest.kt
git commit -m "[US4] Add VerificaConsegnaPreAssegnazioneUseCase for reassignment warning"
```

---

## Task 9: Create test fakes and wire DI

**Files:**
- Create: `composeApp/src/jvmTest/kotlin/org/example/project/feature/output/application/OutputTestFixtures.kt`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/feature/output/di/OutputModule.kt`

**Step 1: Create test fixtures**

```kotlin
package org.example.project.feature.output.application

import org.example.project.core.persistence.TransactionScope
import org.example.project.feature.output.domain.SlipDelivery
import org.example.project.feature.output.domain.SlipDeliveryId
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.Instant

internal class FakeSlipDeliveryStore : SlipDeliveryStore {
    val inserted = mutableListOf<SlipDelivery>()
    val cancelledIds = mutableListOf<SlipDeliveryId>()
    val activeDeliveries = mutableMapOf<Pair<WeeklyPartId, String>, SlipDelivery>()
    val cancelledDeliveries = mutableListOf<SlipDelivery>()

    override suspend fun findActiveDelivery(weeklyPartId: WeeklyPartId, weekPlanId: String): SlipDelivery? =
        activeDeliveries[weeklyPartId to weekPlanId]

    override suspend fun findLastCancelledDelivery(weeklyPartId: WeeklyPartId, weekPlanId: String): SlipDelivery? =
        cancelledDeliveries
            .filter { it.weeklyPartId == weeklyPartId && it.weekPlanId == weekPlanId }
            .maxByOrNull { it.cancelledAt!! }

    override suspend fun listActiveDeliveries(weekPlanIds: List<String>): List<SlipDelivery> =
        activeDeliveries.values.filter { it.weekPlanId in weekPlanIds }

    override suspend fun listCancelledDeliveries(weekPlanIds: List<String>): List<SlipDelivery> =
        cancelledDeliveries.filter { it.weekPlanId in weekPlanIds }

    context(tx: TransactionScope)
    override suspend fun insert(delivery: SlipDelivery) {
        inserted += delivery
        activeDeliveries[delivery.weeklyPartId to delivery.weekPlanId] = delivery
    }

    context(tx: TransactionScope)
    override suspend fun cancel(id: SlipDeliveryId, cancelledAt: Instant) {
        cancelledIds += id
        val key = activeDeliveries.entries.find { it.value.id == id }?.key
        if (key != null) {
            val removed = activeDeliveries.remove(key)!!
            cancelledDeliveries += removed.copy(cancelledAt = cancelledAt)
        }
    }
}
```

Note: `FakeTransactionRunner` — import from wherever the project centralizes it (see review-notes Medium 62). If not yet centralized, use the one from `AssignmentTestFixtures.kt`.

**Step 2: Wire DI in `OutputModule.kt`**

```kotlin
val outputModule = module {
    // Output
    single { PdfAssignmentsRenderer() }
    single { PdfProgramRenderer() }
    single<FileOpener> { DesktopFileOpener() }
    single { GeneraImmaginiAssegnazioni(get(), get(), get(), get()) }
    single { StampaProgrammaUseCase(get(), get(), get(), get(), get()) }

    // Slip delivery
    single<SlipDeliveryStore> { SqlDelightSlipDeliveryStore(get()) }
    single { SegnaComInviatoUseCase(get(), get()) }
    single { AnnullaConsegnaUseCase(get(), get()) }
    single { CaricaStatoConsegneUseCase(get()) }
    single { VerificaConsegnaPreAssegnazioneUseCase(get()) }
}
```

**Step 3: Run all tests**

Run: `./gradlew :composeApp:jvmTest --rerun`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add composeApp/src/jvmTest/kotlin/org/example/project/feature/output/application/OutputTestFixtures.kt \
       composeApp/src/jvmMain/kotlin/org/example/project/feature/output/di/OutputModule.kt
git commit -m "[US4] Wire slip delivery DI bindings and create test fixtures"
```

---

## Task 10: Update `AssignmentManagementViewModel` with delivery state

**Files:**
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/AssignmentManagementViewModel.kt`

**Step 1: Add delivery-related state to `AssignmentManagementUiState`**

```kotlin
// New imports
import org.example.project.feature.output.domain.SlipDeliveryInfo
import org.example.project.feature.weeklyparts.domain.WeeklyPartId

// Add to data class
internal data class AssignmentManagementUiState(
    // ... existing fields ...
    val deliveryStatus: Map<Pair<WeeklyPartId, String>, SlipDeliveryInfo> = emptyMap(),
    val isMarkingDelivered: Boolean = false,
)
```

**Step 2: Add new dependencies to constructor**

```kotlin
internal class AssignmentManagementViewModel(
    // ... existing ...
    private val segnaComInviato: SegnaComInviatoUseCase,
    private val annullaConsegna: AnnullaConsegnaUseCase,
    private val caricaStatoConsegne: CaricaStatoConsegneUseCase,
)
```

**Step 3: Load delivery status after ticket generation**

In `openAssignmentTickets`, after `successUpdate`, load delivery state:

```kotlin
successUpdate = { state, result ->
    // Schedule delivery status load
    scope.launch { loadDeliveryStatus(result.tickets) }
    state.copy(
        isAssignmentTicketsDialogOpen = true,
        isLoadingAssignmentTickets = false,
        assignmentTickets = result.tickets,
        assignmentPartWarnings = result.warnings,
        assignmentTicketsError = null,
    )
},
```

Add a private method:

```kotlin
private suspend fun loadDeliveryStatus(tickets: List<AssignmentTicketImage>) {
    val weekPlanIds = tickets.map { it.weekPlanId }.distinct()
    val status = caricaStatoConsegne(weekPlanIds)
    _uiState.update { it.copy(deliveryStatus = status) }
}
```

**Step 4: Add `markAsDelivered` action**

```kotlin
fun markAsDelivered(ticket: AssignmentTicketImage) {
    if (_uiState.value.isMarkingDelivered) return
    scope.launch {
        _uiState.update { it.copy(isMarkingDelivered = true) }
        val result = segnaComInviato(
            weeklyPartId = ticket.weeklyPartId,
            weekPlanId = ticket.weekPlanId,
            studentName = ticket.fullName,
            assistantName = ticket.assignments.firstOrNull()?.roleLabel, // not needed; use slip data
        )
        result.fold(
            ifLeft = { error ->
                _uiState.update {
                    it.copy(isMarkingDelivered = false, notice = errorNotice(error.toMessage()))
                }
            },
            ifRight = {
                // Reload delivery status
                loadDeliveryStatus(_uiState.value.assignmentTickets)
                _uiState.update { it.copy(isMarkingDelivered = false) }
            }
        )
    }
}
```

Note on `assistantName`: the `AssignmentTicketImage` doesn't carry assistantName directly. We have two options:
1. Add `assistantName` to `AssignmentTicketImage` (clean)
2. Pass it as a parameter from the UI

**Recommendation**: Add `assistantName: String?` to `AssignmentTicketImage` in the same step as Task 4. This keeps the model self-contained.

**Step 5: Update Koin wiring for the new constructor parameters**

The ViewModel is likely created in a composable or a DI module. Search for where `AssignmentManagementViewModel` is instantiated and add the new `get()` calls.

**Step 6: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/AssignmentManagementViewModel.kt
git commit -m "[US4] Add delivery state and markAsDelivered action to AssignmentManagementViewModel"
```

---

## Task 11: Update dialog UI with "Da inviare" / "Inviati" sections

**Files:**
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceComponents.kt`

**Step 1: Update `AssignmentTicketsDialog` signature**

Add `deliveryStatus` parameter and `onMarkAsDelivered` callback:

```kotlin
internal fun AssignmentTicketsDialog(
    monthLabel: String,
    tickets: List<AssignmentTicketImage>,
    partWarnings: List<PartAssignmentWarning>,
    deliveryStatus: Map<Pair<WeeklyPartId, String>, SlipDeliveryInfo>,
    isLoading: Boolean,
    errorMessage: String?,
    onMarkAsDelivered: (AssignmentTicketImage) -> Unit,
    onDismiss: () -> Unit,
)
```

**Step 2: Modify `buildWeekSections()` to separate tickets**

For each week section, split tickets into two groups:
- "Da inviare": tickets where `deliveryStatus[weeklyPartId to weekPlanId]` is `DA_INVIARE`, `DA_REINVIARE`, or absent from the map
- "Inviati": tickets where status is `INVIATO`

Ghost cards (warnings) always go in "Da inviare".

**Step 3: Render two sub-sections per week**

```
Week header: "Settimana 2 marzo - 8 marzo 2026"
  Section "Da inviare" (prominent):
    - Ticket cards with "Segna come inviato" button
    - Ghost cards for warnings
    - DA_REINVIARE cards show "Precedente: {name}" note
  Section "Inviati" (attenuated, alpha = 0.6):
    - Ticket cards with green checkmark, no action button
```

**Step 4: Add "Segna come inviato" button to ticket card**

In `AssignmentTicketCard`, add a filled button (accent color) when the ticket is in "Da inviare":

```kotlin
// Inside the card, after the "Visualizza" button
if (deliveryInfo?.status != SlipDeliveryStatus.INVIATO) {
    Surface(
        onClick = { onMarkAsDelivered() },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Text("Segna come inviato", Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}
```

**Step 5: Add "Precedente: {name}" note for DA_REINVIARE**

```kotlin
if (deliveryInfo?.status == SlipDeliveryStatus.DA_REINVIARE && deliveryInfo.previousStudentName != null) {
    Text(
        "Precedente: ${deliveryInfo.previousStudentName}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
    )
}
```

**Step 6: Run the app visually to verify**

Build: `./gradlew :composeApp:jvmTest --rerun` (to verify no compilation errors)
Optionally: run the app and test the dialog UI.

**Step 7: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceComponents.kt
git commit -m "[US4] Add Da inviare/Inviati sections and Segna come inviato button to tickets dialog"
```

---

## Task 12: Add badge to program button

**Files:**
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceScreen.kt`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/AssignmentManagementViewModel.kt`

**Step 1: Add badge state to ViewModel**

```kotlin
data class TicketBadgeState(
    val pendingCount: Int = 0,
    val blockedCount: Int = 0,
    val allDelivered: Boolean = false,
)

// Add to AssignmentManagementUiState:
val ticketBadge: TicketBadgeState? = null
```

**Step 2: Compute badge after loading tickets + delivery status**

After `loadDeliveryStatus()` completes, compute the badge:

```kotlin
private fun computeBadge(
    tickets: List<AssignmentTicketImage>,
    warnings: List<PartAssignmentWarning>,
    deliveryStatus: Map<Pair<WeeklyPartId, String>, SlipDeliveryInfo>,
): TicketBadgeState {
    val pending = tickets.count { ticket ->
        val info = deliveryStatus[ticket.weeklyPartId to ticket.weekPlanId]
        info == null || info.status != SlipDeliveryStatus.INVIATO
    }
    val blocked = warnings.size
    val allDelivered = pending == 0 && blocked == 0 && tickets.isNotEmpty()
    return TicketBadgeState(pendingCount = pending, blockedCount = blocked, allDelivered = allDelivered)
}
```

**Step 3: Add a method to preload badge (called outside dialog)**

The badge should be visible even before the dialog is opened. Add a `loadTicketBadge(programId)` method that generates tickets (or uses cached) and computes the badge.

Design decision: generating all tickets just for the badge is expensive. **Simpler approach**: load the badge data **after the dialog has been opened once** and cache it in state. On subsequent opens, the badge is already populated. The badge updates after every `markAsDelivered`.

Alternative: add a lightweight query `countPendingDeliveries(programId)` that counts without generating images. This is the better UX (badge visible immediately) but requires a new SQL query.

**Recommended**: Lightweight query approach. Add to `SlipDeliveryStore`:

```kotlin
suspend fun countActiveDeliveriesByProgram(programId: String): Int
```

And a separate `CaricaBadgeBigliettiUseCase` that:
1. Counts total complete parts for the program (reuse logic from `GeneraImmaginiAssegnazioni.completePartIds`)
2. Counts active deliveries
3. Counts warnings (incomplete parts)
4. Returns badge state

This is a design decision the implementer should validate with the user. For now, the plan uses the simpler "after first open" approach.

**Step 4: Render badge on the button**

In `ProgramWorkspaceScreen.kt`, modify the "Biglietti assegnazioni" button:

```kotlin
ProgramRightPanelButton(
    label = if (assignmentState.isLoadingAssignmentTickets)
        "Generazione biglietti..."
    else
        "Biglietti assegnazioni",
    icon = Icons.Filled.Image,
    isPrimary = false,
    enabled = !assignmentState.isLoadingAssignmentTickets,
    badge = assignmentState.ticketBadge?.let { badge ->
        when {
            badge.allDelivered -> "Tutti inviati"
            else -> buildString {
                if (badge.pendingCount > 0) append("${badge.pendingCount} da inviare")
                if (badge.blockedCount > 0) {
                    if (isNotEmpty()) append(" · ")
                    append("${badge.blockedCount} bloccati")
                }
            }
        }
    },
    onClick = { /* ... */ },
    modifier = Modifier.fillMaxWidth(),
)
```

Note: `ProgramRightPanelButton` may need a new `badge: String?` parameter. Inspect the composable and add it.

**Step 5: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceScreen.kt \
       composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/AssignmentManagementViewModel.kt
git commit -m "[US4] Add delivery badge to program tickets button"
```

---

## Task 13: Add reassignment warning dialog

When the user assigns a person to a slot where a delivered slip exists, show a confirmation dialog before proceeding.

**Files:**
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/PersonPickerViewModel.kt`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceComponents.kt` (or whichever file renders the picker)

**Step 1: Add delivery check dependency to PersonPickerViewModel**

```kotlin
internal class PersonPickerViewModel(
    // ... existing ...
    private val verificaConsegna: VerificaConsegnaPreAssegnazioneUseCase,
    private val annullaConsegna: AnnullaConsegnaUseCase,
)
```

**Step 2: Add warning state**

```kotlin
internal data class PersonPickerUiState(
    // ... existing ...
    val deliveryWarning: DeliveryWarningState? = null,
)

data class DeliveryWarningState(
    val previousStudentName: String,
    val pendingPersonId: ProclamatoreId,
)
```

**Step 3: Intercept `confirmAssignment` with delivery check**

Before calling `assegnaPersona`, check if a delivery exists:

```kotlin
fun confirmAssignment(personId: ProclamatoreId, onSuccess: () -> Unit) {
    if (_state.value.isAssigning) return
    val pickerWeeklyPartId = _state.value.pickerWeeklyPartId ?: return
    // Need weekPlanId — this needs to be loaded/available in picker state
    // weekPlanId comes from the WeekPlan that contains this weeklyPartId

    scope.launch {
        val previousStudent = verificaConsegna(pickerWeeklyPartId, weekPlanId)
        if (previousStudent != null) {
            // Show warning, don't assign yet
            _state.update {
                it.copy(deliveryWarning = DeliveryWarningState(previousStudent, personId))
            }
            return@launch
        }
        // No warning needed, proceed directly
        doAssign(personId, onSuccess)
    }
}

fun confirmAssignmentAfterWarning(onSuccess: () -> Unit) {
    val warning = _state.value.deliveryWarning ?: return
    val pickerWeeklyPartId = _state.value.pickerWeeklyPartId ?: return
    _state.update { it.copy(deliveryWarning = null) }

    scope.launch {
        // Cancel the delivery first
        annullaConsegna(pickerWeeklyPartId, weekPlanId)
        // Then assign
        doAssign(warning.pendingPersonId, onSuccess)
    }
}

fun dismissDeliveryWarning() {
    _state.update { it.copy(deliveryWarning = null) }
}
```

**Important**: `weekPlanId` is currently not available in `PersonPickerUiState`. It needs to be added:
- Either pass it when opening the picker (`openPersonPicker` gets an additional `weekPlanId: String` param)
- Or derive it from `weeklyPartId` via a store query

The cleanest approach is to pass `weekPlanId` when opening the picker, since the caller already has the `WeekPlan` context.

**Step 4: Render warning dialog in UI**

```kotlin
if (pickerState.deliveryWarning != null) {
    AlertDialog(
        onDismissRequest = { personPickerVM.dismissDeliveryWarning() },
        title = { Text("Biglietto già inviato") },
        text = {
            Text("Hai già inviato il biglietto a ${pickerState.deliveryWarning.previousStudentName}. " +
                 "Ricordati di avvisarlo del cambio.")
        },
        confirmButton = {
            TextButton(onClick = { personPickerVM.confirmAssignmentAfterWarning(onReload) }) {
                Text("Conferma")
            }
        },
        dismissButton = {
            TextButton(onClick = { personPickerVM.dismissDeliveryWarning() }) {
                Text("Annulla")
            }
        },
    )
}
```

**Step 5: Update Koin wiring for PersonPickerViewModel**

Find where `PersonPickerViewModel` is created and add the new dependencies.

**Step 6: Run all tests**

Run: `./gradlew :composeApp:jvmTest --rerun`
Expected: BUILD SUCCESSFUL

**Step 7: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/PersonPickerViewModel.kt \
       composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceComponents.kt \
       composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceScreen.kt
git commit -m "[US4] Add reassignment warning dialog when slip was already delivered"
```

---

## Task 14: Final integration test and cleanup

**Step 1: Run the full test suite**

Run: `./gradlew :composeApp:jvmTest --rerun`
Expected: BUILD SUCCESSFUL, all tests pass

**Step 2: Manual smoke test**

1. Open the app
2. Select a program with assignments
3. Click "Biglietti assegnazioni"
4. Verify tickets appear with "Segna come inviato" button
5. Click "Segna come inviato" on one ticket → ticket moves to "Inviati"
6. Close and reopen dialog → state persists
7. Reassign a delivered ticket → warning dialog appears
8. Badge updates on button

**Step 3: Commit any fixups**

```bash
git commit -m "[US4] Final cleanup and integration fixes for slip delivery tracking"
```

---

## Dependency Graph

```
Task 1 (schema) ─────┐
                      ├──► Task 3 (store impl)
Task 2 (model+iface) ┘         │
                                ├──► Task 9 (fakes + DI)
Task 4 (weeklyPartId) ─────────┤
                                ├──► Task 5 (SegnaComInviato)
                                ├──► Task 6 (AnnullaConsegna)
                                ├──► Task 7 (CaricaStato)
                                └──► Task 8 (VerificaConsegna)
                                          │
Task 5,6,7,8,9 ──────────────────────────►├──► Task 10 (ViewModel)
                                          │        │
                                          │        ├──► Task 11 (Dialog UI)
                                          │        ├──► Task 12 (Badge)
                                          └────────┴──► Task 13 (Warning dialog)
                                                            │
                                                            └──► Task 14 (Integration)
```

**Parallelizable batches:**
- Batch 1: Tasks 1+2 (parallel, no shared files)
- Batch 2: Tasks 3+4 (parallel after batch 1)
- Batch 3: Tasks 5+6+7+8 (parallel after batch 2 — independent use cases)
- Batch 4: Task 9 (needs all fakes defined)
- Batch 5: Task 10 (ViewModel)
- Batch 6: Tasks 11+12+13 (parallel UI changes — but touch the same file `ProgramWorkspaceComponents.kt`, so may need sequential)
- Batch 7: Task 14 (final)

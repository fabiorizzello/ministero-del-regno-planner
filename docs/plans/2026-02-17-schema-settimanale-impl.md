# Schema Settimanale Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement the weekly schedule management screen (M2) — part type catalog, week navigation, and CRUD for weekly parts.

**Architecture:** Vertical slice following existing Proclamatori patterns. New `part_type` table for catalog with FK from `weekly_part`. GitHub JSON sync for remote catalog + schemas. MVI-lite ViewModel with StateFlow.

**Tech Stack:** Kotlin 2.3.0, Compose Multiplatform 1.10.0, SQLDelight 2.1.0, Arrow Core 2.1.2, Koin 3.5.6, java.net.http.HttpClient, kotlinx-serialization-json 1.8.1

**Design doc:** `docs/plans/2026-02-17-schema-settimanale-design.md`

---

### Task 1: DB Schema Migration

**Files:**
- Modify: `composeApp/src/commonMain/sqldelight/org/example/project/db/MinisteroDatabase.sq`
- Create: `composeApp/src/commonMain/sqldelight/org/example/project/db/1.sqm`

**Step 1: Create migration file `1.sqm`**

Since `weekly_part` has no production data yet, the migration drops and recreates it.

```sql
-- 1.sqm
CREATE TABLE part_type (
    id TEXT NOT NULL PRIMARY KEY,
    code TEXT NOT NULL UNIQUE,
    label TEXT NOT NULL,
    people_count INTEGER NOT NULL,
    sex_rule TEXT NOT NULL,
    fixed INTEGER NOT NULL DEFAULT 0,
    sort_order INTEGER NOT NULL DEFAULT 0
);

DROP TABLE IF EXISTS assignment;
DROP TABLE IF EXISTS weekly_part;

CREATE TABLE weekly_part (
    id TEXT NOT NULL PRIMARY KEY,
    week_plan_id TEXT NOT NULL,
    part_type_id TEXT NOT NULL,
    sort_order INTEGER NOT NULL,
    FOREIGN KEY (week_plan_id) REFERENCES week_plan(id) ON DELETE CASCADE,
    FOREIGN KEY (part_type_id) REFERENCES part_type(id)
);

CREATE TABLE assignment (
    id TEXT NOT NULL PRIMARY KEY,
    weekly_part_id TEXT NOT NULL,
    person_id TEXT NOT NULL,
    slot INTEGER NOT NULL,
    FOREIGN KEY (weekly_part_id) REFERENCES weekly_part(id) ON DELETE CASCADE,
    FOREIGN KEY (person_id) REFERENCES person(id) ON DELETE RESTRICT
);
```

**Step 2: Update `MinisteroDatabase.sq` with new schema + named queries**

Replace `weekly_part` CREATE TABLE and add `part_type` table. Add named queries for the new tables:

```sql
-- Add to MinisteroDatabase.sq (replace existing weekly_part, add part_type)

CREATE TABLE part_type (
    id TEXT NOT NULL PRIMARY KEY,
    code TEXT NOT NULL UNIQUE,
    label TEXT NOT NULL,
    people_count INTEGER NOT NULL,
    sex_rule TEXT NOT NULL,
    fixed INTEGER NOT NULL DEFAULT 0,
    sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE weekly_part (
    id TEXT NOT NULL PRIMARY KEY,
    week_plan_id TEXT NOT NULL,
    part_type_id TEXT NOT NULL,
    sort_order INTEGER NOT NULL,
    FOREIGN KEY (week_plan_id) REFERENCES week_plan(id) ON DELETE CASCADE,
    FOREIGN KEY (part_type_id) REFERENCES part_type(id)
);

-- Named queries for part_type
allPartTypes:
SELECT id, code, label, people_count, sex_rule, fixed, sort_order
FROM part_type
ORDER BY sort_order, label;

findPartTypeByCode:
SELECT id, code, label, people_count, sex_rule, fixed, sort_order
FROM part_type
WHERE code = ?;

findPartTypeById:
SELECT id, code, label, people_count, sex_rule, fixed, sort_order
FROM part_type
WHERE id = ?;

findFixedPartType:
SELECT id, code, label, people_count, sex_rule, fixed, sort_order
FROM part_type
WHERE fixed = 1
LIMIT 1;

upsertPartType:
INSERT INTO part_type(id, code, label, people_count, sex_rule, fixed, sort_order)
VALUES (?, ?, ?, ?, ?, ?, ?)
ON CONFLICT(code) DO UPDATE SET
    label = excluded.label,
    people_count = excluded.people_count,
    sex_rule = excluded.sex_rule,
    fixed = excluded.fixed,
    sort_order = excluded.sort_order;

-- Named queries for week_plan
findWeekPlanByDate:
SELECT id, week_start_date
FROM week_plan
WHERE week_start_date = ?;

insertWeekPlan:
INSERT INTO week_plan(id, week_start_date)
VALUES (?, ?);

deleteWeekPlan:
DELETE FROM week_plan WHERE id = ?;

allWeekPlanDates:
SELECT week_start_date FROM week_plan ORDER BY week_start_date;

-- Named queries for weekly_part (with JOIN to part_type)
partsForWeek:
SELECT
    wp.id,
    wp.week_plan_id,
    wp.part_type_id,
    wp.sort_order,
    pt.code AS part_type_code,
    pt.label AS part_type_label,
    pt.people_count AS part_type_people_count,
    pt.sex_rule AS part_type_sex_rule,
    pt.fixed AS part_type_fixed,
    pt.sort_order AS part_type_sort_order
FROM weekly_part wp
JOIN part_type pt ON wp.part_type_id = pt.id
WHERE wp.week_plan_id = ?
ORDER BY wp.sort_order;

insertWeeklyPart:
INSERT INTO weekly_part(id, week_plan_id, part_type_id, sort_order)
VALUES (?, ?, ?, ?);

deleteWeeklyPart:
DELETE FROM weekly_part WHERE id = ?;

deleteAllPartsForWeek:
DELETE FROM weekly_part WHERE week_plan_id = ?;

updateWeeklyPartSortOrder:
UPDATE weekly_part SET sort_order = ? WHERE id = ?;

maxSortOrderForWeek:
SELECT COALESCE(MAX(sort_order), -1) FROM weekly_part WHERE week_plan_id = ?;
```

**Step 3: Build to verify SQLDelight generates code**

Run: `JAVA_HOME=$HOME/.sdkman/candidates/java/24.0.2-tem ./gradlew compileKotlinJvm 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL (SQLDelight generates query classes)

**Step 4: Commit**

```bash
git add composeApp/src/commonMain/sqldelight/
git commit -m "feat(db): add part_type table and revise weekly_part schema"
```

---

### Task 2: Domain Models

**Files:**
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/domain/PartType.kt`
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/domain/WeekPlan.kt`
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/domain/SexRule.kt`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/domain/WeeklyPart.kt`

**Step 1: Create `SexRule.kt`**

```kotlin
package org.example.project.feature.weeklyparts.domain

enum class SexRule {
    UOMO,
    LIBERO,
}
```

**Step 2: Create `PartType.kt`**

```kotlin
package org.example.project.feature.weeklyparts.domain

@JvmInline
value class PartTypeId(val value: String)

data class PartType(
    val id: PartTypeId,
    val code: String,
    val label: String,
    val peopleCount: Int,
    val sexRule: SexRule,
    val fixed: Boolean,
    val sortOrder: Int,
)
```

**Step 3: Create `WeekPlan.kt`**

```kotlin
package org.example.project.feature.weeklyparts.domain

import java.time.LocalDate

@JvmInline
value class WeekPlanId(val value: String)

data class WeekPlan(
    val id: WeekPlanId,
    val weekStartDate: LocalDate,
    val parts: List<WeeklyPart>,
)
```

**Step 4: Rewrite `WeeklyPart.kt`**

Replace the entire file:

```kotlin
package org.example.project.feature.weeklyparts.domain

@JvmInline
value class WeeklyPartId(val value: String)

data class WeeklyPart(
    val id: WeeklyPartId,
    val partType: PartType,
    val sortOrder: Int,
)
```

This removes `WeeklyPartSexRule` (replaced by `SexRule`), `weekStartDate`, `title`, `numberOfPeople` — all now derived from `PartType`.

**Step 5: Build to verify compilation**

Run: `JAVA_HOME=$HOME/.sdkman/candidates/java/24.0.2-tem ./gradlew compileKotlinJvm 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/domain/
git commit -m "feat(domain): add PartType, WeekPlan and revise WeeklyPart models"
```

---

### Task 3: Application Layer — Interfaces

**Files:**
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/application/PartTypeStore.kt`
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/application/WeekPlanStore.kt`
- Delete or replace: `composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/application/WeeklyPartsRepository.kt`

**Step 1: Create `PartTypeStore.kt`**

```kotlin
package org.example.project.feature.weeklyparts.application

import org.example.project.feature.weeklyparts.domain.PartType

interface PartTypeStore {
    suspend fun all(): List<PartType>
    suspend fun findByCode(code: String): PartType?
    suspend fun findFixed(): PartType?
    suspend fun upsertAll(partTypes: List<PartType>)
}
```

**Step 2: Create `WeekPlanStore.kt`**

```kotlin
package org.example.project.feature.weeklyparts.application

import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.LocalDate

interface WeekPlanStore {
    suspend fun findByDate(weekStartDate: LocalDate): WeekPlan?
    suspend fun save(weekPlan: WeekPlan)
    suspend fun delete(weekPlanId: WeekPlanId)
    suspend fun addPart(weekPlanId: WeekPlanId, partTypeId: String, sortOrder: Int): WeeklyPartId
    suspend fun removePart(weeklyPartId: WeeklyPartId)
    suspend fun updateSortOrders(parts: List<Pair<WeeklyPartId, Int>>)
    suspend fun replaceAllParts(weekPlanId: WeekPlanId, partTypeCodes: List<String>, partTypeStore: PartTypeStore)
    suspend fun allWeekDates(): List<LocalDate>
}
```

**Step 3: Delete old `WeeklyPartsRepository.kt`**

Remove the file — the old interface is incompatible with the new design.

**Step 4: Build to verify**

Run: `JAVA_HOME=$HOME/.sdkman/candidates/java/24.0.2-tem ./gradlew compileKotlinJvm 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/application/
git commit -m "feat(application): add PartTypeStore and WeekPlanStore interfaces"
```

---

### Task 4: Infrastructure — Row Mappers

**Files:**
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/infrastructure/PartTypeRowMapper.kt`
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/infrastructure/WeeklyPartRowMapper.kt`

**Step 1: Create `PartTypeRowMapper.kt`**

```kotlin
package org.example.project.feature.weeklyparts.infrastructure

import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.SexRule

internal fun mapPartTypeRow(
    id: String,
    code: String,
    label: String,
    people_count: Long,
    sex_rule: String,
    fixed: Long,
    sort_order: Long,
): PartType {
    return PartType(
        id = PartTypeId(id),
        code = code,
        label = label,
        peopleCount = people_count.toInt(),
        sexRule = SexRule.valueOf(sex_rule),
        fixed = fixed == 1L,
        sortOrder = sort_order.toInt(),
    )
}
```

**Step 2: Create `WeeklyPartRowMapper.kt`**

Maps the JOIN query result (`partsForWeek`) into domain `WeeklyPart`:

```kotlin
package org.example.project.feature.weeklyparts.infrastructure

import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.SexRule
import org.example.project.feature.weeklyparts.domain.WeeklyPart
import org.example.project.feature.weeklyparts.domain.WeeklyPartId

internal fun mapWeeklyPartWithTypeRow(
    id: String,
    week_plan_id: String,
    part_type_id: String,
    sort_order: Long,
    part_type_code: String,
    part_type_label: String,
    part_type_people_count: Long,
    part_type_sex_rule: String,
    part_type_fixed: Long,
    part_type_sort_order: Long,
): WeeklyPart {
    return WeeklyPart(
        id = WeeklyPartId(id),
        partType = PartType(
            id = PartTypeId(part_type_id),
            code = part_type_code,
            label = part_type_label,
            peopleCount = part_type_people_count.toInt(),
            sexRule = SexRule.valueOf(part_type_sex_rule),
            fixed = part_type_fixed == 1L,
            sortOrder = part_type_sort_order.toInt(),
        ),
        sortOrder = sort_order.toInt(),
    )
}
```

**Step 3: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/infrastructure/
git commit -m "feat(infra): add row mappers for part_type and weekly_part"
```

---

### Task 5: Infrastructure — SQLDelight Stores

**Files:**
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/infrastructure/SqlDelightPartTypeStore.kt`
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/infrastructure/SqlDelightWeekPlanStore.kt`

**Step 1: Create `SqlDelightPartTypeStore.kt`**

```kotlin
package org.example.project.feature.weeklyparts.infrastructure

import org.example.project.db.MinisteroDatabase
import org.example.project.feature.weeklyparts.application.PartTypeStore
import org.example.project.feature.weeklyparts.domain.PartType
import java.util.UUID

class SqlDelightPartTypeStore(
    private val database: MinisteroDatabase,
) : PartTypeStore {

    override suspend fun all(): List<PartType> {
        return database.ministeroDatabaseQueries
            .allPartTypes(::mapPartTypeRow)
            .executeAsList()
    }

    override suspend fun findByCode(code: String): PartType? {
        return database.ministeroDatabaseQueries
            .findPartTypeByCode(code, ::mapPartTypeRow)
            .executeAsOneOrNull()
    }

    override suspend fun findFixed(): PartType? {
        return database.ministeroDatabaseQueries
            .findFixedPartType(::mapPartTypeRow)
            .executeAsOneOrNull()
    }

    override suspend fun upsertAll(partTypes: List<PartType>) {
        database.ministeroDatabaseQueries.transaction {
            partTypes.forEach { pt ->
                database.ministeroDatabaseQueries.upsertPartType(
                    id = pt.id.value.ifBlank { UUID.randomUUID().toString() },
                    code = pt.code,
                    label = pt.label,
                    people_count = pt.peopleCount.toLong(),
                    sex_rule = pt.sexRule.name,
                    fixed = if (pt.fixed) 1L else 0L,
                    sort_order = pt.sortOrder.toLong(),
                )
            }
        }
    }
}
```

**Step 2: Create `SqlDelightWeekPlanStore.kt`**

```kotlin
package org.example.project.feature.weeklyparts.infrastructure

import org.example.project.db.MinisteroDatabase
import org.example.project.feature.weeklyparts.application.PartTypeStore
import org.example.project.feature.weeklyparts.application.WeekPlanStore
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.LocalDate
import java.util.UUID

class SqlDelightWeekPlanStore(
    private val database: MinisteroDatabase,
) : WeekPlanStore {

    override suspend fun findByDate(weekStartDate: LocalDate): WeekPlan? {
        val dateStr = weekStartDate.toString()
        val row = database.ministeroDatabaseQueries
            .findWeekPlanByDate(dateStr)
            .executeAsOneOrNull() ?: return null

        val parts = database.ministeroDatabaseQueries
            .partsForWeek(row.id, ::mapWeeklyPartWithTypeRow)
            .executeAsList()

        return WeekPlan(
            id = WeekPlanId(row.id),
            weekStartDate = weekStartDate,
            parts = parts,
        )
    }

    override suspend fun save(weekPlan: WeekPlan) {
        database.ministeroDatabaseQueries.insertWeekPlan(
            id = weekPlan.id.value,
            week_start_date = weekPlan.weekStartDate.toString(),
        )
    }

    override suspend fun delete(weekPlanId: WeekPlanId) {
        database.ministeroDatabaseQueries.deleteWeekPlan(weekPlanId.value)
    }

    override suspend fun addPart(
        weekPlanId: WeekPlanId,
        partTypeId: String,
        sortOrder: Int,
    ): WeeklyPartId {
        val id = UUID.randomUUID().toString()
        database.ministeroDatabaseQueries.insertWeeklyPart(
            id = id,
            week_plan_id = weekPlanId.value,
            part_type_id = partTypeId,
            sort_order = sortOrder.toLong(),
        )
        return WeeklyPartId(id)
    }

    override suspend fun removePart(weeklyPartId: WeeklyPartId) {
        database.ministeroDatabaseQueries.deleteWeeklyPart(weeklyPartId.value)
    }

    override suspend fun updateSortOrders(parts: List<Pair<WeeklyPartId, Int>>) {
        database.ministeroDatabaseQueries.transaction {
            parts.forEach { (id, order) ->
                database.ministeroDatabaseQueries.updateWeeklyPartSortOrder(
                    sort_order = order.toLong(),
                    id = id.value,
                )
            }
        }
    }

    override suspend fun replaceAllParts(
        weekPlanId: WeekPlanId,
        partTypeCodes: List<String>,
        partTypeStore: PartTypeStore,
    ) {
        database.ministeroDatabaseQueries.transaction {
            database.ministeroDatabaseQueries.deleteAllPartsForWeek(weekPlanId.value)
            partTypeCodes.forEachIndexed { index, code ->
                val partType = partTypeStore.findByCode(code) ?: return@forEachIndexed
                database.ministeroDatabaseQueries.insertWeeklyPart(
                    id = UUID.randomUUID().toString(),
                    week_plan_id = weekPlanId.value,
                    part_type_id = partType.id.value,
                    sort_order = index.toLong(),
                )
            }
        }
    }

    override suspend fun allWeekDates(): List<LocalDate> {
        return database.ministeroDatabaseQueries
            .allWeekPlanDates()
            .executeAsList()
            .map { LocalDate.parse(it) }
    }
}
```

**Step 3: Build to verify**

Run: `JAVA_HOME=$HOME/.sdkman/candidates/java/24.0.2-tem ./gradlew compileKotlinJvm 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/infrastructure/
git commit -m "feat(infra): implement SqlDelightPartTypeStore and SqlDelightWeekPlanStore"
```

---

### Task 6: Infrastructure — GitHub Data Source

**Files:**
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/infrastructure/GitHubDataSource.kt`

**Step 1: Create `GitHubDataSource.kt`**

Uses `java.net.http.HttpClient` (no extra dependency needed on Java 24). Parses JSON manually with `kotlinx.serialization.json` (same pattern as `ImportaProclamatoriDaJsonUseCase`).

```kotlin
package org.example.project.feature.weeklyparts.infrastructure

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.SexRule
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID

data class RemoteWeekSchema(
    val weekStartDate: String,
    val partTypeCodes: List<String>,
)

class GitHubDataSource(
    private val partTypesUrl: String,
    private val weeklySchemasUrl: String,
) {
    private val client = HttpClient.newHttpClient()

    fun fetchPartTypes(): List<PartType> {
        val body = httpGet(partTypesUrl)
        val root = Json.parseToJsonElement(body).jsonObject
        val arr = root["partTypes"]?.jsonArray ?: return emptyList()
        return arr.mapIndexed { index, element ->
            val obj = element.jsonObject
            PartType(
                id = PartTypeId(UUID.randomUUID().toString()),
                code = obj["code"]!!.jsonPrimitive.content,
                label = obj["label"]!!.jsonPrimitive.content,
                peopleCount = obj["peopleCount"]!!.jsonPrimitive.int,
                sexRule = SexRule.valueOf(obj["sexRule"]!!.jsonPrimitive.content),
                fixed = obj["fixed"]?.jsonPrimitive?.boolean ?: false,
                sortOrder = index,
            )
        }
    }

    fun fetchWeeklySchemas(): List<RemoteWeekSchema> {
        val body = httpGet(weeklySchemasUrl)
        val root = Json.parseToJsonElement(body).jsonObject
        val arr = root["weeks"]?.jsonArray ?: return emptyList()
        return arr.map { element ->
            val obj = element.jsonObject
            val parts = obj["parts"]!!.jsonArray.map { partEl ->
                partEl.jsonObject["partTypeCode"]!!.jsonPrimitive.content
            }
            RemoteWeekSchema(
                weekStartDate = obj["weekStartDate"]!!.jsonPrimitive.content,
                partTypeCodes = parts,
            )
        }
    }

    private fun httpGet(url: String): String {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw RuntimeException("HTTP ${response.statusCode()} fetching $url")
        }
        return response.body()
    }
}
```

**Step 2: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/infrastructure/GitHubDataSource.kt
git commit -m "feat(infra): add GitHubDataSource for remote catalog and schemas"
```

---

### Task 7: Use Cases

**Files:**
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/application/CaricaSettimanaUseCase.kt`
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/application/CreaSettimanaUseCase.kt`
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/application/AggiungiParteUseCase.kt`
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/application/RimuoviParteUseCase.kt`
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/application/RiordinaPartiUseCase.kt`
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/application/CercaTipiParteUseCase.kt`
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/application/AggiornaDatiRemotiUseCase.kt`

**Step 1: Create `CaricaSettimanaUseCase.kt`**

```kotlin
package org.example.project.feature.weeklyparts.application

import org.example.project.feature.weeklyparts.domain.WeekPlan
import java.time.LocalDate

class CaricaSettimanaUseCase(
    private val weekPlanStore: WeekPlanStore,
) {
    suspend operator fun invoke(weekStartDate: LocalDate): WeekPlan? {
        return weekPlanStore.findByDate(weekStartDate)
    }
}
```

**Step 2: Create `CreaSettimanaUseCase.kt`**

```kotlin
package org.example.project.feature.weeklyparts.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import java.time.LocalDate
import java.util.UUID

class CreaSettimanaUseCase(
    private val weekPlanStore: WeekPlanStore,
    private val partTypeStore: PartTypeStore,
) {
    suspend operator fun invoke(weekStartDate: LocalDate): Either<DomainError, WeekPlan> = either {
        val existing = weekPlanStore.findByDate(weekStartDate)
        if (existing != null) raise(DomainError.Validation("La settimana esiste gia'"))

        val fixedPartType = partTypeStore.findFixed()
            ?: raise(DomainError.Validation("Catalogo tipi non disponibile. Aggiorna i dati prima."))

        val weekPlan = WeekPlan(
            id = WeekPlanId(UUID.randomUUID().toString()),
            weekStartDate = weekStartDate,
            parts = emptyList(),
        )
        weekPlanStore.save(weekPlan)
        weekPlanStore.addPart(weekPlan.id, fixedPartType.id.value, sortOrder = 0)

        weekPlanStore.findByDate(weekStartDate)
            ?: raise(DomainError.Validation("Errore nel salvataggio della settimana"))
    }
}
```

**Step 3: Create `AggiungiParteUseCase.kt`**

```kotlin
package org.example.project.feature.weeklyparts.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.WeekPlan
import java.time.LocalDate

class AggiungiParteUseCase(
    private val weekPlanStore: WeekPlanStore,
) {
    suspend operator fun invoke(
        weekStartDate: LocalDate,
        partTypeId: PartTypeId,
    ): Either<DomainError, WeekPlan> = either {
        val weekPlan = weekPlanStore.findByDate(weekStartDate)
            ?: raise(DomainError.Validation("Settimana non trovata"))

        val nextOrder = (weekPlan.parts.maxOfOrNull { it.sortOrder } ?: -1) + 1
        weekPlanStore.addPart(weekPlan.id, partTypeId.value, nextOrder)

        weekPlanStore.findByDate(weekStartDate)
            ?: raise(DomainError.Validation("Errore nel salvataggio"))
    }
}
```

**Step 4: Create `RimuoviParteUseCase.kt`**

```kotlin
package org.example.project.feature.weeklyparts.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.LocalDate

class RimuoviParteUseCase(
    private val weekPlanStore: WeekPlanStore,
) {
    suspend operator fun invoke(
        weekStartDate: LocalDate,
        weeklyPartId: WeeklyPartId,
    ): Either<DomainError, WeekPlan> = either {
        val weekPlan = weekPlanStore.findByDate(weekStartDate)
            ?: raise(DomainError.Validation("Settimana non trovata"))

        val part = weekPlan.parts.find { it.id == weeklyPartId }
            ?: raise(DomainError.Validation("Parte non trovata"))

        if (part.partType.fixed) raise(DomainError.Validation("La parte '${part.partType.label}' non puo' essere rimossa"))

        weekPlanStore.removePart(weeklyPartId)

        weekPlanStore.findByDate(weekStartDate)
            ?: raise(DomainError.Validation("Errore nel salvataggio"))
    }
}
```

**Step 5: Create `RiordinaPartiUseCase.kt`**

```kotlin
package org.example.project.feature.weeklyparts.application

import org.example.project.feature.weeklyparts.domain.WeeklyPartId

class RiordinaPartiUseCase(
    private val weekPlanStore: WeekPlanStore,
) {
    suspend operator fun invoke(orderedPartIds: List<WeeklyPartId>) {
        val updates = orderedPartIds.mapIndexed { index, id -> id to index }
        weekPlanStore.updateSortOrders(updates)
    }
}
```

**Step 6: Create `CercaTipiParteUseCase.kt`**

```kotlin
package org.example.project.feature.weeklyparts.application

import org.example.project.feature.weeklyparts.domain.PartType

class CercaTipiParteUseCase(
    private val partTypeStore: PartTypeStore,
) {
    suspend operator fun invoke(): List<PartType> {
        return partTypeStore.all()
    }
}
```

**Step 7: Create `AggiornaDatiRemotiUseCase.kt`**

This use case returns a result that may include weeks needing confirmation. The UI handles the confirmation dialog.

```kotlin
package org.example.project.feature.weeklyparts.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.infrastructure.GitHubDataSource
import org.example.project.feature.weeklyparts.infrastructure.RemoteWeekSchema
import java.time.LocalDate
import java.util.UUID

data class ImportResult(
    val partTypesImported: Int,
    val weeksImported: Int,
    val weeksSkipped: List<String>,
    val weeksNeedingConfirmation: List<RemoteWeekSchema>,
)

class AggiornaDatiRemotiUseCase(
    private val gitHubDataSource: GitHubDataSource,
    private val partTypeStore: PartTypeStore,
    private val weekPlanStore: WeekPlanStore,
) {
    suspend fun fetchAndImport(
        overwriteDates: Set<LocalDate> = emptySet(),
    ): Either<DomainError, ImportResult> = either {
        val remoteTypes = try {
            gitHubDataSource.fetchPartTypes()
        } catch (e: Exception) {
            raise(DomainError.Validation("Errore nel download del catalogo: ${e.message}"))
        }
        partTypeStore.upsertAll(remoteTypes)

        val remoteSchemas = try {
            gitHubDataSource.fetchWeeklySchemas()
        } catch (e: Exception) {
            raise(DomainError.Validation("Errore nel download degli schemi: ${e.message}"))
        }

        var imported = 0
        val skipped = mutableListOf<String>()
        val needConfirmation = mutableListOf<RemoteWeekSchema>()

        for (schema in remoteSchemas) {
            val date = LocalDate.parse(schema.weekStartDate)
            val existing = weekPlanStore.findByDate(date)

            if (existing != null && date !in overwriteDates) {
                needConfirmation.add(schema)
                continue
            }

            if (existing != null) {
                weekPlanStore.delete(existing.id)
            }

            val newPlan = WeekPlanId(UUID.randomUUID().toString())
            weekPlanStore.save(
                org.example.project.feature.weeklyparts.domain.WeekPlan(
                    id = newPlan,
                    weekStartDate = date,
                    parts = emptyList(),
                )
            )
            weekPlanStore.replaceAllParts(newPlan, schema.partTypeCodes, partTypeStore)
            imported++
        }

        ImportResult(
            partTypesImported = remoteTypes.size,
            weeksImported = imported,
            weeksSkipped = skipped,
            weeksNeedingConfirmation = needConfirmation,
        )
    }
}
```

**Step 8: Build to verify**

Run: `JAVA_HOME=$HOME/.sdkman/candidates/java/24.0.2-tem ./gradlew compileKotlinJvm 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

**Step 9: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/application/
git commit -m "feat(application): add all weekly schema use cases"
```

---

### Task 8: Koin Wiring

**Files:**
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/core/di/AppModules.kt`

**Step 1: Add imports and registrations for all new components**

Add after the existing Proclamatori registrations:

```kotlin
// Imports to add:
import org.example.project.feature.weeklyparts.application.AggiungiParteUseCase
import org.example.project.feature.weeklyparts.application.AggiornaDatiRemotiUseCase
import org.example.project.feature.weeklyparts.application.CaricaSettimanaUseCase
import org.example.project.feature.weeklyparts.application.CercaTipiParteUseCase
import org.example.project.feature.weeklyparts.application.CreaSettimanaUseCase
import org.example.project.feature.weeklyparts.application.PartTypeStore
import org.example.project.feature.weeklyparts.application.RimuoviParteUseCase
import org.example.project.feature.weeklyparts.application.RiordinaPartiUseCase
import org.example.project.feature.weeklyparts.application.WeekPlanStore
import org.example.project.feature.weeklyparts.infrastructure.GitHubDataSource
import org.example.project.feature.weeklyparts.infrastructure.SqlDelightPartTypeStore
import org.example.project.feature.weeklyparts.infrastructure.SqlDelightWeekPlanStore

// Registrations to add inside appModule:
single<PartTypeStore> { SqlDelightPartTypeStore(get()) }
single<WeekPlanStore> { SqlDelightWeekPlanStore(get()) }
single {
    GitHubDataSource(
        partTypesUrl = "https://raw.githubusercontent.com/<OWNER>/<REPO>/main/data/part-types.json",
        weeklySchemasUrl = "https://raw.githubusercontent.com/<OWNER>/<REPO>/main/data/weekly-schemas.json",
    )
}

single { CaricaSettimanaUseCase(get()) }
single { CreaSettimanaUseCase(get(), get()) }
single { AggiungiParteUseCase(get()) }
single { RimuoviParteUseCase(get()) }
single { RiordinaPartiUseCase(get()) }
single { CercaTipiParteUseCase(get()) }
single { AggiornaDatiRemotiUseCase(get(), get(), get()) }
```

Note: Replace `<OWNER>/<REPO>` with the actual GitHub repository for the data files. This URL will be configured by the developer.

**Step 2: Build to verify DI graph**

Run: `JAVA_HOME=$HOME/.sdkman/candidates/java/24.0.2-tem ./gradlew compileKotlinJvm 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/core/di/AppModules.kt
git commit -m "feat(di): wire weekly schema stores, data source, and use cases"
```

---

### Task 9: ViewModel

**Files:**
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/ui/weeklyparts/WeeklyPartsViewModel.kt`

**Step 1: Create ViewModel with state and actions**

```kotlin
package org.example.project.ui.weeklyparts

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.feature.weeklyparts.application.AggiungiParteUseCase
import org.example.project.feature.weeklyparts.application.AggiornaDatiRemotiUseCase
import org.example.project.feature.weeklyparts.application.CaricaSettimanaUseCase
import org.example.project.feature.weeklyparts.application.CercaTipiParteUseCase
import org.example.project.feature.weeklyparts.application.CreaSettimanaUseCase
import org.example.project.feature.weeklyparts.application.RimuoviParteUseCase
import org.example.project.feature.weeklyparts.application.RiordinaPartiUseCase
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import org.example.project.feature.weeklyparts.infrastructure.RemoteWeekSchema
import org.example.project.ui.components.FeedbackBannerKind
import org.example.project.ui.components.FeedbackBannerModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class WeekTimeIndicator { PASSATA, CORRENTE, FUTURA }

data class WeeklyPartsUiState(
    val currentMonday: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
    val weekPlan: WeekPlan? = null,
    val isLoading: Boolean = false,
    val partTypes: List<PartType> = emptyList(),
    val notice: FeedbackBannerModel? = null,
    val isImporting: Boolean = false,
    val weeksNeedingConfirmation: List<RemoteWeekSchema> = emptyList(),
) {
    val weekIndicator: WeekTimeIndicator
        get() {
            val today = LocalDate.now()
            val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            return when {
                currentMonday == thisMonday -> WeekTimeIndicator.CORRENTE
                currentMonday.isAfter(thisMonday) -> WeekTimeIndicator.FUTURA
                else -> WeekTimeIndicator.PASSATA
            }
        }

    val sundayDate: LocalDate get() = currentMonday.plusDays(6)
}

class WeeklyPartsViewModel(
    private val scope: CoroutineScope,
    private val caricaSettimana: CaricaSettimanaUseCase,
    private val creaSettimana: CreaSettimanaUseCase,
    private val aggiungiParte: AggiungiParteUseCase,
    private val rimuoviParte: RimuoviParteUseCase,
    private val riordinaParti: RiordinaPartiUseCase,
    private val cercaTipiParte: CercaTipiParteUseCase,
    private val aggiornaDatiRemoti: AggiornaDatiRemotiUseCase,
) {
    private val _state = MutableStateFlow(WeeklyPartsUiState())
    val state: StateFlow<WeeklyPartsUiState> = _state.asStateFlow()

    init {
        loadWeek()
        loadPartTypes()
    }

    fun navigateToPreviousWeek() {
        _state.update { it.copy(currentMonday = it.currentMonday.minusWeeks(1)) }
        loadWeek()
    }

    fun navigateToNextWeek() {
        _state.update { it.copy(currentMonday = it.currentMonday.plusWeeks(1)) }
        loadWeek()
    }

    fun createWeek() {
        scope.launch {
            _state.update { it.copy(isLoading = true) }
            creaSettimana(_state.value.currentMonday).fold(
                ifLeft = { error ->
                    _state.update { it.copy(
                        isLoading = false,
                        notice = FeedbackBannerModel(error.message, FeedbackBannerKind.ERROR),
                    ) }
                },
                ifRight = { weekPlan ->
                    _state.update { it.copy(isLoading = false, weekPlan = weekPlan) }
                },
            )
        }
    }

    fun addPart(partTypeId: PartTypeId) {
        scope.launch {
            aggiungiParte(_state.value.currentMonday, partTypeId).fold(
                ifLeft = { error ->
                    _state.update { it.copy(
                        notice = FeedbackBannerModel(error.message, FeedbackBannerKind.ERROR),
                    ) }
                },
                ifRight = { weekPlan ->
                    _state.update { it.copy(weekPlan = weekPlan) }
                },
            )
        }
    }

    fun removePart(weeklyPartId: WeeklyPartId) {
        scope.launch {
            rimuoviParte(_state.value.currentMonday, weeklyPartId).fold(
                ifLeft = { error ->
                    _state.update { it.copy(
                        notice = FeedbackBannerModel(error.message, FeedbackBannerKind.ERROR),
                    ) }
                },
                ifRight = { weekPlan ->
                    _state.update { it.copy(weekPlan = weekPlan) }
                },
            )
        }
    }

    fun movePart(fromIndex: Int, toIndex: Int) {
        val currentPlan = _state.value.weekPlan ?: return
        val parts = currentPlan.parts.toMutableList()
        val moved = parts.removeAt(fromIndex)
        parts.add(toIndex, moved)
        val reordered = parts.mapIndexed { i, p -> p.copy(sortOrder = i) }
        _state.update { it.copy(weekPlan = currentPlan.copy(parts = reordered)) }
        scope.launch {
            riordinaParti(reordered.map { it.id })
        }
    }

    fun syncRemoteData(overwriteDates: Set<LocalDate> = emptySet()) {
        scope.launch {
            _state.update { it.copy(isImporting = true, weeksNeedingConfirmation = emptyList()) }
            aggiornaDatiRemoti.fetchAndImport(overwriteDates).fold(
                ifLeft = { error ->
                    _state.update { it.copy(
                        isImporting = false,
                        notice = FeedbackBannerModel(error.message, FeedbackBannerKind.ERROR),
                    ) }
                },
                ifRight = { result ->
                    if (result.weeksNeedingConfirmation.isNotEmpty()) {
                        _state.update { it.copy(
                            isImporting = false,
                            weeksNeedingConfirmation = result.weeksNeedingConfirmation,
                        ) }
                    } else {
                        _state.update { it.copy(
                            isImporting = false,
                            notice = FeedbackBannerModel(
                                "Aggiornamento completato: ${result.partTypesImported} tipi, ${result.weeksImported} settimane",
                                FeedbackBannerKind.SUCCESS,
                            ),
                        ) }
                        loadWeek()
                        loadPartTypes()
                    }
                },
            )
        }
    }

    fun confirmOverwrite(dates: Set<LocalDate>) {
        syncRemoteData(overwriteDates = dates)
    }

    fun dismissConfirmation() {
        _state.update { it.copy(weeksNeedingConfirmation = emptyList()) }
        loadWeek()
        loadPartTypes()
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }

    private fun loadWeek() {
        scope.launch {
            _state.update { it.copy(isLoading = true) }
            val weekPlan = caricaSettimana(_state.value.currentMonday)
            _state.update { it.copy(isLoading = false, weekPlan = weekPlan) }
        }
    }

    private fun loadPartTypes() {
        scope.launch {
            val types = cercaTipiParte()
            _state.update { it.copy(partTypes = types) }
        }
    }

    private val org.example.project.core.domain.DomainError.message: String
        get() = when (this) {
            is org.example.project.core.domain.DomainError.Validation -> message
            is org.example.project.core.domain.DomainError.NotImplemented -> "Non implementato: $area"
        }
}
```

**Step 2: Build to verify**

Run: `JAVA_HOME=$HOME/.sdkman/candidates/java/24.0.2-tem ./gradlew compileKotlinJvm 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/ui/weeklyparts/WeeklyPartsViewModel.kt
git commit -m "feat(ui): add WeeklyPartsViewModel with week navigation and sync"
```

---

### Task 10: UI Screen

**Files:**
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/weeklyparts/WeeklyPartsScreen.kt`

This is the largest task. The screen includes:
1. "Aggiorna dati" button (top)
2. Week navigator with arrows and temporal indicator
3. "Crea settimana" button (when week doesn't exist)
4. Parts table with N., Tipo, Persone, Regola, delete button
5. "Aggiungi parte" dropdown/autocomplete
6. Overwrite confirmation dialog

**This is a design-heavy composable — use the `frontend-design` skill for the actual UI composition.**

The implementer should:

**Step 1:** Replace the stub in `WeeklyPartsScreen.kt` with a composable that:
- Instantiates `WeeklyPartsViewModel` (same pattern as `ProclamatoriScreen` — manual Koin injection via `GlobalContext.get()`)
- Collects `state` via `collectAsState()`
- Renders the layout from the design doc

**Step 2:** Wire up composable components:
- `WeekNavigator` — Row with IconButton (ChevronLeft/Right), week date text, temporal indicator chip
- `PartsTable` — reuses `StandardTableHeader` / `StandardTableViewport` patterns from `TableStandard.kt`
- `AddPartDropdown` — `ExposedDropdownMenuBox` with `TextField` + `DropdownMenu` filtering `partTypes` by label
- `OverwriteConfirmDialog` — `AlertDialog` listing weeks that need confirmation, with checkboxes

**Step 3:** Build and test manually

Run: `JAVA_HOME=$HOME/.sdkman/candidates/java/24.0.2-tem ./gradlew run`
Verify: Navigate to "Parti" tab, see week navigator, create a week, add parts.

**Step 4: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/ui/weeklyparts/
git commit -m "feat(ui): implement WeeklyPartsScreen with week navigation and parts table"
```

---

### Task 11: GitHub Data Files (Seed Data)

**Files:**
- Create: `data/part-types.json` (at repo root, or in a separate data repo)
- Create: `data/weekly-schemas.json`

**Step 1: Create `data/part-types.json`**

Seed with the known part types from the meeting workbook:

```json
{
  "version": 1,
  "partTypes": [
    {
      "code": "LETTURA_BIBLICA",
      "label": "Lettura biblica",
      "peopleCount": 1,
      "sexRule": "UOMO",
      "fixed": true
    },
    {
      "code": "INIZIARE_CONVERSAZIONE",
      "label": "Iniziare una conversazione",
      "peopleCount": 2,
      "sexRule": "LIBERO",
      "fixed": false
    },
    {
      "code": "FARE_REVISITA",
      "label": "Fare una revisita",
      "peopleCount": 2,
      "sexRule": "LIBERO",
      "fixed": false
    },
    {
      "code": "STUDIO_BIBLICO",
      "label": "Tenere uno studio biblico",
      "peopleCount": 2,
      "sexRule": "LIBERO",
      "fixed": false
    },
    {
      "code": "SPIEGARE_CIO_CHE_SI_CREDE",
      "label": "Spiegare quello in cui si crede",
      "peopleCount": 2,
      "sexRule": "LIBERO",
      "fixed": false
    },
    {
      "code": "DISCORSO",
      "label": "Discorso",
      "peopleCount": 1,
      "sexRule": "UOMO",
      "fixed": false
    },
    {
      "code": "DISCORSO_CON_UDITORIO",
      "label": "Discorso con domande dall'uditorio",
      "peopleCount": 1,
      "sexRule": "UOMO",
      "fixed": false
    }
  ]
}
```

**Step 2: Create `data/weekly-schemas.json`**

Seed with a sample week for testing:

```json
{
  "version": 1,
  "weeks": [
    {
      "weekStartDate": "2026-02-16",
      "parts": [
        { "partTypeCode": "LETTURA_BIBLICA", "sortOrder": 0 },
        { "partTypeCode": "INIZIARE_CONVERSAZIONE", "sortOrder": 1 },
        { "partTypeCode": "INIZIARE_CONVERSAZIONE", "sortOrder": 2 },
        { "partTypeCode": "SPIEGARE_CIO_CHE_SI_CREDE", "sortOrder": 3 }
      ]
    }
  ]
}
```

**Step 3: Commit**

```bash
git add data/
git commit -m "feat(data): add seed part types catalog and sample weekly schema"
```

---

### Task 12: Integration Verification

**Step 1: Run full build**

Run: `JAVA_HOME=$HOME/.sdkman/candidates/java/24.0.2-tem ./gradlew build 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

**Step 2: Run app and verify manually**

Run: `JAVA_HOME=$HOME/.sdkman/candidates/java/24.0.2-tem ./gradlew run`

Verify checklist:
- [ ] Navigate to "Parti" tab
- [ ] See week navigator with current week displayed
- [ ] Temporal indicator shows "Corrente" in green
- [ ] Navigate forward/backward with arrows
- [ ] Click "Crea settimana" — week is created with Lettura biblica pre-populated
- [ ] Click "Aggiorna dati" — catalogo and schemas download from GitHub
- [ ] Add a part via dropdown — appears in table
- [ ] Remove a part via x button — removed from table
- [ ] Lettura biblica cannot be removed (x button not shown)
- [ ] Navigate to existing imported week — parts are displayed

**Step 3: Final commit if any fixes needed**

```bash
git add -A
git commit -m "fix: integration fixes for weekly schema feature"
```

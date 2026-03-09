package org.example.project.feature.weeklyparts

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.runBlocking
import org.example.project.db.MinisteroDatabase
import org.example.project.core.persistence.SqlDelightTransactionRunner
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.SexRule
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanAggregate
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeeklyPart
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import org.example.project.feature.weeklyparts.infrastructure.SqlDelightPartTypeStore
import org.example.project.feature.weeklyparts.infrastructure.SqlDelightWeekPlanStore
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WeeklyPartSnapshotTest {

    private fun inMemoryDb(): MinisteroDatabase {
        val driver = JdbcSqliteDriver(url = JdbcSqliteDriver.IN_MEMORY, schema = MinisteroDatabase.Schema)
        driver.execute(null, "PRAGMA foreign_keys = ON;", 0)
        return MinisteroDatabase(driver)
    }

    private fun lettura(label: String = "Lettura", peopleCount: Int = 1) = PartType(
        id = PartTypeId(""),
        code = "LETTURA",
        label = label,
        peopleCount = peopleCount,
        sexRule = SexRule.STESSO_SESSO,
        fixed = false,
        sortOrder = 0,
    )

    @Test
    fun `partsForWeek include snapshot quando revisione esiste`() = runBlocking<Unit> {
        val db = inMemoryDb()
        val partTypeStore = SqlDelightPartTypeStore(db)
        val weekStore = SqlDelightWeekPlanStore(db)
        val txRunner = SqlDelightTransactionRunner(db)

        txRunner.runInTransaction { partTypeStore.upsertAll(listOf(lettura())) }
        val ptId = partTypeStore.findByCode("LETTURA")!!.id
        val revisionId = partTypeStore.getLatestRevisionId(ptId)!!

        val weekPlan = WeekPlan(
            id = WeekPlanId("week-1"),
            weekStartDate = LocalDate.of(2026, 3, 2),
            parts = listOf(
                WeeklyPart(
                    id = WeeklyPartId("part-1"),
                    partType = partTypeStore.findByCode("LETTURA")!!,
                    partTypeRevisionId = revisionId,
                    sortOrder = 0,
                ),
            ),
        )
        txRunner.runInTransaction {
            weekStore.saveAggregate(WeekPlanAggregate(weekPlan = weekPlan, assignments = emptyList()))
        }

        val loaded = weekStore.findByDate(LocalDate.of(2026, 3, 2))!!
        val part = loaded.parts.first()

        val snapshot = assertNotNull(part.snapshot)
        assertEquals("Lettura", snapshot.label)
        assertEquals(1, snapshot.peopleCount)
    }

    @Test
    fun `partsForWeek ha null snapshot quando nessuna revisione salvata`() = runBlocking<Unit> {
        val db = inMemoryDb()
        val partTypeStore = SqlDelightPartTypeStore(db)
        val weekStore = SqlDelightWeekPlanStore(db)
        val txRunner = SqlDelightTransactionRunner(db)

        txRunner.runInTransaction { partTypeStore.upsertAll(listOf(lettura())) }
        val ptId = partTypeStore.findByCode("LETTURA")!!.id

        val weekPlan = WeekPlan(
            id = WeekPlanId("week-1"),
            weekStartDate = LocalDate.of(2026, 3, 2),
            parts = listOf(
                WeeklyPart(
                    id = WeeklyPartId("part-1"),
                    partType = partTypeStore.findByCode("LETTURA")!!,
                    partTypeRevisionId = null,
                    sortOrder = 0,
                ),
            ),
        )
        txRunner.runInTransaction {
            weekStore.saveAggregate(WeekPlanAggregate(weekPlan = weekPlan, assignments = emptyList()))
        }

        val loaded = weekStore.findByDate(LocalDate.of(2026, 3, 2))!!
        val part = loaded.parts.first()

        assertNull(part.snapshot)
    }

    @Test
    fun `snapshot riflette attributi al momento di creazione non quelli attuali`() = runBlocking<Unit> {
        val db = inMemoryDb()
        val partTypeStore = SqlDelightPartTypeStore(db)
        val weekStore = SqlDelightWeekPlanStore(db)
        val txRunner = SqlDelightTransactionRunner(db)

        // Import v1: label="Lettura", peopleCount=1
        txRunner.runInTransaction { partTypeStore.upsertAll(listOf(lettura(label = "Lettura", peopleCount = 1))) }
        val ptId = partTypeStore.findByCode("LETTURA")!!.id
        val revV1 = partTypeStore.getLatestRevisionId(ptId)!!

        // Create week with v1 snapshot
        val weekPlan = WeekPlan(
            id = WeekPlanId("week-1"),
            weekStartDate = LocalDate.of(2026, 3, 2),
            parts = listOf(
                WeeklyPart(
                    id = WeeklyPartId("part-1"),
                    partType = partTypeStore.findByCode("LETTURA")!!,
                    partTypeRevisionId = revV1,
                    sortOrder = 0,
                ),
            ),
        )
        txRunner.runInTransaction {
            weekStore.saveAggregate(WeekPlanAggregate(weekPlan = weekPlan, assignments = emptyList()))
        }

        // Re-import v2: label cambiato, peopleCount=2
        txRunner.runInTransaction { partTypeStore.upsertAll(listOf(lettura(label = "Lettura Biblica", peopleCount = 2))) }

        // Load week: deve mostrare snapshot v1, non gli attributi live v2
        val loaded = weekStore.findByDate(LocalDate.of(2026, 3, 2))!!
        val part = loaded.parts.first()

        val snapshot = assertNotNull(part.snapshot)
        assertEquals("Lettura", snapshot.label)      // NOT "Lettura Biblica"
        assertEquals(1, snapshot.peopleCount)         // NOT 2
    }
}

package org.example.project.feature.assignments

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.runBlocking
import org.example.project.db.MinisteroDatabase
import org.example.project.feature.assignments.infrastructure.SqlDelightAssignmentStore
import org.example.project.feature.weeklyparts.domain.PartTypeId
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class SqlDelightAssignmentStoreTest {

    @Test
    fun `suggestedProclamatori uses absolute distance when latest assignment is in the future`() = runBlocking {
        val driver = JdbcSqliteDriver(
            url = JdbcSqliteDriver.IN_MEMORY,
            schema = MinisteroDatabase.Schema,
        )
        driver.execute(null, "PRAGMA foreign_keys = ON;", 0)
        val database = MinisteroDatabase(driver)
        val queries = database.ministeroDatabaseQueries

        queries.upsertProclaimer(
            id = "person-1",
            first_name = "Mario",
            last_name = "Rossi",
            sex = "M",
            suspended = 0,
            can_assist = 1,
        )
        queries.upsertPartType(
            id = "part-type-1",
            code = "LETTURA",
            label = "Lettura",
            people_count = 1,
            sex_rule = "STESSO_SESSO",
            fixed = 0,
            sort_order = 0,
        )

        queries.insertWeekPlan(
            id = "week-past",
            week_start_date = "2026-02-02",
        )
        queries.insertWeekPlan(
            id = "week-future",
            week_start_date = "2026-02-16",
        )
        queries.insertWeeklyPart(
            id = "weekly-part-past",
            week_plan_id = "week-past",
            part_type_id = "part-type-1",
            sort_order = 0,
        )
        queries.insertWeeklyPart(
            id = "weekly-part-future",
            week_plan_id = "week-future",
            part_type_id = "part-type-1",
            sort_order = 0,
        )
        queries.upsertAssignment(
            id = "assignment-past",
            weekly_part_id = "weekly-part-past",
            person_id = "person-1",
            slot = 1,
        )
        queries.upsertAssignment(
            id = "assignment-future",
            weekly_part_id = "weekly-part-future",
            person_id = "person-1",
            slot = 1,
        )

        val store = SqlDelightAssignmentStore(database)
        val suggestion = store.suggestedProclamatori(
            partTypeId = PartTypeId("part-type-1"),
            slot = 1,
            referenceDate = LocalDate.parse("2026-02-09"),
        ).firstOrNull { it.proclamatore.id.value == "person-1" }

        assertNotNull(suggestion)
        assertEquals(1, suggestion.lastGlobalWeeks)
        assertEquals(7, suggestion.lastGlobalDays)
        assertEquals(1, suggestion.lastForPartTypeWeeks)
        assertEquals(7, suggestion.lastForPartTypeDays)
        assertTrue(suggestion.lastGlobalInFuture)
        assertTrue(suggestion.lastForPartTypeInFuture)
    }

    @Test
    fun `suggestedProclamatori uses absolute distance for future-only assignments`() = runBlocking {
        val driver = JdbcSqliteDriver(
            url = JdbcSqliteDriver.IN_MEMORY,
            schema = MinisteroDatabase.Schema,
        )
        driver.execute(null, "PRAGMA foreign_keys = ON;", 0)
        val database = MinisteroDatabase(driver)
        val queries = database.ministeroDatabaseQueries

        queries.upsertProclaimer(
            id = "person-1",
            first_name = "Mario",
            last_name = "Rossi",
            sex = "M",
            suspended = 0,
            can_assist = 1,
        )
        queries.upsertPartType(
            id = "part-type-1",
            code = "LETTURA",
            label = "Lettura",
            people_count = 1,
            sex_rule = "STESSO_SESSO",
            fixed = 0,
            sort_order = 0,
        )

        queries.insertWeekPlan(
            id = "week-future",
            week_start_date = "2026-02-16",
        )
        queries.insertWeeklyPart(
            id = "weekly-part-future",
            week_plan_id = "week-future",
            part_type_id = "part-type-1",
            sort_order = 0,
        )
        queries.upsertAssignment(
            id = "assignment-future",
            weekly_part_id = "weekly-part-future",
            person_id = "person-1",
            slot = 1,
        )

        val store = SqlDelightAssignmentStore(database)
        val suggestion = store.suggestedProclamatori(
            partTypeId = PartTypeId("part-type-1"),
            slot = 1,
            referenceDate = LocalDate.parse("2026-02-09"),
        ).firstOrNull { it.proclamatore.id.value == "person-1" }

        assertNotNull(suggestion)
        assertEquals(1, suggestion.lastGlobalWeeks)
        assertEquals(7, suggestion.lastGlobalDays)
        assertEquals(1, suggestion.lastForPartTypeWeeks)
        assertEquals(7, suggestion.lastForPartTypeDays)
        assertTrue(suggestion.lastGlobalInFuture)
        assertTrue(suggestion.lastForPartTypeInFuture)
    }
}

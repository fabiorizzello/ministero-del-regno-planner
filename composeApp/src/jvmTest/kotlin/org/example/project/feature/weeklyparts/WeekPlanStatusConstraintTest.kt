package org.example.project.feature.weeklyparts

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.example.project.db.MinisteroDatabase
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WeekPlanStatusConstraintTest {

    @Test
    fun `insert with invalid status violates check constraint`() {
        val driver = JdbcSqliteDriver(
            url = JdbcSqliteDriver.IN_MEMORY,
            schema = MinisteroDatabase.Schema,
        )
        driver.execute(null, "PRAGMA foreign_keys = ON;", 0)
        val database = MinisteroDatabase(driver)

        val error = assertFailsWith<Exception> {
            database.ministeroDatabaseQueries.insertWeekPlanWithProgram(
                id = "week-invalid",
                week_start_date = "2026-03-02",
                program_id = null,
                status = "INVALID",
            )
        }

        assertTrue(error.message?.contains("check", ignoreCase = true) == true)
    }

    @Test
    fun `update with invalid status violates check constraint`() {
        val driver = JdbcSqliteDriver(
            url = JdbcSqliteDriver.IN_MEMORY,
            schema = MinisteroDatabase.Schema,
        )
        driver.execute(null, "PRAGMA foreign_keys = ON;", 0)
        val database = MinisteroDatabase(driver)

        database.ministeroDatabaseQueries.insertWeekPlanWithProgram(
            id = "week-valid",
            week_start_date = "2026-03-09",
            program_id = null,
            status = "ACTIVE",
        )

        val error = assertFailsWith<Exception> {
            database.ministeroDatabaseQueries.updateWeekPlanStatus(
                status = "BROKEN",
                id = "week-valid",
            )
        }

        assertTrue(error.message?.contains("check", ignoreCase = true) == true)
    }
}

package org.example.project.feature.diagnostics

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.example.project.db.MinisteroDatabase
import org.example.project.feature.diagnostics.infrastructure.SqlDelightEquitaQuery
import org.example.project.feature.people.domain.ProclamatoreId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlDelightEquitaQueryTest {

    @Test
    fun `returns people with zero assignments and ignores outside window plus future assignments`() = runTest {
        val database = createDb()
        seedScenario(database)
        val query = SqlDelightEquitaQuery(database)

        val rows = query.listPersonAggregates(
            sinceDate = LocalDate.parse("2026-02-01"),
            untilDate = LocalDate.parse("2026-04-13"),
        )

        assertEquals(3, rows.size)

        val zero = rows.first { it.proclamatore.id == ProclamatoreId("person-zero") }
        val inside = rows.first { it.proclamatore.id == ProclamatoreId("person-inside") }
        val outside = rows.first { it.proclamatore.id == ProclamatoreId("person-outside") }

        assertEquals(0, zero.totaleInFinestra)
        assertTrue(zero.settimaneAssegnate.isEmpty())
        assertNull(zero.ultimaAssegnazione)

        assertEquals(1, inside.totaleInFinestra)
        assertEquals(1, inside.conduzioniInFinestra)
        assertEquals(0, inside.assistenzeInFinestra)
        assertEquals(LocalDate.parse("2026-03-09"), inside.ultimaAssegnazione)
        assertEquals(setOf(LocalDate.parse("2026-03-09")), inside.settimaneAssegnate)

        assertEquals(0, outside.totaleInFinestra)
        assertNull(outside.ultimaAssegnazione)
        assertTrue(outside.settimaneAssegnate.isEmpty())
    }

    private fun createDb(): MinisteroDatabase {
        val driver = JdbcSqliteDriver(
            url = JdbcSqliteDriver.IN_MEMORY,
            schema = MinisteroDatabase.Schema,
        )
        driver.execute(null, "PRAGMA foreign_keys = ON;", 0)
        return MinisteroDatabase(driver)
    }

    private fun seedScenario(database: MinisteroDatabase) {
        val q = database.ministeroDatabaseQueries
        q.upsertPartType("pt-1", "LETT", "Lettura", 1, "UOMO", 0, 0)

        q.upsertProclaimer("person-zero", "Anna", "Zero", "F", 0, 1)
        q.upsertProclaimer("person-inside", "Bruno", "Dentro", "M", 0, 1)
        q.upsertProclaimer("person-outside", "Carlo", "Fuori", "M", 0, 1)

        q.insertWeekPlan("week-old", "2025-01-06")
        q.insertWeekPlan("week-inside", "2026-03-09")
        q.insertWeekPlan("week-future", "2026-05-04")

        q.insertWeeklyPart("wp-old", "week-old", "pt-1", null, 0)
        q.insertWeeklyPart("wp-inside", "week-inside", "pt-1", null, 0)
        q.insertWeeklyPart("wp-future", "week-future", "pt-1", null, 0)

        q.upsertAssignment("a-inside", "wp-inside", "person-inside", 1)
        q.upsertAssignment("a-future", "wp-future", "person-inside", 1)
        q.upsertAssignment("a-old", "wp-old", "person-outside", 1)

        val rawInside = q.equityPersonAggregates(
            sinceDate = "2026-02-01",
            untilDate = "2026-04-13",
        ).executeAsList()
        assertNotNull(rawInside.firstOrNull { it.person_id == "person-zero" })
    }
}

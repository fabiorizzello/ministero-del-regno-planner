package org.example.project.core.persistence

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.example.project.db.MinisteroDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MigrationV2ToV3Test {

    private fun freshDriver(): JdbcSqliteDriver =
        JdbcSqliteDriver(url = JdbcSqliteDriver.IN_MEMORY).also { driver ->
            driver.execute(null, "PRAGMA foreign_keys = ON;", 0)
            MinisteroDatabase.Schema.create(driver).value
        }

    private fun execute(driver: JdbcSqliteDriver, sql: String) {
        driver.execute(identifier = null, sql = sql, parameters = 0)
    }

    private fun queryString(driver: JdbcSqliteDriver, sql: String): String? =
        driver.executeQuery(
            identifier = null,
            sql = sql,
            mapper = { cursor ->
                app.cash.sqldelight.db.QueryResult.Value(
                    if (cursor.next().value) cursor.getString(0) else null,
                )
            },
            parameters = 0,
        ).value

    private fun queryLong(driver: JdbcSqliteDriver, sql: String): Long? =
        driver.executeQuery(
            identifier = null,
            sql = sql,
            mapper = { cursor ->
                app.cash.sqldelight.db.QueryResult.Value(
                    if (cursor.next().value) cursor.getLong(0) else null,
                )
            },
            parameters = 0,
        ).value

    @Test
    fun `adds Cosa direste as a distinct one-brother part type`() {
        val driver = freshDriver()

        MinisteroDatabase.Schema.migrate(driver, 2L, 3L).value

        val id = assertNotNull(
            queryString(driver, "SELECT id FROM part_type WHERE code = 'COSA_DIRESTE'"),
        )
        assertEquals("Cosa direste?", queryString(driver, "SELECT label FROM part_type WHERE id = '$id'"))
        assertEquals(1L, queryLong(driver, "SELECT people_count FROM part_type WHERE id = '$id'"))
        assertEquals("UOMO", queryString(driver, "SELECT sex_rule FROM part_type WHERE id = '$id'"))
        assertEquals(0L, queryLong(driver, "SELECT fixed FROM part_type WHERE id = '$id'"))
    }

    @Test
    fun `leaves Cosa direste eligibility unchecked for every existing person`() {
        val driver = freshDriver()
        execute(
            driver,
            "INSERT INTO part_type(id, code, label, people_count, sex_rule, fixed, active, sort_order) " +
                "VALUES ('pt-discorso', 'DISCORSO', 'Discorso', 1, 'UOMO', 0, 1, 4)",
        )
        listOf("andrea", "bruno", "cesare").forEach { person ->
            execute(
                driver,
                "INSERT INTO person(id, first_name, last_name, sex) " +
                    "VALUES ('$person', '$person', 'Test', 'M')",
            )
        }
        execute(
            driver,
            "INSERT INTO person_part_type_eligibility(id, person_id, part_type_id, can_lead) " +
                "VALUES ('e1', 'andrea', 'pt-discorso', 1)",
        )
        execute(
            driver,
            "INSERT INTO person_part_type_eligibility(id, person_id, part_type_id, can_lead) " +
                "VALUES ('e2', 'bruno', 'pt-discorso', 0)",
        )

        MinisteroDatabase.Schema.migrate(driver, 2L, 3L).value

        val newTypeId = assertNotNull(
            queryString(driver, "SELECT id FROM part_type WHERE code = 'COSA_DIRESTE'"),
        )
        assertNull(
            queryLong(
                driver,
                "SELECT can_lead FROM person_part_type_eligibility " +
                    "WHERE person_id = 'andrea' AND part_type_id = '$newTypeId'",
            ),
        )
        assertNull(
            queryLong(
                driver,
                "SELECT can_lead FROM person_part_type_eligibility " +
                    "WHERE person_id = 'bruno' AND part_type_id = '$newTypeId'",
            ),
        )
        assertNull(
            queryLong(
                driver,
                "SELECT can_lead FROM person_part_type_eligibility " +
                    "WHERE person_id = 'cesare' AND part_type_id = '$newTypeId'",
            ),
        )
    }
}

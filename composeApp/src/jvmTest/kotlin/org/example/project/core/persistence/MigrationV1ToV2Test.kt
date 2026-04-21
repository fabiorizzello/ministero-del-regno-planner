package org.example.project.core.persistence

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.example.project.db.MinisteroDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MigrationV1ToV2Test {

    private fun freshDriverAtV1(): JdbcSqliteDriver {
        val driver = JdbcSqliteDriver(url = JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, "PRAGMA foreign_keys = ON;", 0)
        MinisteroDatabase.Schema.create(driver).value
        return driver
    }

    private fun seedPartType(
        driver: JdbcSqliteDriver,
        id: String,
        code: String,
        label: String,
        peopleCount: Int,
        sexRule: String,
        sortOrder: Int,
    ) {
        driver.execute(
            identifier = null,
            sql = """
                INSERT INTO part_type(id, code, label, people_count, sex_rule, fixed, active, sort_order, current_revision_id)
                VALUES ('$id', '$code', '$label', $peopleCount, '$sexRule', 0, 1, $sortOrder, NULL)
            """.trimIndent(),
            parameters = 0,
        )
    }

    private fun seedPerson(driver: JdbcSqliteDriver, id: String, sex: String) {
        driver.execute(
            identifier = null,
            sql = "INSERT INTO person(id, first_name, last_name, sex, suspended, can_assist) " +
                "VALUES ('$id', 'N$id', 'L$id', '$sex', 0, 1)",
            parameters = 0,
        )
    }

    private fun seedEligibility(
        driver: JdbcSqliteDriver,
        id: String,
        personId: String,
        partTypeId: String,
        canLead: Int,
    ) {
        driver.execute(
            identifier = null,
            sql = "INSERT INTO person_part_type_eligibility(id, person_id, part_type_id, can_lead) " +
                "VALUES ('$id', '$personId', '$partTypeId', $canLead)",
            parameters = 0,
        )
    }

    private fun queryPartType(driver: JdbcSqliteDriver, code: String): PartTypeRow? {
        return driver.executeQuery(
            identifier = null,
            sql = "SELECT id, label, people_count, sex_rule, sort_order FROM part_type WHERE code = '$code'",
            mapper = { cursor ->
                app.cash.sqldelight.db.QueryResult.Value(
                    if (cursor.next().value) {
                        PartTypeRow(
                            id = cursor.getString(0)!!,
                            label = cursor.getString(1)!!,
                            peopleCount = cursor.getLong(2)!!.toInt(),
                            sexRule = cursor.getString(3)!!,
                            sortOrder = cursor.getLong(4)!!.toInt(),
                        )
                    } else null,
                )
            },
            parameters = 0,
        ).value
    }

    private fun queryEligibilityCount(driver: JdbcSqliteDriver, partTypeId: String): Int {
        return driver.executeQuery(
            identifier = null,
            sql = "SELECT COUNT(*) FROM person_part_type_eligibility WHERE part_type_id = '$partTypeId'",
            mapper = { cursor ->
                app.cash.sqldelight.db.QueryResult.Value(
                    if (cursor.next().value) cursor.getLong(0)!!.toInt() else 0,
                )
            },
            parameters = 0,
        ).value
    }

    private fun queryCanLead(
        driver: JdbcSqliteDriver,
        personId: String,
        partTypeId: String,
    ): Int? {
        return driver.executeQuery(
            identifier = null,
            sql = "SELECT can_lead FROM person_part_type_eligibility " +
                "WHERE person_id = '$personId' AND part_type_id = '$partTypeId'",
            mapper = { cursor ->
                app.cash.sqldelight.db.QueryResult.Value(
                    if (cursor.next().value) cursor.getLong(0)!!.toInt() else null,
                )
            },
            parameters = 0,
        ).value
    }

    private data class PartTypeRow(
        val id: String,
        val label: String,
        val peopleCount: Int,
        val sexRule: String,
        val sortOrder: Int,
    )

    @Test
    fun `updates SPIEGARE label and appends new part type at the end of sort order`() {
        val driver = freshDriverAtV1()
        seedPartType(
            driver,
            id = "pt-spiegare",
            code = "SPIEGARE_CIO_CHE_SI_CREDE",
            label = "Spiegare cosa si crede",
            peopleCount = 2,
            sexRule = "STESSO_SESSO",
            sortOrder = 4,
        )
        seedPartType(
            driver,
            id = "pt-discorso",
            code = "DISCORSO",
            label = "Discorso",
            peopleCount = 1,
            sexRule = "UOMO",
            sortOrder = 6,
        )
        seedPartType(
            driver,
            id = "pt-extra",
            code = "EXTRA",
            label = "Extra",
            peopleCount = 1,
            sexRule = "UOMO",
            sortOrder = 10,
        )

        MinisteroDatabase.Schema.migrate(driver, 1L, 2L).value

        val spiegare = assertNotNull(queryPartType(driver, "SPIEGARE_CIO_CHE_SI_CREDE"))
        assertEquals("Spiegare quello in cui si crede - Dimostrazione", spiegare.label)

        val newType = assertNotNull(queryPartType(driver, "SPIEGARE_CIO_CHE_SI_CREDE_DISCORSO"))
        assertEquals("Spiegare quello in cui si crede - Discorso", newType.label)
        assertEquals(1, newType.peopleCount)
        assertEquals("UOMO", newType.sexRule)
        assertEquals(11, newType.sortOrder)
    }

    @Test
    fun `copies DISCORSO eligibility only for can_lead 1 rows`() {
        val driver = freshDriverAtV1()
        seedPartType(driver, "pt-spiegare", "SPIEGARE_CIO_CHE_SI_CREDE", "old", 2, "STESSO_SESSO", 4)
        seedPartType(driver, "pt-discorso", "DISCORSO", "Discorso", 1, "UOMO", 6)
        seedPerson(driver, "p-andrea", "M")
        seedPerson(driver, "p-bruno", "M")
        seedPerson(driver, "p-cesare", "M")
        seedEligibility(driver, "e1", "p-andrea", "pt-discorso", canLead = 1)
        seedEligibility(driver, "e2", "p-bruno", "pt-discorso", canLead = 0)

        MinisteroDatabase.Schema.migrate(driver, 1L, 2L).value

        val newType = assertNotNull(queryPartType(driver, "SPIEGARE_CIO_CHE_SI_CREDE_DISCORSO"))
        assertEquals(1, queryEligibilityCount(driver, newType.id))
        assertEquals(1, queryCanLead(driver, "p-andrea", newType.id))
        assertNull(queryCanLead(driver, "p-bruno", newType.id))
        assertNull(queryCanLead(driver, "p-cesare", newType.id))
    }

    @Test
    fun `is a no-op when part_type is empty`() {
        val driver = freshDriverAtV1()

        MinisteroDatabase.Schema.migrate(driver, 1L, 2L).value

        assertNull(queryPartType(driver, "SPIEGARE_CIO_CHE_SI_CREDE_DISCORSO"))
        assertNull(queryPartType(driver, "SPIEGARE_CIO_CHE_SI_CREDE"))
    }

    @Test
    fun `is idempotent when migrate is applied twice`() {
        val driver = freshDriverAtV1()
        seedPartType(driver, "pt-spiegare", "SPIEGARE_CIO_CHE_SI_CREDE", "old", 2, "STESSO_SESSO", 4)
        seedPartType(driver, "pt-discorso", "DISCORSO", "Discorso", 1, "UOMO", 6)
        seedPerson(driver, "p-andrea", "M")
        seedEligibility(driver, "e1", "p-andrea", "pt-discorso", canLead = 1)

        MinisteroDatabase.Schema.migrate(driver, 1L, 2L).value
        val firstId = assertNotNull(queryPartType(driver, "SPIEGARE_CIO_CHE_SI_CREDE_DISCORSO")).id

        MinisteroDatabase.Schema.migrate(driver, 1L, 2L).value
        val secondId = assertNotNull(queryPartType(driver, "SPIEGARE_CIO_CHE_SI_CREDE_DISCORSO")).id

        assertEquals(firstId, secondId)
        assertEquals(1, queryEligibilityCount(driver, secondId))
    }
}

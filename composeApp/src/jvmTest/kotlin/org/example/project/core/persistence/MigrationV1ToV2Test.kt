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
        fixed: Int = 0,
    ) {
        driver.execute(
            identifier = null,
            sql = """
                INSERT INTO part_type(id, code, label, people_count, sex_rule, fixed, active, sort_order, current_revision_id)
                VALUES ('$id', '$code', '$label', $peopleCount, '$sexRule', $fixed, 1, $sortOrder, NULL)
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
            sql = "SELECT id, label, people_count, sex_rule, sort_order, fixed, active FROM part_type WHERE code = '$code'",
            mapper = { cursor ->
                app.cash.sqldelight.db.QueryResult.Value(
                    if (cursor.next().value) {
                        PartTypeRow(
                            id = cursor.getString(0)!!,
                            label = cursor.getString(1)!!,
                            peopleCount = cursor.getLong(2)!!.toInt(),
                            sexRule = cursor.getString(3)!!,
                            sortOrder = cursor.getLong(4)!!.toInt(),
                            fixed = cursor.getLong(5)!!.toInt(),
                            active = cursor.getLong(6)!!.toInt(),
                        )
                    } else null,
                )
            },
            parameters = 0,
        ).value
    }

    private fun queryPartTypeCount(driver: JdbcSqliteDriver): Int {
        return driver.executeQuery(
            identifier = null,
            sql = "SELECT COUNT(*) FROM part_type",
            mapper = { cursor ->
                app.cash.sqldelight.db.QueryResult.Value(
                    if (cursor.next().value) cursor.getLong(0)!!.toInt() else 0,
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
        val fixed: Int,
        val active: Int,
    )

    @Test
    fun `seeds 7 canonical part types on an empty database`() {
        val driver = freshDriverAtV1()

        MinisteroDatabase.Schema.migrate(driver, 1L, 2L).value

        assertEquals(7, queryPartTypeCount(driver))

        val lettura = assertNotNull(queryPartType(driver, "LETTURA_DELLA_BIBBIA"))
        assertEquals("Lettura Biblica", lettura.label)
        assertEquals(1, lettura.peopleCount)
        assertEquals("UOMO", lettura.sexRule)
        assertEquals(1, lettura.fixed)
        assertEquals(0, lettura.sortOrder)

        val iniziare = assertNotNull(queryPartType(driver, "INIZIARE_CONVERSAZIONE"))
        assertEquals("Iniziare una conversazione", iniziare.label)
        assertEquals(2, iniziare.peopleCount)
        assertEquals("STESSO_SESSO", iniziare.sexRule)
        assertEquals(1, iniziare.sortOrder)

        val coltivare = assertNotNull(queryPartType(driver, "COLTIVARE_INTERESSE"))
        assertEquals("Coltivare l'interesse", coltivare.label)
        assertEquals(2, coltivare.sortOrder)

        val fareDiscepoli = assertNotNull(queryPartType(driver, "FARE_DISCEPOLI"))
        assertEquals("Fare discepoli", fareDiscepoli.label)
        assertEquals(3, fareDiscepoli.sortOrder)

        val discorso = assertNotNull(queryPartType(driver, "DISCORSO"))
        assertEquals("Discorso", discorso.label)
        assertEquals(4, discorso.sortOrder)

        val spiegareDimostrazione = assertNotNull(queryPartType(driver, "SPIEGARE_CIO_CHE_SI_CREDE"))
        assertEquals("Spiegare quello in cui si crede - Dimostrazione", spiegareDimostrazione.label)
        assertEquals(5, spiegareDimostrazione.sortOrder)

        val spiegareDiscorso = assertNotNull(queryPartType(driver, "SPIEGARE_CIO_CHE_SI_CREDE_DISCORSO"))
        assertEquals("Spiegare quello in cui si crede - Discorso", spiegareDiscorso.label)
        assertEquals(1, spiegareDiscorso.peopleCount)
        assertEquals("UOMO", spiegareDiscorso.sexRule)
        assertEquals(6, spiegareDiscorso.sortOrder)
    }

    @Test
    fun `upserts realign existing rows to canonical metadata and reactivate them`() {
        val driver = freshDriverAtV1()
        // Pre-existing v1-era rows with stale label and deactivated state
        seedPartType(
            driver,
            id = "pt-spiegare",
            code = "SPIEGARE_CIO_CHE_SI_CREDE",
            label = "Spiegare cosa si crede",
            peopleCount = 2,
            sexRule = "STESSO_SESSO",
            sortOrder = 99,
        )
        // Deactivate to verify reactivation via DO UPDATE SET active = 1
        driver.execute(
            identifier = null,
            sql = "UPDATE part_type SET active = 0 WHERE code = 'SPIEGARE_CIO_CHE_SI_CREDE'",
            parameters = 0,
        )

        MinisteroDatabase.Schema.migrate(driver, 1L, 2L).value

        val spiegare = assertNotNull(queryPartType(driver, "SPIEGARE_CIO_CHE_SI_CREDE"))
        assertEquals("Spiegare quello in cui si crede - Dimostrazione", spiegare.label)
        assertEquals(5, spiegare.sortOrder) // realigned to canonical sort_order
        assertEquals(1, spiegare.active) // reactivated
        // id preserved (same row updated, not re-inserted)
        assertEquals("pt-spiegare", spiegare.id)
    }

    @Test
    fun `copies DISCORSO eligibility only for can_lead 1 rows`() {
        val driver = freshDriverAtV1()
        // Seed a pre-existing DISCORSO row so we can attach eligibility to it
        // before migration runs (and then migration will upsert it in-place).
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
    fun `fresh install bootstrap seeds 7 canonical part types via queries`() {
        // Replicates DatabaseProvider.createDatabase() bootstrap path: Schema.create()
        // only (no migrations) + seed queries. Reproduces the reviewer's empirical
        // finding that Schema.create alone leaves part_type empty.
        val driver = JdbcSqliteDriver(url = JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, "PRAGMA foreign_keys = ON;", 0)
        MinisteroDatabase.Schema.create(driver).value

        // Sanity: before the seed, the table is empty (the bug condition).
        assertEquals(0, queryPartTypeCount(driver))

        val db = MinisteroDatabase(driver)
        with(db.ministeroDatabaseQueries) {
            seedPartTypeLetturaDellaBibbia()
            seedPartTypeIniziareConversazione()
            seedPartTypeColtivareInteresse()
            seedPartTypeFareDiscepoli()
            seedPartTypeDiscorso()
            seedPartTypeSpiegareCioCheSiCrede()
            seedPartTypeSpiegareCioCheSiCredeDiscorso()
        }

        assertEquals(7, queryPartTypeCount(driver))
        val expectedCodes = listOf(
            "LETTURA_DELLA_BIBBIA",
            "INIZIARE_CONVERSAZIONE",
            "COLTIVARE_INTERESSE",
            "FARE_DISCEPOLI",
            "DISCORSO",
            "SPIEGARE_CIO_CHE_SI_CREDE",
            "SPIEGARE_CIO_CHE_SI_CREDE_DISCORSO",
        )
        expectedCodes.forEach { code ->
            assertNotNull(queryPartType(driver, code), "expected $code to be seeded")
        }
    }

    @Test
    fun `is idempotent when migrate is applied twice`() {
        val driver = freshDriverAtV1()
        seedPartType(driver, "pt-discorso", "DISCORSO", "Discorso", 1, "UOMO", 6)
        seedPerson(driver, "p-andrea", "M")
        seedEligibility(driver, "e1", "p-andrea", "pt-discorso", canLead = 1)

        MinisteroDatabase.Schema.migrate(driver, 1L, 2L).value
        val firstId = assertNotNull(queryPartType(driver, "SPIEGARE_CIO_CHE_SI_CREDE_DISCORSO")).id
        val firstCount = queryPartTypeCount(driver)

        MinisteroDatabase.Schema.migrate(driver, 1L, 2L).value
        val secondId = assertNotNull(queryPartType(driver, "SPIEGARE_CIO_CHE_SI_CREDE_DISCORSO")).id
        val secondCount = queryPartTypeCount(driver)

        assertEquals(firstId, secondId)
        assertEquals(firstCount, secondCount)
        assertEquals(1, queryEligibilityCount(driver, secondId))
    }
}

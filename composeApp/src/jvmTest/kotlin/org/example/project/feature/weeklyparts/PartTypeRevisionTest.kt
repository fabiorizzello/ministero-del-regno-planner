package org.example.project.feature.weeklyparts

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.test.runTest
import org.example.project.core.persistence.SqlDelightTransactionRunner
import org.example.project.db.MinisteroDatabase
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.SexRule
import org.example.project.feature.weeklyparts.infrastructure.SqlDelightPartTypeStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PartTypeRevisionTest {

    private fun inMemoryDb(): MinisteroDatabase {
        val driver = JdbcSqliteDriver(url = JdbcSqliteDriver.IN_MEMORY, schema = MinisteroDatabase.Schema)
        driver.execute(null, "PRAGMA foreign_keys = ON;", 0)
        return MinisteroDatabase(driver)
    }

    private fun lettura() = PartType(
        id = PartTypeId(""),
        code = "LETTURA",
        label = "Lettura",
        peopleCount = 1,
        sexRule = SexRule.STESSO_SESSO,
        fixed = false,
        sortOrder = 0,
    )

    @Test
    fun `upsertAll crea revisione e imposta current_revision_id`() = runTest {
        val db = inMemoryDb()
        val store = SqlDelightPartTypeStore(db)
        val txRunner = SqlDelightTransactionRunner(db)

        txRunner.runInTransaction { store.upsertAll(listOf(lettura())) }

        val ptId = store.findByCode("LETTURA")!!.id
        val revisionId = store.getLatestRevisionId(ptId)
        assertNotNull(revisionId)
    }

    @Test
    fun `secondo upsertAll crea nuova revisione con numero incrementato`() = runTest {
        val db = inMemoryDb()
        val store = SqlDelightPartTypeStore(db)
        val txRunner = SqlDelightTransactionRunner(db)

        txRunner.runInTransaction { store.upsertAll(listOf(lettura())) }
        txRunner.runInTransaction { store.upsertAll(listOf(lettura().copy(label = "Lettura Biblica"))) }

        val ptId = store.findByCode("LETTURA")!!.id
        data class Rev(val number: Long, val label: String)
        val latest = db.ministeroDatabaseQueries
            .latestPartTypeRevisionByPartType(ptId.value) { _, _, label, _, _, _, revision_number, _ -> Rev(revision_number, label) }
            .executeAsOneOrNull()

        assertNotNull(latest)
        assertEquals(2L, latest.number)
        assertEquals("Lettura Biblica", latest.label)
    }

    @Test
    fun `secondo upsertAll aggiorna current_revision_id al nuovo snapshot`() = runTest {
        val db = inMemoryDb()
        val store = SqlDelightPartTypeStore(db)
        val txRunner = SqlDelightTransactionRunner(db)

        txRunner.runInTransaction { store.upsertAll(listOf(lettura())) }
        val ptId = store.findByCode("LETTURA")!!.id
        val revV1 = store.getLatestRevisionId(ptId)!!

        txRunner.runInTransaction { store.upsertAll(listOf(lettura().copy(label = "Lettura Biblica"))) }
        val revV2 = store.getLatestRevisionId(ptId)!!

        assertNotNull(revV2)
        assert(revV1 != revV2) { "Il revision ID deve cambiare dopo il re-import" }
    }

    @Test
    fun `upsertAll con snapshot identico non crea nuova revisione`() = runTest {
        val db = inMemoryDb()
        val store = SqlDelightPartTypeStore(db)
        val txRunner = SqlDelightTransactionRunner(db)

        txRunner.runInTransaction { store.upsertAll(listOf(lettura())) }
        val ptId = store.findByCode("LETTURA")!!.id
        val revV1 = store.getLatestRevisionId(ptId)!!

        txRunner.runInTransaction { store.upsertAll(listOf(lettura())) }
        val revV2 = store.getLatestRevisionId(ptId)!!

        assertEquals(revV1, revV2, "current_revision_id deve restare invariato se snapshot identico")

        val latestNumber = db.ministeroDatabaseQueries
            .latestPartTypeRevisionByPartType(ptId.value) { _, _, _, _, _, _, revision_number, _ -> revision_number }
            .executeAsOneOrNull()
        assertEquals(1L, latestNumber, "Non deve essere creata una seconda revisione")
    }

    @Test
    fun `upsertAll ripetuto con peopleCount diverso crea nuova revisione`() = runTest {
        val db = inMemoryDb()
        val store = SqlDelightPartTypeStore(db)
        val txRunner = SqlDelightTransactionRunner(db)

        txRunner.runInTransaction { store.upsertAll(listOf(lettura())) }
        txRunner.runInTransaction { store.upsertAll(listOf(lettura())) }
        txRunner.runInTransaction { store.upsertAll(listOf(lettura().copy(peopleCount = 2))) }

        val ptId = store.findByCode("LETTURA")!!.id
        val latestNumber = db.ministeroDatabaseQueries
            .latestPartTypeRevisionByPartType(ptId.value) { _, _, _, _, _, _, revision_number, _ -> revision_number }
            .executeAsOneOrNull()
        assertEquals(2L, latestNumber, "Due revisioni totali: iniziale + peopleCount cambiato")
    }
}

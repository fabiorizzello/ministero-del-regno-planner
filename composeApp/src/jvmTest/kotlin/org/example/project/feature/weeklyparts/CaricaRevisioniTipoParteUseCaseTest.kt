package org.example.project.feature.weeklyparts

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.test.runTest
import org.example.project.core.persistence.SqlDelightTransactionRunner
import org.example.project.db.MinisteroDatabase
import org.example.project.feature.weeklyparts.application.CaricaRevisioniTipoParteUseCase
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeFieldDelta
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.SexRule
import org.example.project.feature.weeklyparts.infrastructure.SqlDelightPartTypeStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CaricaRevisioniTipoParteUseCaseTest {

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
    fun `lista vuota se nessun tipo parte`() = runTest {
        val db = inMemoryDb()
        val store = SqlDelightPartTypeStore(db)
        val useCase = CaricaRevisioniTipoParteUseCase(store)

        val views = useCase(PartTypeId("non-esiste"))

        assertTrue(views.isEmpty())
    }

    @Test
    fun `singola revisione iniziale - delta vuoto, isCurrent true`() = runTest {
        val db = inMemoryDb()
        val store = SqlDelightPartTypeStore(db)
        val txRunner = SqlDelightTransactionRunner(db)
        val useCase = CaricaRevisioniTipoParteUseCase(store)

        txRunner.runInTransaction { store.upsertAll(listOf(lettura())) }
        val ptId = store.findByCode("LETTURA")!!.id

        val views = useCase(ptId)

        assertEquals(1, views.size)
        val v = views.single()
        assertEquals(1, v.revisionNumber)
        assertTrue(v.isCurrent)
        assertTrue(v.deltaFromPrevious.isEmpty())
        assertEquals("Lettura", v.snapshot.label)
    }

    @Test
    fun `tre revisioni - ordine DESC, isCurrent solo sull'ultima, delta corretto`() = runTest {
        val db = inMemoryDb()
        val store = SqlDelightPartTypeStore(db)
        val txRunner = SqlDelightTransactionRunner(db)
        val useCase = CaricaRevisioniTipoParteUseCase(store)

        txRunner.runInTransaction { store.upsertAll(listOf(lettura())) }
        txRunner.runInTransaction { store.upsertAll(listOf(lettura().copy(label = "Lettura Biblica"))) }
        txRunner.runInTransaction { store.upsertAll(listOf(lettura().copy(label = "Lettura Biblica", peopleCount = 2))) }

        val ptId = store.findByCode("LETTURA")!!.id
        val views = useCase(ptId)

        assertEquals(3, views.size)
        // Ordine DESC
        assertEquals(3, views[0].revisionNumber)
        assertEquals(2, views[1].revisionNumber)
        assertEquals(1, views[2].revisionNumber)
        // isCurrent solo sulla più recente
        assertTrue(views[0].isCurrent)
        assertTrue(!views[1].isCurrent)
        assertTrue(!views[2].isCurrent)
        // Delta della v3: peopleCount 1 → 2 (rispetto a v2)
        val v3Delta = views[0].deltaFromPrevious
        assertEquals(1, v3Delta.size)
        val pc = assertIs<PartTypeFieldDelta.PeopleCount>(v3Delta.first())
        assertEquals(1, pc.from)
        assertEquals(2, pc.to)
        // Delta della v2: label "Lettura" → "Lettura Biblica"
        val v2Delta = views[1].deltaFromPrevious
        val lbl = assertIs<PartTypeFieldDelta.Label>(v2Delta.first())
        assertEquals("Lettura", lbl.from)
        assertEquals("Lettura Biblica", lbl.to)
        // Delta della v1: vuoto (genesi)
        assertTrue(views[2].deltaFromPrevious.isEmpty())
    }
}

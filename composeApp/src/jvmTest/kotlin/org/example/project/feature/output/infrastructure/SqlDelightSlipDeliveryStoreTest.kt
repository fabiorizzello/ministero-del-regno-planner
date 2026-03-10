package org.example.project.feature.output.infrastructure

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.runBlocking
import org.example.project.core.persistence.DefaultTransactionScope
import org.example.project.db.MinisteroDatabase
import org.example.project.feature.output.domain.SlipDelivery
import org.example.project.feature.output.domain.SlipDeliveryId
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlDelightSlipDeliveryStoreTest {

    private fun createDb(): MinisteroDatabase {
        val driver = JdbcSqliteDriver(
            url = JdbcSqliteDriver.IN_MEMORY,
            schema = MinisteroDatabase.Schema,
        )
        driver.execute(null, "PRAGMA foreign_keys = ON;", 0)
        return MinisteroDatabase(driver)
    }

    private fun delivery(
        id: String = "del-1",
        weeklyPartId: String = "wp-1",
        weekPlanId: String = "week-1",
        studentName: String = "Mario Rossi",
        assistantName: String? = "Luigi Bianchi",
        sentAt: Instant = Instant.parse("2026-03-10T08:00:00Z"),
    ) = SlipDelivery(
        id = SlipDeliveryId(id),
        weeklyPartId = WeeklyPartId(weeklyPartId),
        weekPlanId = WeekPlanId(weekPlanId),
        studentName = studentName,
        assistantName = assistantName,
        sentAt = sentAt,
        cancelledAt = null,
    )

    @Test
    fun `insert and findActiveDelivery returns the delivery`() = runBlocking {
        val database = createDb()
        val store = SqlDelightSlipDeliveryStore(database)
        val d = delivery()

        with(DefaultTransactionScope) { store.insert(d) }

        val found = store.findActiveDelivery(WeeklyPartId("wp-1"), WeekPlanId("week-1"))
        assertNotNull(found)
        assertEquals(d.id, found.id)
        assertEquals(d.weeklyPartId, found.weeklyPartId)
        assertEquals(d.weekPlanId, found.weekPlanId)
        assertEquals(d.studentName, found.studentName)
        assertEquals(d.assistantName, found.assistantName)
        assertEquals(d.sentAt, found.sentAt)
        assertNull(found.cancelledAt)
        assertTrue(found.isActive)
    }

    @Test
    fun `insert without assistant name`() = runBlocking {
        val database = createDb()
        val store = SqlDelightSlipDeliveryStore(database)
        val d = delivery(assistantName = null)

        with(DefaultTransactionScope) { store.insert(d) }

        val found = store.findActiveDelivery(WeeklyPartId("wp-1"), WeekPlanId("week-1"))
        assertNotNull(found)
        assertNull(found.assistantName)
    }

    @Test
    fun `findActiveDelivery returns null when no delivery exists`() = runBlocking {
        val database = createDb()
        val store = SqlDelightSlipDeliveryStore(database)

        val found = store.findActiveDelivery(WeeklyPartId("wp-1"), WeekPlanId("week-1"))
        assertNull(found)
    }

    @Test
    fun `cancel sets cancelledAt and findActiveDelivery returns null`() = runBlocking {
        val database = createDb()
        val store = SqlDelightSlipDeliveryStore(database)
        val d = delivery()
        val cancelTime = Instant.parse("2026-03-10T10:00:00Z")

        with(DefaultTransactionScope) { store.insert(d) }
        with(DefaultTransactionScope) { store.cancel(d.id, cancelTime) }

        val active = store.findActiveDelivery(WeeklyPartId("wp-1"), WeekPlanId("week-1"))
        assertNull(active)
    }

    @Test
    fun `findLastCancelledDelivery returns most recent cancelled`() = runBlocking {
        val database = createDb()
        val store = SqlDelightSlipDeliveryStore(database)

        val d1 = delivery(id = "del-1", sentAt = Instant.parse("2026-03-08T08:00:00Z"))
        val d2 = delivery(id = "del-2", sentAt = Instant.parse("2026-03-09T08:00:00Z"))
        val cancelTime1 = Instant.parse("2026-03-08T12:00:00Z")
        val cancelTime2 = Instant.parse("2026-03-09T12:00:00Z")

        with(DefaultTransactionScope) {
            store.insert(d1)
            store.insert(d2)
            store.cancel(d1.id, cancelTime1)
            store.cancel(d2.id, cancelTime2)
        }

        val lastCancelled = store.findLastCancelledDelivery(WeeklyPartId("wp-1"), WeekPlanId("week-1"))
        assertNotNull(lastCancelled)
        assertEquals(SlipDeliveryId("del-2"), lastCancelled.id)
        assertEquals(cancelTime2, lastCancelled.cancelledAt)
    }

    @Test
    fun `findLastCancelledDelivery returns null when no cancelled delivery exists`() = runBlocking {
        val database = createDb()
        val store = SqlDelightSlipDeliveryStore(database)
        val d = delivery()

        with(DefaultTransactionScope) { store.insert(d) }

        val cancelled = store.findLastCancelledDelivery(WeeklyPartId("wp-1"), WeekPlanId("week-1"))
        assertNull(cancelled)
    }

    @Test
    fun `listActiveDeliveries returns only active across multiple weekPlanIds`() = runBlocking {
        val database = createDb()
        val store = SqlDelightSlipDeliveryStore(database)

        val active1 = delivery(id = "del-1", weekPlanId = "week-1", weeklyPartId = "wp-1")
        val active2 = delivery(id = "del-2", weekPlanId = "week-2", weeklyPartId = "wp-2")
        val cancelled = delivery(id = "del-3", weekPlanId = "week-1", weeklyPartId = "wp-3")
        val other = delivery(id = "del-4", weekPlanId = "week-99", weeklyPartId = "wp-4")

        with(DefaultTransactionScope) {
            store.insert(active1)
            store.insert(active2)
            store.insert(cancelled)
            store.insert(other)
            store.cancel(cancelled.id, Instant.parse("2026-03-10T12:00:00Z"))
        }

        val result = store.listActiveDeliveries(listOf(WeekPlanId("week-1"), WeekPlanId("week-2")))
        assertEquals(2, result.size)
        val ids = result.map { it.id }.toSet()
        assertTrue(ids.contains(SlipDeliveryId("del-1")))
        assertTrue(ids.contains(SlipDeliveryId("del-2")))
    }

    @Test
    fun `listActiveDeliveries returns empty for empty weekPlanIds`() = runBlocking {
        val database = createDb()
        val store = SqlDelightSlipDeliveryStore(database)

        val result = store.listActiveDeliveries(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `listCancelledDeliveries returns only cancelled`() = runBlocking {
        val database = createDb()
        val store = SqlDelightSlipDeliveryStore(database)

        val active = delivery(id = "del-1", weekPlanId = "week-1", weeklyPartId = "wp-1")
        val cancelled = delivery(id = "del-2", weekPlanId = "week-1", weeklyPartId = "wp-2")

        with(DefaultTransactionScope) {
            store.insert(active)
            store.insert(cancelled)
            store.cancel(cancelled.id, Instant.parse("2026-03-10T12:00:00Z"))
        }

        val result = store.listCancelledDeliveries(listOf(WeekPlanId("week-1")))
        assertEquals(1, result.size)
        assertEquals(SlipDeliveryId("del-2"), result.first().id)
        assertNotNull(result.first().cancelledAt); Unit
    }

    @Test
    fun `listCancelledDeliveries returns empty for empty weekPlanIds`() = runBlocking {
        val database = createDb()
        val store = SqlDelightSlipDeliveryStore(database)

        val result = store.listCancelledDeliveries(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `findActiveDelivery scopes by weeklyPartId and weekPlanId`() = runBlocking {
        val database = createDb()
        val store = SqlDelightSlipDeliveryStore(database)

        val d1 = delivery(id = "del-1", weeklyPartId = "wp-1", weekPlanId = "week-1")
        val d2 = delivery(id = "del-2", weeklyPartId = "wp-2", weekPlanId = "week-1")
        val d3 = delivery(id = "del-3", weeklyPartId = "wp-1", weekPlanId = "week-2")

        with(DefaultTransactionScope) {
            store.insert(d1)
            store.insert(d2)
            store.insert(d3)
        }

        val found = store.findActiveDelivery(WeeklyPartId("wp-1"), WeekPlanId("week-1"))
        assertNotNull(found)
        assertEquals(SlipDeliveryId("del-1"), found.id)

        val found2 = store.findActiveDelivery(WeeklyPartId("wp-2"), WeekPlanId("week-1"))
        assertNotNull(found2)
        assertEquals(SlipDeliveryId("del-2"), found2.id)

        val foundNone = store.findActiveDelivery(WeeklyPartId("wp-99"), WeekPlanId("week-1"))
        assertNull(foundNone)
    }
}

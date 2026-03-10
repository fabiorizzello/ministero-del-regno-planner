package org.example.project.feature.output.application

import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.example.project.feature.output.domain.SlipDelivery
import org.example.project.feature.output.domain.SlipDeliveryId
import org.example.project.feature.output.domain.SlipDeliveryStatus
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CaricaStatoConsegneUseCaseTest {

    private val store = FakeSlipDeliveryStore()
    private val useCase = CaricaStatoConsegneUseCase(store)

    @Test
    fun `returns empty map when no deliveries exist`() = runTest {
        val result = useCase(listOf(WeekPlanId("wp-1"), WeekPlanId("wp-2")))

        assertTrue(result.isEmpty())
    }

    @Test
    fun `returns INVIATO for active delivery`() = runTest {
        val delivery = SlipDelivery(
            id = SlipDeliveryId("d1"),
            weeklyPartId = WeeklyPartId("wp1"),
            weekPlanId = WeekPlanId("plan-1"),
            studentName = "Mario Rossi",
            assistantName = null,
            sentAt = Instant.parse("2026-03-10T10:00:00Z"),
            cancelledAt = null,
        )
        store.activeDeliveries[delivery.weeklyPartId to delivery.weekPlanId] = delivery

        val result = useCase(listOf(WeekPlanId("plan-1")))

        val key = WeeklyPartId("wp1") to WeekPlanId("plan-1")
        val info = result[key]!!
        assertEquals(SlipDeliveryStatus.INVIATO, info.status)
        assertEquals(delivery, info.activeDelivery)
        assertNull(info.previousStudentName)
    }

    @Test
    fun `returns DA_REINVIARE with previousStudentName when cancelled exists but no active`() = runTest {
        val cancelled = SlipDelivery(
            id = SlipDeliveryId("d1"),
            weeklyPartId = WeeklyPartId("wp1"),
            weekPlanId = WeekPlanId("plan-1"),
            studentName = "Luigi Bianchi",
            assistantName = null,
            sentAt = Instant.parse("2026-03-08T10:00:00Z"),
            cancelledAt = Instant.parse("2026-03-09T12:00:00Z"),
        )
        store.cancelledDeliveries += cancelled

        val result = useCase(listOf(WeekPlanId("plan-1")))

        val key = WeeklyPartId("wp1") to WeekPlanId("plan-1")
        val info = result[key]!!
        assertEquals(SlipDeliveryStatus.DA_REINVIARE, info.status)
        assertNull(info.activeDelivery)
        assertEquals("Luigi Bianchi", info.previousStudentName)
    }

    @Test
    fun `active delivery takes priority over cancelled for same key`() = runTest {
        val key = WeeklyPartId("wp1") to WeekPlanId("plan-1")

        // Set up both an active and a cancelled delivery for the same key
        val activeDelivery = SlipDelivery(
            id = SlipDeliveryId("d2"),
            weeklyPartId = WeeklyPartId("wp1"),
            weekPlanId = WeekPlanId("plan-1"),
            studentName = "Mario Rossi",
            assistantName = null,
            sentAt = Instant.parse("2026-03-10T10:00:00Z"),
            cancelledAt = null,
        )
        store.activeDeliveries[key] = activeDelivery

        val cancelledDelivery = SlipDelivery(
            id = SlipDeliveryId("d1"),
            weeklyPartId = WeeklyPartId("wp1"),
            weekPlanId = WeekPlanId("plan-1"),
            studentName = "Luigi Bianchi",
            assistantName = null,
            sentAt = Instant.parse("2026-03-08T10:00:00Z"),
            cancelledAt = Instant.parse("2026-03-09T12:00:00Z"),
        )
        store.cancelledDeliveries += cancelledDelivery

        val result = useCase(listOf(WeekPlanId("plan-1")))

        val info = result[key]!!
        assertEquals(SlipDeliveryStatus.INVIATO, info.status)
        assertEquals(activeDelivery, info.activeDelivery)
        assertNull(info.previousStudentName)
    }
}

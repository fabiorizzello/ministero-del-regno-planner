package org.example.project.feature.output.application

import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.example.project.feature.output.domain.SlipDelivery
import org.example.project.feature.output.domain.SlipDeliveryId
import org.example.project.feature.output.domain.SlipDeliveryStatus
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
        val result = useCase(listOf("wp-1", "wp-2"))

        assertTrue(result.isEmpty())
    }

    @Test
    fun `returns INVIATO for active delivery`() = runTest {
        val delivery = SlipDelivery(
            id = SlipDeliveryId("d1"),
            weeklyPartId = WeeklyPartId("wp1"),
            weekPlanId = "plan-1",
            studentName = "Mario Rossi",
            assistantName = null,
            sentAt = Instant.parse("2026-03-10T10:00:00Z"),
            cancelledAt = null,
        )
        store.activeDeliveries[delivery.weeklyPartId to delivery.weekPlanId] = delivery

        val result = useCase(listOf("plan-1"))

        val key = WeeklyPartId("wp1") to "plan-1"
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
            weekPlanId = "plan-1",
            studentName = "Luigi Bianchi",
            assistantName = null,
            sentAt = Instant.parse("2026-03-08T10:00:00Z"),
            cancelledAt = Instant.parse("2026-03-09T12:00:00Z"),
        )
        store.cancelledDeliveries += cancelled

        val result = useCase(listOf("plan-1"))

        val key = WeeklyPartId("wp1") to "plan-1"
        val info = result[key]!!
        assertEquals(SlipDeliveryStatus.DA_REINVIARE, info.status)
        assertNull(info.activeDelivery)
        assertEquals("Luigi Bianchi", info.previousStudentName)
    }
}

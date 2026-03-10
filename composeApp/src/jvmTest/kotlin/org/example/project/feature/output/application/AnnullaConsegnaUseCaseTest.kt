package org.example.project.feature.output.application

import kotlinx.coroutines.test.runTest
import org.example.project.feature.output.domain.SlipDelivery
import org.example.project.feature.output.domain.SlipDeliveryId
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnnullaConsegnaUseCaseTest {

    private val store = FakeSlipDeliveryStore()
    private val useCase = AnnullaConsegnaUseCase(store, ImmediateTransactionRunner())

    @Test
    fun `cancels active delivery for the given part and week`() = runTest {
        val weeklyPartId = WeeklyPartId("wp-1")
        val weekPlanId = "plan-1"
        val delivery = SlipDelivery(
            id = SlipDeliveryId("del-1"),
            weeklyPartId = weeklyPartId,
            weekPlanId = weekPlanId,
            studentName = "Mario Rossi",
            assistantName = null,
            sentAt = Instant.parse("2026-03-01T10:00:00Z"),
            cancelledAt = null,
        )
        store.activeDeliveries[weeklyPartId to weekPlanId] = delivery

        val result = useCase(weeklyPartId, weekPlanId)

        assertTrue(result.isRight())
        assertTrue(store.cancelledIds.contains(SlipDeliveryId("del-1")))
    }

    @Test
    fun `noop if no active delivery exists`() = runTest {
        val result = useCase(WeeklyPartId("wp-999"), "plan-999")

        assertTrue(result.isRight())
        assertEquals(emptyList(), store.cancelledIds)
    }
}

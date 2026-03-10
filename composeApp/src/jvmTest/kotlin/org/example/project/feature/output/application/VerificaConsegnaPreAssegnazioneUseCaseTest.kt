package org.example.project.feature.output.application

import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.example.project.feature.output.domain.SlipDelivery
import org.example.project.feature.output.domain.SlipDeliveryId
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VerificaConsegnaPreAssegnazioneUseCaseTest {

    private val store = FakeSlipDeliveryStore()
    private val useCase = VerificaConsegnaPreAssegnazioneUseCase(store)

    @Test
    fun `returns null when no active delivery`() = runTest {
        val result = useCase(WeeklyPartId("wp1"), WeekPlanId("plan1"))

        assertNull(result)
    }

    @Test
    fun `returns student name when active delivery exists`() = runTest {
        val delivery = SlipDelivery(
            id = SlipDeliveryId("d1"),
            weeklyPartId = WeeklyPartId("wp1"),
            weekPlanId = WeekPlanId("plan1"),
            studentName = "Mario Rossi",
            assistantName = null,
            sentAt = Instant.now(),
            cancelledAt = null,
        )
        store.activeDeliveries[delivery.weeklyPartId to delivery.weekPlanId] = delivery

        val result = useCase(WeeklyPartId("wp1"), WeekPlanId("plan1"))

        assertEquals("Mario Rossi", result)
    }
}

package org.example.project.feature.output.application

import arrow.core.Either
import kotlinx.coroutines.runBlocking
import org.example.project.feature.output.domain.SlipDelivery
import org.example.project.feature.output.domain.SlipDeliveryId
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SegnaComInviatoUseCaseTest {

    private val weeklyPartId = WeeklyPartId("wp-1")
    private val weekPlanId = WeekPlanId("plan-1")

    @Test
    fun `marks slip as delivered creates new delivery record`() = runBlocking {
        val store = FakeSlipDeliveryStore()
        val useCase = SegnaComInviatoUseCase(store, ImmediateTransactionRunner())

        val result = useCase(
            weeklyPartId = weeklyPartId,
            weekPlanId = weekPlanId,
            studentName = "Mario Rossi",
            assistantName = "Luigi Verdi",
        )

        assertIs<Either.Right<Unit>>(result)
        assertEquals(1, store.inserted.size)
        assertEquals("Mario Rossi", store.inserted[0].studentName)
        assertEquals("Luigi Verdi", store.inserted[0].assistantName)
        assertEquals(weeklyPartId, store.inserted[0].weeklyPartId)
        assertEquals(weekPlanId, store.inserted[0].weekPlanId)
        Unit
    }

    @Test
    fun `idempotent - does not create duplicate if active delivery exists`() = runBlocking {
        val store = FakeSlipDeliveryStore()
        store.activeDeliveries[weeklyPartId to weekPlanId] = SlipDelivery(
            id = SlipDeliveryId("existing-1"),
            weeklyPartId = weeklyPartId,
            weekPlanId = weekPlanId,
            studentName = "Mario Rossi",
            assistantName = null,
            sentAt = Instant.now(),
            cancelledAt = null,
        )
        val useCase = SegnaComInviatoUseCase(store, ImmediateTransactionRunner())

        val result = useCase(
            weeklyPartId = weeklyPartId,
            weekPlanId = weekPlanId,
            studentName = "Mario Rossi",
            assistantName = null,
        )

        assertIs<Either.Right<Unit>>(result)
        assertEquals(0, store.inserted.size)
        Unit
    }
}

package org.example.project.feature.weeklyparts

import arrow.core.Either
import kotlinx.coroutines.runBlocking
import org.example.project.core.persistence.TransactionRunner
import org.example.project.feature.weeklyparts.application.RimuoviParteUseCase
import org.example.project.feature.weeklyparts.application.WeekPlanStore
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.SexRule
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeekPlanSummary
import org.example.project.feature.weeklyparts.domain.WeeklyPart
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RimuoviParteUseCaseTransactionTest {

    @Test
    fun `remove part executes through transaction runner`() = runBlocking {
        val txRunner = TrackingTransactionRunner()
        val weekStore = TransactionAwareWeekPlanStore()
        val useCase = RimuoviParteUseCase(
            weekPlanStore = weekStore,
            transactionRunner = txRunner,
        )

        val result = useCase(
            weekStartDate = weekStore.weekDate,
            weeklyPartId = weekStore.initialPart.id,
        )

        assertIs<Either.Right<WeekPlan>>(result)
        assertEquals(1, txRunner.invocationCount)
    }
}

private class TrackingTransactionRunner : TransactionRunner {
    var invocationCount: Int = 0
        private set

    override suspend fun <T> runInTransaction(block: suspend () -> T): T {
        invocationCount++
        return block()
    }
}

private class TransactionAwareWeekPlanStore : WeekPlanStore {
    private val partType = PartType(
        id = PartTypeId("pt-1"),
        code = "LETTURA",
        label = "Lettura",
        peopleCount = 1,
        sexRule = SexRule.STESSO_SESSO,
        fixed = false,
        sortOrder = 0,
    )
    val initialPart = WeeklyPart(
        id = WeeklyPartId("part-1"),
        partType = partType,
        sortOrder = 0,
    )
    val weekDate: LocalDate = LocalDate.of(2026, 3, 2)
    private var week: WeekPlan = WeekPlan(
        id = WeekPlanId("week-1"),
        weekStartDate = weekDate,
        parts = listOf(initialPart),
    )

    override suspend fun findByDate(weekStartDate: LocalDate): WeekPlan? =
        if (week.weekStartDate == weekStartDate) week else null

    override suspend fun listInRange(startDate: LocalDate, endDate: LocalDate): List<WeekPlanSummary> = emptyList()

    override suspend fun totalSlotsByWeekInRange(startDate: LocalDate, endDate: LocalDate): Map<WeekPlanId, Int> = emptyMap()

    override suspend fun save(weekPlan: WeekPlan) {
        week = weekPlan
    }

    override suspend fun delete(weekPlanId: WeekPlanId) {}

    override suspend fun addPart(weekPlanId: WeekPlanId, partTypeId: PartTypeId, sortOrder: Int): WeeklyPartId =
        WeeklyPartId("unused")

    override suspend fun removePart(weeklyPartId: WeeklyPartId) {
        week = week.copy(parts = week.parts.filterNot { it.id == weeklyPartId })
    }

    override suspend fun updateSortOrders(parts: List<Pair<WeeklyPartId, Int>>) {
        val newSortById = parts.toMap()
        week = week.copy(
            parts = week.parts
                .map { part -> part.copy(sortOrder = newSortById[part.id] ?: part.sortOrder) }
                .sortedBy { it.sortOrder },
        )
    }

    override suspend fun replaceAllParts(weekPlanId: WeekPlanId, partTypeIds: List<PartTypeId>) {}
}


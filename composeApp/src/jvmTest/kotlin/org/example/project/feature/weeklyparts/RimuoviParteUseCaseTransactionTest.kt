package org.example.project.feature.weeklyparts

import arrow.core.Either
import kotlinx.coroutines.test.runTest
import org.example.project.core.CountingTransactionRunner
import org.example.project.feature.weeklyparts.application.RimuoviParteUseCase
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.SexRule
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanAggregate
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeeklyPart
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RimuoviParteUseCaseTransactionTest {

    @Test
    fun `remove part executes through transaction runner`() = runTest {
        val txRunner = CountingTransactionRunner()
        val weekStore = TransactionAwareWeekPlanStore()
        val useCase = RimuoviParteUseCase(
            weekPlanStore = weekStore,
            transactionRunner = txRunner,
        )

        val result = useCase(
            weekStartDate = weekStore.weekDate,
            weeklyPartId = weekStore.initialPart.id,
            referenceDate = weekStore.weekDate,
        )

        assertIs<Either.Right<Unit>>(result)
        assertEquals(1, txRunner.calls)
    }
}

private class TransactionAwareWeekPlanStore : TestWeekPlanStore() {
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

    override suspend fun loadAggregateByDate(weekStartDate: LocalDate): WeekPlanAggregate? =
        if (week.weekStartDate == weekStartDate) {
            WeekPlanAggregate(weekPlan = week, assignments = emptyList())
        } else {
            null
        }

    context(tx: org.example.project.core.persistence.TransactionScope)
    override suspend fun saveAggregate(aggregate: WeekPlanAggregate) {
        week = aggregate.weekPlan
    }

    override suspend fun loadAggregateById(weekPlanId: WeekPlanId): WeekPlanAggregate? =
        if (week.id == weekPlanId) {
            WeekPlanAggregate(weekPlan = week, assignments = emptyList())
        } else {
            null
        }
}

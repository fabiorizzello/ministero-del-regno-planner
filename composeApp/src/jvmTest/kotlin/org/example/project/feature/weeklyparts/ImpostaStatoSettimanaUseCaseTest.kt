package org.example.project.feature.weeklyparts

import arrow.core.Either
import kotlinx.coroutines.test.runTest
import org.example.project.core.PassthroughTransactionRunner
import org.example.project.core.domain.DomainError
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.weeklyparts.application.ImpostaStatoSettimanaUseCase
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.SexRule
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanAggregate
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeekPlanStatus
import org.example.project.feature.weeklyparts.domain.WeeklyPart
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ImpostaStatoSettimanaUseCaseTest {

    // A Monday in the future relative to referenceDate used in tests
    private val futureMonday = LocalDate.of(2026, 3, 16)
    private val pastMonday = LocalDate.of(2026, 2, 23)
    private val referenceDate = LocalDate.of(2026, 3, 9) // current Monday

    private fun aggregateForDate(
        weekStartDate: LocalDate,
        status: WeekPlanStatus = WeekPlanStatus.ACTIVE,
    ): WeekPlanAggregate {
        val partType = PartType(
            id = PartTypeId("pt-1"),
            code = "PT",
            label = "Parte",
            peopleCount = 1,
            sexRule = SexRule.STESSO_SESSO,
            fixed = false,
            sortOrder = 0,
        )
        val week = WeekPlan(
            id = WeekPlanId("w-${weekStartDate}"),
            weekStartDate = weekStartDate,
            parts = listOf(WeeklyPart(id = WeeklyPartId("wp-1"), partType = partType, sortOrder = 0)),
            status = status,
        )
        return WeekPlanAggregate(weekPlan = week, assignments = emptyList())
    }

    @Test
    fun `setting SKIPPED on future ACTIVE week succeeds and updates status`() = runTest {
        val aggregate = aggregateForDate(futureMonday, WeekPlanStatus.ACTIVE)
        val store = SingleIdWeekStore(aggregate)
        val useCase = ImpostaStatoSettimanaUseCase(
            weekPlanStore = store,
            transactionRunner = PassthroughTransactionRunner,
        )

        val result = useCase(aggregate.weekPlan.id, WeekPlanStatus.SKIPPED, referenceDate = referenceDate)

        assertIs<Either.Right<Unit>>(result)
        assertEquals(WeekPlanStatus.SKIPPED, store.currentAggregate!!.weekPlan.status)
    }

    @Test
    fun `setting SKIPPED on current week (referenceDate Monday) succeeds`() = runTest {
        val aggregate = aggregateForDate(referenceDate, WeekPlanStatus.ACTIVE)
        val store = SingleIdWeekStore(aggregate)
        val useCase = ImpostaStatoSettimanaUseCase(
            weekPlanStore = store,
            transactionRunner = PassthroughTransactionRunner,
        )

        val result = useCase(aggregate.weekPlan.id, WeekPlanStatus.SKIPPED, referenceDate = referenceDate)

        assertIs<Either.Right<Unit>>(result)
        assertEquals(WeekPlanStatus.SKIPPED, store.currentAggregate!!.weekPlan.status)
    }

    @Test
    fun `setting SKIPPED on a past week succeeds to keep skip action available`() = runTest {
        val aggregate = aggregateForDate(pastMonday, WeekPlanStatus.ACTIVE)
        val store = SingleIdWeekStore(aggregate)
        val useCase = ImpostaStatoSettimanaUseCase(
            weekPlanStore = store,
            transactionRunner = PassthroughTransactionRunner,
        )

        val result = useCase(aggregate.weekPlan.id, WeekPlanStatus.SKIPPED, referenceDate = referenceDate)

        assertIs<Either.Right<Unit>>(result)
        assertEquals(WeekPlanStatus.SKIPPED, store.currentAggregate!!.weekPlan.status)
    }

    @Test
    fun `setting ACTIVE on future SKIPPED week succeeds and updates status`() = runTest {
        val aggregate = aggregateForDate(futureMonday, WeekPlanStatus.SKIPPED)
        val store = SingleIdWeekStore(aggregate)
        val useCase = ImpostaStatoSettimanaUseCase(
            weekPlanStore = store,
            transactionRunner = PassthroughTransactionRunner,
        )

        val result = useCase(aggregate.weekPlan.id, WeekPlanStatus.ACTIVE, referenceDate = referenceDate)

        assertIs<Either.Right<Unit>>(result)
        assertEquals(WeekPlanStatus.ACTIVE, store.currentAggregate!!.weekPlan.status)
    }

    @Test
    fun `setting ACTIVE on a past SKIPPED week succeeds to keep context actionable`() = runTest {
        val aggregate = aggregateForDate(pastMonday, WeekPlanStatus.SKIPPED)
        val store = SingleIdWeekStore(aggregate)
        val useCase = ImpostaStatoSettimanaUseCase(
            weekPlanStore = store,
            transactionRunner = PassthroughTransactionRunner,
        )

        val result = useCase(aggregate.weekPlan.id, WeekPlanStatus.ACTIVE, referenceDate = referenceDate)

        assertIs<Either.Right<Unit>>(result)
        assertEquals(WeekPlanStatus.ACTIVE, store.currentAggregate!!.weekPlan.status)
    }

    @Test
    fun `no-op status transition on a past week succeeds without guard`() = runTest {
        val aggregate = aggregateForDate(pastMonday, WeekPlanStatus.SKIPPED)
        val store = SingleIdWeekStore(aggregate)
        val useCase = ImpostaStatoSettimanaUseCase(
            weekPlanStore = store,
            transactionRunner = PassthroughTransactionRunner,
        )

        val result = useCase(aggregate.weekPlan.id, WeekPlanStatus.SKIPPED, referenceDate = referenceDate)

        assertIs<Either.Right<Unit>>(result)
        assertEquals(WeekPlanStatus.SKIPPED, store.currentAggregate!!.weekPlan.status)
    }

    @Test
    fun `invoke on missing week returns NotFound error`() = runTest {
        val store = EmptyImpostaWeekStore()
        val useCase = ImpostaStatoSettimanaUseCase(
            weekPlanStore = store,
            transactionRunner = PassthroughTransactionRunner,
        )

        val result = useCase(WeekPlanId("nonexistent"), WeekPlanStatus.SKIPPED, referenceDate = referenceDate)

        val left = assertIs<Either.Left<DomainError>>(result).value
        assertIs<DomainError.NotFound>(left)
        Unit
    }
}

private class SingleIdWeekStore(
    initial: WeekPlanAggregate,
) : TestWeekPlanStore() {
    var currentAggregate: WeekPlanAggregate? = initial

    override suspend fun loadAggregateById(weekPlanId: WeekPlanId): WeekPlanAggregate? =
        if (currentAggregate?.weekPlan?.id == weekPlanId) currentAggregate else null

    override suspend fun listByProgram(programId: ProgramMonthId): List<WeekPlan> = emptyList()

    context(tx: org.example.project.core.persistence.TransactionScope)
    override suspend fun saveAggregate(aggregate: WeekPlanAggregate) {
        currentAggregate = aggregate
    }
}

private class EmptyImpostaWeekStore : TestWeekPlanStore()

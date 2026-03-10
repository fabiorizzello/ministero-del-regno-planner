package org.example.project.feature.assignments

import arrow.core.Either
import kotlinx.coroutines.runBlocking
import org.example.project.core.CountingTransactionRunner
import org.example.project.core.PassthroughTransactionRunner
import org.example.project.core.domain.DomainError
import org.example.project.feature.assignments.application.RimuoviAssegnazioniSettimanaUseCase
import org.example.project.feature.assignments.domain.Assignment
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.weeklyparts.TestWeekPlanStore
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

class RimuoviAssegnazioniSettimanaUseCaseTest {

    private val weekStart = LocalDate.of(2026, 3, 9)

    private fun sampleAggregate(assignments: List<Assignment>): WeekPlanAggregate {
        val partType = PartType(
            id = PartTypeId("pt-1"),
            code = "PT",
            label = "Parte",
            peopleCount = 2,
            sexRule = SexRule.STESSO_SESSO,
            fixed = false,
            sortOrder = 0,
        )
        val week = WeekPlan(
            id = WeekPlanId("w-1"),
            weekStartDate = weekStart,
            parts = listOf(
                WeeklyPart(id = WeeklyPartId("wp-1"), partType = partType, sortOrder = 0),
            ),
        )
        return WeekPlanAggregate(weekPlan = week, assignments = assignments)
    }

    private fun assignment(id: String, slot: Int = 1): Assignment = Assignment(
        id = AssignmentId(id),
        weeklyPartId = WeeklyPartId("wp-1"),
        personId = ProclamatoreId("p$id"),
        slot = slot,
    )

    @Test
    fun `removing assignments on week with two assignments leaves aggregate empty`() = runBlocking {
        val initialAssignments = listOf(assignment("a1", slot = 1), assignment("a2", slot = 2))
        val store = SingleAggregateWeekStore(sampleAggregate(initialAssignments))
        val useCase = RimuoviAssegnazioniSettimanaUseCase(
            weekPlanStore = store,
            transactionRunner = PassthroughTransactionRunner,
        )

        val result = useCase(weekStart)

        assertIs<Either.Right<Unit>>(result)
        assertEquals(0, store.currentAggregate!!.assignments.size)
    }

    @Test
    fun `invoke on missing week returns NotFound`() = runBlocking {
        val store = EmptyWeekStore()
        val useCase = RimuoviAssegnazioniSettimanaUseCase(
            weekPlanStore = store,
            transactionRunner = PassthroughTransactionRunner,
        )

        val result = useCase(weekStart)

        val error = assertIs<Either.Left<DomainError>>(result).value
        assertEquals(DomainError.NotFound("Piano settimanale"), error)
    }

    @Test
    fun `removal runs inside a transaction`() = runBlocking {
        val store = SingleAggregateWeekStore(sampleAggregate(listOf(assignment("a1"))))
        val txRunner = CountingTransactionRunner()
        val useCase = RimuoviAssegnazioniSettimanaUseCase(
            weekPlanStore = store,
            transactionRunner = txRunner,
        )

        useCase(weekStart)

        assertEquals(1, txRunner.calls)
    }
}

private class SingleAggregateWeekStore(
    initial: WeekPlanAggregate,
) : TestWeekPlanStore() {
    var currentAggregate: WeekPlanAggregate? = initial

    override suspend fun loadAggregateByDate(weekStartDate: LocalDate): WeekPlanAggregate? =
        if (currentAggregate?.weekPlan?.weekStartDate == weekStartDate) currentAggregate else null

    override suspend fun loadAggregateById(weekPlanId: WeekPlanId): WeekPlanAggregate? =
        if (currentAggregate?.weekPlan?.id == weekPlanId) currentAggregate else null

    override suspend fun listByProgram(programId: ProgramMonthId): List<WeekPlan> = emptyList()

    context(tx: org.example.project.core.persistence.TransactionScope)
    override suspend fun saveAggregate(aggregate: WeekPlanAggregate) {
        currentAggregate = aggregate
    }
}

private class EmptyWeekStore : TestWeekPlanStore()

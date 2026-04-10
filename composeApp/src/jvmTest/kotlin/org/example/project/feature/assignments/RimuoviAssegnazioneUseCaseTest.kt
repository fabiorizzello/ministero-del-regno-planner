package org.example.project.feature.assignments

import arrow.core.Either
import kotlinx.coroutines.test.runTest
import org.example.project.core.PassthroughTransactionRunner
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionScope
import org.example.project.feature.assignments.application.AssignmentRepository
import org.example.project.feature.assignments.application.RimuoviAssegnazioneUseCase
import org.example.project.feature.assignments.domain.Assignment
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.weeklyparts.TestWeekPlanStore
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.SexRule
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanAggregate
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeekPlanStatus
import org.example.project.feature.weeklyparts.domain.WeeklyPart
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import org.example.project.feature.people.domain.ProclamatoreId
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RimuoviAssegnazioneUseCaseTest {

    private val assignmentId = AssignmentId("a1")
    private val weekPlanId = WeekPlanId("w1")

    @Test
    fun `happy path removes assignment from active week and persists updated aggregate`() = runTest {
        val aggregate = activeAggregateWithAssignment()
        val store = RecordingWeekPlanStore(aggregate)
        val repository = StubRepositoryWithLookup(mapOf(assignmentId to weekPlanId))

        val useCase = RimuoviAssegnazioneUseCase(
            weekPlanStore = store,
            assignmentRepository = repository,
            transactionRunner = PassthroughTransactionRunner,
        )

        val result = useCase(assignmentId)

        assertIs<Either.Right<Unit>>(result)
        val saved = store.savedAggregates.single()
        assertEquals(emptyList(), saved.assignments)
        Unit
    }

    @Test
    fun `returns NotFound Assegnazione when assignment id does not resolve to any week`() = runTest {
        val store = RecordingWeekPlanStore(activeAggregateWithAssignment())
        val repository = StubRepositoryWithLookup(emptyMap())

        val useCase = RimuoviAssegnazioneUseCase(
            weekPlanStore = store,
            assignmentRepository = repository,
            transactionRunner = PassthroughTransactionRunner,
        )

        val result = useCase(assignmentId)
        val left = assertIs<Either.Left<DomainError>>(result).value
        assertEquals(DomainError.NotFound("Assegnazione"), left)
        assertEquals(0, store.savedAggregates.size)
    }

    @Test
    fun `returns NotFound Settimana when week plan cannot be loaded`() = runTest {
        val store = object : TestWeekPlanStore() {
            val savedAggregates = mutableListOf<WeekPlanAggregate>()
            override suspend fun loadAggregateById(weekPlanId: WeekPlanId): WeekPlanAggregate? = null
            context(tx: TransactionScope)
            override suspend fun saveAggregate(aggregate: WeekPlanAggregate) {
                savedAggregates += aggregate
            }
        }
        val repository = StubRepositoryWithLookup(mapOf(assignmentId to weekPlanId))

        val useCase = RimuoviAssegnazioneUseCase(
            weekPlanStore = store,
            assignmentRepository = repository,
            transactionRunner = PassthroughTransactionRunner,
        )

        val result = useCase(assignmentId)
        val left = assertIs<Either.Left<DomainError>>(result).value
        assertEquals(DomainError.NotFound("Settimana"), left)
        assertEquals(0, store.savedAggregates.size)
    }

    @Test
    fun `returns SettimanaImmutabile when week is SKIPPED (R15-002 regression)`() = runTest {
        val skippedAggregate = aggregate(
            status = WeekPlanStatus.SKIPPED,
            assignments = listOf(sampleAssignment()),
        )
        val store = RecordingWeekPlanStore(skippedAggregate)
        val repository = StubRepositoryWithLookup(mapOf(assignmentId to weekPlanId))

        val useCase = RimuoviAssegnazioneUseCase(
            weekPlanStore = store,
            assignmentRepository = repository,
            transactionRunner = PassthroughTransactionRunner,
        )

        val result = useCase(assignmentId)
        val left = assertIs<Either.Left<DomainError>>(result).value
        assertEquals(DomainError.SettimanaImmutabile, left)
        assertEquals(0, store.savedAggregates.size)
    }

    private fun sampleAssignment() = Assignment(
        id = assignmentId,
        weeklyPartId = WeeklyPartId("wp-1"),
        personId = ProclamatoreId("p1"),
        slot = 1,
    )

    private fun activeAggregateWithAssignment(): WeekPlanAggregate =
        aggregate(status = WeekPlanStatus.ACTIVE, assignments = listOf(sampleAssignment()))

    private fun aggregate(
        status: WeekPlanStatus,
        assignments: List<Assignment>,
    ): WeekPlanAggregate {
        val partType = PartType(
            id = PartTypeId("pt-1"),
            code = "PT",
            label = "Parte",
            peopleCount = 2,
            sexRule = SexRule.STESSO_SESSO,
            fixed = false,
            sortOrder = 0,
        )
        val weekPlan = WeekPlan(
            id = weekPlanId,
            weekStartDate = LocalDate.of(2026, 3, 2),
            parts = listOf(
                WeeklyPart(
                    id = WeeklyPartId("wp-1"),
                    partType = partType,
                    sortOrder = 0,
                ),
            ),
            status = status,
        )
        return WeekPlanAggregate(weekPlan = weekPlan, assignments = assignments)
    }

    private class RecordingWeekPlanStore(
        private val aggregate: WeekPlanAggregate,
    ) : TestWeekPlanStore() {
        val savedAggregates = mutableListOf<WeekPlanAggregate>()

        override suspend fun loadAggregateById(weekPlanId: WeekPlanId): WeekPlanAggregate? =
            if (weekPlanId == aggregate.weekPlan.id) aggregate else null

        context(tx: TransactionScope)
        override suspend fun saveAggregate(aggregate: WeekPlanAggregate) {
            savedAggregates += aggregate
        }
    }

    private class StubRepositoryWithLookup(
        private val lookup: Map<AssignmentId, WeekPlanId>,
    ) : AssignmentRepository by EmptyAssignmentsRepository {
        override suspend fun findWeekPlanIdByAssignmentId(assignmentId: AssignmentId): WeekPlanId? =
            lookup[assignmentId]
    }
}

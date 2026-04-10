package org.example.project.feature.assignments

import arrow.core.Either
import kotlinx.coroutines.test.runTest
import org.example.project.core.PassthroughTransactionRunner
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionScope
import org.example.project.feature.assignments.application.AssegnaPersonaUseCase
import org.example.project.feature.assignments.domain.Assignment
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.people.application.ProclamatoriAggregateStore
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.domain.Sesso
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

class DomainErrorMappingAssignmentsUseCaseTest {

    @Test
    fun `assegna persona happy path saves assignment with correct slot and personId`() = runTest {
        val week = sampleWeek(peopleCount = 2)
        val store = SingleWeekStore(week, assignments = emptyList())
        val useCase = AssegnaPersonaUseCase(
            weekPlanStore = store,
            transactionRunner = PassthroughTransactionRunner,
            personStore = SinglePersonStore(activePerson("p1")),
        )

        val result = useCase(
            weekStartDate = week.weekStartDate,
            weeklyPartId = week.parts.first().id,
            personId = ProclamatoreId("p1"),
            slot = 1,
        )

        assertIs<Either.Right<Unit>>(result)
        val savedAssignments = store.aggregate.assignments
        assertEquals(1, savedAssignments.size)
        assertEquals(ProclamatoreId("p1"), savedAssignments.single().personId)
        assertEquals(1, savedAssignments.single().slot)
        assertEquals(week.parts.first().id, savedAssignments.single().weeklyPartId)
    }

    @Test
    fun `assegna persona maps domain violations to typed errors`() = runTest {
        val week = sampleWeek(peopleCount = 2)

        // suspended person → PersonaSospesa
        val suspendedUseCase = AssegnaPersonaUseCase(
            weekPlanStore = SingleWeekStore(week, assignments = emptyList()),
            transactionRunner = PassthroughTransactionRunner,
            personStore = SinglePersonStore(
                Proclamatore(
                    id = ProclamatoreId("p1"), nome = "Mario", cognome = "Rossi",
                    sesso = Sesso.M, sospeso = true,
                ),
            ),
        )
        assertEquals(
            DomainError.PersonaSospesa,
            assertIs<Either.Left<DomainError>>(
                suspendedUseCase(week.weekStartDate, week.parts.first().id, ProclamatoreId("p1"), 1),
            ).value,
        )

        // invalid slot → SlotNonValido
        val slotUseCase = AssegnaPersonaUseCase(
            weekPlanStore = SingleWeekStore(week, assignments = emptyList()),
            transactionRunner = PassthroughTransactionRunner,
            personStore = SinglePersonStore(activePerson("p1")),
        )
        assertEquals(
            DomainError.SlotNonValido(slot = 3, max = 2),
            assertIs<Either.Left<DomainError>>(
                slotUseCase(week.weekStartDate, week.parts.first().id, ProclamatoreId("p1"), 3),
            ).value,
        )

        // duplicate person in week → PersonaGiaAssegnata
        val dupUseCase = AssegnaPersonaUseCase(
            weekPlanStore = SingleWeekStore(
                week = week,
                assignments = listOf(
                    Assignment(
                        id = AssignmentId("a-existing"),
                        weeklyPartId = WeeklyPartId("wp-1"),
                        personId = ProclamatoreId("p1"),
                        slot = 1,
                    ),
                ),
            ),
            transactionRunner = PassthroughTransactionRunner,
            personStore = SinglePersonStore(activePerson("p1")),
        )
        assertEquals(
            DomainError.PersonaGiaAssegnata,
            assertIs<Either.Left<DomainError>>(
                dupUseCase(week.weekStartDate, week.parts.first().id, ProclamatoreId("p1"), 1),
            ).value,
        )
    }

    private fun sampleWeek(peopleCount: Int): WeekPlan {
        val partType = PartType(
            id = PartTypeId("pt-1"),
            code = "PT",
            label = "Parte",
            peopleCount = peopleCount,
            sexRule = SexRule.STESSO_SESSO,
            fixed = false,
            sortOrder = 0,
        )
        return WeekPlan(
            id = WeekPlanId("w-1"),
            weekStartDate = LocalDate.of(2026, 3, 2),
            parts = listOf(
                WeeklyPart(
                    id = WeeklyPartId("wp-1"),
                    partType = partType,
                    sortOrder = 0,
                ),
            ),
        )
    }

    private fun activePerson(id: String) = Proclamatore(
        id = ProclamatoreId(id),
        nome = "Mario",
        cognome = "Rossi",
        sesso = Sesso.M,
        sospeso = false,
    )
}

private class SingleWeekStore(
    private val week: WeekPlan,
    assignments: List<Assignment>,
) : TestWeekPlanStore() {
    var aggregate = WeekPlanAggregate(
        weekPlan = week,
        assignments = assignments,
    )

    override suspend fun loadAggregateByDate(weekStartDate: LocalDate): WeekPlanAggregate? =
        if (weekStartDate == aggregate.weekPlan.weekStartDate) aggregate else null

    context(tx: org.example.project.core.persistence.TransactionScope)
    override suspend fun saveAggregate(aggregate: WeekPlanAggregate) {
        this.aggregate = aggregate
    }

    override suspend fun loadAggregateById(weekPlanId: WeekPlanId): WeekPlanAggregate? =
        if (weekPlanId == aggregate.weekPlan.id) aggregate else null

    override suspend fun findByDateAndProgram(weekStartDate: LocalDate, programId: ProgramMonthId): WeekPlan? = null
    override suspend fun listByProgram(programId: ProgramMonthId): List<WeekPlan> = emptyList()
}

private class SinglePersonStore(
    private val person: Proclamatore,
) : ProclamatoriAggregateStore {
    override suspend fun load(id: ProclamatoreId): Proclamatore? = if (id == person.id) person else null
    context(tx: TransactionScope)
    override suspend fun persist(aggregateRoot: Proclamatore) {}
    context(tx: TransactionScope)
    override suspend fun persistAll(aggregateRoots: Collection<Proclamatore>) {}
    context(tx: TransactionScope)
    override suspend fun remove(id: ProclamatoreId) {}
}


package org.example.project.feature.assignments

import arrow.core.Either
import kotlinx.coroutines.runBlocking
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionRunner
import org.example.project.feature.assignments.application.AssegnaPersonaUseCase
import org.example.project.feature.assignments.application.AssignmentRepository
import org.example.project.feature.assignments.application.RimuoviAssegnazioneUseCase
import org.example.project.feature.assignments.domain.Assignment
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.assignments.domain.AssignmentWithPerson
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
    fun `assegna persona maps suspended to PersonaSospesa`() = runBlocking {
        val week = sampleWeek(peopleCount = 2)
        val useCase = AssegnaPersonaUseCase(
            weekPlanStore = SingleWeekStore(week, assignments = emptyList()),
            transactionRunner = PassthroughTransactionRunner,
            personStore = SinglePersonStore(
                Proclamatore(
                    id = ProclamatoreId("p1"),
                    nome = "Mario",
                    cognome = "Rossi",
                    sesso = Sesso.M,
                    sospeso = true,
                ),
            ),
        )

        val result = useCase(
            weekStartDate = week.weekStartDate,
            weeklyPartId = week.parts.first().id,
            personId = ProclamatoreId("p1"),
            slot = 1,
        )

        val left = assertIs<Either.Left<DomainError>>(result).value
        assertEquals(DomainError.PersonaSospesa, left)
    }

    @Test
    fun `assegna persona maps invalid slot to SlotNonValido`() = runBlocking {
        val week = sampleWeek(peopleCount = 2)
        val useCase = AssegnaPersonaUseCase(
            weekPlanStore = SingleWeekStore(week, assignments = emptyList()),
            transactionRunner = PassthroughTransactionRunner,
            personStore = SinglePersonStore(activePerson("p1")),
        )

        val result = useCase(
            weekStartDate = week.weekStartDate,
            weeklyPartId = week.parts.first().id,
            personId = ProclamatoreId("p1"),
            slot = 3,
        )

        val left = assertIs<Either.Left<DomainError>>(result).value
        assertEquals(DomainError.SlotNonValido(slot = 3, max = 2), left)
    }

    @Test
    fun `assegna persona maps duplicate in week to PersonaGiaAssegnata`() = runBlocking {
        val week = sampleWeek(peopleCount = 2)
        val useCase = AssegnaPersonaUseCase(
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

        val result = useCase(
            weekStartDate = week.weekStartDate,
            weeklyPartId = week.parts.first().id,
            personId = ProclamatoreId("p1"),
            slot = 1,
        )

        val left = assertIs<Either.Left<DomainError>>(result).value
        assertEquals(DomainError.PersonaGiaAssegnata, left)
    }

    @Test
    fun `rimuovi assegnazione maps repository exceptions to typed domain error`() {
        runBlocking {
            val useCase = RimuoviAssegnazioneUseCase(
                assignmentStore = object : AssignmentRepository by FakeAssignmentRepository() {
                    override suspend fun remove(assignmentId: AssignmentId) {
                        error("db down")
                    }
                },
                transactionRunner = PassthroughTransactionRunner,
            )

            val result = useCase(AssignmentId("a1"))
            val left = assertIs<Either.Left<DomainError>>(result).value
            assertEquals(DomainError.RimozioneAssegnazioniFallita("db down"), left)
        }
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
    private var aggregate = WeekPlanAggregate(
        weekPlan = week,
        assignments = assignments,
    )

    override suspend fun loadAggregateByDate(weekStartDate: LocalDate): WeekPlanAggregate? =
        if (weekStartDate == aggregate.weekPlan.weekStartDate) aggregate else null

    context(org.example.project.core.persistence.TransactionScope)
    override suspend fun saveAggregate(aggregate: WeekPlanAggregate) {
        this.aggregate = aggregate
    }

    override suspend fun loadAggregateById(weekPlanId: WeekPlanId): WeekPlanAggregate? =
        if (weekPlanId == aggregate.weekPlan.id) aggregate else null

    override suspend fun findByDateAndProgram(weekStartDate: LocalDate, programId: ProgramMonthId): WeekPlan? = null
    override suspend fun listByProgram(programId: ProgramMonthId): List<WeekPlan> = emptyList()
}

private class FakeAssignmentRepository(
) : AssignmentRepository {
    override suspend fun listByWeek(weekPlanId: WeekPlanId): List<AssignmentWithPerson> = emptyList()
    override suspend fun listByWeekPlanIds(weekPlanIds: Set<WeekPlanId>): Map<WeekPlanId, List<AssignmentWithPerson>> = emptyMap()

    override suspend fun save(assignment: Assignment) {}
    override suspend fun remove(assignmentId: AssignmentId) {}
    override suspend fun removeAllByWeekPlan(weekPlanId: WeekPlanId) {}
    override suspend fun countAssignmentsForWeek(weekPlanId: WeekPlanId): Int = 0
    override suspend fun countAssignmentsByWeekInRange(startDate: LocalDate, endDate: LocalDate): Map<WeekPlanId, Int> = emptyMap()
    override suspend fun deleteByProgramFromDate(programId: ProgramMonthId, fromDate: LocalDate): Int = 0
    override suspend fun countByProgramFromDate(programId: ProgramMonthId, fromDate: LocalDate): Int = 0
}

private class SinglePersonStore(
    private val person: Proclamatore,
) : ProclamatoriAggregateStore {
    override suspend fun load(id: ProclamatoreId): Proclamatore? = if (id == person.id) person else null
    override suspend fun persist(aggregateRoot: Proclamatore) {}
    override suspend fun persistAll(aggregateRoots: Collection<Proclamatore>) {}
    override suspend fun remove(id: ProclamatoreId) {}
}

private object PassthroughTransactionRunner : TransactionRunner {
    override suspend fun <T> runInTransaction(block: suspend org.example.project.core.persistence.TransactionScope.() -> T): T = with(org.example.project.core.persistence.DefaultTransactionScope) { block() }
}

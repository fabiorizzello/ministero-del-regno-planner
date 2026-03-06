package org.example.project.feature.assignments

import kotlinx.coroutines.runBlocking
import org.example.project.feature.assignments.application.AssegnaPersonaUseCase
import org.example.project.feature.assignments.application.AutoAssegnaProgrammaUseCase
import org.example.project.feature.assignments.application.SuggerisciProclamatoriUseCase
import org.example.project.feature.assignments.domain.SuggestedProclamatore
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

class AutoAssegnaProgrammaUseCaseTransactionTest {

    @Test
    fun `auto assign uses one outer transaction and does not open nested assignment transactions`() = runBlocking {
        val weekStart = LocalDate.of(2026, 3, 9)
        val programId = ProgramMonthId("program-1")
        val partType = PartType(
            id = PartTypeId("pt-1"),
            code = "PT-1",
            label = "Parte",
            peopleCount = 1,
            sexRule = SexRule.STESSO_SESSO,
            fixed = false,
            sortOrder = 0,
        )
        val week = WeekPlan(
            id = WeekPlanId("week-1"),
            weekStartDate = weekStart,
            parts = listOf(
                WeeklyPart(
                    id = WeeklyPartId("part-1"),
                    partType = partType,
                    sortOrder = 0,
                ),
            ),
            programId = programId,
        )
        val weekStore = InMemoryWeekPlanStore(
            WeekPlanAggregate(
                weekPlan = week,
                assignments = emptyList(),
            ),
        )
        val candidate = Proclamatore(
            id = ProclamatoreId("p-1"),
            nome = "Mario",
            cognome = "Rossi",
            sesso = Sesso.M,
            puoAssistere = true,
        )
        val assignmentTx = CountingTransactionRunner()
        val autoAssignTx = CountingTransactionRunner()

        val assignUseCase = AssegnaPersonaUseCase(
            weekPlanStore = weekStore,
            transactionRunner = assignmentTx,
            personStore = TransactionTestPersonStore(candidate),
        )
        val candidateSuggestion = SuggestedProclamatore(
            proclamatore = candidate,
            lastGlobalWeeks = 6,
            lastForPartTypeWeeks = 4,
            lastConductorWeeks = 6,
        )
        val suggestUseCase = SuggerisciProclamatoriUseCase(
            weekPlanStore = weekStore,
            assignmentStore = StaticAssignmentRanking(listOf(candidateSuggestion)),
            assignmentRepository = EmptyAssignmentsRepository,
            eligibilityStore = SingleCandidateEligibilityStore(candidate.id, partType.id),
            assignmentSettingsStore = StaticSettingsStore,
        )
        val ranking = StaticAssignmentRanking(listOf(candidateSuggestion))
        val eligibility = SingleCandidateEligibilityStore(candidate.id, partType.id)
        val useCase = AutoAssegnaProgrammaUseCase(
            weekPlanStore = weekStore,
            assignmentRepository = EmptyAssignmentsRepository,
            suggerisciProclamatori = suggestUseCase,
            assegnaPersona = assignUseCase,
            transactionRunner = autoAssignTx,
            assignmentRanking = ranking,
            eligibilityStore = eligibility,
        )

        val result = useCase(programId = programId, referenceDate = weekStart)
        val saved = weekStore.loadAggregateByDate(weekStart)

        assertEquals(1, result.assignedCount)
        assertEquals(0, result.unresolved.size)
        assertEquals(1, autoAssignTx.calls)
        assertEquals(0, assignmentTx.calls)
        assertEquals(1, saved?.assignments?.size ?: 0)
    }
}

private class InMemoryWeekPlanStore(
    private var aggregate: WeekPlanAggregate,
) : TestWeekPlanStore() {
    override suspend fun findByDate(weekStartDate: LocalDate): WeekPlan? =
        if (aggregate.weekPlan.weekStartDate == weekStartDate) aggregate.weekPlan else null

    override suspend fun listByProgram(programId: ProgramMonthId): List<WeekPlan> =
        if (aggregate.weekPlan.programId == programId) listOf(aggregate.weekPlan) else emptyList()

    override suspend fun loadAggregateByDate(weekStartDate: LocalDate): WeekPlanAggregate? =
        if (aggregate.weekPlan.weekStartDate == weekStartDate) aggregate else null

    override suspend fun loadAggregateById(weekPlanId: WeekPlanId): WeekPlanAggregate? =
        if (aggregate.weekPlan.id == weekPlanId) aggregate else null

    context(org.example.project.core.persistence.TransactionScope)
    override suspend fun saveAggregate(aggregate: WeekPlanAggregate) {
        this.aggregate = aggregate
    }
}

private class TransactionTestPersonStore(
    private val person: Proclamatore,
) : ProclamatoriAggregateStore {
    override suspend fun load(id: ProclamatoreId): Proclamatore? = if (id == person.id) person else null

    override suspend fun persist(aggregateRoot: Proclamatore) {}

    override suspend fun persistAll(aggregateRoots: Collection<Proclamatore>) {}

    override suspend fun remove(id: ProclamatoreId) {}
}


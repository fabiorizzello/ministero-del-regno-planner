package org.example.project.feature.assignments

import kotlinx.coroutines.test.runTest
import org.example.project.core.PassthroughTransactionRunner
import org.example.project.core.persistence.TransactionScope
import org.example.project.feature.assignments.application.AssegnaPersonaUseCase
import org.example.project.feature.assignments.application.AssignmentRanking
import org.example.project.feature.assignments.application.AssignmentRepository
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
import org.example.project.feature.weeklyparts.domain.WeekPlanStatus
import org.example.project.feature.weeklyparts.domain.WeeklyPart
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AutoAssegnaProgrammaUseCaseTest {

    // -- Shared fixtures -------------------------------------------------------

    private val weekStart = LocalDate.of(2026, 3, 9)
    private val programId = ProgramMonthId("program-1")

    private val partType = PartType(
        id = PartTypeId("pt-1"),
        code = "PT-1",
        label = "Parte",
        peopleCount = 1,
        sexRule = SexRule.STESSO_SESSO,
        fixed = false,
        sortOrder = 0,
    )

    private val week = WeekPlan(
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

    private val candidate = Proclamatore(
        id = ProclamatoreId("p-1"),
        nome = "Mario",
        cognome = "Rossi",
        sesso = Sesso.M,
        puoAssistere = true,
    )

    private val candidateSuggestion = SuggestedProclamatore(
        proclamatore = candidate,
        lastGlobalWeeks = 6,
        lastForPartTypeWeeks = 4,
        lastConductorWeeks = 6,
    )

    private fun buildUseCase(
        weekStore: InMemoryWeekPlanStore = InMemoryWeekPlanStore(
            WeekPlanAggregate(weekPlan = week, assignments = emptyList()),
        ),
        assignmentRepository: AssignmentRepository = EmptyAssignmentsRepository,
        suggestUseCase: SuggerisciProclamatoriUseCase = SuggerisciProclamatoriUseCase(
            weekPlanStore = weekStore,
            assignmentStore = StaticAssignmentRanking(listOf(candidateSuggestion)),
            assignmentRepository = assignmentRepository,
            eligibilityStore = SingleCandidateEligibilityStore(candidate.id, partType.id),
            assignmentSettingsStore = StaticSettingsStore,
        ),
        assignUseCase: AssegnaPersonaUseCase = AssegnaPersonaUseCase(
            weekPlanStore = weekStore,
            transactionRunner = PassthroughTransactionRunner,
            personStore = TransactionTestPersonStore(candidate),
        ),
        ranking: AssignmentRanking = StaticAssignmentRanking(listOf(candidateSuggestion)),
    ): AutoAssegnaProgrammaUseCase = AutoAssegnaProgrammaUseCase(
        weekPlanStore = weekStore,
        assignmentRepository = assignmentRepository,
        suggerisciProclamatori = suggestUseCase,
        assegnaPersona = assignUseCase,
        transactionRunner = PassthroughTransactionRunner,
        assignmentRanking = ranking,
        eligibilityStore = SingleCandidateEligibilityStore(candidate.id, partType.id),
    )

    // -- Happy path ------------------------------------------------------------

    @Test
    fun `auto assign creates assignment and reports success for single slot`() = runTest {
        val weekStore = InMemoryWeekPlanStore(
            WeekPlanAggregate(weekPlan = week, assignments = emptyList()),
        )
        val useCase = buildUseCase(weekStore = weekStore)

        val result = useCase(programId = programId, referenceDate = weekStart)
        val saved = weekStore.loadAggregateByDate(weekStart)

        assertEquals(1, result.assignedCount)
        assertEquals(0, result.unresolved.size)
        assertEquals(1, saved?.assignments?.size ?: 0)
        assertEquals(candidate.id, saved?.assignments?.single()?.personId)
    }

    // -- Error paths -----------------------------------------------------------

    @Test
    fun `no weeks for program returns zero assignments and no unresolved`() = runTest {
        val emptyWeekStore = InMemoryWeekPlanStore(
            WeekPlanAggregate(weekPlan = week, assignments = emptyList()),
        )
        val useCase = buildUseCase(weekStore = emptyWeekStore)

        // Use a different programId so listByProgram returns empty
        val result = useCase(programId = ProgramMonthId("nonexistent"), referenceDate = weekStart)

        assertEquals(0, result.assignedCount)
        assertEquals(0, result.unresolved.size)
    }

    @Test
    fun `skipped weeks are filtered out and produce zero assignments`() = runTest {
        val skippedWeek = WeekPlan(
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
            status = WeekPlanStatus.SKIPPED,
        )
        val weekStore = InMemoryWeekPlanStore(
            WeekPlanAggregate(weekPlan = skippedWeek, assignments = emptyList()),
        )
        val useCase = buildUseCase(weekStore = weekStore)

        val result = useCase(programId = programId, referenceDate = weekStart)

        assertEquals(0, result.assignedCount)
        assertEquals(0, result.unresolved.size)
    }

    @Test
    fun `all candidates in cooldown produces unresolved slot`() = runTest {
        // lastGlobalWeeks=1 is less than both leadCooldownWeeks=4 and assistCooldownWeeks=2
        // (default settings from StaticSettingsStore), so candidate will be in cooldown.
        val recentCandidate = candidateSuggestion.copy(
            lastGlobalWeeks = 1,
            lastConductorWeeks = 1,
        )
        val weekStore = InMemoryWeekPlanStore(
            WeekPlanAggregate(weekPlan = week, assignments = emptyList()),
        )
        val suggestUseCase = SuggerisciProclamatoriUseCase(
            weekPlanStore = weekStore,
            assignmentStore = StaticAssignmentRanking(listOf(recentCandidate)),
            assignmentRepository = EmptyAssignmentsRepository,
            eligibilityStore = SingleCandidateEligibilityStore(candidate.id, partType.id),
            assignmentSettingsStore = StaticSettingsStore,
        )
        val useCase = buildUseCase(
            weekStore = weekStore,
            suggestUseCase = suggestUseCase,
        )

        val result = useCase(programId = programId, referenceDate = weekStart)

        assertEquals(0, result.assignedCount)
        assertEquals(1, result.unresolved.size)
        assertEquals(weekStart, result.unresolved[0].weekStartDate)
        assertEquals("Parte", result.unresolved[0].partLabel)
        assertEquals(1, result.unresolved[0].slot)
        assertEquals("Nessun candidato idoneo", result.unresolved[0].reason)
    }

    @Test
    fun `no eligible candidates at all produces unresolved slot`() = runTest {
        val weekStore = InMemoryWeekPlanStore(
            WeekPlanAggregate(weekPlan = week, assignments = emptyList()),
        )
        // Empty ranking returns no suggestions — simulates no one being eligible
        val suggestUseCase = SuggerisciProclamatoriUseCase(
            weekPlanStore = weekStore,
            assignmentStore = StaticAssignmentRanking(emptyList()),
            assignmentRepository = EmptyAssignmentsRepository,
            eligibilityStore = SingleCandidateEligibilityStore(candidate.id, partType.id),
            assignmentSettingsStore = StaticSettingsStore,
        )
        val useCase = buildUseCase(
            weekStore = weekStore,
            suggestUseCase = suggestUseCase,
            ranking = StaticAssignmentRanking(emptyList()),
        )

        val result = useCase(programId = programId, referenceDate = weekStart)

        assertEquals(0, result.assignedCount)
        assertEquals(1, result.unresolved.size)
        assertEquals(weekStart, result.unresolved[0].weekStartDate)
        assertEquals("Parte", result.unresolved[0].partLabel)
        assertEquals(1, result.unresolved[0].slot)
        assertEquals("Nessun candidato idoneo", result.unresolved[0].reason)
    }

    @Test
    fun `assegnaPersona returning Left records unresolved with error message`() = runTest {
        val weekStore = InMemoryWeekPlanStore(
            WeekPlanAggregate(weekPlan = week, assignments = emptyList()),
        )
        // Use a person store that returns null (person not found), so AssegnaPersonaUseCase returns Left(NotFound)
        val failingAssignUseCase = AssegnaPersonaUseCase(
            weekPlanStore = weekStore,
            transactionRunner = PassthroughTransactionRunner,
            personStore = EmptyPersonStore,
        )
        val useCase = buildUseCase(
            weekStore = weekStore,
            assignUseCase = failingAssignUseCase,
        )

        val result = useCase(programId = programId, referenceDate = weekStart)

        assertEquals(0, result.assignedCount)
        assertEquals(1, result.unresolved.size)
        assertEquals(weekStart, result.unresolved[0].weekStartDate)
        assertEquals("Parte", result.unresolved[0].partLabel)
        assertEquals(1, result.unresolved[0].slot)
        assertTrue(result.unresolved[0].reason.contains("non trovato"))
    }

    @Test
    fun `partial failure persists successful slot and records unresolved for failed slot`() = runTest {
        // Part type with 2 slots (studente + assistente)
        val twoSlotPartType = partType.copy(peopleCount = 2)
        val twoSlotPart = WeeklyPart(
            id = WeeklyPartId("part-1"),
            partType = twoSlotPartType,
            sortOrder = 0,
        )
        val twoSlotWeek = WeekPlan(
            id = WeekPlanId("week-1"),
            weekStartDate = weekStart,
            parts = listOf(twoSlotPart),
            programId = programId,
        )

        // Candidate B: puoAssistere but NOT known by the person store → will trigger NotFound
        val candidateB = Proclamatore(
            id = ProclamatoreId("p-2"),
            nome = "Luigi",
            cognome = "Verdi",
            sesso = Sesso.M,
            puoAssistere = true,
        )
        val candidateBSuggestion = SuggestedProclamatore(
            proclamatore = candidateB,
            lastGlobalWeeks = 5,
            lastForPartTypeWeeks = 3,
            lastConductorWeeks = 5,
        )

        val weekStore = InMemoryWeekPlanStore(
            WeekPlanAggregate(weekPlan = twoSlotWeek, assignments = emptyList()),
        )
        val ranking = StaticAssignmentRanking(listOf(candidateSuggestion, candidateBSuggestion))
        val eligibility = SingleCandidateEligibilityStore(candidate.id, twoSlotPartType.id)
        val suggestUseCase = SuggerisciProclamatoriUseCase(
            weekPlanStore = weekStore,
            assignmentStore = ranking,
            assignmentRepository = EmptyAssignmentsRepository,
            eligibilityStore = eligibility,
            assignmentSettingsStore = StaticSettingsStore,
        )
        // Person store only knows candidate A — slot 2 will fail with NotFound for candidate B
        val assignUseCase = AssegnaPersonaUseCase(
            weekPlanStore = weekStore,
            transactionRunner = PassthroughTransactionRunner,
            personStore = TransactionTestPersonStore(candidate),
        )
        val useCase = buildUseCase(
            weekStore = weekStore,
            suggestUseCase = suggestUseCase,
            assignUseCase = assignUseCase,
            ranking = ranking,
            assignmentRepository = EmptyAssignmentsRepository,
        )

        val result = useCase(programId = programId, referenceDate = weekStart)
        val saved = weekStore.loadAggregateByDate(weekStart)

        // Slot 1 succeeded, slot 2 failed
        assertEquals(1, result.assignedCount)
        assertEquals(1, result.unresolved.size)
        assertEquals(2, result.unresolved[0].slot)
        assertEquals(weekStart, result.unresolved[0].weekStartDate)
        assertTrue(result.unresolved[0].reason.contains("non trovato"))

        // Slot 1 assignment was persisted
        assertEquals(1, saved?.assignments?.size ?: 0)
        assertEquals(candidate.id, saved?.assignments?.single()?.personId)
    }

    @Test
    fun `weeks before referenceDate are excluded from auto-assignment`() = runTest {
        val weekStore = InMemoryWeekPlanStore(
            WeekPlanAggregate(weekPlan = week, assignments = emptyList()),
        )
        val useCase = buildUseCase(weekStore = weekStore)

        // referenceDate is after the week start, so the week is excluded
        val futureDate = weekStart.plusWeeks(1)
        val result = useCase(programId = programId, referenceDate = futureDate)

        assertEquals(0, result.assignedCount)
        assertEquals(0, result.unresolved.size)
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

    context(tx: org.example.project.core.persistence.TransactionScope)
    override suspend fun saveAggregate(aggregate: WeekPlanAggregate) {
        this.aggregate = aggregate
    }
}

private class TransactionTestPersonStore(
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

/** Person store that always returns null — triggers NotFound in AssegnaPersonaUseCase. */
private object EmptyPersonStore : ProclamatoriAggregateStore {
    override suspend fun load(id: ProclamatoreId): Proclamatore? = null

    context(tx: TransactionScope)
    override suspend fun persist(aggregateRoot: Proclamatore) {}

    context(tx: TransactionScope)
    override suspend fun persistAll(aggregateRoots: Collection<Proclamatore>) {}

    context(tx: TransactionScope)
    override suspend fun remove(id: ProclamatoreId) {}
}


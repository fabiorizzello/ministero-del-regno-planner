package org.example.project.feature.assignments

import kotlinx.coroutines.runBlocking
import org.example.project.core.persistence.TransactionScope
import org.example.project.feature.assignments.application.AssegnaPersonaUseCase
import org.example.project.feature.assignments.application.AssignmentRepository
import org.example.project.feature.assignments.application.AssignmentSettings
import org.example.project.feature.assignments.application.AutoAssegnaProgrammaUseCase
import org.example.project.feature.assignments.application.SuggerisciProclamatoriUseCase
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.assignments.domain.AssignmentWithPerson
import org.example.project.feature.assignments.domain.SuggestedProclamatore
import org.example.project.feature.people.application.EligibilityCleanupCandidate
import org.example.project.feature.people.application.ProclamatoriAggregateStore
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.domain.Sesso
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.weeklyparts.TestWeekPlanStore
import org.example.project.feature.weeklyparts.application.WeekPlanQueries
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
import kotlin.test.assertTrue

class SexMismatchPolicyTest {

    @Test
    fun `manual suggestions keep STESSO_SESSO mismatch candidates annotated`() = runBlocking {
        val fixture = SexMismatchFixture()
        val suggest = fixture.createSuggestUseCase()

        val result = suggest(
            weekStartDate = fixture.week.weekStartDate,
            weeklyPartId = fixture.part.id,
            slot = 1,
        )

        assertEquals(1, result.size)
        assertTrue(result.first().sexMismatch)
    }

    @Test
    fun `auto assign leaves slot unresolved when only mismatch candidates exist`() = runBlocking {
        val fixture = SexMismatchFixture()
        val suggest = fixture.createSuggestUseCase()
        val autoAssign = AutoAssegnaProgrammaUseCase(
            weekPlanStore = fixture.weekQueries,
            assignmentRepository = fixture.assignmentRepository,
            suggerisciProclamatori = suggest,
            assegnaPersona = neverCalledAssignUseCase(),
            transactionRunner = PassthroughTransactionRunner,
            assignmentRanking = fixture.staticRanking,
            eligibilityStore = fixture.eligibilityStore,
        )

        val result = autoAssign(
            programId = fixture.programId,
            referenceDate = fixture.week.weekStartDate,
        )

        assertEquals(0, result.assignedCount)
        assertEquals(1, result.unresolved.size)
        assertEquals("Nessun candidato idoneo", result.unresolved.first().reason)
        assertEquals(1, result.unresolved.first().slot)
    }
}

private class SexMismatchFixture {
    val programId = ProgramMonthId("program-1")

    val part = WeeklyPart(
        id = WeeklyPartId("part-1"),
        partType = PartType(
            id = PartTypeId("pt-1"),
            code = "PT-1",
            label = "Parte 1",
            peopleCount = 2,
            sexRule = SexRule.STESSO_SESSO,
            fixed = false,
            sortOrder = 0,
        ),
        sortOrder = 0,
    )

    val week = WeekPlan(
        id = WeekPlanId("week-1"),
        weekStartDate = LocalDate.of(2026, 3, 9),
        parts = listOf(part),
        programId = programId,
    )

    private val existingMale = person(id = "p-m", nome = "Mario", cognome = "Rossi", sesso = Sesso.M)
    private val candidateFemale = person(id = "p-f", nome = "Anna", cognome = "Bianchi", sesso = Sesso.F)
    private val rankingSuggestions = listOf(
        SuggestedProclamatore(
            proclamatore = candidateFemale,
            lastGlobalWeeks = 8,
            lastForPartTypeWeeks = 6,
            lastConductorWeeks = 8,
        ),
    )

    val weekQueries: WeekPlanQueries = StaticWeekPlanQueries(week)
    val assignmentRepository: AssignmentRepository = StaticAssignmentRepository(
        assignments = listOf(
            AssignmentWithPerson(
                id = AssignmentId("a-existing"),
                weeklyPartId = part.id,
                personId = existingMale.id,
                slot = 2,
                proclamatore = existingMale,
            ),
        ),
    )

    val staticRanking = StaticAssignmentRanking(rankingSuggestions)
    val eligibilityStore = StaticEligibilityStore(
        eligible = setOf(EligibilityCleanupCandidate(candidateFemale.id, part.partType.id)),
    )

    fun createSuggestUseCase(): SuggerisciProclamatoriUseCase = SuggerisciProclamatoriUseCase(
        weekPlanStore = weekQueries,
        assignmentStore = staticRanking,
        assignmentRepository = assignmentRepository,
        eligibilityStore = eligibilityStore,
        assignmentSettingsStore = FixedSettingsStore(settings = AssignmentSettings(strictCooldown = true)),
    )
}

private class StaticWeekPlanQueries(
    private val week: WeekPlan,
) : WeekPlanQueries {
    override suspend fun findByDate(weekStartDate: LocalDate): WeekPlan? =
        if (week.weekStartDate == weekStartDate) week else null

    override suspend fun listInRange(startDate: LocalDate, endDate: LocalDate): List<WeekPlanSummary> = emptyList()

    override suspend fun totalSlotsByWeekInRange(startDate: LocalDate, endDate: LocalDate): Map<WeekPlanId, Int> = emptyMap()

    override suspend fun findByDateAndProgram(weekStartDate: LocalDate, programId: ProgramMonthId): WeekPlan? =
        if (week.weekStartDate == weekStartDate && week.programId == programId) week else null

    override suspend fun listByProgram(programId: ProgramMonthId): List<WeekPlan> =
        if (week.programId == programId) listOf(week) else emptyList()
}

private fun neverCalledAssignUseCase(): AssegnaPersonaUseCase = AssegnaPersonaUseCase(
    weekPlanStore = object : TestWeekPlanStore() {
        override suspend fun loadAggregateByDate(weekStartDate: LocalDate) =
            error("AssegnaPersonaUseCase non deve essere invocato in questo test")
    },
    transactionRunner = PassthroughTransactionRunner,
    personStore = object : ProclamatoriAggregateStore {
        override suspend fun load(id: ProclamatoreId): Proclamatore? =
            error("AssegnaPersonaUseCase non deve essere invocato in questo test")

        override suspend fun persist(aggregateRoot: Proclamatore) {}

        context(tx: TransactionScope)
        override suspend fun persistAll(aggregateRoots: Collection<Proclamatore>) {}

        override suspend fun remove(id: ProclamatoreId) {}
    },
)

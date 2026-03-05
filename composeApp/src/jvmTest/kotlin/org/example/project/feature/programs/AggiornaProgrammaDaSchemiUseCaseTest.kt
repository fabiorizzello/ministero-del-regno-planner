package org.example.project.feature.programs

import arrow.core.Either
import kotlinx.coroutines.runBlocking
import org.example.project.core.persistence.TransactionRunner
import org.example.project.feature.assignments.application.AssignmentRepository
import org.example.project.feature.assignments.domain.Assignment
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.assignments.domain.AssignmentWithPerson
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.domain.Sesso
import org.example.project.feature.programs.application.AggiornaProgrammaDaSchemiUseCase
import org.example.project.feature.programs.application.ProgramCreationContext
import org.example.project.feature.programs.application.ProgramDeleteImpact
import org.example.project.feature.programs.application.ProgramStore
import org.example.project.feature.programs.domain.ProgramMonth
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.schemas.application.SchemaTemplateStore
import org.example.project.feature.schemas.application.StoredSchemaWeekTemplate
import org.example.project.feature.weeklyparts.application.PartTypeStore
import org.example.project.feature.weeklyparts.application.PartTypeWithStatus
import org.example.project.feature.weeklyparts.application.WeekPlanStore
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.SexRule
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeekPlanStatus
import org.example.project.feature.weeklyparts.domain.WeekPlanSummary
import org.example.project.feature.weeklyparts.domain.WeeklyPart
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AggiornaProgrammaDaSchemiUseCaseTest {

    @Test
    fun `dry run reports preserved and removed assignments`() = runBlocking {
        val fixture = refreshFixture()
        val useCase = buildUseCase(fixture)

        val result = useCase(
            programId = fixture.program.id,
            referenceDate = LocalDate.of(2026, 3, 1),
            dryRun = true,
        )

        val report = assertIs<Either.Right<org.example.project.feature.programs.application.SchemaRefreshReport>>(result).value
        assertEquals(1, report.weeksUpdated)
        assertEquals(1, report.assignmentsPreserved)
        assertEquals(1, report.assignmentsRemoved)
        assertEquals(0, fixture.assignmentRepository.savedAssignments.size)
    }

    @Test
    fun `apply refresh preserves only matching assignments and updates timestamp`() = runBlocking {
        val fixture = refreshFixture()
        val useCase = buildUseCase(fixture)

        val result = useCase(
            programId = fixture.program.id,
            referenceDate = LocalDate.of(2026, 3, 1),
            dryRun = false,
        )

        val report = assertIs<Either.Right<org.example.project.feature.programs.application.SchemaRefreshReport>>(result).value
        assertEquals(1, report.weeksUpdated)
        assertEquals(1, report.assignmentsPreserved)
        assertEquals(1, report.assignmentsRemoved)

        assertEquals(1, fixture.assignmentRepository.savedAssignments.size)
        val saved = fixture.assignmentRepository.savedAssignments.single()
        assertEquals(ProclamatoreId("p1"), saved.personId)
        assertEquals(1, fixture.programStore.templateAppliedUpdates.size)
    }

    @Test
    fun `skipped weeks are not refreshed`() = runBlocking {
        val fixture = refreshFixture(weekStatus = WeekPlanStatus.SKIPPED)
        val useCase = buildUseCase(fixture)

        val result = useCase(
            programId = fixture.program.id,
            referenceDate = LocalDate.of(2026, 3, 1),
            dryRun = true,
        )

        val report = assertIs<Either.Right<org.example.project.feature.programs.application.SchemaRefreshReport>>(result).value
        assertEquals(0, report.weeksUpdated)
        assertEquals(0, report.assignmentsPreserved)
        assertEquals(0, report.assignmentsRemoved)
        assertEquals(0, fixture.assignmentRepository.savedAssignments.size)
    }

    private fun buildUseCase(fixture: RefreshFixture): AggiornaProgrammaDaSchemiUseCase {
        return AggiornaProgrammaDaSchemiUseCase(
            programStore = fixture.programStore,
            weekPlanStore = fixture.weekStore,
            schemaTemplateStore = fixture.schemaStore,
            partTypeStore = NoopPartTypeStore(),
            assignmentRepository = fixture.assignmentRepository,
            transactionRunner = ImmediateTxRunner(),
        )
    }

    private fun refreshFixture(weekStatus: WeekPlanStatus = WeekPlanStatus.ACTIVE): RefreshFixture {
        val program = fixtureProgramMonth(YearMonth.of(2026, 3), id = "program-refresh")
        val partA = PartType(PartTypeId("A"), "A", "Parte A", 1, SexRule.STESSO_SESSO, fixed = false, sortOrder = 1)
        val partB = PartType(PartTypeId("B"), "B", "Parte B", 1, SexRule.STESSO_SESSO, fixed = false, sortOrder = 2)
        val partC = PartType(PartTypeId("C"), "C", "Parte C", 1, SexRule.STESSO_SESSO, fixed = false, sortOrder = 3)

        val weekId = WeekPlanId("week-1")
        val week = WeekPlan(
            id = weekId,
            weekStartDate = LocalDate.of(2026, 3, 2),
            parts = listOf(
                WeeklyPart(WeeklyPartId("old-a"), partA, sortOrder = 0),
                WeeklyPart(WeeklyPartId("old-b"), partB, sortOrder = 1),
            ),
            programId = program.id,
            status = weekStatus,
        )

        val assignments = listOf(
            AssignmentWithPerson(
                id = AssignmentId("as-1"),
                weeklyPartId = WeeklyPartId("old-a"),
                personId = ProclamatoreId("p1"),
                slot = 1,
                proclamatore = Proclamatore(ProclamatoreId("p1"), "Mario", "Rossi", Sesso.M),
            ),
            AssignmentWithPerson(
                id = AssignmentId("as-2"),
                weeklyPartId = WeeklyPartId("old-b"),
                personId = ProclamatoreId("p2"),
                slot = 1,
                proclamatore = Proclamatore(ProclamatoreId("p2"), "Luigi", "Verdi", Sesso.M),
            ),
        )

        val schemaStore = InMemorySchemaTemplateStore(
            mapOf(
                LocalDate.of(2026, 3, 2) to StoredSchemaWeekTemplate(
                    weekStartDate = LocalDate.of(2026, 3, 2),
                    partTypeIds = listOf(partA.id, partC.id),
                ),
            ),
        )

        return RefreshFixture(
            program = program,
            programStore = RefreshProgramStore(program),
            weekStore = RefreshWeekStore(program.id, week, partA, partC),
            schemaStore = schemaStore,
            assignmentRepository = RefreshAssignmentRepository(weekId, assignments),
        )
    }
}

private data class RefreshFixture(
    val program: ProgramMonth,
    val programStore: RefreshProgramStore,
    val weekStore: RefreshWeekStore,
    val schemaStore: SchemaTemplateStore,
    val assignmentRepository: RefreshAssignmentRepository,
)

private class ImmediateTxRunner : TransactionRunner {
    override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
}

private class RefreshProgramStore(
    private val program: ProgramMonth,
) : ProgramStore {
    val templateAppliedUpdates = mutableListOf<Pair<ProgramMonthId, LocalDateTime>>()

    override suspend fun listCurrentAndFuture(referenceDate: LocalDate): List<ProgramMonth> = listOf(program)

    override suspend fun findByYearMonth(year: Int, month: Int): ProgramMonth? =
        if (program.year == year && program.month == month) program else null

    override suspend fun findById(id: ProgramMonthId): ProgramMonth? = if (id == program.id) program else null

    override suspend fun save(program: ProgramMonth) {
        // no-op
    }

    override suspend fun delete(id: ProgramMonthId) {
        // no-op
    }

    override suspend fun updateTemplateAppliedAt(id: ProgramMonthId, templateAppliedAt: LocalDateTime) {
        templateAppliedUpdates += id to templateAppliedAt
    }

    override suspend fun countDeleteImpact(id: ProgramMonthId): ProgramDeleteImpact = ProgramDeleteImpact(0, 0)

    override suspend fun loadCreationContext(referenceDate: LocalDate): ProgramCreationContext {
        return ProgramCreationContext(setOf(program.yearMonth), hasCurrent = true, futureMonths = emptySet())
    }
}

private class RefreshWeekStore(
    private val programId: ProgramMonthId,
    initialWeek: WeekPlan,
    private val partA: PartType,
    private val partC: PartType,
) : WeekPlanStore {
    private var week = initialWeek

    override suspend fun findByDate(weekStartDate: LocalDate): WeekPlan? = null

    override suspend fun listInRange(startDate: LocalDate, endDate: LocalDate): List<WeekPlanSummary> = emptyList()

    override suspend fun totalSlotsByWeekInRange(startDate: LocalDate, endDate: LocalDate): Map<WeekPlanId, Int> = emptyMap()

    override suspend fun save(weekPlan: WeekPlan) {
        // no-op
    }

    override suspend fun delete(weekPlanId: WeekPlanId) {
        // no-op
    }

    override suspend fun addPart(weekPlanId: WeekPlanId, partTypeId: PartTypeId, sortOrder: Int, partTypeRevisionId: String?): WeeklyPartId = WeeklyPartId("unused")

    override suspend fun removePart(weeklyPartId: WeeklyPartId) {
        // no-op
    }

    override suspend fun updateSortOrders(parts: List<Pair<WeeklyPartId, Int>>) {
        // no-op
    }

    override suspend fun replaceAllParts(weekPlanId: WeekPlanId, partTypeIds: List<PartTypeId>, revisionIds: List<String?>) {
        val first = WeeklyPart(
            id = WeeklyPartId("new-a"),
            partType = partA,
            sortOrder = 0,
        )
        val second = WeeklyPart(
            id = WeeklyPartId("new-c"),
            partType = partC,
            sortOrder = 1,
        )
        week = week.copy(parts = listOf(first, second))
    }

    override suspend fun findByDateAndProgram(weekStartDate: LocalDate, programId: ProgramMonthId): WeekPlan? {
        if (programId != this.programId) return null
        return if (week.weekStartDate == weekStartDate) week else null
    }

    override suspend fun listByProgram(programId: ProgramMonthId): List<WeekPlan> {
        if (programId != this.programId) return emptyList()
        return listOf(week)
    }

    override suspend fun updateWeekStatus(weekPlanId: WeekPlanId, status: WeekPlanStatus) {
        // no-op
    }
}

private class RefreshAssignmentRepository(
    private val weekId: WeekPlanId,
    private val assignments: List<AssignmentWithPerson>,
) : AssignmentRepository {
    val savedAssignments = mutableListOf<Assignment>()

    override suspend fun listByWeek(weekPlanId: WeekPlanId): List<AssignmentWithPerson> {
        return if (weekPlanId == weekId) assignments else emptyList()
    }

    override suspend fun save(assignment: Assignment) {
        savedAssignments += assignment
    }

    override suspend fun remove(assignmentId: AssignmentId) {
        // no-op
    }

    override suspend fun removeAllByWeekPlan(weekPlanId: WeekPlanId) {
        // no-op
    }

    override suspend fun isPersonAssignedInWeek(weekPlanId: WeekPlanId, personId: ProclamatoreId): Boolean = false

    override suspend fun countAssignmentsForWeek(weekPlanId: WeekPlanId): Int = 0

    override suspend fun countAssignmentsByWeekInRange(startDate: LocalDate, endDate: LocalDate): Map<WeekPlanId, Int> = emptyMap()

    override suspend fun deleteByProgramFromDate(programId: ProgramMonthId, fromDate: LocalDate): Int = 0

    override suspend fun countByProgramFromDate(programId: ProgramMonthId, fromDate: LocalDate): Int = 0
}

private class NoopPartTypeStore : PartTypeStore {
    override suspend fun all(): List<PartType> = emptyList()
    override suspend fun findByCode(code: String): PartType? = null
    override suspend fun findFixed(): PartType? = null
    override suspend fun upsertAll(partTypes: List<PartType>) {}
}

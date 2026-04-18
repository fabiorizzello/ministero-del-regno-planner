package org.example.project.feature.programs

import arrow.core.Either
import kotlinx.coroutines.test.runTest
import org.example.project.core.PassthroughTransactionRunner
import org.example.project.core.persistence.TransactionScope
import org.example.project.feature.assignments.domain.Assignment
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.programs.application.AggiornaProgrammaDaSchemiUseCase
import org.example.project.feature.programs.application.ProgramCreationContext
import org.example.project.feature.programs.application.SchemaRefreshMode
import org.example.project.feature.programs.application.ProgramStore
import org.example.project.feature.programs.application.SchemaRefreshReport
import org.example.project.feature.programs.application.WeekRefreshDetail
import org.example.project.feature.programs.domain.ProgramMonth
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.schemas.application.SchemaTemplateStore
import org.example.project.feature.schemas.application.StoredSchemaWeekTemplate
import org.example.project.feature.weeklyparts.TestWeekPlanStore
import org.example.project.feature.weeklyparts.application.PartTypeStore
import org.example.project.feature.weeklyparts.application.PartTypeWithStatus
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
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AggiornaProgrammaDaSchemiUseCaseTest {

    @Test
    fun `dry run reports preserved and removed assignments`() = runTest {
        val fixture = refreshFixture()
        val useCase = buildUseCase(fixture)

        val result = useCase(
            programId = fixture.program.id,
            referenceDate = LocalDate.of(2026, 3, 1),
            dryRun = true,
        )

        val report = assertIs<Either.Right<SchemaRefreshReport>>(result).value
        assertEquals(1, report.weeksUpdated)
        assertEquals(1, report.assignmentsPreserved)
        assertEquals(1, report.assignmentsRemoved)
        assertEquals(0, fixture.weekStore.saveCount)

        assertEquals(1, report.weekDetails.size)
        val detail = report.weekDetails.single()
        assertEquals(LocalDate.of(2026, 3, 2), detail.weekStartDate)
        // Old parts: A(sort=0), B(sort=1). New schema: A(sort=0), C(sort=1).
        // Kept: A at sort=0. Added: C at sort=1. Removed: B at sort=1.
        assertEquals(1, detail.partsKept)
        assertEquals(1, detail.partsAdded)
        assertEquals(1, detail.partsRemoved)
        assertEquals(1, detail.assignmentsPreserved)
        assertEquals(1, detail.assignmentsRemoved)
    }

    @Test
    fun `dry run with instantiated week already aligned reports no effective changes`() = runTest {
        val fixture = refreshFixture(templatePartCodes = listOf("A", "B"))
        val useCase = buildUseCase(fixture)

        val result = useCase(
            programId = fixture.program.id,
            referenceDate = LocalDate.of(2026, 3, 1),
            dryRun = true,
        )

        val report = assertIs<Either.Right<SchemaRefreshReport>>(result).value
        assertEquals(1, report.weeksUpdated)
        assertEquals(2, report.assignmentsPreserved)
        assertEquals(0, report.assignmentsRemoved)
        assertEquals(0, fixture.weekStore.saveCount)

        val detail = report.weekDetails.single()
        assertEquals(0, detail.partsAdded)
        assertEquals(0, detail.partsRemoved)
        assertEquals(2, detail.partsKept)
        assertEquals(2, detail.assignmentsPreserved)
        assertEquals(0, detail.assignmentsRemoved)
    }

    @Test
    fun `apply refresh preserves only matching assignments and updates timestamp`() = runTest {
        val fixture = refreshFixture()
        val useCase = buildUseCase(fixture)

        val result = useCase(
            programId = fixture.program.id,
            referenceDate = LocalDate.of(2026, 3, 1),
            dryRun = false,
        )

        val report = assertIs<Either.Right<SchemaRefreshReport>>(result).value
        assertEquals(1, report.weeksUpdated)
        assertEquals(1, report.assignmentsPreserved)
        assertEquals(1, report.assignmentsRemoved)

        assertEquals(1, fixture.weekStore.saveCount)
        val savedAssignments = fixture.weekStore.currentAggregate.assignments
        assertEquals(1, savedAssignments.size)
        assertEquals(org.example.project.feature.people.domain.ProclamatoreId("p1"), savedAssignments.single().personId)
        assertEquals(1, fixture.programStore.templateAppliedUpdates.size)
    }

    @Test
    fun `only unassigned refresh preserves assigned removed parts`() = runTest {
        val fixture = refreshFixture()
        val useCase = buildUseCase(fixture)

        val result = useCase(
            programId = fixture.program.id,
            referenceDate = LocalDate.of(2026, 3, 1),
            dryRun = false,
            mode = SchemaRefreshMode.ONLY_UNASSIGNED,
        )

        val report = assertIs<Either.Right<SchemaRefreshReport>>(result).value
        assertEquals(1, report.weeksUpdated)
        assertEquals(2, report.assignmentsPreserved)
        assertEquals(0, report.assignmentsRemoved)

        val savedAggregate = fixture.weekStore.currentAggregate
        assertEquals(3, savedAggregate.weekPlan.parts.size)
        assertEquals(listOf("A", "C", "B"), savedAggregate.weekPlan.parts.map { it.partType.code })
        assertEquals(setOf("p1", "p2"), savedAggregate.assignments.map { it.personId.value }.toSet())
    }

    @Test
    fun `skipped weeks are not refreshed`() = runTest {
        val fixture = refreshFixture(weekStatus = WeekPlanStatus.SKIPPED)
        val useCase = buildUseCase(fixture)

        val result = useCase(
            programId = fixture.program.id,
            referenceDate = LocalDate.of(2026, 3, 1),
            dryRun = true,
        )

        val report = assertIs<Either.Right<SchemaRefreshReport>>(result).value
        assertEquals(0, report.weeksUpdated)
        assertEquals(0, report.assignmentsPreserved)
        assertEquals(0, report.assignmentsRemoved)
        assertTrue(report.weekDetails.isEmpty())
        assertEquals(0, fixture.weekStore.saveCount)
    }

    private fun buildUseCase(fixture: RefreshFixture): AggiornaProgrammaDaSchemiUseCase {
        return AggiornaProgrammaDaSchemiUseCase(
            programStore = fixture.programStore,
            weekPlanStore = fixture.weekStore,
            schemaTemplateStore = fixture.schemaStore,
            partTypeStore = fixture.partTypeStore,
            transactionRunner = PassthroughTransactionRunner,
        )
    }

    private fun localFixtureProgramMonth(
        yearMonth: YearMonth,
        id: String = "program-${yearMonth.year}-${yearMonth.monthValue}",
        templateAppliedAt: LocalDateTime? = null,
        createdAt: LocalDateTime = LocalDateTime.of(yearMonth.year, yearMonth.monthValue, 1, 9, 0),
    ): ProgramMonth = ProgramMonth(
        id = ProgramMonthId(id),
        year = yearMonth.year,
        month = yearMonth.monthValue,
        startDate = yearMonth.atDay(1).with(java.time.temporal.TemporalAdjusters.firstInMonth(java.time.DayOfWeek.MONDAY)),
        endDate = yearMonth.atEndOfMonth().with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY)),
        templateAppliedAt = templateAppliedAt,
        createdAt = createdAt,
    )

    private fun refreshFixture(
        weekStatus: WeekPlanStatus = WeekPlanStatus.ACTIVE,
        templatePartCodes: List<String> = listOf("A", "C"),
    ): RefreshFixture {
        val program = localFixtureProgramMonth(YearMonth.of(2026, 3), id = "program-refresh")
        val partA = PartType(PartTypeId("A"), "A", "Parte A", 1, SexRule.STESSO_SESSO, fixed = false, sortOrder = 1)
        val partB = PartType(PartTypeId("B"), "B", "Parte B", 1, SexRule.STESSO_SESSO, fixed = false, sortOrder = 2)
        val partC = PartType(PartTypeId("C"), "C", "Parte C", 1, SexRule.STESSO_SESSO, fixed = false, sortOrder = 3)
        val partsByCode = listOf(partA, partB, partC).associateBy { it.code }

        val week = WeekPlan(
            id = WeekPlanId("week-1"),
            weekStartDate = LocalDate.of(2026, 3, 2),
            parts = listOf(
                WeeklyPart(WeeklyPartId("old-a"), partA, sortOrder = 0),
                WeeklyPart(WeeklyPartId("old-b"), partB, sortOrder = 1),
            ),
            programId = program.id,
            status = weekStatus,
        )

        val assignments = listOf(
            Assignment(
                id = AssignmentId("as-1"),
                weeklyPartId = WeeklyPartId("old-a"),
                personId = org.example.project.feature.people.domain.ProclamatoreId("p1"),
                slot = 1,
            ),
            Assignment(
                id = AssignmentId("as-2"),
                weeklyPartId = WeeklyPartId("old-b"),
                personId = org.example.project.feature.people.domain.ProclamatoreId("p2"),
                slot = 1,
            ),
        )

        val schemaStore = RefreshSchemaTemplateStore(
            mapOf(
                LocalDate.of(2026, 3, 2) to StoredSchemaWeekTemplate(
                    weekStartDate = LocalDate.of(2026, 3, 2),
                    partTypeIds = templatePartCodes.map { code -> partsByCode.getValue(code).id },
                ),
            ),
        )

        val partTypeStore = RefreshPartTypeStore(
            partTypes = listOf(partA, partB, partC),
            fixedPart = null,
        )

        return RefreshFixture(
            program = program,
            programStore = RefreshProgramStore(program),
            weekStore = RefreshWeekStore(program.id, WeekPlanAggregate(week, assignments)),
            schemaStore = schemaStore,
            partTypeStore = partTypeStore,
        )
    }
}

private data class RefreshFixture(
    val program: ProgramMonth,
    val programStore: RefreshProgramStore,
    val weekStore: RefreshWeekStore,
    val schemaStore: SchemaTemplateStore,
    val partTypeStore: RefreshPartTypeStore,
)

private class RefreshProgramStore(
    private val program: ProgramMonth,
) : ProgramStore {
    val templateAppliedUpdates = mutableListOf<Pair<ProgramMonthId, LocalDateTime>>()

    override suspend fun listCurrentAndFuture(referenceDate: LocalDate): List<ProgramMonth> = listOf(program)

    override suspend fun findMostRecentPast(referenceDate: LocalDate): ProgramMonth? = null

    override suspend fun findById(id: ProgramMonthId): ProgramMonth? = if (id == program.id) program else null

    context(tx: TransactionScope)
    override suspend fun save(program: ProgramMonth) {
        // no-op
    }

    context(tx: TransactionScope)
    override suspend fun delete(id: ProgramMonthId) {
        // no-op
    }

    context(tx: TransactionScope)
    override suspend fun updateTemplateAppliedAt(id: ProgramMonthId, templateAppliedAt: LocalDateTime) {
        templateAppliedUpdates += id to templateAppliedAt
    }

    override suspend fun loadCreationContext(referenceDate: LocalDate): ProgramCreationContext {
        return ProgramCreationContext(setOf(program.yearMonth), futureMonths = emptySet())
    }
}

private class RefreshWeekStore(
    private val programId: ProgramMonthId,
    initialAggregate: WeekPlanAggregate,
) : TestWeekPlanStore() {
    var currentAggregate: WeekPlanAggregate = initialAggregate
        private set

    var saveCount: Int = 0
        private set

    override suspend fun listByProgram(programId: ProgramMonthId): List<WeekPlan> {
        if (programId != this.programId) return emptyList()
        return listOf(currentAggregate.weekPlan)
    }

    override suspend fun listAggregatesByProgram(programId: ProgramMonthId): List<WeekPlanAggregate> {
        if (programId != this.programId) return emptyList()
        return listOf(currentAggregate)
    }

    context(tx: TransactionScope)
    override suspend fun saveAggregate(aggregate: WeekPlanAggregate) {
        saveCount += 1
        currentAggregate = aggregate
    }

    override suspend fun loadAggregateByDate(weekStartDate: LocalDate): WeekPlanAggregate? =
        if (currentAggregate.weekPlan.weekStartDate == weekStartDate) currentAggregate else null

    override suspend fun loadAggregateById(weekPlanId: WeekPlanId): WeekPlanAggregate? =
        if (currentAggregate.weekPlan.id == weekPlanId) currentAggregate else null

    override suspend fun loadAggregateByDateAndProgram(
        weekStartDate: LocalDate,
        programId: ProgramMonthId,
    ): WeekPlanAggregate? {
        if (programId != this.programId) return null
        return if (currentAggregate.weekPlan.weekStartDate == weekStartDate) currentAggregate else null
    }
}

private class RefreshSchemaTemplateStore(
    private val templates: Map<LocalDate, StoredSchemaWeekTemplate>,
) : SchemaTemplateStore {
    context(tx: TransactionScope)
    override suspend fun replaceAll(templates: List<StoredSchemaWeekTemplate>) {
        // no-op
    }

    override suspend fun listAll(): List<StoredSchemaWeekTemplate> = templates.values.toList()

    override suspend fun findByWeekStartDate(weekStartDate: LocalDate): StoredSchemaWeekTemplate? = templates[weekStartDate]

    override suspend fun isEmpty(): Boolean = templates.isEmpty()
}

private class RefreshPartTypeStore(
    private val partTypes: List<PartType>,
    private val fixedPart: PartType?,
) : PartTypeStore {
    override suspend fun all(): List<PartType> = partTypes

    override suspend fun allWithStatus(): List<PartTypeWithStatus> = partTypes.map { PartTypeWithStatus(it, active = true) }

    override suspend fun findByCode(code: String): PartType? = partTypes.firstOrNull { it.code == code }

    override suspend fun findFixed(): PartType? = fixedPart

    context(tx: TransactionScope)
    override suspend fun upsertAll(partTypes: List<PartType>) {
        // no-op
    }

    override suspend fun getLatestRevisionId(partTypeId: PartTypeId): String? = null
}

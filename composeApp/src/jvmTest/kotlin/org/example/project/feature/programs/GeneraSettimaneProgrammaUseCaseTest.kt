package org.example.project.feature.programs

import arrow.core.Either
import kotlinx.coroutines.runBlocking
import org.example.project.core.persistence.TransactionRunner
import org.example.project.feature.programs.application.GeneraSettimaneProgrammaUseCase
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
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GeneraSettimaneProgrammaUseCaseTest {

    @Test
    fun `generates all weeks with template and fixed fallback, honoring skipped weeks`() = runBlocking {
        val program = fixtureProgramMonth(YearMonth.of(2026, 2), id = "program-1")
        val programStore = InMemoryProgramStoreGeneration(program)
        val templatePart = partType("tpl")
        val fixedPart = partType("fixed", fixed = true)
        val schemaStore = InMemorySchemaTemplateStore(
            mapOf(
                LocalDate.of(2026, 2, 2) to StoredSchemaWeekTemplate(
                    weekStartDate = LocalDate.of(2026, 2, 2),
                    partTypeIds = listOf(templatePart.id),
                ),
            ),
        )
        val partTypeStore = InMemoryPartTypeStore(
            partTypes = listOf(templatePart, fixedPart),
            fixedPart = fixedPart,
        )
        val weekStore = InMemoryWeekPlanStoreGeneration()
        val useCase = GeneraSettimaneProgrammaUseCase(
            programStore = programStore,
            weekPlanStore = weekStore,
            schemaTemplateStore = schemaStore,
            partTypeStore = partTypeStore,
            transactionRunner = ImmediateTransactionRunner(),
        )

        val result = useCase(
            programId = program.id.value,
            skippedWeeks = setOf(LocalDate.of(2026, 2, 9)),
        )

        assertIs<Either.Right<Unit>>(result)
        assertEquals(
            listOf(
                LocalDate.of(2026, 2, 2),
                LocalDate.of(2026, 2, 9),
                LocalDate.of(2026, 2, 16),
                LocalDate.of(2026, 2, 23),
            ),
            weekStore.createdWeeks.map { it.weekStartDate },
        )
        assertEquals(WeekPlanStatus.SKIPPED, weekStore.statusByWeekStart[LocalDate.of(2026, 2, 9)])
        assertEquals(WeekPlanStatus.ACTIVE, weekStore.statusByWeekStart[LocalDate.of(2026, 2, 2)])

        assertEquals(listOf(templatePart.id), weekStore.partTypeIdsByWeekStart[LocalDate.of(2026, 2, 2)])
        assertEquals(listOf(fixedPart.id), weekStore.partTypeIdsByWeekStart[LocalDate.of(2026, 2, 9)])
        assertEquals(listOf(fixedPart.id), weekStore.partTypeIdsByWeekStart[LocalDate.of(2026, 2, 16)])
        assertEquals(listOf(fixedPart.id), weekStore.partTypeIdsByWeekStart[LocalDate.of(2026, 2, 23)])
        assertEquals(1, programStore.templateAppliedUpdates.size)
    }
}

internal fun partType(code: String, fixed: Boolean = false): PartType {
    return PartType(
        id = PartTypeId("pt-$code"),
        code = code,
        label = code,
        peopleCount = 1,
        sexRule = SexRule.LIBERO,
        fixed = fixed,
        sortOrder = 1,
    )
}

internal class ImmediateTransactionRunner : TransactionRunner {
    override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
}

internal class InMemoryProgramStoreGeneration(
    private val program: ProgramMonth,
) : ProgramStore {
    val templateAppliedUpdates = mutableListOf<Pair<ProgramMonthId, LocalDateTime>>()

    override suspend fun listCurrentAndFuture(referenceDate: LocalDate): List<ProgramMonth> = listOf(program)

    override suspend fun findByYearMonth(year: Int, month: Int): ProgramMonth? {
        return if (program.year == year && program.month == month) program else null
    }

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
        return ProgramCreationContext(
            existingByMonth = setOf(program.yearMonth),
            hasCurrent = true,
            futureMonths = emptySet(),
        )
    }
}

internal class InMemorySchemaTemplateStore(
    private val templates: Map<LocalDate, StoredSchemaWeekTemplate>,
) : SchemaTemplateStore {
    override suspend fun replaceAll(templates: List<StoredSchemaWeekTemplate>) {
        // no-op
    }

    override suspend fun listAll(): List<StoredSchemaWeekTemplate> = templates.values.toList()

    override suspend fun findByWeekStartDate(weekStartDate: LocalDate): StoredSchemaWeekTemplate? = templates[weekStartDate]

    override suspend fun isEmpty(): Boolean = templates.isEmpty()
}

internal class InMemoryPartTypeStore(
    private val partTypes: List<PartType>,
    private val fixedPart: PartType?,
) : PartTypeStore {
    override suspend fun all(): List<PartType> = partTypes

    override suspend fun allWithStatus(): List<PartTypeWithStatus> = partTypes.map { PartTypeWithStatus(it, active = true) }

    override suspend fun findByCode(code: String): PartType? = partTypes.firstOrNull { it.code == code }

    override suspend fun findFixed(): PartType? = fixedPart

    override suspend fun upsertAll(partTypes: List<PartType>) {
        // no-op
    }

    override suspend fun deactivateMissingCodes(codes: Set<String>) {
        // no-op
    }
}

internal class InMemoryWeekPlanStoreGeneration(
    initialWeeksByProgram: Map<String, List<WeekPlan>> = emptyMap(),
) : WeekPlanStore {
    private val weeksByProgram = initialWeeksByProgram.mapValues { it.value.toMutableList() }.toMutableMap()

    val deletedPrograms = mutableListOf<String>()
    val createdWeeks = mutableListOf<WeekPlan>()
    val statusByWeekStart = mutableMapOf<LocalDate, WeekPlanStatus>()
    val partTypeIdsByWeekStart = mutableMapOf<LocalDate, List<PartTypeId>>()
    private val weekStartById = mutableMapOf<String, LocalDate>()

    override suspend fun findByDate(weekStartDate: LocalDate): WeekPlan? = null

    override suspend fun listInRange(startDate: LocalDate, endDate: LocalDate): List<WeekPlanSummary> = emptyList()

    override suspend fun totalSlotsByWeekInRange(startDate: LocalDate, endDate: LocalDate): Map<WeekPlanId, Int> = emptyMap()

    override suspend fun save(weekPlan: WeekPlan) {
        // no-op
    }

    override suspend fun delete(weekPlanId: WeekPlanId) {
        weeksByProgram.values.forEach { list -> list.removeIf { it.id == weekPlanId } }
    }

    override suspend fun addPart(weekPlanId: WeekPlanId, partTypeId: PartTypeId, sortOrder: Int): WeeklyPartId {
        return WeeklyPartId("part")
    }

    override suspend fun removePart(weeklyPartId: WeeklyPartId) {
        // no-op
    }

    override suspend fun updateSortOrders(parts: List<Pair<WeeklyPartId, Int>>) {
        // no-op
    }

    override suspend fun replaceAllParts(weekPlanId: WeekPlanId, partTypeIds: List<PartTypeId>) {
        val weekStart = weekStartById[weekPlanId.value] ?: return
        partTypeIdsByWeekStart[weekStart] = partTypeIds
    }

    override suspend fun saveWithProgram(weekPlan: WeekPlan, programId: String, status: WeekPlanStatus) {
        val stored = weekPlan.copy(programId = programId, status = status)
        weeksByProgram.getOrPut(programId) { mutableListOf() }.add(stored)
        weekStartById[weekPlan.id.value] = weekPlan.weekStartDate
        createdWeeks += stored
        statusByWeekStart[weekPlan.weekStartDate] = status
    }

    override suspend fun findByDateAndProgram(weekStartDate: LocalDate, programId: String): WeekPlan? {
        return weeksByProgram[programId]?.firstOrNull { it.weekStartDate == weekStartDate }
    }

    override suspend fun listByProgram(programId: String): List<WeekPlan> = weeksByProgram[programId].orEmpty()

    override suspend fun deleteByProgram(programId: String) {
        deletedPrograms += programId
        weeksByProgram[programId] = mutableListOf()
    }

    override suspend fun updateWeekStatus(weekPlanId: WeekPlanId, status: WeekPlanStatus) {
        // no-op
    }
}

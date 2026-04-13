package org.example.project.feature.programs

import arrow.core.Either
import kotlinx.coroutines.test.runTest
import org.example.project.core.PassthroughTransactionRunner
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionRunner
import org.example.project.core.persistence.TransactionScope
import org.example.project.feature.programs.application.GeneraSettimaneProgrammaUseCase
import org.example.project.feature.programs.application.ProgramCreationContext
import org.example.project.feature.programs.application.ProgramStore
import org.example.project.feature.programs.domain.ProgramMonth
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.schemas.application.StoredSchemaWeekTemplate
import org.example.project.feature.weeklyparts.domain.WeekPlanStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GeneraSettimaneProgrammaEdgeCaseTest {

    @Test
    fun `program not found returns Left NotFound`() = runTest {
        val programStore = NullProgramStore()
        val useCase = GeneraSettimaneProgrammaUseCase(
            programStore = programStore,
            weekPlanStore = InMemoryWeekPlanStoreGeneration(),
            schemaTemplateStore = InMemorySchemaTemplateStore(emptyMap()),
            partTypeStore = InMemoryPartTypeStore(partTypes = emptyList(), fixedPart = null),
            transactionRunner = PassthroughTransactionRunner,
        )

        val result = useCase(ProgramMonthId("non-existent"))

        assertIs<Either.Left<DomainError>>(result)
        assertIs<DomainError.NotFound>(result.value)
        assertEquals("Programma", (result.value as DomainError.NotFound).entity)
        Unit
    }

    @Test
    fun `program with no weeks in date range returns Right with empty weeks generated`() = runTest {
        // Create a program where startDate > endDate, so the while loop body never runs
        val program = ProgramMonth(
            id = ProgramMonthId("empty-program"),
            year = 2026,
            month = 3,
            startDate = LocalDate.of(2026, 3, 16), // Monday
            endDate = LocalDate.of(2026, 3, 15),   // before startDate → 0 weeks
            templateAppliedAt = null,
            createdAt = LocalDateTime.of(2026, 3, 1, 9, 0),
        )
        val programStore = InMemoryProgramStoreGeneration(program)
        val weekStore = InMemoryWeekPlanStoreGeneration()
        val useCase = GeneraSettimaneProgrammaUseCase(
            programStore = programStore,
            weekPlanStore = weekStore,
            schemaTemplateStore = InMemorySchemaTemplateStore(emptyMap()),
            partTypeStore = InMemoryPartTypeStore(partTypes = emptyList(), fixedPart = null),
            transactionRunner = PassthroughTransactionRunner,
        )

        val result = useCase(program.id)

        assertIs<Either.Right<Unit>>(result)
        assertTrue(weekStore.createdWeeks.isEmpty())
        assertEquals(1, programStore.templateAppliedUpdates.size)
        Unit
    }

    @Test
    fun `week with no template and no fixed part returns Left SettimanaSenzaTemplateENessunaParteFissa`() = runTest {
        val program = fixtureProgramMonth(
            YearMonth.of(2026, 3),
            id = "no-template-no-fixed",
            // March 2026: first Monday is Mar 2
            startDate = LocalDate.of(2026, 3, 2),
            endDate = LocalDate.of(2026, 3, 8),
        )
        val programStore = InMemoryProgramStoreGeneration(program)
        val useCase = GeneraSettimaneProgrammaUseCase(
            programStore = programStore,
            weekPlanStore = InMemoryWeekPlanStoreGeneration(),
            schemaTemplateStore = InMemorySchemaTemplateStore(emptyMap()),
            partTypeStore = InMemoryPartTypeStore(partTypes = emptyList(), fixedPart = null),
            transactionRunner = PassthroughTransactionRunner,
        )

        val result = useCase(program.id)

        assertIs<Either.Left<DomainError>>(result)
        val error = result.value
        assertIs<DomainError.SettimanaSenzaTemplateENessunaParteFissa>(error)
        assertEquals(LocalDate.of(2026, 3, 2), error.weekStartDate)
        Unit
    }

    @Test
    fun `week with no template but fixed part exists generates week with only the fixed part`() = runTest {
        val program = fixtureProgramMonth(
            YearMonth.of(2026, 3),
            id = "no-template-with-fixed",
            startDate = LocalDate.of(2026, 3, 2),
            endDate = LocalDate.of(2026, 3, 8), // single week
        )
        val fixedPart = partType("fisso", fixed = true)
        val programStore = InMemoryProgramStoreGeneration(program)
        val weekStore = InMemoryWeekPlanStoreGeneration()
        val useCase = GeneraSettimaneProgrammaUseCase(
            programStore = programStore,
            weekPlanStore = weekStore,
            schemaTemplateStore = InMemorySchemaTemplateStore(emptyMap()), // no templates
            partTypeStore = InMemoryPartTypeStore(partTypes = listOf(fixedPart), fixedPart = fixedPart),
            transactionRunner = PassthroughTransactionRunner,
        )

        val result = useCase(program.id)

        assertIs<Either.Right<Unit>>(result)
        assertEquals(1, weekStore.createdWeeks.size)
        assertEquals(LocalDate.of(2026, 3, 2), weekStore.createdWeeks.first().weekStartDate)
        assertEquals(listOf(fixedPart.id), weekStore.partTypeIdsByWeekStart[LocalDate.of(2026, 3, 2)])
        Unit
    }

    @Test
    fun `multiple weeks with mixed template and fixed fallback generates correctly`() = runTest {
        val program = fixtureProgramMonth(
            YearMonth.of(2026, 3),
            id = "mixed-weeks",
            startDate = LocalDate.of(2026, 3, 2),
            endDate = LocalDate.of(2026, 3, 22), // 3 weeks: Mar 2, 9, 16
        )
        val tplPart = partType("tpl")
        val fixedPart = partType("fixed", fixed = true)
        val programStore = InMemoryProgramStoreGeneration(program)
        val weekStore = InMemoryWeekPlanStoreGeneration()
        // Only week Mar 2 has a template; weeks Mar 9 and Mar 16 fall back to fixed
        val schemaStore = InMemorySchemaTemplateStore(
            mapOf(
                LocalDate.of(2026, 3, 2) to StoredSchemaWeekTemplate(
                    weekStartDate = LocalDate.of(2026, 3, 2),
                    partTypeIds = listOf(tplPart.id),
                ),
            ),
        )
        val useCase = GeneraSettimaneProgrammaUseCase(
            programStore = programStore,
            weekPlanStore = weekStore,
            schemaTemplateStore = schemaStore,
            partTypeStore = InMemoryPartTypeStore(partTypes = listOf(tplPart, fixedPart), fixedPart = fixedPart),
            transactionRunner = PassthroughTransactionRunner,
        )

        val result = useCase(program.id)

        assertIs<Either.Right<Unit>>(result)
        assertEquals(3, weekStore.createdWeeks.size)
        // Week with template
        assertEquals(listOf(tplPart.id), weekStore.partTypeIdsByWeekStart[LocalDate.of(2026, 3, 2)])
        // Weeks without template fall back to fixed part
        assertEquals(listOf(fixedPart.id), weekStore.partTypeIdsByWeekStart[LocalDate.of(2026, 3, 9)])
        assertEquals(listOf(fixedPart.id), weekStore.partTypeIdsByWeekStart[LocalDate.of(2026, 3, 16)])
        Unit
    }

    @Test
    fun `template referencing unknown partType returns Left NotFound`() = runTest {
        val program = fixtureProgramMonth(
            YearMonth.of(2026, 3),
            id = "unknown-pt",
            startDate = LocalDate.of(2026, 3, 2),
            endDate = LocalDate.of(2026, 3, 8),
        )
        val unknownPartTypeId = org.example.project.feature.weeklyparts.domain.PartTypeId("pt-unknown")
        val schemaStore = InMemorySchemaTemplateStore(
            mapOf(
                LocalDate.of(2026, 3, 2) to StoredSchemaWeekTemplate(
                    weekStartDate = LocalDate.of(2026, 3, 2),
                    partTypeIds = listOf(unknownPartTypeId),
                ),
            ),
        )
        val useCase = GeneraSettimaneProgrammaUseCase(
            programStore = InMemoryProgramStoreGeneration(program),
            weekPlanStore = InMemoryWeekPlanStoreGeneration(),
            schemaTemplateStore = schemaStore,
            partTypeStore = InMemoryPartTypeStore(partTypes = emptyList(), fixedPart = null),
            transactionRunner = PassthroughTransactionRunner,
        )

        val result = useCase(program.id)

        assertIs<Either.Left<DomainError>>(result)
        val error = result.value
        assertIs<DomainError.NotFound>(error)
        assertEquals("Tipo parte", error.entity)
        Unit
    }

    @Test
    fun `all weeks are ACTIVE when no skipped weeks specified`() = runTest {
        val program = fixtureProgramMonth(
            YearMonth.of(2026, 3),
            id = "all-active",
            startDate = LocalDate.of(2026, 3, 2),
            endDate = LocalDate.of(2026, 3, 15), // 2 weeks
        )
        val fixedPart = partType("fixed", fixed = true)
        val weekStore = InMemoryWeekPlanStoreGeneration()
        val useCase = GeneraSettimaneProgrammaUseCase(
            programStore = InMemoryProgramStoreGeneration(program),
            weekPlanStore = weekStore,
            schemaTemplateStore = InMemorySchemaTemplateStore(emptyMap()),
            partTypeStore = InMemoryPartTypeStore(partTypes = listOf(fixedPart), fixedPart = fixedPart),
            transactionRunner = PassthroughTransactionRunner,
        )

        val result = useCase(program.id)

        assertIs<Either.Right<Unit>>(result)
        assertEquals(2, weekStore.createdWeeks.size)
        assertTrue(weekStore.statusByWeekStart.values.all { it == WeekPlanStatus.ACTIVE })
        Unit
    }

    @Test
    fun `template with multiple part types generates week with correct sort order`() = runTest {
        val program = fixtureProgramMonth(
            YearMonth.of(2026, 3),
            id = "multi-part",
            startDate = LocalDate.of(2026, 3, 2),
            endDate = LocalDate.of(2026, 3, 8),
        )
        val partA = partType("a")
        val partB = partType("b")
        val partC = partType("c")
        val schemaStore = InMemorySchemaTemplateStore(
            mapOf(
                LocalDate.of(2026, 3, 2) to StoredSchemaWeekTemplate(
                    weekStartDate = LocalDate.of(2026, 3, 2),
                    partTypeIds = listOf(partA.id, partB.id, partC.id),
                ),
            ),
        )
        val weekStore = InMemoryWeekPlanStoreGeneration()
        val useCase = GeneraSettimaneProgrammaUseCase(
            programStore = InMemoryProgramStoreGeneration(program),
            weekPlanStore = weekStore,
            schemaTemplateStore = schemaStore,
            partTypeStore = InMemoryPartTypeStore(partTypes = listOf(partA, partB, partC), fixedPart = null),
            transactionRunner = PassthroughTransactionRunner,
        )

        val result = useCase(program.id)

        assertIs<Either.Right<Unit>>(result)
        val week = weekStore.createdWeeks.single()
        assertEquals(3, week.parts.size)
        assertEquals(listOf(0, 1, 2), week.parts.map { it.sortOrder })
        assertEquals(listOf(partA.id, partB.id, partC.id), week.parts.map { it.partType.id })
        Unit
    }

    @Test
    fun `error on second week still returns Left without saving anything`() = runTest {
        // First week has a template, second week has no template and no fixed part → error
        val program = fixtureProgramMonth(
            YearMonth.of(2026, 3),
            id = "fail-second-week",
            startDate = LocalDate.of(2026, 3, 2),
            endDate = LocalDate.of(2026, 3, 15), // 2 weeks: Mar 2, Mar 9
        )
        val tplPart = partType("tpl")
        val schemaStore = InMemorySchemaTemplateStore(
            mapOf(
                LocalDate.of(2026, 3, 2) to StoredSchemaWeekTemplate(
                    weekStartDate = LocalDate.of(2026, 3, 2),
                    partTypeIds = listOf(tplPart.id),
                ),
                // No template for Mar 9 → and fixedPart is null → error
            ),
        )
        val weekStore = InMemoryWeekPlanStoreGeneration()
        val useCase = GeneraSettimaneProgrammaUseCase(
            programStore = InMemoryProgramStoreGeneration(program),
            weekPlanStore = weekStore,
            schemaTemplateStore = schemaStore,
            partTypeStore = InMemoryPartTypeStore(partTypes = listOf(tplPart), fixedPart = null),
            transactionRunner = PassthroughTransactionRunner,
        )

        val result = useCase(program.id)

        assertIs<Either.Left<DomainError>>(result)
        val error = result.value
        assertIs<DomainError.SettimanaSenzaTemplateENessunaParteFissa>(error)
        assertEquals(LocalDate.of(2026, 3, 9), error.weekStartDate)
        // Nothing should have been saved because the either block short-circuited
        assertTrue(weekStore.createdWeeks.isEmpty())
        Unit
    }

    @Test
    fun `templateAppliedAt is updated after successful generation`() = runTest {
        val program = fixtureProgramMonth(
            YearMonth.of(2026, 3),
            id = "template-ts",
            startDate = LocalDate.of(2026, 3, 2),
            endDate = LocalDate.of(2026, 3, 8),
        )
        val fixedPart = partType("fixed", fixed = true)
        val programStore = InMemoryProgramStoreGeneration(program)
        val useCase = GeneraSettimaneProgrammaUseCase(
            programStore = programStore,
            weekPlanStore = InMemoryWeekPlanStoreGeneration(),
            schemaTemplateStore = InMemorySchemaTemplateStore(emptyMap()),
            partTypeStore = InMemoryPartTypeStore(partTypes = listOf(fixedPart), fixedPart = fixedPart),
            transactionRunner = PassthroughTransactionRunner,
        )

        val result = useCase(program.id)

        assertIs<Either.Right<Unit>>(result)
        assertEquals(1, programStore.templateAppliedUpdates.size)
        assertEquals(program.id, programStore.templateAppliedUpdates.first().first)
        Unit
    }

    @Test
    fun `templateAppliedAt is NOT updated when generation fails`() = runTest {
        val program = fixtureProgramMonth(
            YearMonth.of(2026, 3),
            id = "no-ts-on-error",
            startDate = LocalDate.of(2026, 3, 2),
            endDate = LocalDate.of(2026, 3, 8),
        )
        val programStore = InMemoryProgramStoreGeneration(program)
        val useCase = GeneraSettimaneProgrammaUseCase(
            programStore = programStore,
            weekPlanStore = InMemoryWeekPlanStoreGeneration(),
            schemaTemplateStore = InMemorySchemaTemplateStore(emptyMap()),
            partTypeStore = InMemoryPartTypeStore(partTypes = emptyList(), fixedPart = null),
            transactionRunner = PassthroughTransactionRunner,
        )

        val result = useCase(program.id)

        assertIs<Either.Left<DomainError>>(result)
        assertTrue(programStore.templateAppliedUpdates.isEmpty())
        Unit
    }

    @Test
    fun `all generated weeks have programId set`() = runTest {
        val program = fixtureProgramMonth(
            YearMonth.of(2026, 3),
            id = "prog-id-check",
            startDate = LocalDate.of(2026, 3, 2),
            endDate = LocalDate.of(2026, 3, 15), // 2 weeks
        )
        val fixedPart = partType("fixed", fixed = true)
        val weekStore = InMemoryWeekPlanStoreGeneration()
        val useCase = GeneraSettimaneProgrammaUseCase(
            programStore = InMemoryProgramStoreGeneration(program),
            weekPlanStore = weekStore,
            schemaTemplateStore = InMemorySchemaTemplateStore(emptyMap()),
            partTypeStore = InMemoryPartTypeStore(partTypes = listOf(fixedPart), fixedPart = fixedPart),
            transactionRunner = PassthroughTransactionRunner,
        )

        val result = useCase(program.id)

        assertIs<Either.Right<Unit>>(result)
        assertTrue(weekStore.createdWeeks.all { it.programId == program.id })
        Unit
    }

    @Test
    fun `DB error during save propagates as exception`() = runTest {
        val program = fixtureProgramMonth(
            YearMonth.of(2026, 3),
            id = "db-error",
            startDate = LocalDate.of(2026, 3, 2),
            endDate = LocalDate.of(2026, 3, 8),
        )
        val fixedPart = partType("fixed", fixed = true)
        val failingTransactionRunner = object : TransactionRunner {
            override suspend fun <T> runInTransaction(block: suspend TransactionScope.() -> T): T {
                throw RuntimeException("DB connection failed")
            }
        }
        val useCase = GeneraSettimaneProgrammaUseCase(
            programStore = InMemoryProgramStoreGeneration(program),
            weekPlanStore = InMemoryWeekPlanStoreGeneration(),
            schemaTemplateStore = InMemorySchemaTemplateStore(emptyMap()),
            partTypeStore = InMemoryPartTypeStore(partTypes = listOf(fixedPart), fixedPart = fixedPart),
            transactionRunner = failingTransactionRunner,
        )

        val result = useCase(program.id)
        assertIs<Either.Left<*>>(result)
        Unit
    }
}

/** A ProgramStore that always returns null for findById. */
private class NullProgramStore : ProgramStore {
    override suspend fun listCurrentAndFuture(referenceDate: LocalDate): List<ProgramMonth> = emptyList()
    override suspend fun findMostRecentPast(referenceDate: LocalDate): ProgramMonth? = null
    override suspend fun findById(id: ProgramMonthId): ProgramMonth? = null
    context(tx: TransactionScope) override suspend fun save(program: ProgramMonth) {}
    context(tx: TransactionScope) override suspend fun delete(id: ProgramMonthId) {}
    context(tx: TransactionScope) override suspend fun updateTemplateAppliedAt(id: ProgramMonthId, templateAppliedAt: LocalDateTime) {}
    override suspend fun loadCreationContext(referenceDate: LocalDate): ProgramCreationContext =
        ProgramCreationContext(existingByMonth = emptySet(), futureMonths = emptySet())
}

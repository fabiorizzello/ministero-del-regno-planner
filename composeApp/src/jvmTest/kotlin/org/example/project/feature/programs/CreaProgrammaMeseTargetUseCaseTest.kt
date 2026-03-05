package org.example.project.feature.programs

import arrow.core.Either
import kotlinx.coroutines.runBlocking
import org.example.project.feature.programs.application.CreaProssimoProgrammaUseCase
import org.example.project.feature.programs.application.ProgramStore
import org.example.project.feature.programs.domain.ProgramMonth
import org.example.project.feature.programs.domain.ProgramMonthId
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CreaProgrammaMeseTargetUseCaseTest {

    @Test
    fun `blocks target outside current plus two window`() = runBlocking {
        val referenceDate = LocalDate.of(2026, 2, 10)
        val store = InMemoryProgramStore()
        val useCase = CreaProssimoProgrammaUseCase(store)

        val result = useCase(2026, 5, referenceDate)

        assertTrue(result.isLeft())
    }

    @Test
    fun `blocks current plus two when current plus one is missing`() = runBlocking {
        val referenceDate = LocalDate.of(2026, 2, 10)
        val store = InMemoryProgramStore()
        val useCase = CreaProssimoProgrammaUseCase(store)

        val result = useCase(2026, 4, referenceDate)

        assertTrue(result.isLeft())
    }

    @Test
    fun `allows first creation as current plus one when current is missing`() = runBlocking {
        val referenceDate = LocalDate.of(2026, 2, 10)
        val store = InMemoryProgramStore()
        val useCase = CreaProssimoProgrammaUseCase(store)

        val result = useCase(2026, 3, referenceDate)

        val created = assertIs<Either.Right<ProgramMonth>>(result).value
        assertEquals(2026, created.year)
        assertEquals(3, created.month)
        assertEquals(1, store.programs.size)
    }

    @Test
    fun `allows current month creation when no programs exist`() = runBlocking {
        val referenceDate = LocalDate.of(2026, 2, 10)
        val store = InMemoryProgramStore()
        val useCase = CreaProssimoProgrammaUseCase(store)

        val result = useCase(2026, 2, referenceDate)

        val created = assertIs<Either.Right<ProgramMonth>>(result).value
        assertEquals(2026, created.year)
        assertEquals(2, created.month)
        assertEquals(1, store.programs.size)
    }

    @Test
    fun `allows current month backfill after creating current plus one`() = runBlocking {
        val referenceDate = LocalDate.of(2026, 2, 10)
        val store = InMemoryProgramStore(
            programs = mutableListOf(fixtureProgramMonth(YearMonth.of(2026, 3))),
        )
        val useCase = CreaProssimoProgrammaUseCase(store)

        val result = useCase(2026, 2, referenceDate)

        val created = assertIs<Either.Right<ProgramMonth>>(result).value
        assertEquals(2, created.month)
    }

    @Test
    fun `allows current plus two when current plus one exists`() = runBlocking {
        val referenceDate = LocalDate.of(2026, 2, 10)
        val store = InMemoryProgramStore(
            programs = mutableListOf(fixtureProgramMonth(YearMonth.of(2026, 3))),
        )
        val useCase = CreaProssimoProgrammaUseCase(store)

        val result = useCase(2026, 4, referenceDate)

        val created = assertIs<Either.Right<ProgramMonth>>(result).value
        assertEquals(4, created.month)
    }

    @Test
    fun `allows current backfill even when two future programs already exist`() = runBlocking {
        val referenceDate = LocalDate.of(2026, 2, 10)
        val store = InMemoryProgramStore(
            programs = mutableListOf(
                fixtureProgramMonth(YearMonth.of(2026, 3)),
                fixtureProgramMonth(YearMonth.of(2026, 4)),
            ),
        )
        val useCase = CreaProssimoProgrammaUseCase(store)

        val result = useCase(2026, 2, referenceDate)

        val created = assertIs<Either.Right<ProgramMonth>>(result).value
        assertEquals(2, created.month)
    }
}

private class InMemoryProgramStore(
    val programs: MutableList<ProgramMonth> = mutableListOf(),
) : ProgramStore {

    override suspend fun listCurrentAndFuture(referenceDate: LocalDate): List<ProgramMonth> =
        programs.sortedBy { it.yearMonth }

    override suspend fun findByYearMonth(year: Int, month: Int): ProgramMonth? =
        programs.firstOrNull { it.year == year && it.month == month }

    override suspend fun findById(id: ProgramMonthId): ProgramMonth? =
        programs.firstOrNull { it.id == id }

    override suspend fun save(program: ProgramMonth) {
        programs.add(program)
    }

    override suspend fun delete(id: ProgramMonthId) {
        programs.removeIf { it.id == id }
    }

    override suspend fun updateTemplateAppliedAt(id: ProgramMonthId, templateAppliedAt: LocalDateTime) {
        // no-op for tests
    }
}

package org.example.project.feature.programs

import arrow.core.getOrElse
import kotlinx.coroutines.runBlocking
import org.example.project.feature.programs.application.CaricaProgrammiAttiviUseCase
import org.example.project.feature.programs.application.ProgramStore
import org.example.project.feature.programs.domain.ProgramMonth
import org.example.project.feature.programs.domain.ProgramMonthId
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

class CaricaProgrammiAttiviUseCaseTest {

    @Test
    fun `returns current and max two future programs sorted chronologically`() = runBlocking {
        val referenceDate = LocalDate.of(2026, 2, 12)
        val store = SnapshotStore(
            programs = mutableListOf(
                fixtureProgramMonth(YearMonth.of(2026, 2), id = "current"),
                fixtureProgramMonth(YearMonth.of(2026, 4), id = "future-2"),
                fixtureProgramMonth(YearMonth.of(2026, 3), id = "future-1"),
                fixtureProgramMonth(YearMonth.of(2026, 5), id = "future-3"),
            ),
        )
        val useCase = CaricaProgrammiAttiviUseCase(store)

        val snapshot = useCase(referenceDate).getOrElse { fail("Expected Right but got Left: $it") }

        assertEquals("current", snapshot.current?.id?.value)
        assertEquals(listOf("future-1", "future-2"), snapshot.futures.map { it.id.value })
    }

    @Test
    fun `returns null current when only future programs exist`() = runBlocking {
        val referenceDate = LocalDate.of(2026, 2, 12)
        val store = SnapshotStore(
            programs = mutableListOf(
                fixtureProgramMonth(YearMonth.of(2026, 3), id = "future-1"),
            ),
        )
        val useCase = CaricaProgrammiAttiviUseCase(store)

        val snapshot = useCase(referenceDate).getOrElse { fail("Expected Right but got Left: $it") }

        assertNull(snapshot.current)
        assertEquals(listOf("future-1"), snapshot.futures.map { it.id.value })
    }
}

private class SnapshotStore(
    val programs: MutableList<ProgramMonth> = mutableListOf(),
) : ProgramStore {
    override suspend fun listCurrentAndFuture(referenceDate: LocalDate): List<ProgramMonth> =
        programs.sortedBy { it.startDate }

    override suspend fun findById(id: ProgramMonthId): ProgramMonth? =
        programs.firstOrNull { it.id == id }

    override suspend fun save(program: ProgramMonth) {
        programs.add(program)
    }

    override suspend fun delete(id: ProgramMonthId) {
        programs.removeIf { it.id == id }
    }

    override suspend fun updateTemplateAppliedAt(id: ProgramMonthId, templateAppliedAt: LocalDateTime) {
        // no-op
    }
}

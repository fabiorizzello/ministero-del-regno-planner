package org.example.project.feature.programs

import arrow.core.Either
import kotlinx.coroutines.runBlocking
import org.example.project.core.domain.DomainError
import org.example.project.core.PassthroughTransactionRunner
import org.example.project.core.persistence.TransactionScope
import org.example.project.feature.programs.application.EliminaProgrammaUseCase
import org.example.project.feature.programs.application.ProgramStore
import org.example.project.feature.programs.domain.ProgramMonth
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.weeklyparts.TestWeekPlanStore
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeekPlanStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EliminaProgrammaUseCaseTest {

    @Test
    fun `allows deleting current program`() {
        runBlocking {
            val referenceDate = LocalDate.of(2026, 2, 12)
            val program = fixtureProgramMonth(YearMonth.of(2026, 2), id = "current")
            val programStore = DeleteProgramStore(program)
            val weekStore = DeleteWeekStore(program.id)
            val useCase = EliminaProgrammaUseCase(programStore, weekStore, PassthroughTransactionRunner)

            val result = useCase(program.id, referenceDate)

            assertIs<Either.Right<Unit>>(result)
            assertEquals(listOf(program.id), programStore.deletedPrograms)
            assertEquals(2, weekStore.deletedWeeks.size)
        }
    }

    @Test
    fun `allows deleting future program`() {
        runBlocking {
            val referenceDate = LocalDate.of(2026, 2, 12)
            val program = fixtureProgramMonth(YearMonth.of(2026, 3), id = "future")
            val programStore = DeleteProgramStore(program)
            val weekStore = DeleteWeekStore(program.id)
            val useCase = EliminaProgrammaUseCase(programStore, weekStore, PassthroughTransactionRunner)

            val result = useCase(program.id, referenceDate)

            assertIs<Either.Right<Unit>>(result)
        }
    }

    @Test
    fun `blocks deleting past program`() {
        runBlocking {
            val referenceDate = LocalDate.of(2026, 2, 12)
            val program = fixtureProgramMonth(YearMonth.of(2026, 1), id = "past")
            val programStore = DeleteProgramStore(program)
            val weekStore = DeleteWeekStore(program.id)
            val useCase = EliminaProgrammaUseCase(programStore, weekStore, PassthroughTransactionRunner)

            val result = useCase(program.id, referenceDate)

            val left = assertIs<Either.Left<DomainError>>(result).value
            assertEquals(DomainError.ProgrammaPassatoNonEliminabile, left)
            assertTrue(programStore.deletedPrograms.isEmpty())
        }
    }
}

private class DeleteProgramStore(
    private val program: ProgramMonth,
) : ProgramStore {
    val deletedPrograms = mutableListOf<ProgramMonthId>()

    override suspend fun listCurrentAndFuture(referenceDate: LocalDate): List<ProgramMonth> = listOf(program)

    override suspend fun findById(id: ProgramMonthId): ProgramMonth? = if (id == program.id) program else null

    override suspend fun save(program: ProgramMonth) {
        // no-op
    }

    override suspend fun delete(id: ProgramMonthId) {
        deletedPrograms.add(id)
    }

    override suspend fun updateTemplateAppliedAt(id: ProgramMonthId, templateAppliedAt: LocalDateTime) {
        // no-op
    }
}

private class DeleteWeekStore(
    private val programId: ProgramMonthId,
) : TestWeekPlanStore() {
    val deletedWeeks = mutableListOf<WeekPlanId>()

    override suspend fun listByProgram(programId: ProgramMonthId): List<WeekPlan> {
        if (programId != this.programId) return emptyList()
        return listOf(
            WeekPlan(
                id = WeekPlanId("w1"),
                weekStartDate = LocalDate.of(2026, 2, 2),
                parts = emptyList(),
                programId = programId,
                status = WeekPlanStatus.ACTIVE,
            ),
            WeekPlan(
                id = WeekPlanId("w2"),
                weekStartDate = LocalDate.of(2026, 2, 9),
                parts = emptyList(),
                programId = programId,
                status = WeekPlanStatus.ACTIVE,
            ),
        )
    }

    context(tx: TransactionScope)
    override suspend fun deleteByProgram(programId: ProgramMonthId) {
        deletedWeeks += listByProgram(programId).map { week -> week.id }
    }
}

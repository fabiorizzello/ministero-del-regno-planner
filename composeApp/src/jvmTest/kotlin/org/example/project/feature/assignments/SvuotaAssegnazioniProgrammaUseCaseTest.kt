package org.example.project.feature.assignments

import kotlinx.coroutines.test.runTest
import org.example.project.core.PassthroughTransactionRunner
import org.example.project.core.persistence.TransactionScope
import org.example.project.feature.assignments.application.AssignmentRepository
import org.example.project.feature.assignments.application.SvuotaAssegnazioniProgrammaUseCase
import org.example.project.feature.assignments.domain.AssignmentWithPerson
import org.example.project.feature.assignments.domain.Assignment
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class SvuotaAssegnazioniProgrammaUseCaseTest {

    private val programId = ProgramMonthId("prog-1")

    // Simulates assignments stored for a program, partitioned by date
    private fun repositoryWith(entries: List<Pair<LocalDate, Assignment>>): AssignmentRepository {
        return object : FakeSvuotaAssignmentRepository() {
            private val stored = entries.toMutableList()

            override suspend fun countByProgramFromDate(programId: ProgramMonthId, fromDate: LocalDate): Int =
                stored.count { (date, _) -> date >= fromDate }

            context(tx: TransactionScope) override suspend fun deleteByProgramFromDate(programId: ProgramMonthId, fromDate: LocalDate): Int {
                val toRemove = stored.filter { (date, _) -> date >= fromDate }
                stored.removeAll(toRemove)
                return toRemove.size
            }
        }
    }

    private fun assignment(id: String): Assignment = Assignment(
        id = AssignmentId(id),
        weeklyPartId = WeeklyPartId("wp-1"),
        personId = ProclamatoreId("p1"),
        slot = 1,
    )

    @Test
    fun `count returns correct number of assignments from fromDate`() = runTest {
        val fromDate = LocalDate.of(2026, 3, 9)
        val repo = repositoryWith(
            listOf(
                LocalDate.of(2026, 3, 2) to assignment("a1"),
                LocalDate.of(2026, 3, 9) to assignment("a2"),
                LocalDate.of(2026, 3, 16) to assignment("a3"),
            ),
        )
        val useCase = SvuotaAssegnazioniProgrammaUseCase(repo, PassthroughTransactionRunner)

        val count = useCase.count(programId, fromDate)

        assertEquals(2, count)
    }

    @Test
    fun `count with future fromDate excludes past assignments`() = runTest {
        val fromDate = LocalDate.of(2026, 4, 1)
        val repo = repositoryWith(
            listOf(
                LocalDate.of(2026, 3, 2) to assignment("a1"),
                LocalDate.of(2026, 3, 9) to assignment("a2"),
            ),
        )
        val useCase = SvuotaAssegnazioniProgrammaUseCase(repo, PassthroughTransactionRunner)

        val count = useCase.count(programId, fromDate)

        assertEquals(0, count)
    }

    @Test
    fun `execute removes only assignments on or after fromDate and preserves earlier ones`() = runTest {
        val fromDate = LocalDate.of(2026, 3, 9)
        val earlyDate = LocalDate.of(2026, 3, 2)
        val stored = mutableListOf(
            earlyDate to assignment("a1"),
            fromDate to assignment("a2"),
            LocalDate.of(2026, 3, 16) to assignment("a3"),
        )
        val repo = object : FakeSvuotaAssignmentRepository() {
            override suspend fun countByProgramFromDate(programId: ProgramMonthId, fromDate: LocalDate): Int =
                stored.count { (date, _) -> date >= fromDate }

            context(tx: TransactionScope) override suspend fun deleteByProgramFromDate(programId: ProgramMonthId, fromDate: LocalDate): Int {
                val toRemove = stored.filter { (date, _) -> date >= fromDate }
                stored.removeAll(toRemove)
                return toRemove.size
            }
        }
        val useCase = SvuotaAssegnazioniProgrammaUseCase(repo, PassthroughTransactionRunner)

        val result = useCase.execute(programId, fromDate)

        assertEquals(2, result.getOrNull())
        // Remaining: only the one before fromDate
        assertEquals(1, repo.countByProgramFromDate(programId, earlyDate))
        assertEquals(0, repo.countByProgramFromDate(programId, fromDate))
    }

    @Test
    fun `execute on program with no assignments returns 0 without error`() = runTest {
        val repo = repositoryWith(emptyList())
        val useCase = SvuotaAssegnazioniProgrammaUseCase(repo, PassthroughTransactionRunner)

        val result = useCase.execute(programId, LocalDate.of(2026, 3, 1))

        assertEquals(0, result.getOrNull())
    }
}

private open class FakeSvuotaAssignmentRepository : AssignmentRepository {
    override suspend fun listByWeek(weekPlanId: WeekPlanId): List<AssignmentWithPerson> = emptyList()
    override suspend fun listByWeekPlanIds(weekPlanIds: Set<WeekPlanId>): Map<WeekPlanId, List<AssignmentWithPerson>> = emptyMap()
    context(tx: TransactionScope) override suspend fun save(assignment: Assignment) {}
    context(tx: TransactionScope) override suspend fun remove(assignmentId: AssignmentId) {}
    context(tx: TransactionScope) override suspend fun removeAllByWeekPlan(weekPlanId: WeekPlanId) {}
    override suspend fun countAssignmentsForWeek(weekPlanId: WeekPlanId): Int = 0
    override suspend fun countAssignmentsByWeekInRange(startDate: LocalDate, endDate: LocalDate): Map<WeekPlanId, Int> = emptyMap()
    context(tx: TransactionScope) override suspend fun deleteByProgramFromDate(programId: ProgramMonthId, fromDate: LocalDate): Int = 0
    override suspend fun countByProgramFromDate(programId: ProgramMonthId, fromDate: LocalDate): Int = 0
}

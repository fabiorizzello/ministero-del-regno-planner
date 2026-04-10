package org.example.project.feature.output.application

import arrow.core.getOrElse
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.example.project.core.persistence.TransactionScope
import org.example.project.feature.assignments.application.AssignmentRepository
import org.example.project.feature.assignments.domain.Assignment
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.assignments.domain.AssignmentWithPerson
import org.example.project.feature.output.domain.ProgramDeliverySnapshot
import org.example.project.feature.output.domain.SlipDelivery
import org.example.project.feature.output.domain.SlipDeliveryId
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.domain.Sesso
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.weeklyparts.application.WeekPlanQueries
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.SexRule
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeekPlanStatus
import org.example.project.feature.weeklyparts.domain.WeekPlanSummary
import org.example.project.feature.weeklyparts.domain.WeeklyPart
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CaricaRiepilogoConsegneProgrammaUseCaseTest {

    private val programId = ProgramMonthId("prog-2026-03")
    private val referenceDate = LocalDate.of(2026, 3, 9) // Monday

    private val mario = Proclamatore(
        id = ProclamatoreId("p-1"),
        nome = "Mario",
        cognome = "Rossi",
        sesso = Sesso.M,
    )
    // --- Test 1: Complete parts without delivery are pending ---

    @Test
    fun `complete parts without delivery are pending`() = runTest {
        val part = weeklyPart("wp1", "pt-1", peopleCount = 1)
        val week = weekPlan("w1", LocalDate.of(2026, 3, 9), listOf(part))

        val weekQueries = FakeWeekPlanQueries(listOf(week))
        val assignmentRepo = FakeAssignmentRepo(
            mapOf(week.id to listOf(assignment("a1", part.id, mario, slot = 1)))
        )
        val deliveryStore = FakeSlipDeliveryStore()

        val useCase = CaricaRiepilogoConsegneProgrammaUseCase(weekQueries, assignmentRepo, deliveryStore)
        val result = useCase(programId, referenceDate).getOrElse { error("unexpected: $it") }

        assertEquals(1, result.pending)
        assertEquals(0, result.blocked)
        assertFalse(result.allDelivered)
    }

    // --- Test 2: Incomplete parts are blocked ---

    @Test
    fun `incomplete parts are blocked`() = runTest {
        val part = weeklyPart("wp1", "pt-1", peopleCount = 2) // needs 2 assignments
        val week = weekPlan("w1", LocalDate.of(2026, 3, 9), listOf(part))

        val weekQueries = FakeWeekPlanQueries(listOf(week))
        // only 1 assignment for a part that needs 2
        val assignmentRepo = FakeAssignmentRepo(
            mapOf(week.id to listOf(assignment("a1", part.id, mario, slot = 1)))
        )
        val deliveryStore = FakeSlipDeliveryStore()

        val useCase = CaricaRiepilogoConsegneProgrammaUseCase(weekQueries, assignmentRepo, deliveryStore)
        val result = useCase(programId, referenceDate).getOrElse { error("unexpected: $it") }

        assertEquals(0, result.pending)
        assertEquals(1, result.blocked)
        assertFalse(result.allDelivered)
    }

    // --- Test 3: Delivered parts are not pending (allDelivered) ---

    @Test
    fun `delivered parts are not pending and allDelivered is true`() = runTest {
        val part = weeklyPart("wp1", "pt-1", peopleCount = 1)
        val week = weekPlan("w1", LocalDate.of(2026, 3, 9), listOf(part))

        val weekQueries = FakeWeekPlanQueries(listOf(week))
        val assignmentRepo = FakeAssignmentRepo(
            mapOf(week.id to listOf(assignment("a1", part.id, mario, slot = 1)))
        )
        val deliveryStore = FakeSlipDeliveryStore().apply {
            activeDeliveries[part.id to week.id] = SlipDelivery(
                id = SlipDeliveryId("d1"),
                weeklyPartId = part.id,
                weekPlanId = week.id,
                studentName = "Mario Rossi",
                assistantName = null,
                sentAt = Instant.parse("2026-03-10T10:00:00Z"),
                cancelledAt = null,
            )
        }

        val useCase = CaricaRiepilogoConsegneProgrammaUseCase(weekQueries, assignmentRepo, deliveryStore)
        val result = useCase(programId, referenceDate).getOrElse { error("unexpected: $it") }

        assertEquals(0, result.pending)
        assertEquals(0, result.blocked)
        assertTrue(result.allDelivered)
    }

    // --- Test 4: Past weeks are excluded ---

    @Test
    fun `past weeks are excluded`() = runTest {
        val part = weeklyPart("wp1", "pt-1", peopleCount = 1)
        // week is before referenceDate
        val pastWeek = weekPlan("w1", LocalDate.of(2026, 3, 2), listOf(part))

        val weekQueries = FakeWeekPlanQueries(listOf(pastWeek))
        val assignmentRepo = FakeAssignmentRepo(
            mapOf(pastWeek.id to listOf(assignment("a1", part.id, mario, slot = 1)))
        )
        val deliveryStore = FakeSlipDeliveryStore()

        val useCase = CaricaRiepilogoConsegneProgrammaUseCase(weekQueries, assignmentRepo, deliveryStore)
        val result = useCase(programId, referenceDate).getOrElse { error("unexpected: $it") }

        assertEquals(ProgramDeliverySnapshot(pending = 0, blocked = 0), result)
        assertTrue(result.allDelivered)
    }

    // --- Test 5: Skipped weeks are excluded ---

    @Test
    fun `skipped weeks are excluded`() = runTest {
        val part = weeklyPart("wp1", "pt-1", peopleCount = 1)
        val skippedWeek = weekPlan("w1", LocalDate.of(2026, 3, 9), listOf(part), status = WeekPlanStatus.SKIPPED)

        val weekQueries = FakeWeekPlanQueries(listOf(skippedWeek))
        val assignmentRepo = FakeAssignmentRepo(
            mapOf(skippedWeek.id to listOf(assignment("a1", part.id, mario, slot = 1)))
        )
        val deliveryStore = FakeSlipDeliveryStore()

        val useCase = CaricaRiepilogoConsegneProgrammaUseCase(weekQueries, assignmentRepo, deliveryStore)
        val result = useCase(programId, referenceDate).getOrElse { error("unexpected: $it") }

        assertEquals(ProgramDeliverySnapshot(pending = 0, blocked = 0), result)
        assertTrue(result.allDelivered)
    }

    // --- Test 6: No weeks returns zero snapshot ---

    @Test
    fun `no weeks returns zero snapshot`() = runTest {
        val weekQueries = FakeWeekPlanQueries(emptyList())
        val assignmentRepo = FakeAssignmentRepo(emptyMap())
        val deliveryStore = FakeSlipDeliveryStore()

        val useCase = CaricaRiepilogoConsegneProgrammaUseCase(weekQueries, assignmentRepo, deliveryStore)
        val result = useCase(programId, referenceDate).getOrElse { error("unexpected: $it") }

        assertEquals(ProgramDeliverySnapshot(pending = 0, blocked = 0), result)
        assertTrue(result.allDelivered)
    }

    // --- Helpers ---

    private fun weeklyPart(
        partId: String,
        partTypeId: String,
        peopleCount: Int,
        sortOrder: Int = 0,
    ) = WeeklyPart(
        id = WeeklyPartId(partId),
        partType = PartType(
            id = PartTypeId(partTypeId),
            code = partTypeId.uppercase(),
            label = "Parte $partTypeId",
            peopleCount = peopleCount,
            sexRule = SexRule.STESSO_SESSO,
            fixed = false,
            sortOrder = sortOrder,
        ),
        sortOrder = sortOrder,
    )

    private fun weekPlan(
        id: String,
        weekStartDate: LocalDate,
        parts: List<WeeklyPart>,
        status: WeekPlanStatus = WeekPlanStatus.ACTIVE,
    ) = WeekPlan(
        id = WeekPlanId(id),
        weekStartDate = weekStartDate,
        parts = parts,
        programId = programId,
        status = status,
    )

    private fun assignment(
        id: String,
        weeklyPartId: WeeklyPartId,
        proclamatore: Proclamatore,
        slot: Int,
    ) = AssignmentWithPerson(
        id = AssignmentId(id),
        weeklyPartId = weeklyPartId,
        personId = proclamatore.id,
        slot = slot,
        proclamatore = proclamatore,
    )
}

// --- Test fakes ---

private class FakeWeekPlanQueries(
    private val weeks: List<WeekPlan>,
) : WeekPlanQueries {
    override suspend fun findByDate(weekStartDate: LocalDate): WeekPlan? =
        weeks.find { it.weekStartDate == weekStartDate }

    override suspend fun listInRange(startDate: LocalDate, endDate: LocalDate): List<WeekPlanSummary> = emptyList()

    override suspend fun totalSlotsByWeekInRange(startDate: LocalDate, endDate: LocalDate): Map<WeekPlanId, Int> = emptyMap()

    override suspend fun findByDateAndProgram(weekStartDate: LocalDate, programId: ProgramMonthId): WeekPlan? =
        weeks.find { it.weekStartDate == weekStartDate && it.programId == programId }

    override suspend fun listByProgram(programId: ProgramMonthId): List<WeekPlan> =
        weeks.filter { it.programId == programId }
}

private class FakeAssignmentRepo(
    private val assignmentsByWeek: Map<WeekPlanId, List<AssignmentWithPerson>>,
) : AssignmentRepository {
    override suspend fun listByWeek(weekPlanId: WeekPlanId): List<AssignmentWithPerson> =
        assignmentsByWeek[weekPlanId].orEmpty()

    override suspend fun listByWeekPlanIds(weekPlanIds: Set<WeekPlanId>): Map<WeekPlanId, List<AssignmentWithPerson>> =
        weekPlanIds.associateWith { assignmentsByWeek[it].orEmpty() }

    context(tx: TransactionScope) override suspend fun save(assignment: Assignment) = error("not needed")
    context(tx: TransactionScope) override suspend fun remove(assignmentId: AssignmentId) = error("not needed")
    context(tx: TransactionScope) override suspend fun removeAllByWeekPlan(weekPlanId: WeekPlanId) = error("not needed")
    override suspend fun countAssignmentsForWeek(weekPlanId: WeekPlanId): Int = error("not needed")
    override suspend fun countAssignmentsByWeekInRange(startDate: LocalDate, endDate: LocalDate): Map<WeekPlanId, Int> = error("not needed")
    context(tx: TransactionScope) override suspend fun deleteByProgramFromDate(programId: ProgramMonthId, fromDate: LocalDate): Int = error("not needed")
    override suspend fun countByProgramFromDate(programId: ProgramMonthId, fromDate: LocalDate): Int = error("not needed")
    override suspend fun findWeekPlanIdByAssignmentId(assignmentId: AssignmentId): WeekPlanId? = error("not needed")
}

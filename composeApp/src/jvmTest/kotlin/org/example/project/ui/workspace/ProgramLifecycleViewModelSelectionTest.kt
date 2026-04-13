package org.example.project.ui.workspace

import org.example.project.feature.assignments.domain.AssignmentWithPerson
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.assignments.person
import org.example.project.feature.people.domain.Sesso
import org.example.project.feature.programs.application.ProgramSelectionSnapshot
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.programs.fixtureProgramMonth
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.SexRule
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeeklyPart
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.YearMonth
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProgramLifecycleViewModelSelectionTest {

    @Test
    fun `keeps previously selected program when still present`() {
        val current = fixtureProgramMonth(YearMonth.of(2026, 2), id = "current")
        val future = fixtureProgramMonth(YearMonth.of(2026, 3), id = "future-1")

        val selected = resolveSelectedProgramId(
            previousSelectedId = future.id,
            currentProgram = current,
            futurePrograms = listOf(future),
        )

        assertEquals(future.id, selected)
    }

    @Test
    fun `falls back to current when previous selection is no longer available`() {
        val current = fixtureProgramMonth(YearMonth.of(2026, 2), id = "current")
        val future = fixtureProgramMonth(YearMonth.of(2026, 3), id = "future-1")

        val selected = resolveSelectedProgramId(
            previousSelectedId = ProgramMonthId("deleted-id"),
            currentProgram = current,
            futurePrograms = listOf(future),
        )

        assertEquals(current.id, selected)
    }

    @Test
    fun `selects nearest future when no current exists`() {
        val future1 = fixtureProgramMonth(YearMonth.of(2026, 3), id = "future-1")
        val future2 = fixtureProgramMonth(YearMonth.of(2026, 4), id = "future-2")

        val selected = resolveSelectedProgramId(
            previousSelectedId = null,
            currentProgram = null,
            futurePrograms = listOf(future1, future2),
        )

        assertEquals(future1.id, selected)
    }

    @Test
    fun `applyProgramSnapshot clears stale weeks and assignments when no program remains`() {
        val staleWeek = WeekPlan(
            id = WeekPlanId("week-1"),
            weekStartDate = LocalDate.of(2026, 3, 2),
            parts = emptyList(),
            programId = ProgramMonthId("deleted-program"),
        )
        val initial = ProgramLifecycleUiState(
            isLoading = true,
            selectedProgramId = ProgramMonthId("deleted-program"),
            selectedProgramWeeks = listOf(staleWeek),
            selectedProgramAssignments = mapOf("week-1" to emptyList<AssignmentWithPerson>()),
        )

        val updated = applyProgramSnapshot(
            state = initial,
            snapshot = ProgramSelectionSnapshot(
                current = null,
                futures = emptyList(),
            ),
        )

        assertEquals(null, updated.selectedProgramId)
        assertTrue(updated.selectedProgramWeeks.isEmpty())
        assertTrue(updated.selectedProgramAssignments.isEmpty())
    }

    @Test
    fun `calculateProgramSidebarStatus marks month as to generate when no weeks exist`() {
        val status = calculateProgramSidebarStatus(
            weeks = emptyList(),
            assignmentsByWeek = emptyMap(),
        )

        assertEquals(ProgramSidebarStatus.TO_GENERATE, status)
    }

    @Test
    fun `calculateProgramSidebarStatus marks month as to assign when weeks exist without assignments`() {
        val week = fixtureWeekPlan(id = "week-1", peopleCount = 2)

        val status = calculateProgramSidebarStatus(
            weeks = listOf(week),
            assignmentsByWeek = emptyMap(),
        )

        assertEquals(ProgramSidebarStatus.TO_ASSIGN, status)
    }

    @Test
    fun `calculateProgramSidebarStatus marks month as partial when some slots are assigned`() {
        val week = fixtureWeekPlan(id = "week-1", peopleCount = 2)

        val status = calculateProgramSidebarStatus(
            weeks = listOf(week),
            assignmentsByWeek = mapOf(
                week.id.value to listOf(fixtureAssignment(week.parts.first().id, slot = 1)),
            ),
        )

        assertEquals(ProgramSidebarStatus.PARTIAL, status)
    }

    @Test
    fun `calculateProgramSidebarStatus marks month as ready when all slots are assigned`() {
        val week = fixtureWeekPlan(id = "week-1", peopleCount = 2)

        val status = calculateProgramSidebarStatus(
            weeks = listOf(week),
            assignmentsByWeek = mapOf(
                week.id.value to listOf(
                    fixtureAssignment(week.parts.first().id, slot = 1),
                    fixtureAssignment(week.parts.first().id, slot = 2),
                ),
            ),
        )

        assertEquals(ProgramSidebarStatus.READY, status)
    }
}

private fun fixtureWeekPlan(
    id: String,
    peopleCount: Int,
): WeekPlan {
    val partType = PartType(
        id = PartTypeId("part-type-$id"),
        code = "PT-$id",
        label = "Parte $id",
        peopleCount = peopleCount,
        sexRule = SexRule.STESSO_SESSO,
        fixed = false,
        sortOrder = 0,
    )
    return WeekPlan(
        id = WeekPlanId(id),
        weekStartDate = LocalDate.of(2026, 3, 2),
        parts = listOf(
            WeeklyPart(
                id = WeeklyPartId("part-$id"),
                partType = partType,
                sortOrder = 0,
            ),
        ),
        programId = ProgramMonthId("program-$id"),
    )
}

private fun fixtureAssignment(
    weeklyPartId: WeeklyPartId,
    slot: Int,
): AssignmentWithPerson = AssignmentWithPerson.of(
    id = AssignmentId("assignment-${weeklyPartId.value}-$slot"),
    weeklyPartId = weeklyPartId,
    personId = person("person-$slot", "Mario", "Rossi", Sesso.M).id,
    slot = slot,
    proclamatore = person("person-$slot", "Mario", "Rossi", Sesso.M),
).fold(
    ifLeft = { error -> throw AssertionError(error.message) },
    ifRight = { it },
)

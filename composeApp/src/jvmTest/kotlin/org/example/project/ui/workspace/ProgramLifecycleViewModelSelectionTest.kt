package org.example.project.ui.workspace

import org.example.project.feature.assignments.domain.AssignmentWithPerson
import org.example.project.feature.programs.application.ProgramSelectionSnapshot
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.programs.fixtureProgramMonth
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanId
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
}

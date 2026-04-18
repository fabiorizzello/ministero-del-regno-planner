package org.example.project.ui.workspace

import org.example.project.feature.programs.application.ProgramSelectionSnapshot
import org.example.project.feature.programs.domain.ProgramMonth
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeekPlanStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProgramLifecycleViewModelTest {

    // ── resolveSelectedProgramId ──────────────────────────────────────────────

    @Test
    fun `resolveSelectedProgramId preserva selezione precedente se ancora valida`() {
        val currentId = ProgramMonthId("m1")
        val futureId = ProgramMonthId("m2")
        val result = resolveSelectedProgramId(
            previousSelectedId = futureId,
            previousProgram = null,
            currentProgram = program(currentId, 2026, 3),
            futurePrograms = listOf(program(futureId, 2026, 4)),
        )
        assertEquals(futureId, result)
    }

    @Test
    fun `resolveSelectedProgramId torna al corrente se precedente non esiste piu'`() {
        val currentId = ProgramMonthId("m1")
        val result = resolveSelectedProgramId(
            previousSelectedId = ProgramMonthId("gone"),
            previousProgram = null,
            currentProgram = program(currentId, 2026, 3),
            futurePrograms = emptyList(),
        )
        assertEquals(currentId, result)
    }

    @Test
    fun `resolveSelectedProgramId seleziona primo futuro se non c'e' corrente`() {
        val futureId = ProgramMonthId("m2")
        val result = resolveSelectedProgramId(
            previousSelectedId = null,
            previousProgram = null,
            currentProgram = null,
            futurePrograms = listOf(program(futureId, 2026, 4)),
        )
        assertEquals(futureId, result)
    }

    @Test
    fun `resolveSelectedProgramId restituisce null se non ci sono programmi`() {
        val result = resolveSelectedProgramId(
            previousSelectedId = null,
            previousProgram = null,
            currentProgram = null,
            futurePrograms = emptyList(),
        )
        assertNull(result)
    }

    @Test
    fun `resolveSelectedProgramId non sceglie mai previous come default`() {
        val previousId = ProgramMonthId("prev")
        val currentId = ProgramMonthId("curr")
        val result = resolveSelectedProgramId(
            previousSelectedId = null,
            previousProgram = program(previousId, 2026, 3),
            currentProgram = program(currentId, 2026, 4),
            futurePrograms = emptyList(),
        )
        assertEquals(currentId, result)
    }

    @Test
    fun `resolveSelectedProgramId default a primo futuro se previous esiste ma corrente no`() {
        val previousId = ProgramMonthId("prev")
        val futureId = ProgramMonthId("future")
        val result = resolveSelectedProgramId(
            previousSelectedId = null,
            previousProgram = program(previousId, 2026, 3),
            currentProgram = null,
            futurePrograms = listOf(program(futureId, 2026, 5)),
        )
        assertEquals(futureId, result)
    }

    @Test
    fun `resolveSelectedProgramId preserva selezione previous se utente l'ha scelta`() {
        val previousId = ProgramMonthId("prev")
        val currentId = ProgramMonthId("curr")
        val result = resolveSelectedProgramId(
            previousSelectedId = previousId,
            previousProgram = program(previousId, 2026, 3),
            currentProgram = program(currentId, 2026, 4),
            futurePrograms = emptyList(),
        )
        assertEquals(previousId, result)
    }

    // ── computeCreatableTargets ───────────────────────────────────────────────

    @Test
    fun `computeCreatableTargets restituisce solo mese corrente e prossimo quando non ci sono programmi`() {
        // May richiede April già esistente (regola month+2 → month+1 deve esistere)
        val today = LocalDate.of(2026, 3, 6)
        val targets = computeCreatableTargets(today = today, currentProgram = null, futurePrograms = emptyList())
        assertEquals(2, targets.size)
        assertTrue(YearMonth.of(2026, 3) in targets)
        assertTrue(YearMonth.of(2026, 4) in targets)
        assertFalse(YearMonth.of(2026, 5) in targets)
    }

    @Test
    fun `computeCreatableTargets esclude mesi con programma gia' esistente`() {
        val today = LocalDate.of(2026, 3, 6)
        val targets = computeCreatableTargets(
            today = today,
            currentProgram = program(ProgramMonthId("m1"), 2026, 3),
            futurePrograms = listOf(program(ProgramMonthId("m2"), 2026, 4)),
        )
        assertEquals(1, targets.size)
        assertEquals(YearMonth.of(2026, 5), targets.single())
    }

    @Test
    fun `computeCreatableTargets restituisce lista vuota se tutti e 3 i mesi sono occupati`() {
        val today = LocalDate.of(2026, 3, 6)
        val targets = computeCreatableTargets(
            today = today,
            currentProgram = program(ProgramMonthId("m1"), 2026, 3),
            futurePrograms = listOf(
                program(ProgramMonthId("m2"), 2026, 4),
                program(ProgramMonthId("m3"), 2026, 5),
            ),
        )
        assertTrue(targets.isEmpty())
    }

    // ── computePastCreatableTarget ────────────────────────────────────────────

    @Test
    fun `computePastCreatableTarget restituisce mese precedente quando vuoto`() {
        val today = LocalDate.of(2026, 4, 13)
        val target = computePastCreatableTarget(
            today = today,
            previousProgram = null,
            currentProgram = null,
            futurePrograms = emptyList(),
        )
        assertEquals(YearMonth.of(2026, 3), target)
    }

    @Test
    fun `computePastCreatableTarget restituisce null se previousProgram esiste`() {
        val today = LocalDate.of(2026, 4, 13)
        val target = computePastCreatableTarget(
            today = today,
            previousProgram = program(ProgramMonthId("prev"), 2026, 3),
            currentProgram = null,
            futurePrograms = emptyList(),
        )
        assertNull(target)
    }

    @Test
    fun `computePastCreatableTarget non e' bloccato dalla quota futuri`() {
        val today = LocalDate.of(2026, 4, 13)
        val target = computePastCreatableTarget(
            today = today,
            previousProgram = null,
            currentProgram = program(ProgramMonthId("curr"), 2026, 4),
            futurePrograms = listOf(
                program(ProgramMonthId("f1"), 2026, 5),
                program(ProgramMonthId("f2"), 2026, 6),
            ),
        )
        assertEquals(YearMonth.of(2026, 3), target)
    }

    @Test
    fun `applyProgramSnapshot popola pastCreatableTarget quando assente`() {
        val state = ProgramLifecycleUiState(today = LocalDate.of(2026, 4, 13))
        val snapshot = ProgramSelectionSnapshot(
            previous = null,
            current = program(ProgramMonthId("curr"), 2026, 4),
            futures = emptyList(),
        )
        val result = applyProgramSnapshot(state, snapshot)
        assertEquals(YearMonth.of(2026, 3), result.pastCreatableTarget)
    }

    @Test
    fun `applyProgramSnapshot azzera pastCreatableTarget quando previous esiste`() {
        val state = ProgramLifecycleUiState(today = LocalDate.of(2026, 4, 13))
        val snapshot = ProgramSelectionSnapshot(
            previous = program(ProgramMonthId("prev"), 2026, 3),
            current = program(ProgramMonthId("curr"), 2026, 4),
            futures = emptyList(),
        )
        val result = applyProgramSnapshot(state, snapshot)
        assertNull(result.pastCreatableTarget)
    }

    // ── applyProgramSnapshot ──────────────────────────────────────────────────

    @Test
    fun `applyProgramSnapshot preserva selezione corrente se ancora valida`() {
        val currentId = ProgramMonthId("m1")
        val futureId = ProgramMonthId("m2")
        val state = ProgramLifecycleUiState(
            selectedProgramId = futureId,
            today = LocalDate.of(2026, 3, 6),
        )
        val snapshot = ProgramSelectionSnapshot(
            previous = null,
            current = program(currentId, 2026, 3),
            futures = listOf(program(futureId, 2026, 4)),
        )
        val result = applyProgramSnapshot(state, snapshot)
        assertEquals(futureId, result.selectedProgramId)
        assertNull(result.deleteImpactConfirm)
        assertTrue(!result.isLoading)
    }

    @Test
    fun `applyProgramSnapshot azzera settimane e assegnazioni quando selezione diventa null`() {
        val state = ProgramLifecycleUiState(
            selectedProgramId = ProgramMonthId("gone"),
            selectedProgramWeeks = listOf(),
            selectedProgramAssignments = mapOf("x" to emptyList()),
            today = LocalDate.of(2026, 3, 6),
        )
        val snapshot = ProgramSelectionSnapshot(previous = null, current = null, futures = emptyList())
        val result = applyProgramSnapshot(state, snapshot)
        assertNull(result.selectedProgramId)
        assertTrue(result.selectedProgramWeeks.isEmpty())
        assertTrue(result.selectedProgramAssignments.isEmpty())
    }

    @Test
    fun `applyProgramSnapshot popola previousProgram dallo snapshot`() {
        val previousId = ProgramMonthId("prev")
        val currentId = ProgramMonthId("curr")
        val state = ProgramLifecycleUiState(today = LocalDate.of(2026, 4, 13))
        val snapshot = ProgramSelectionSnapshot(
            previous = program(previousId, 2026, 3),
            current = program(currentId, 2026, 4),
            futures = emptyList(),
        )
        val result = applyProgramSnapshot(state, snapshot)
        assertEquals(previousId, result.previousProgram?.id)
        assertEquals(currentId, result.currentProgram?.id)
        assertEquals(currentId, result.selectedProgramId, "Default selection rimane il corrente")
    }

    @Test
    fun `selectedProgram getter risolve il previousProgram quando selezionato`() {
        val previousId = ProgramMonthId("prev")
        val currentId = ProgramMonthId("curr")
        val state = ProgramLifecycleUiState(
            today = LocalDate.of(2026, 4, 13),
            previousProgram = program(previousId, 2026, 3),
            currentProgram = program(currentId, 2026, 4),
            selectedProgramId = previousId,
        )
        assertEquals(previousId, state.selectedProgram?.id)
        assertTrue(state.isSelectedProgramPast)
        assertFalse(state.canDeleteSelectedProgram, "Past program non deve essere eliminabile")
    }

    @Test
    fun `canDeleteSelectedProgram resta vero per il corrente`() {
        val currentId = ProgramMonthId("curr")
        val state = ProgramLifecycleUiState(
            today = LocalDate.of(2026, 4, 13),
            previousProgram = program(ProgramMonthId("prev"), 2026, 3),
            currentProgram = program(currentId, 2026, 4),
            selectedProgramId = currentId,
        )
        assertFalse(state.isSelectedProgramPast)
        assertTrue(state.canDeleteSelectedProgram)
    }

    @Test
    fun `resolveWeekActionUiState keeps Salta settimana available for past active weeks`() {
        val week = WeekPlan(
            id = WeekPlanId("past-week"),
            weekStartDate = LocalDate.of(2026, 3, 2),
            parts = emptyList(),
            status = WeekPlanStatus.ACTIVE,
        )

        val actions = resolveWeekActionUiState(week)

        assertTrue(actions.canSkipWeek)
        assertFalse(actions.canReactivateWeek)
    }

    @Test
    fun `resolveWeekActionUiState swaps to reactivate when week is skipped`() {
        val week = WeekPlan(
            id = WeekPlanId("skipped-week"),
            weekStartDate = LocalDate.of(2026, 3, 9),
            parts = emptyList(),
            status = WeekPlanStatus.SKIPPED,
        )

        val actions = resolveWeekActionUiState(week)

        assertFalse(actions.canSkipWeek)
        assertTrue(actions.canReactivateWeek)
    }

    @Test
    fun `applyProgramSnapshot resetta deleteImpactConfirm`() {
        val id = ProgramMonthId("m1")
        val state = ProgramLifecycleUiState(
            selectedProgramId = id,
            deleteImpactConfirm = DeleteProgramImpact(2026, 3, 4, 12),
            today = LocalDate.of(2026, 3, 6),
        )
        val snapshot = ProgramSelectionSnapshot(
            previous = null,
            current = program(id, 2026, 3),
            futures = emptyList(),
        )
        val result = applyProgramSnapshot(state, snapshot)
        assertNull(result.deleteImpactConfirm)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun program(id: ProgramMonthId, year: Int, month: Int): ProgramMonth = ProgramMonth(
        id = id,
        year = year,
        month = month,
        startDate = LocalDate.of(year, month, 1),
        endDate = LocalDate.of(year, month, YearMonth.of(year, month).lengthOfMonth()),
        templateAppliedAt = null,
        createdAt = LocalDateTime.of(2026, 1, 1, 0, 0),
    )
}

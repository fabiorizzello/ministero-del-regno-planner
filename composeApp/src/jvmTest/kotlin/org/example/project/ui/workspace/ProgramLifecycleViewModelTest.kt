package org.example.project.ui.workspace

import org.example.project.feature.programs.application.ProgramSelectionSnapshot
import org.example.project.feature.programs.domain.ProgramMonth
import org.example.project.feature.programs.domain.ProgramMonthId
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
            currentProgram = null,
            futurePrograms = listOf(program(futureId, 2026, 4)),
        )
        assertEquals(futureId, result)
    }

    @Test
    fun `resolveSelectedProgramId restituisce null se non ci sono programmi`() {
        val result = resolveSelectedProgramId(
            previousSelectedId = null,
            currentProgram = null,
            futurePrograms = emptyList(),
        )
        assertNull(result)
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
        val snapshot = ProgramSelectionSnapshot(current = null, futures = emptyList())
        val result = applyProgramSnapshot(state, snapshot)
        assertNull(result.selectedProgramId)
        assertTrue(result.selectedProgramWeeks.isEmpty())
        assertTrue(result.selectedProgramAssignments.isEmpty())
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

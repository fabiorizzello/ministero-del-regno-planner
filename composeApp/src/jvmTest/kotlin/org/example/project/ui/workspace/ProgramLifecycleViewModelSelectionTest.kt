package org.example.project.ui.workspace

import org.example.project.feature.programs.fixtureProgramMonth
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

class ProgramLifecycleViewModelSelectionTest {

    @Test
    fun `keeps previously selected program when still present`() {
        val current = fixtureProgramMonth(YearMonth.of(2026, 2), id = "current")
        val future = fixtureProgramMonth(YearMonth.of(2026, 3), id = "future-1")

        val selected = resolveSelectedProgramId(
            previousSelectedId = future.id.value,
            currentProgram = current,
            futurePrograms = listOf(future),
        )

        assertEquals("future-1", selected)
    }

    @Test
    fun `falls back to current when previous selection is no longer available`() {
        val current = fixtureProgramMonth(YearMonth.of(2026, 2), id = "current")
        val future = fixtureProgramMonth(YearMonth.of(2026, 3), id = "future-1")

        val selected = resolveSelectedProgramId(
            previousSelectedId = "deleted-id",
            currentProgram = current,
            futurePrograms = listOf(future),
        )

        assertEquals("current", selected)
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

        assertEquals("future-1", selected)
    }
}

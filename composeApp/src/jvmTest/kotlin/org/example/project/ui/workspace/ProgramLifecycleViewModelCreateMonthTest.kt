package org.example.project.ui.workspace

import org.example.project.feature.programs.fixtureProgramMonth
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

class ProgramLifecycleViewModelCreateMonthTest {

    @Test
    fun `when no current and no future current plus one and plus two are creatable`() {
        val targets = computeCreatableTargets(
            today = LocalDate.of(2026, 2, 10),
            currentProgram = null,
            futurePrograms = emptyList(),
        )

        assertEquals(
            listOf(
                YearMonth.of(2026, 2),
                YearMonth.of(2026, 3),
                YearMonth.of(2026, 4),
            ),
            targets,
        )
    }

    @Test
    fun `when no current and current plus one exists backfill current and plus two are creatable`() {
        val targets = computeCreatableTargets(
            today = LocalDate.of(2026, 2, 10),
            currentProgram = null,
            futurePrograms = listOf(fixtureProgramMonth(YearMonth.of(2026, 3))),
        )

        assertEquals(
            listOf(
                YearMonth.of(2026, 2),
                YearMonth.of(2026, 4),
            ),
            targets,
        )
    }

    @Test
    fun `with current and no futures plus one and plus two are creatable`() {
        val targets = computeCreatableTargets(
            today = LocalDate.of(2026, 2, 10),
            currentProgram = fixtureProgramMonth(YearMonth.of(2026, 2)),
            futurePrograms = emptyList(),
        )

        assertEquals(
            listOf(
                YearMonth.of(2026, 3),
                YearMonth.of(2026, 4),
            ),
            targets,
        )
    }

    @Test
    fun `with current and two futures no more targets are creatable`() {
        val targets = computeCreatableTargets(
            today = LocalDate.of(2026, 2, 10),
            currentProgram = fixtureProgramMonth(YearMonth.of(2026, 2)),
            futurePrograms = listOf(
                fixtureProgramMonth(YearMonth.of(2026, 3)),
                fixtureProgramMonth(YearMonth.of(2026, 4)),
            ),
        )

        assertEquals(emptyList(), targets)
    }
}

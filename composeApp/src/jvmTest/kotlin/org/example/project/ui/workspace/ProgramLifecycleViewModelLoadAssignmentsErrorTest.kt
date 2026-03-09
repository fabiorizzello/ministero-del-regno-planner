package org.example.project.ui.workspace

import arrow.core.Either
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.example.project.feature.assignments.application.CaricaAssegnazioniUseCase
import org.example.project.feature.programs.application.CaricaProgrammiAttiviUseCase
import org.example.project.feature.programs.application.CreaProssimoProgrammaUseCase
import org.example.project.feature.programs.application.EliminaProgrammaUseCase
import org.example.project.feature.programs.application.GeneraSettimaneProgrammaUseCase
import org.example.project.feature.programs.application.ProgramSelectionSnapshot
import org.example.project.feature.programs.fixtureProgramMonth
import org.example.project.feature.schemas.application.SchemaTemplateStore
import org.example.project.feature.weeklyparts.application.CercaTipiParteUseCase
import org.example.project.feature.weeklyparts.application.WeekPlanQueries
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.ui.components.FeedbackBannerKind
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProgramLifecycleViewModelLoadAssignmentsErrorTest {

    @Test
    fun `loadWeeksForSelectedProgram shows error notice when assignment load fails`() = runTest {
        val currentProgram = fixtureProgramMonth(YearMonth.of(2026, 3), id = "program-current")
        val week = WeekPlan(
            id = WeekPlanId("week-1"),
            weekStartDate = LocalDate.of(2026, 3, 2),
            parts = emptyList(),
            programId = currentProgram.id,
        )

        val caricaProgrammiAttivi = mockk<CaricaProgrammiAttiviUseCase>()
        coEvery { caricaProgrammiAttivi(any()) } returns Either.Right(
            ProgramSelectionSnapshot(
                current = currentProgram,
                futures = emptyList(),
            ),
        )

        val weekPlanStore = mockk<WeekPlanQueries>()
        coEvery { weekPlanStore.listByProgram(currentProgram.id) } returns listOf(week)

        val caricaAssegnazioni = mockk<CaricaAssegnazioniUseCase>()
        coEvery { caricaAssegnazioni(week.weekStartDate) } throws RuntimeException("db unavailable")

        val cercaTipiParte = mockk<CercaTipiParteUseCase>()
        coEvery { cercaTipiParte() } returns emptyList()

        val vm = ProgramLifecycleViewModel(
            scope = this,
            caricaProgrammiAttivi = caricaProgrammiAttivi,
            creaProssimoProgramma = mockk<CreaProssimoProgrammaUseCase>(relaxed = true),
            eliminaProgramma = mockk<EliminaProgrammaUseCase>(relaxed = true),
            generaSettimaneProgramma = mockk<GeneraSettimaneProgrammaUseCase>(relaxed = true),
            schemaTemplateStore = mockk<SchemaTemplateStore>(relaxed = true),
            weekPlanStore = weekPlanStore,
            caricaAssegnazioni = caricaAssegnazioni,
            cercaTipiParte = cercaTipiParte,
        )

        vm.onScreenEntered()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(listOf(week), state.selectedProgramWeeks)
        assertTrue(state.selectedProgramAssignments.isEmpty())
        assertEquals(FeedbackBannerKind.ERROR, state.notice?.kind)
        assertTrue(
            state.notice?.details?.contains("Errore caricamento assegnazioni") == true,
            "Expected assignment-loading error details, got: ${state.notice?.details}",
        )
    }
}

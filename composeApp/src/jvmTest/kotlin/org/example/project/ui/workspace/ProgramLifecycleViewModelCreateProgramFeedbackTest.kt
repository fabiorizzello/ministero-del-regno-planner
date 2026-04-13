package org.example.project.ui.workspace

import arrow.core.Either
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.example.project.core.domain.DomainError
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
import org.example.project.ui.components.FeedbackBannerKind
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProgramLifecycleViewModelCreateProgramFeedbackTest {

    @Test
    fun `createProgramForTarget shows error notice when schema catalog is empty`() = runTest {
        val schemaTemplateStore = mockk<SchemaTemplateStore>()
        coEvery { schemaTemplateStore.isEmpty() } returns true

        val vm = ProgramLifecycleViewModel(
            scope = this,
            caricaProgrammiAttivi = mockk<CaricaProgrammiAttiviUseCase>(relaxed = true),
            creaProssimoProgramma = mockk<CreaProssimoProgrammaUseCase>(relaxed = true),
            eliminaProgramma = mockk<EliminaProgrammaUseCase>(relaxed = true),
            generaSettimaneProgramma = mockk<GeneraSettimaneProgrammaUseCase>(relaxed = true),
            schemaTemplateStore = schemaTemplateStore,
            weekPlanStore = mockk<WeekPlanQueries>(relaxed = true),
            caricaAssegnazioni = mockk<CaricaAssegnazioniUseCase>(relaxed = true),
            cercaTipiParte = mockk<CercaTipiParteUseCase>(relaxed = true),
        )

        vm.createProgramForTarget(2026, 4)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isCreatingProgram)
        assertEquals(FeedbackBannerKind.ERROR, state.notice?.kind)
        assertTrue(
            state.notice?.details?.contains("Aggiorna schemi prima di creare il programma") == true,
            "Expected schema refresh guidance, got: ${state.notice?.details}",
        )
    }

    @Test
    fun `createProgramForTarget rolls back created month and shows guidance when schema generation fails`() = runTest {
        val createdProgram = fixtureProgramMonth(YearMonth.of(2026, 4), id = "created-program")
        val schemaTemplateStore = mockk<SchemaTemplateStore>()
        val creaProssimoProgramma = mockk<CreaProssimoProgrammaUseCase>()
        val generaSettimaneProgramma = mockk<GeneraSettimaneProgrammaUseCase>()
        val eliminaProgramma = mockk<EliminaProgrammaUseCase>()

        coEvery { schemaTemplateStore.isEmpty() } returns false
        coEvery { creaProssimoProgramma(2026, 4, any()) } returns Either.Right(createdProgram)
        coEvery {
            generaSettimaneProgramma(createdProgram.id, any())
        } returns Either.Left(
            DomainError.SettimanaSenzaTemplateENessunaParteFissa(LocalDate.of(2026, 4, 6)),
        )
        coEvery { eliminaProgramma(createdProgram.id, any()) } returns Either.Right(Unit)

        val vm = ProgramLifecycleViewModel(
            scope = this,
            caricaProgrammiAttivi = mockk<CaricaProgrammiAttiviUseCase>(relaxed = true),
            creaProssimoProgramma = creaProssimoProgramma,
            eliminaProgramma = eliminaProgramma,
            generaSettimaneProgramma = generaSettimaneProgramma,
            schemaTemplateStore = schemaTemplateStore,
            weekPlanStore = mockk<WeekPlanQueries>(relaxed = true),
            caricaAssegnazioni = mockk<CaricaAssegnazioniUseCase>(relaxed = true),
            cercaTipiParte = mockk<CercaTipiParteUseCase>(relaxed = true),
        )

        vm.createProgramForTarget(2026, 4)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isCreatingProgram)
        assertEquals(FeedbackBannerKind.ERROR, state.notice?.kind)
        assertTrue(
            state.notice?.details?.contains("Aggiorna schemi e riprova") == true,
            "Expected schema refresh follow-up, got: ${state.notice?.details}",
        )
        coVerify(exactly = 1) { eliminaProgramma(createdProgram.id, any()) }
    }

    @Test
    fun `createProgramForTarget auto-selects the freshly created program`() = runTest {
        val currentProgram = fixtureProgramMonth(YearMonth.of(2026, 4), id = "program-current")
        val createdProgram = fixtureProgramMonth(YearMonth.of(2026, 5), id = "program-created")

        val schemaTemplateStore = mockk<SchemaTemplateStore>()
        coEvery { schemaTemplateStore.isEmpty() } returns false

        val creaProssimoProgramma = mockk<CreaProssimoProgrammaUseCase>()
        coEvery { creaProssimoProgramma(2026, 5, any()) } returns Either.Right(createdProgram)

        val generaSettimaneProgramma = mockk<GeneraSettimaneProgrammaUseCase>()
        coEvery { generaSettimaneProgramma(createdProgram.id, any()) } returns Either.Right(Unit)

        val caricaProgrammiAttivi = mockk<CaricaProgrammiAttiviUseCase>()
        coEvery { caricaProgrammiAttivi(any()) } returns Either.Right(
            ProgramSelectionSnapshot(
                previous = null,
                current = currentProgram,
                futures = listOf(createdProgram),
            ),
        )

        val weekPlanStore = mockk<WeekPlanQueries>()
        coEvery { weekPlanStore.listByProgram(any()) } returns emptyList()

        val vm = ProgramLifecycleViewModel(
            scope = this,
            caricaProgrammiAttivi = caricaProgrammiAttivi,
            creaProssimoProgramma = creaProssimoProgramma,
            eliminaProgramma = mockk<EliminaProgrammaUseCase>(relaxed = true),
            generaSettimaneProgramma = generaSettimaneProgramma,
            schemaTemplateStore = schemaTemplateStore,
            weekPlanStore = weekPlanStore,
            caricaAssegnazioni = mockk<CaricaAssegnazioniUseCase>(relaxed = true),
            cercaTipiParte = mockk<CercaTipiParteUseCase>(relaxed = true),
        )

        vm.createProgramForTarget(2026, 5)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isCreatingProgram)
        assertEquals(createdProgram.id, state.selectedProgramId)
    }
}

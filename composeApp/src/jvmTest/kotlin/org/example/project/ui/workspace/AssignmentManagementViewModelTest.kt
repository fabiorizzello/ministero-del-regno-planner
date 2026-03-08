package org.example.project.ui.workspace

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.nio.file.Paths
import org.example.project.feature.assignments.application.AutoAssegnaProgrammaUseCase
import org.example.project.feature.assignments.application.CaricaImpostazioniAssegnatoreUseCase
import org.example.project.feature.assignments.application.RimuoviAssegnazioniSettimanaUseCase
import org.example.project.feature.assignments.application.SalvaImpostazioniAssegnatoreUseCase
import org.example.project.feature.assignments.application.SvuotaAssegnazioniProgrammaUseCase
import org.example.project.feature.assignments.application.AutoAssignProgramResult
import org.example.project.feature.assignments.application.AutoAssignUnresolvedSlot
import org.example.project.feature.output.application.StampaProgrammaUseCase
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.ui.components.FeedbackBannerKind
import com.russhwolf.settings.Settings
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AssignmentManagementViewModelTest {

    private val programId = ProgramMonthId("p1")
    private val referenceDate = LocalDate.of(2026, 3, 1)

    @AfterTest
    fun tearDown() = unmockkAll()

    // ── saveAssignmentSettings ────────────────────────────────────────────────

    @Test
    fun `saveAssignmentSettings con stringa non numerica mostra errore senza chiamare IO`() = runTest {
        val salva = mockk<SalvaImpostazioniAssegnatoreUseCase>(relaxed = true)
        val vm = makeViewModel(scope = this, salva = salva)

        vm.setLeadCooldownWeeks("non-un-numero")
        vm.saveAssignmentSettings()

        assertEquals(FeedbackBannerKind.ERROR, vm.uiState.value.notice?.kind)
        coVerify(exactly = 0) { salva(any()) }
    }

    @Test
    fun `saveAssignmentSettings con valore negativo mostra errore`() = runTest {
        val salva = mockk<SalvaImpostazioniAssegnatoreUseCase>(relaxed = true)
        val vm = makeViewModel(scope = this, salva = salva)

        vm.setLeadWeight("-1")
        vm.saveAssignmentSettings()

        assertEquals(FeedbackBannerKind.ERROR, vm.uiState.value.notice?.kind)
        coVerify(exactly = 0) { salva(any()) }
    }

    // ── autoAssignSelectedProgram busy-guard ──────────────────────────────────

    @Test
    fun `autoAssignSelectedProgram ignora seconda chiamata mentre la prima e' in corso`() = runTest {
        val blocker = CompletableDeferred<Unit>()
        val autoAssegna = mockk<AutoAssegnaProgrammaUseCase>()
        coEvery { autoAssegna(any(), any()) } coAnswers {
            blocker.await()
            AutoAssignProgramResult(assignedCount = 1, unresolved = emptyList())
        }

        val vm = makeViewModel(scope = this, autoAssegna = autoAssegna)

        vm.autoAssignSelectedProgram(programId, referenceDate, onSuccess = {})
        advanceUntilIdle() // runs to blocker.await(), isAutoAssigning = true

        assertTrue(vm.uiState.value.isAutoAssigning)
        vm.autoAssignSelectedProgram(programId, referenceDate, onSuccess = {}) // guard

        coVerify(exactly = 1) { autoAssegna(any(), any()) }

        blocker.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isAutoAssigning)
    }

    @Test
    fun `autoAssignSelectedProgram chiama onSuccess solo quando ha successo`() = runTest {
        val autoAssegna = mockk<AutoAssegnaProgrammaUseCase>()
        coEvery { autoAssegna(any(), any()) } returns AutoAssignProgramResult(2, emptyList())

        val vm = makeViewModel(scope = this, autoAssegna = autoAssegna)

        var successCalled = false
        vm.autoAssignSelectedProgram(programId, referenceDate, onSuccess = { successCalled = true })
        advanceUntilIdle()

        assertTrue(successCalled)
        assertEquals(FeedbackBannerKind.SUCCESS, vm.uiState.value.notice?.kind)
    }

    @Test
    fun `autoAssignSelectedProgram non chiama onSuccess in caso di errore`() = runTest {
        val autoAssegna = mockk<AutoAssegnaProgrammaUseCase>()
        coEvery { autoAssegna(any(), any()) } throws RuntimeException("db error")

        val vm = makeViewModel(scope = this, autoAssegna = autoAssegna)

        var successCalled = false
        vm.autoAssignSelectedProgram(programId, referenceDate, onSuccess = { successCalled = true })
        advanceUntilIdle()

        assertFalse(successCalled)
        assertEquals(FeedbackBannerKind.ERROR, vm.uiState.value.notice?.kind)
    }

    @Test
    fun `autoAssignSelectedProgram include slot non assegnati nei dettagli del banner`() = runTest {
        val unresolved = listOf(
            AutoAssignUnresolvedSlot(
                weekStartDate = referenceDate,
                partLabel = "Parte 1",
                slot = 2,
                reason = "Nessun candidato idoneo",
            ),
        )
        val autoAssegna = mockk<AutoAssegnaProgrammaUseCase>()
        coEvery { autoAssegna(any(), any()) } returns AutoAssignProgramResult(
            assignedCount = 3,
            unresolved = unresolved,
        )

        val vm = makeViewModel(scope = this, autoAssegna = autoAssegna)
        vm.autoAssignSelectedProgram(programId, referenceDate, onSuccess = {})
        advanceUntilIdle()

        val details = vm.uiState.value.notice?.details ?: ""
        assertTrue(details.contains("3"), "Expected '3 slot assegnati' in: $details")
        assertTrue(details.contains("1 slot non assegnati"), "Expected '1 slot non assegnati' in: $details")
        assertEquals(1, vm.uiState.value.autoAssignUnresolved.size)
    }

    // ── requestClearAssignments / confirmClearAssignments ─────────────────────

    @Test
    fun `requestClearAssignments imposta count nel dialogo di conferma`() = runTest {
        val svuota = mockk<SvuotaAssegnazioniProgrammaUseCase>()
        coEvery { svuota.count(any(), any()) } returns 5

        val vm = makeViewModel(scope = this, svuota = svuota)
        vm.requestClearAssignments(programId, referenceDate)
        advanceUntilIdle()

        assertEquals(5, vm.uiState.value.clearAssignmentsConfirm)
    }

    @Test
    fun `confirmClearAssignments chiama onSuccess dopo esecuzione`() = runTest {
        val svuota = mockk<SvuotaAssegnazioniProgrammaUseCase>()
        coEvery { svuota.count(any(), any()) } returns 3
        coEvery { svuota.execute(any(), any()) } returns 3

        val vm = makeViewModel(scope = this, svuota = svuota)
        vm.requestClearAssignments(programId, referenceDate)
        advanceUntilIdle()

        var successCalled = false
        vm.confirmClearAssignments(programId, referenceDate, onSuccess = { successCalled = true })
        advanceUntilIdle()

        assertTrue(successCalled)
        assertNull(vm.uiState.value.clearAssignmentsConfirm)
    }

    @Test
    fun `confirmClearAssignments non chiama onSuccess in caso di errore`() = runTest {
        val svuota = mockk<SvuotaAssegnazioniProgrammaUseCase>()
        coEvery { svuota.count(any(), any()) } returns 3
        coEvery { svuota.execute(any(), any()) } throws RuntimeException("db error")

        val vm = makeViewModel(scope = this, svuota = svuota)
        vm.requestClearAssignments(programId, referenceDate)
        advanceUntilIdle()

        var successCalled = false
        vm.confirmClearAssignments(programId, referenceDate, onSuccess = { successCalled = true })
        advanceUntilIdle()

        assertFalse(successCalled)
        assertNotNull(vm.uiState.value.notice)
        assertEquals(FeedbackBannerKind.ERROR, vm.uiState.value.notice?.kind)
    }

    @Test
    fun `dismissClearAssignments azzera il dialogo di conferma`() = runTest {
        val svuota = mockk<SvuotaAssegnazioniProgrammaUseCase>()
        coEvery { svuota.count(any(), any()) } returns 2

        val vm = makeViewModel(scope = this, svuota = svuota)
        vm.requestClearAssignments(programId, referenceDate)
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.clearAssignmentsConfirm)
        vm.dismissClearAssignments()
        assertNull(vm.uiState.value.clearAssignmentsConfirm)
    }

    @Test
    fun `printSelectedProgram apre pdf senza mostrare banner di successo`() = runTest {
        val stampa = mockk<StampaProgrammaUseCase>()
        coEvery { stampa(programId) } returns Paths.get("C:\\temp\\programma.pdf")

        val vm = makeViewModel(scope = this, stampa = stampa)
        vm.printSelectedProgram(programId)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isPrintingProgram)
        assertNull(vm.uiState.value.notice)
        coVerify(exactly = 1) { stampa(programId) }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun makeViewModel(
        scope: kotlinx.coroutines.CoroutineScope,
        autoAssegna: AutoAssegnaProgrammaUseCase = mockk(relaxed = true),
        salva: SalvaImpostazioniAssegnatoreUseCase = mockk(relaxed = true),
        svuota: SvuotaAssegnazioniProgrammaUseCase = mockk(relaxed = true),
        stampa: StampaProgrammaUseCase = mockk(relaxed = true),
    ) = AssignmentManagementViewModel(
        scope = scope,
        autoAssegnaProgramma = autoAssegna,
        caricaImpostazioniAssegnatore = mockk(relaxed = true),
        salvaImpostazioniAssegnatore = salva,
        svuotaAssegnazioni = svuota,
        rimuoviAssegnazioniSettimana = mockk<RimuoviAssegnazioniSettimanaUseCase>(relaxed = true),
        stampaProgramma = stampa,
        settings = mockk<Settings>(relaxed = true),
    )
}

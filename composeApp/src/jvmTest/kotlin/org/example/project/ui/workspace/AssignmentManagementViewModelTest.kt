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
import org.example.project.feature.output.application.AssignmentTicketImage
import org.example.project.feature.output.application.AssignmentTicketLine
import org.example.project.feature.output.application.CaricaRiepilogoConsegneProgrammaUseCase
import org.example.project.feature.output.application.CaricaStatoConsegneUseCase
import org.example.project.feature.output.application.GeneraImmaginiAssegnazioni
import org.example.project.feature.output.application.AnnullaConsegnaUseCase
import org.example.project.feature.output.application.SegnaComInviatoUseCase
import org.example.project.feature.output.application.TicketGenerationResult
import org.example.project.feature.output.application.StampaProgrammaUseCase
import org.example.project.feature.output.domain.ProgramDeliverySnapshot
import org.example.project.feature.output.domain.SlipDeliveryInfo
import org.example.project.feature.output.domain.SlipDeliveryStatus
import arrow.core.Either
import org.example.project.core.domain.DomainError
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.ui.components.FeedbackBannerKind
import com.russhwolf.settings.Settings
import java.time.LocalDate
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
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

        vm.setLeadCooldownWeeks("-1")
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
            Either.Right(AutoAssignProgramResult(assignedCount = 1, unresolved = emptyList()))
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
        coEvery { autoAssegna(any(), any()) } returns Either.Right(AutoAssignProgramResult(2, emptyList()))

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
        coEvery { autoAssegna(any(), any()) } returns Either.Left(DomainError.Validation("db error"))

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
        coEvery { autoAssegna(any(), any()) } returns Either.Right(AutoAssignProgramResult(
            assignedCount = 3,
            unresolved = unresolved,
        ))

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
        coEvery { svuota.execute(any(), any()) } returns Either.Right(3)

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
        coEvery { svuota.execute(any(), any()) } returns Either.Left(DomainError.RimozioneAssegnazioniFallita("db error"))

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
        coEvery { stampa(programId) } returns Either.Right(Paths.get("C:\\temp\\programma.pdf"))

        val vm = makeViewModel(scope = this, stampa = stampa)
        vm.printSelectedProgram(programId)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isPrintingProgram)
        assertNull(vm.uiState.value.notice)
        coVerify(exactly = 1) { stampa(programId) }
    }

    @Test
    fun `openAssignmentTickets apre modale con biglietti senza banner di successo`() = runTest {
        val genera = mockk<GeneraImmaginiAssegnazioni>()
        val ticket = AssignmentTicketImage(
            fullName = "Mario Rossi",
            assistantName = null,
            weekStart = LocalDate.of(2027, 3, 2),
            weekEnd = LocalDate.of(2027, 3, 8),
            imagePath = Paths.get("C:\\exports\\assegnazioni\\biglietto.png"),
            assignments = listOf(AssignmentTicketLine(partLabel = "Studio biblico", roleLabel = null, partNumber = 3)),
            weeklyPartId = WeeklyPartId("p1"),
            weekPlanId = WeekPlanId("week-1"),
        )
        coEvery { genera.generateProgramTickets(programId) } returns Either.Right(TicketGenerationResult(tickets = listOf(ticket), warnings = emptyList()))

        val vm = makeViewModel(scope = this, genera = genera)
        vm.openAssignmentTickets(programId)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isAssignmentTicketsDialogOpen)
        assertFalse(vm.uiState.value.isLoadingAssignmentTickets)
        assertEquals(listOf(ticket), vm.uiState.value.assignmentTickets)
        assertNull(vm.uiState.value.notice)
    }

    @Test
    fun `closeAssignmentTicketsDialog chiude modale e pulisce stato`() = runTest {
        val genera = mockk<GeneraImmaginiAssegnazioni>()
        val ticket = AssignmentTicketImage(
            fullName = "Mario Rossi",
            assistantName = null,
            weekStart = LocalDate.of(2027, 3, 2),
            weekEnd = LocalDate.of(2027, 3, 8),
            imagePath = Paths.get("C:\\exports\\assegnazioni\\biglietto.png"),
            assignments = listOf(AssignmentTicketLine(partLabel = "Studio biblico", roleLabel = null, partNumber = 3)),
            weeklyPartId = WeeklyPartId("p1"),
            weekPlanId = WeekPlanId("week-1"),
        )
        coEvery { genera.generateProgramTickets(programId) } returns Either.Right(TicketGenerationResult(
            tickets = listOf(ticket),
            warnings = emptyList(),
        ))

        val vm = makeViewModel(scope = this, genera = genera)
        vm.openAssignmentTickets(programId)
        advanceUntilIdle()
        vm.closeAssignmentTicketsDialog()

        assertFalse(vm.uiState.value.isAssignmentTicketsDialogOpen)
        assertEquals(emptyList(), vm.uiState.value.assignmentTickets)
        assertNull(vm.uiState.value.assignmentTicketsError)
    }

    @Test
    fun `printSelectedProgram mostra errore e disabilita loading su Either Left`() = runTest {
        val stampa = mockk<StampaProgrammaUseCase>()
        coEvery { stampa(programId) } returns Either.Left(DomainError.NotFound("Programma"))

        val vm = makeViewModel(scope = this, stampa = stampa)
        vm.printSelectedProgram(programId)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isPrintingProgram)
        assertEquals(FeedbackBannerKind.ERROR, vm.uiState.value.notice?.kind)
    }

    @Test
    fun `openAssignmentTickets mostra errore e testa loading su Either Left`() = runTest {
        val genera = mockk<GeneraImmaginiAssegnazioni>()
        coEvery { genera.generateProgramTickets(programId) } returns Either.Left(DomainError.NotFound("Programma"))

        val vm = makeViewModel(scope = this, genera = genera)
        vm.openAssignmentTickets(programId)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isAssignmentTicketsDialogOpen)
        assertFalse(vm.uiState.value.isLoadingAssignmentTickets)
        assertTrue(vm.uiState.value.assignmentTickets.isEmpty())
        assertEquals(FeedbackBannerKind.ERROR, vm.uiState.value.notice?.kind)
    }

    // ── markAsDelivered ────────────────────────────────────────────────────────

    @Test
    fun `markAsDelivered successo ricarica delivery status e summary`() = runTest {
        val ticket = makeTicket()
        val segna = mockk<SegnaComInviatoUseCase>()
        coEvery { segna(any(), any(), any(), any()) } returns Either.Right(Unit)

        val deliveryInfo = SlipDeliveryInfo(
            status = SlipDeliveryStatus.INVIATO,
            activeDelivery = null,
            previousStudentName = null,
        )
        val caricaStato = mockk<CaricaStatoConsegneUseCase>()
        coEvery { caricaStato(any()) } returns mapOf(
            (ticket.weeklyPartId to ticket.weekPlanId) to deliveryInfo,
        )

        val caricaRiep = mockk<CaricaRiepilogoConsegneProgrammaUseCase>()
        coEvery { caricaRiep(any(), any()) } returns Either.Right(ProgramDeliverySnapshot(pending = 0, blocked = 0))

        val genera = mockk<GeneraImmaginiAssegnazioni>()
        coEvery { genera.generateProgramTickets(any()) } returns Either.Right(
            TicketGenerationResult(tickets = listOf(ticket), warnings = emptyList()),
        )

        val vm = makeViewModel(
            scope = this,
            genera = genera,
            segnaComInviato = segna,
            caricaStatoConsegne = caricaStato,
            caricaRiepilogo = caricaRiep,
        )
        // Set reference date so tickets pass the cutoff filter
        vm.loadDeliverySummary(programId, referenceDate)
        advanceUntilIdle()
        vm.openAssignmentTickets(programId)
        advanceUntilIdle()

        vm.markAsDelivered(ticket)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isMarkingDelivered)
        assertNull(vm.uiState.value.notice)
        coVerify(exactly = 1) { segna(ticket.weeklyPartId, ticket.weekPlanId, ticket.fullName, ticket.assistantName) }
        // deliveryStatus was reloaded
        assertEquals(SlipDeliveryStatus.INVIATO, vm.uiState.value.deliveryStatus[ticket.weeklyPartId to ticket.weekPlanId]?.status)
    }

    @Test
    fun `markAsDelivered errore mostra notice di errore`() = runTest {
        val ticket = makeTicket()
        val segna = mockk<SegnaComInviatoUseCase>()
        coEvery { segna(any(), any(), any(), any()) } returns Either.Left(DomainError.Validation("consegna fallita"))

        val vm = makeViewModel(scope = this, segnaComInviato = segna)
        vm.markAsDelivered(ticket)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isMarkingDelivered)
        assertNotNull(vm.uiState.value.notice)
        assertEquals(FeedbackBannerKind.ERROR, vm.uiState.value.notice?.kind)
    }

    @Test
    fun `markAsDelivered ignora seconda chiamata mentre la prima e' in corso`() = runTest {
        val ticket = makeTicket()
        val blocker = CompletableDeferred<Either<DomainError, Unit>>()
        val segna = mockk<SegnaComInviatoUseCase>()
        coEvery { segna(any(), any(), any(), any()) } coAnswers { blocker.await() }

        val vm = makeViewModel(scope = this, segnaComInviato = segna)
        vm.markAsDelivered(ticket)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isMarkingDelivered)
        vm.markAsDelivered(ticket) // guard — should be ignored

        coVerify(exactly = 1) { segna(any(), any(), any(), any()) }

        blocker.complete(Either.Right(Unit))
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isMarkingDelivered)
    }

    // ── cancelDelivery ───────────────────────────────────────────────────────

    @Test
    fun `cancelDelivery successo ricarica delivery status e summary`() = runTest {
        val ticket = makeTicket()
        val annulla = mockk<AnnullaConsegnaUseCase>()
        coEvery { annulla(any(), any()) } returns Either.Right(Unit)

        val deliveryInfo = SlipDeliveryInfo(
            status = SlipDeliveryStatus.DA_REINVIARE,
            activeDelivery = null,
            previousStudentName = "Mario Rossi",
        )
        val caricaStato = mockk<CaricaStatoConsegneUseCase>()
        coEvery { caricaStato(any()) } returns mapOf(
            (ticket.weeklyPartId to ticket.weekPlanId) to deliveryInfo,
        )

        val caricaRiep = mockk<CaricaRiepilogoConsegneProgrammaUseCase>()
        coEvery { caricaRiep(any(), any()) } returns Either.Right(ProgramDeliverySnapshot(pending = 1, blocked = 0))

        val genera = mockk<GeneraImmaginiAssegnazioni>()
        coEvery { genera.generateProgramTickets(any()) } returns Either.Right(
            TicketGenerationResult(tickets = listOf(ticket), warnings = emptyList()),
        )

        val vm = makeViewModel(
            scope = this,
            genera = genera,
            annullaConsegna = annulla,
            caricaStatoConsegne = caricaStato,
            caricaRiepilogo = caricaRiep,
        )
        // Set reference date so tickets pass the cutoff filter
        vm.loadDeliverySummary(programId, referenceDate)
        advanceUntilIdle()
        vm.openAssignmentTickets(programId)
        advanceUntilIdle()

        vm.cancelDelivery(ticket)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isCancellingDelivery)
        assertNull(vm.uiState.value.notice)
        coVerify(exactly = 1) { annulla(ticket.weeklyPartId, ticket.weekPlanId) }
        // deliveryStatus was reloaded
        assertEquals(SlipDeliveryStatus.DA_REINVIARE, vm.uiState.value.deliveryStatus[ticket.weeklyPartId to ticket.weekPlanId]?.status)
        // delivery summary was refreshed
        assertEquals(1, vm.uiState.value.deliverySnapshot?.pending)
    }

    @Test
    fun `cancelDelivery errore mostra notice di errore`() = runTest {
        val ticket = makeTicket()
        val annulla = mockk<AnnullaConsegnaUseCase>()
        coEvery { annulla(any(), any()) } returns Either.Left(DomainError.Validation("annullamento fallito"))

        val vm = makeViewModel(scope = this, annullaConsegna = annulla)
        vm.cancelDelivery(ticket)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isCancellingDelivery)
        assertNotNull(vm.uiState.value.notice)
        assertEquals(FeedbackBannerKind.ERROR, vm.uiState.value.notice?.kind)
    }

    @Test
    fun `cancelDelivery ignora seconda chiamata mentre la prima e' in corso`() = runTest {
        val ticket = makeTicket()
        val blocker = CompletableDeferred<Either<DomainError, Unit>>()
        val annulla = mockk<AnnullaConsegnaUseCase>()
        coEvery { annulla(any(), any()) } coAnswers { blocker.await() }

        val vm = makeViewModel(scope = this, annullaConsegna = annulla)
        vm.cancelDelivery(ticket)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isCancellingDelivery)
        vm.cancelDelivery(ticket) // guard — should be ignored

        coVerify(exactly = 1) { annulla(any(), any()) }

        blocker.complete(Either.Right(Unit))
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isCancellingDelivery)
    }

    // ── loadDeliveryStatus ───────────────────────────────────────────────────

    @Test
    fun `openAssignmentTickets popola deliveryStatus per i ticket generati`() = runTest {
        val ticket = makeTicket()
        val deliveryInfo = SlipDeliveryInfo(
            status = SlipDeliveryStatus.INVIATO,
            activeDelivery = null,
            previousStudentName = null,
        )
        val caricaStato = mockk<CaricaStatoConsegneUseCase>()
        coEvery { caricaStato(any()) } returns mapOf(
            (ticket.weeklyPartId to ticket.weekPlanId) to deliveryInfo,
        )

        val genera = mockk<GeneraImmaginiAssegnazioni>()
        coEvery { genera.generateProgramTickets(any()) } returns Either.Right(
            TicketGenerationResult(tickets = listOf(ticket), warnings = emptyList()),
        )

        val caricaRiep = mockk<CaricaRiepilogoConsegneProgrammaUseCase>()
        coEvery { caricaRiep(any(), any()) } returns Either.Right(ProgramDeliverySnapshot(pending = 0, blocked = 0))

        val vm = makeViewModel(
            scope = this,
            genera = genera,
            caricaStatoConsegne = caricaStato,
            caricaRiepilogo = caricaRiep,
        )
        // Set reference date so tickets pass the cutoff filter
        vm.loadDeliverySummary(programId, referenceDate)
        advanceUntilIdle()
        vm.openAssignmentTickets(programId)
        advanceUntilIdle()

        val status = vm.uiState.value.deliveryStatus
        assertEquals(1, status.size)
        val key = ticket.weeklyPartId to ticket.weekPlanId
        assertEquals(SlipDeliveryStatus.INVIATO, status[key]?.status)
        coVerify { caricaStato(listOf(ticket.weekPlanId)) }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun makeViewModel(
        scope: kotlinx.coroutines.CoroutineScope,
        autoAssegna: AutoAssegnaProgrammaUseCase = mockk(relaxed = true),
        salva: SalvaImpostazioniAssegnatoreUseCase = mockk(relaxed = true),
        svuota: SvuotaAssegnazioniProgrammaUseCase = mockk(relaxed = true),
        stampa: StampaProgrammaUseCase = mockk(relaxed = true),
        genera: GeneraImmaginiAssegnazioni = mockk(relaxed = true),
        segnaComInviato: SegnaComInviatoUseCase = mockk(relaxed = true),
        annullaConsegna: AnnullaConsegnaUseCase = mockk(relaxed = true),
        caricaStatoConsegne: CaricaStatoConsegneUseCase = mockk(relaxed = true),
        caricaRiepilogo: CaricaRiepilogoConsegneProgrammaUseCase = mockk(relaxed = true),
    ) = AssignmentManagementViewModel(
        scope = scope,
        autoAssegnaProgramma = autoAssegna,
        caricaImpostazioniAssegnatore = mockk(relaxed = true),
        salvaImpostazioniAssegnatore = salva,
        svuotaAssegnazioni = svuota,
        rimuoviAssegnazioniSettimana = mockk<RimuoviAssegnazioniSettimanaUseCase>(relaxed = true),
        stampaProgramma = stampa,
        generaImmaginiAssegnazioni = genera,
        settings = mockk<Settings>(relaxed = true),
        segnaComInviato = segnaComInviato,
        annullaConsegna = annullaConsegna,
        caricaStatoConsegne = caricaStatoConsegne,
        caricaRiepilogo = caricaRiepilogo,
    )

    private fun makeTicket(
        weeklyPartId: WeeklyPartId = WeeklyPartId("wp1"),
        weekPlanId: WeekPlanId = WeekPlanId("week-1"),
        fullName: String = "Mario Rossi",
        assistantName: String? = null,
    ) = AssignmentTicketImage(
        fullName = fullName,
        assistantName = assistantName,
        weekStart = LocalDate.of(2027, 3, 2),
        weekEnd = LocalDate.of(2027, 3, 8),
        imagePath = Paths.get("/tmp/biglietto.png"),
        assignments = listOf(AssignmentTicketLine(partLabel = "Lettura", roleLabel = null, partNumber = 1)),
        weeklyPartId = weeklyPartId,
        weekPlanId = weekPlanId,
    )
}

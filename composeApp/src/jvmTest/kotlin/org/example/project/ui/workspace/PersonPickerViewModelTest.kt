package org.example.project.ui.workspace

import arrow.core.Either
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.example.project.core.domain.DomainError
import org.example.project.feature.assignments.application.AssegnaPersonaUseCase
import org.example.project.feature.assignments.application.RimuoviAssegnazioneUseCase
import org.example.project.feature.assignments.application.SuggerisciProclamatoriUseCase
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.assignments.domain.SuggestedProclamatore
import org.example.project.feature.output.application.AnnullaConsegnaUseCase
import org.example.project.feature.output.application.VerificaConsegnaPreAssegnazioneUseCase
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.domain.Sesso
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import org.example.project.ui.components.FeedbackBannerKind
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersonPickerViewModelTest {

    private val weekStartDate = LocalDate.of(2026, 3, 2)
    private val weeklyPartId = WeeklyPartId("wp-1")
    private val weekPlanId = WeekPlanId("week-1")
    private val slot = 1
    private val personId = ProclamatoreId("person-1")

    @AfterTest
    fun tearDown() = unmockkAll()

    // ── openPersonPicker ──────────────────────────────────────────────────────

    @Test
    fun `openPersonPicker sets picker fields and loads suggestions`() = runTest {
        val suggestions = listOf(makeSuggestion("person-1", "Mario", "Rossi"))
        val suggerisci = mockk<SuggerisciProclamatoriUseCase>()
        coEvery { suggerisci(any(), any(), any(), any(), any(), any(), any()) } returns suggestions

        val vm = makeViewModel(scope = this, suggerisci = suggerisci)
        vm.openPersonPicker(weekStartDate, weeklyPartId, slot, weekPlanId)
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.isPickerOpen)
        assertEquals(weekStartDate, state.pickerWeekStartDate)
        assertEquals(weeklyPartId, state.pickerWeeklyPartId)
        assertEquals(slot, state.pickerSlot)
        assertEquals(weekPlanId, state.pickerWeekPlanId)
        assertEquals("", state.pickerSearchTerm)
        assertEquals(suggestions, state.pickerSuggestions)
        assertFalse(state.isPickerLoading)
        Unit
    }

    @Test
    fun `openPersonPicker resets search term from previous session`() = runTest {
        val suggerisci = mockk<SuggerisciProclamatoriUseCase>()
        coEvery { suggerisci(any(), any(), any(), any(), any(), any(), any()) } returns emptyList()

        val vm = makeViewModel(scope = this, suggerisci = suggerisci)
        vm.openPersonPicker(weekStartDate, weeklyPartId, slot, weekPlanId)
        advanceUntilIdle()
        vm.setPickerSearchTerm("test")
        assertEquals("test", vm.state.value.pickerSearchTerm)

        // Re-open should reset
        vm.openPersonPicker(weekStartDate, weeklyPartId, slot, weekPlanId)
        advanceUntilIdle()

        assertEquals("", vm.state.value.pickerSearchTerm)
        Unit
    }

    @Test
    fun `openPersonPicker shows error notice when suggestions fail`() = runTest {
        val suggerisci = mockk<SuggerisciProclamatoriUseCase>()
        coEvery { suggerisci(any(), any(), any(), any(), any(), any(), any()) } throws RuntimeException("DB error")

        val vm = makeViewModel(scope = this, suggerisci = suggerisci)
        vm.openPersonPicker(weekStartDate, weeklyPartId, slot, weekPlanId)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isPickerLoading)
        assertNotNull(state.notice)
        assertEquals(FeedbackBannerKind.ERROR, state.notice?.kind)
        assertTrue(state.notice?.message?.contains("DB error") == true)
        assertEquals(emptyList(), state.pickerSuggestions)
        Unit
    }

    // ── closePersonPicker ─────────────────────────────────────────────────────

    @Test
    fun `closePersonPicker resets all picker state`() = runTest {
        val suggerisci = mockk<SuggerisciProclamatoriUseCase>()
        coEvery { suggerisci(any(), any(), any(), any(), any(), any(), any()) } returns listOf(
            makeSuggestion("p1", "Mario", "Rossi"),
        )

        val vm = makeViewModel(scope = this, suggerisci = suggerisci)
        vm.openPersonPicker(weekStartDate, weeklyPartId, slot, weekPlanId)
        advanceUntilIdle()
        assertTrue(vm.state.value.isPickerOpen)

        vm.closePersonPicker()

        val state = vm.state.value
        assertFalse(state.isPickerOpen)
        assertNull(state.pickerWeekStartDate)
        assertNull(state.pickerWeeklyPartId)
        assertNull(state.pickerSlot)
        assertNull(state.pickerWeekPlanId)
        assertEquals("", state.pickerSearchTerm)
        assertEquals(emptyList(), state.pickerSuggestions)
        assertFalse(state.isPickerLoading)
        assertNull(state.deliveryWarning)
        Unit
    }

    // ── reloadSuggestions ─────────────────────────────────────────────────────

    @Test
    fun `reloadSuggestions reloads when picker is open`() = runTest {
        val suggerisci = mockk<SuggerisciProclamatoriUseCase>()
        val firstSuggestions = listOf(makeSuggestion("p1", "Mario", "Rossi"))
        val secondSuggestions = listOf(
            makeSuggestion("p1", "Mario", "Rossi"),
            makeSuggestion("p2", "Luigi", "Verdi"),
        )
        coEvery { suggerisci(any(), any(), any(), any(), any(), any(), any()) } returnsMany listOf(
            firstSuggestions,
            secondSuggestions,
        )

        val vm = makeViewModel(scope = this, suggerisci = suggerisci)
        vm.openPersonPicker(weekStartDate, weeklyPartId, slot, weekPlanId)
        advanceUntilIdle()
        assertEquals(firstSuggestions, vm.state.value.pickerSuggestions)

        vm.reloadSuggestions()
        advanceUntilIdle()

        assertEquals(secondSuggestions, vm.state.value.pickerSuggestions)
        coVerify(exactly = 2) { suggerisci(any(), any(), any(), any(), any(), any(), any()) }
        Unit
    }

    @Test
    fun `reloadSuggestions preserves current search term while refreshing suggestions`() = runTest {
        val suggerisci = mockk<SuggerisciProclamatoriUseCase>()
        coEvery { suggerisci(any(), any(), any(), any(), any(), any(), any()) } returnsMany listOf(
            listOf(makeSuggestion("p1", "Mario", "Rossi")),
            listOf(makeSuggestion("p2", "Luigi", "Verdi")),
        )

        val vm = makeViewModel(scope = this, suggerisci = suggerisci)
        vm.openPersonPicker(weekStartDate, weeklyPartId, slot, weekPlanId)
        advanceUntilIdle()

        vm.setPickerSearchTerm("mari")
        vm.reloadSuggestions()
        advanceUntilIdle()

        assertEquals("mari", vm.state.value.pickerSearchTerm)
        assertEquals(ProclamatoreId("p2"), vm.state.value.pickerSuggestions.single().proclamatore.id)
    }

    @Test
    fun `reloadSuggestions passes strict cooldown override immediately`() = runTest {
        val suggerisci = mockk<SuggerisciProclamatoriUseCase>()
        coEvery { suggerisci(any(), any(), any(), any(), any(), any(), any()) } returnsMany listOf(
            listOf(makeSuggestion("p1", "Mario", "Rossi")),
            listOf(makeSuggestion("p2", "Luigi", "Verdi")),
        )

        val vm = makeViewModel(scope = this, suggerisci = suggerisci)
        vm.openPersonPicker(weekStartDate, weeklyPartId, slot, weekPlanId)
        advanceUntilIdle()

        vm.reloadSuggestions(strictCooldownOverride = false)
        advanceUntilIdle()

        coVerify {
            suggerisci(
                weekStartDate = weekStartDate,
                weeklyPartId = weeklyPartId,
                slot = slot,
                additionalExcludedIds = any(),
                rankingCache = any(),
                eligibilityCache = any(),
                strictCooldownOverride = false,
            )
        }
    }

    @Test
    fun `reloadSuggestions keeps only the latest completed result when requests overlap`() = runTest {
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val firstSuggestions = listOf(makeSuggestion("p1", "Mario", "Rossi"))
        val secondSuggestions = listOf(makeSuggestion("p2", "Luigi", "Verdi"))
        var invocation = 0
        val suggerisci = mockk<SuggerisciProclamatoriUseCase>()
        coEvery { suggerisci(any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            invocation += 1
            if (invocation == 1) {
                firstStarted.complete(Unit)
                releaseFirst.await()
                firstSuggestions
            } else {
                secondSuggestions
            }
        }

        val vm = makeViewModel(scope = this, suggerisci = suggerisci)
        vm.openPersonPicker(weekStartDate, weeklyPartId, slot, weekPlanId)
        firstStarted.await()
        vm.reloadSuggestions()
        advanceUntilIdle()
        releaseFirst.complete(Unit)
        advanceUntilIdle()

        assertEquals(secondSuggestions, vm.state.value.pickerSuggestions)
        assertFalse(vm.state.value.isPickerLoading)
    }

    // ── confirmAssignment — no delivery warning ───────────────────────────────

    @Test
    fun `confirmAssignment assigns person and closes picker when no previous delivery`() = runTest {
        val suggerisci = mockk<SuggerisciProclamatoriUseCase>()
        coEvery { suggerisci(any(), any(), any(), any(), any(), any(), any()) } returns emptyList()
        val verificaConsegna = mockk<VerificaConsegnaPreAssegnazioneUseCase>()
        coEvery { verificaConsegna(any(), any()) } returns null
        val assegna = mockk<AssegnaPersonaUseCase>()
        coEvery { assegna(any(), any(), any(), any()) } returns Either.Right(Unit)

        val vm = makeViewModel(
            scope = this,
            suggerisci = suggerisci,
            assegna = assegna,
            verificaConsegna = verificaConsegna,
        )
        vm.openPersonPicker(weekStartDate, weeklyPartId, slot, weekPlanId)
        advanceUntilIdle()

        var successCalled = false
        vm.confirmAssignment(personId) { successCalled = true }
        advanceUntilIdle()

        assertTrue(successCalled)
        assertFalse(vm.state.value.isPickerOpen) // picker closed after assignment
        assertFalse(vm.state.value.isAssigning)
        coVerify(exactly = 1) { assegna(weekStartDate, weeklyPartId, personId, slot) }
        Unit
    }

    @Test
    fun `confirmAssignment shows error when verificaConsegna throws`() = runTest {
        val suggerisci = mockk<SuggerisciProclamatoriUseCase>()
        coEvery { suggerisci(any(), any(), any(), any(), any(), any(), any()) } returns emptyList()
        val verificaConsegna = mockk<VerificaConsegnaPreAssegnazioneUseCase>()
        coEvery { verificaConsegna(any(), any()) } throws RuntimeException("connection error")

        val vm = makeViewModel(
            scope = this,
            suggerisci = suggerisci,
            verificaConsegna = verificaConsegna,
        )
        vm.openPersonPicker(weekStartDate, weeklyPartId, slot, weekPlanId)
        advanceUntilIdle()

        vm.confirmAssignment(personId) {}
        advanceUntilIdle()

        assertNotNull(vm.state.value.notice)
        assertEquals(FeedbackBannerKind.ERROR, vm.state.value.notice?.kind)
        assertTrue(vm.state.value.notice?.details?.contains("connection error") == true)
        Unit
    }

    // ── confirmAssignment — delivery warning flow ─────────────────────────────

    @Test
    fun `confirmAssignment shows delivery warning when previous delivery exists`() = runTest {
        val suggerisci = mockk<SuggerisciProclamatoriUseCase>()
        coEvery { suggerisci(any(), any(), any(), any(), any(), any(), any()) } returns emptyList()
        val verificaConsegna = mockk<VerificaConsegnaPreAssegnazioneUseCase>()
        coEvery { verificaConsegna(any(), any()) } returns "Mario Rossi"

        val vm = makeViewModel(
            scope = this,
            suggerisci = suggerisci,
            verificaConsegna = verificaConsegna,
        )
        vm.openPersonPicker(weekStartDate, weeklyPartId, slot, weekPlanId)
        advanceUntilIdle()

        vm.confirmAssignment(personId) {}
        advanceUntilIdle()

        val warning = vm.state.value.deliveryWarning
        assertNotNull(warning)
        assertEquals("Mario Rossi", warning.previousStudentName)
        assertEquals(personId, warning.pendingPersonId)
        Unit
    }

    @Test
    fun `confirmAssignmentAfterWarning cancels delivery and assigns`() = runTest {
        val suggerisci = mockk<SuggerisciProclamatoriUseCase>()
        coEvery { suggerisci(any(), any(), any(), any(), any(), any(), any()) } returns emptyList()
        val verificaConsegna = mockk<VerificaConsegnaPreAssegnazioneUseCase>()
        coEvery { verificaConsegna(any(), any()) } returns "Mario Rossi"
        val annullaConsegna = mockk<AnnullaConsegnaUseCase>()
        coEvery { annullaConsegna(any(), any()) } returns Either.Right(Unit)
        val assegna = mockk<AssegnaPersonaUseCase>()
        coEvery { assegna(any(), any(), any(), any()) } returns Either.Right(Unit)

        val vm = makeViewModel(
            scope = this,
            suggerisci = suggerisci,
            assegna = assegna,
            verificaConsegna = verificaConsegna,
            annullaConsegna = annullaConsegna,
        )
        vm.openPersonPicker(weekStartDate, weeklyPartId, slot, weekPlanId)
        advanceUntilIdle()

        // Trigger the warning
        vm.confirmAssignment(personId) {}
        advanceUntilIdle()
        assertNotNull(vm.state.value.deliveryWarning)

        // Confirm after warning
        var successCalled = false
        vm.confirmAssignmentAfterWarning { successCalled = true }
        advanceUntilIdle()

        assertTrue(successCalled)
        assertNull(vm.state.value.deliveryWarning)
        assertFalse(vm.state.value.isPickerOpen) // picker closed
        coVerify(exactly = 1) { annullaConsegna(weeklyPartId, weekPlanId) }
        coVerify(exactly = 1) { assegna(weekStartDate, weeklyPartId, personId, slot) }
        Unit
    }

    @Test
    fun `confirmAssignmentAfterWarning shows error when annullaConsegna fails`() = runTest {
        val suggerisci = mockk<SuggerisciProclamatoriUseCase>()
        coEvery { suggerisci(any(), any(), any(), any(), any(), any(), any()) } returns emptyList()
        val verificaConsegna = mockk<VerificaConsegnaPreAssegnazioneUseCase>()
        coEvery { verificaConsegna(any(), any()) } returns "Mario Rossi"
        val annullaConsegna = mockk<AnnullaConsegnaUseCase>()
        coEvery { annullaConsegna(any(), any()) } returns Either.Left(DomainError.Validation("annullamento fallito"))
        val assegna = mockk<AssegnaPersonaUseCase>()

        val vm = makeViewModel(
            scope = this,
            suggerisci = suggerisci,
            assegna = assegna,
            verificaConsegna = verificaConsegna,
            annullaConsegna = annullaConsegna,
        )
        vm.openPersonPicker(weekStartDate, weeklyPartId, slot, weekPlanId)
        advanceUntilIdle()

        vm.confirmAssignment(personId) {}
        advanceUntilIdle()

        var successCalled = false
        vm.confirmAssignmentAfterWarning { successCalled = true }
        advanceUntilIdle()

        assertFalse(successCalled)
        assertNotNull(vm.state.value.notice)
        assertEquals(FeedbackBannerKind.ERROR, vm.state.value.notice?.kind)
        coVerify(exactly = 0) { assegna(any(), any(), any(), any()) }
        Unit
    }

    @Test
    fun `confirmAssignmentAfterWarning does nothing when no warning present`() = runTest {
        val annullaConsegna = mockk<AnnullaConsegnaUseCase>()

        val vm = makeViewModel(scope = this, annullaConsegna = annullaConsegna)

        var successCalled = false
        vm.confirmAssignmentAfterWarning { successCalled = true }
        advanceUntilIdle()

        assertFalse(successCalled)
        coVerify(exactly = 0) { annullaConsegna(any(), any()) }
        Unit
    }

    // ── dismissDeliveryWarning ────────────────────────────────────────────────

    @Test
    fun `dismissDeliveryWarning clears the warning`() = runTest {
        val suggerisci = mockk<SuggerisciProclamatoriUseCase>()
        coEvery { suggerisci(any(), any(), any(), any(), any(), any(), any()) } returns emptyList()
        val verificaConsegna = mockk<VerificaConsegnaPreAssegnazioneUseCase>()
        coEvery { verificaConsegna(any(), any()) } returns "Mario Rossi"

        val vm = makeViewModel(
            scope = this,
            suggerisci = suggerisci,
            verificaConsegna = verificaConsegna,
        )
        vm.openPersonPicker(weekStartDate, weeklyPartId, slot, weekPlanId)
        advanceUntilIdle()

        vm.confirmAssignment(personId) {}
        advanceUntilIdle()
        assertNotNull(vm.state.value.deliveryWarning)

        vm.dismissDeliveryWarning()

        assertNull(vm.state.value.deliveryWarning)
        Unit
    }

    // ── confirmAssignment busy guard ──────────────────────────────────────────

    @Test
    fun `confirmAssignment ignores second call while assigning`() = runTest {
        val suggerisci = mockk<SuggerisciProclamatoriUseCase>()
        coEvery { suggerisci(any(), any(), any(), any(), any(), any(), any()) } returns emptyList()
        val verificaConsegna = mockk<VerificaConsegnaPreAssegnazioneUseCase>()
        coEvery { verificaConsegna(any(), any()) } returns null
        val blocker = CompletableDeferred<Either<DomainError, Unit>>()
        val assegna = mockk<AssegnaPersonaUseCase>()
        coEvery { assegna(any(), any(), any(), any()) } coAnswers { blocker.await() }

        val vm = makeViewModel(
            scope = this,
            suggerisci = suggerisci,
            assegna = assegna,
            verificaConsegna = verificaConsegna,
        )
        vm.openPersonPicker(weekStartDate, weeklyPartId, slot, weekPlanId)
        advanceUntilIdle()

        vm.confirmAssignment(personId) {}
        advanceUntilIdle()
        assertTrue(vm.state.value.isAssigning)

        // Second call should be ignored
        vm.confirmAssignment(personId) {}

        // Only one assegna call should have been made
        coVerify(exactly = 1) { assegna(any(), any(), any(), any()) }

        blocker.complete(Either.Right(Unit))
        advanceUntilIdle()
        assertFalse(vm.state.value.isAssigning)
        Unit
    }

    // ── removeAssignment ──────────────────────────────────────────────────────

    @Test
    fun `removeAssignment calls use case and triggers onSuccess`() = runTest {
        val rimuovi = mockk<RimuoviAssegnazioneUseCase>()
        coEvery { rimuovi(any()) } returns Either.Right(Unit)

        val vm = makeViewModel(scope = this, rimuovi = rimuovi)
        val assignmentId = AssignmentId("a-1")

        var successCalled = false
        vm.removeAssignment(assignmentId) { successCalled = true }
        advanceUntilIdle()

        assertTrue(successCalled)
        assertFalse(vm.state.value.isRemovingAssignment)
        coVerify(exactly = 1) { rimuovi(assignmentId) }
        Unit
    }

    @Test
    fun `removeAssignment ignores second call while removing`() = runTest {
        val blocker = CompletableDeferred<Either<DomainError, Unit>>()
        val rimuovi = mockk<RimuoviAssegnazioneUseCase>()
        coEvery { rimuovi(any()) } coAnswers { blocker.await() }

        val vm = makeViewModel(scope = this, rimuovi = rimuovi)
        val assignmentId = AssignmentId("a-1")

        vm.removeAssignment(assignmentId) {}
        advanceUntilIdle()
        assertTrue(vm.state.value.isRemovingAssignment)

        // Second call should be ignored
        vm.removeAssignment(assignmentId) {}

        coVerify(exactly = 1) { rimuovi(any()) }

        blocker.complete(Either.Right(Unit))
        advanceUntilIdle()
        assertFalse(vm.state.value.isRemovingAssignment)
        Unit
    }

    // ── dismissNotice ─────────────────────────────────────────────────────────

    @Test
    fun `dismissNotice clears notice`() = runTest {
        val suggerisci = mockk<SuggerisciProclamatoriUseCase>()
        coEvery { suggerisci(any(), any(), any(), any(), any(), any(), any()) } throws RuntimeException("fail")

        val vm = makeViewModel(scope = this, suggerisci = suggerisci)
        vm.openPersonPicker(weekStartDate, weeklyPartId, slot, weekPlanId)
        advanceUntilIdle()
        assertNotNull(vm.state.value.notice)

        vm.dismissNotice()

        assertNull(vm.state.value.notice)
        Unit
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun makeViewModel(
        scope: kotlinx.coroutines.CoroutineScope,
        assegna: AssegnaPersonaUseCase = mockk(relaxed = true),
        rimuovi: RimuoviAssegnazioneUseCase = mockk(relaxed = true),
        suggerisci: SuggerisciProclamatoriUseCase = mockk(relaxed = true),
        verificaConsegna: VerificaConsegnaPreAssegnazioneUseCase = mockk(relaxed = true),
        annullaConsegna: AnnullaConsegnaUseCase = mockk(relaxed = true),
    ) = PersonPickerViewModel(
        scope = scope,
        assegnaPersona = assegna,
        rimuoviAssegnazione = rimuovi,
        suggerisciProclamatori = suggerisci,
        verificaConsegna = verificaConsegna,
        annullaConsegna = annullaConsegna,
    )

    private fun makeSuggestion(
        id: String,
        nome: String,
        cognome: String,
    ) = SuggestedProclamatore(
        proclamatore = Proclamatore.of(
            id = ProclamatoreId(id),
            nome = nome,
            cognome = cognome,
            sesso = Sesso.M,
        ).getOrNull()!!,
        lastGlobalWeeks = 5,
        lastForPartTypeWeeks = null,
        lastConductorWeeks = null,
    )
}

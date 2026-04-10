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
import org.example.project.feature.weeklyparts.application.AggiornaPartiSettimanaUseCase
import org.example.project.feature.weeklyparts.application.CercaTipiParteUseCase
import org.example.project.feature.weeklyparts.application.ImpostaStatoSettimanaUseCase
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.SexRule
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeekPlanStatus
import org.example.project.feature.weeklyparts.domain.WeeklyPart
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

class PartEditorViewModelTest {

    // A future Monday that will always pass canBeMutated
    private val futureMonday = LocalDate.of(2099, 1, 4) // Monday

    private val editablePartType = PartType(
        id = PartTypeId("pt-edit"),
        code = "LETTURA",
        label = "Lettura",
        peopleCount = 1,
        sexRule = SexRule.STESSO_SESSO,
        fixed = false,
        sortOrder = 0,
    )

    private val fixedPartType = PartType(
        id = PartTypeId("pt-fixed"),
        code = "PREGHIERA",
        label = "Preghiera",
        peopleCount = 1,
        sexRule = SexRule.UOMO,
        fixed = true,
        sortOrder = 1,
    )

    private val editablePartType2 = PartType(
        id = PartTypeId("pt-edit-2"),
        code = "DISCORSO",
        label = "Discorso",
        peopleCount = 2,
        sexRule = SexRule.STESSO_SESSO,
        fixed = false,
        sortOrder = 2,
    )

    private val allPartTypes = listOf(editablePartType, fixedPartType, editablePartType2)

    @AfterTest
    fun tearDown() = unmockkAll()

    // ── init / loadPartTypes ────────────────────────────────────────────────

    @Test
    fun `init loadPartTypes error sets notice`() = runTest {
        val cercaTipiParte = mockk<CercaTipiParteUseCase>()
        coEvery { cercaTipiParte() } throws RuntimeException("db failure")

        val vm = makeViewModel(scope = this, cercaTipiParte = cercaTipiParte)
        advanceUntilIdle()

        assertNotNull(vm.state.value.notice)
        assertEquals(FeedbackBannerKind.ERROR, vm.state.value.notice?.kind)
        assertTrue(vm.state.value.notice?.message?.contains("db failure") == true)
    }

    // ── dismissNotice ───────────────────────────────────────────────────────

    @Test
    fun `dismissNotice clears notice from state`() = runTest {
        val cercaTipiParte = mockk<CercaTipiParteUseCase>()
        coEvery { cercaTipiParte() } throws RuntimeException("error")

        val vm = makeViewModel(scope = this, cercaTipiParte = cercaTipiParte)
        advanceUntilIdle()
        assertNotNull(vm.state.value.notice)

        vm.dismissNotice()

        assertNull(vm.state.value.notice)
    }

    // ── openPartEditor ──────────────────────────────────────────────────────

    @Test
    fun `openPartEditor sets editor state for mutable week`() = runTest {
        val vm = makeViewModel(scope = this, partTypes = allPartTypes)
        advanceUntilIdle()

        val week = makeWeekPlan(futureMonday)
        vm.openPartEditor(week)

        assertTrue(vm.state.value.isPartEditorOpen)
        assertEquals(week.id.value, vm.state.value.partEditorWeekId)
        assertEquals(week.parts.size, vm.state.value.partEditorParts.size)
    }

    @Test
    fun `openPartEditor sorts parts by sortOrder`() = runTest {
        val vm = makeViewModel(scope = this, partTypes = allPartTypes)
        advanceUntilIdle()

        val parts = listOf(
            makeWeeklyPart("p2", editablePartType2, sortOrder = 1),
            makeWeeklyPart("p1", editablePartType, sortOrder = 0),
        )
        val week = makeWeekPlan(futureMonday, parts = parts)
        vm.openPartEditor(week)

        assertEquals(0, vm.state.value.partEditorParts[0].sortOrder)
        assertEquals(1, vm.state.value.partEditorParts[1].sortOrder)
    }

    @Test
    fun `openPartEditor opens past week and marks historical mode`() = runTest {
        val vm = makeViewModel(scope = this, partTypes = allPartTypes)
        advanceUntilIdle()

        val pastMonday = LocalDate.of(2020, 1, 6) // Past Monday
        val week = makeWeekPlan(pastMonday)
        vm.openPartEditor(week)

        assertTrue(vm.state.value.isPartEditorOpen)
        assertTrue(vm.state.value.partEditorIsPast)
    }

    // ── dismissPartEditor ───────────────────────────────────────────────────

    @Test
    fun `dismissPartEditor clears editor state`() = runTest {
        val vm = makeViewModel(scope = this, partTypes = allPartTypes)
        advanceUntilIdle()

        vm.openPartEditor(makeWeekPlan(futureMonday))
        assertTrue(vm.state.value.isPartEditorOpen)

        vm.dismissPartEditor()

        assertFalse(vm.state.value.isPartEditorOpen)
        assertNull(vm.state.value.partEditorWeekId)
        assertFalse(vm.state.value.partEditorIsPast)
        assertTrue(vm.state.value.partEditorParts.isEmpty())
    }

    // ── addPartToEditor ─────────────────────────────────────────────────────

    @Test
    fun `addPartToEditor adds new part with correct sortOrder`() = runTest {
        val vm = makeViewModel(scope = this, partTypes = allPartTypes)
        advanceUntilIdle()

        vm.openPartEditor(makeWeekPlan(futureMonday, parts = emptyList()))
        assertEquals(0, vm.state.value.partEditorParts.size)

        vm.addPartToEditor(editablePartType.id)

        assertEquals(1, vm.state.value.partEditorParts.size)
        assertEquals(editablePartType.id, vm.state.value.partEditorParts[0].partType.id)
        assertEquals(0, vm.state.value.partEditorParts[0].sortOrder)
    }

    @Test
    fun `addPartToEditor appends to existing parts`() = runTest {
        val vm = makeViewModel(scope = this, partTypes = allPartTypes)
        advanceUntilIdle()

        val existingPart = makeWeeklyPart("p1", editablePartType, sortOrder = 0)
        vm.openPartEditor(makeWeekPlan(futureMonday, parts = listOf(existingPart)))

        vm.addPartToEditor(editablePartType2.id)

        assertEquals(2, vm.state.value.partEditorParts.size)
        assertEquals(1, vm.state.value.partEditorParts[1].sortOrder)
    }

    @Test
    fun `addPartToEditor ignores unknown partTypeId`() = runTest {
        val vm = makeViewModel(scope = this, partTypes = allPartTypes)
        advanceUntilIdle()

        vm.openPartEditor(makeWeekPlan(futureMonday, parts = emptyList()))
        vm.addPartToEditor(PartTypeId("non-existent"))

        assertEquals(0, vm.state.value.partEditorParts.size)
    }

    @Test
    fun `addPartToEditor ignores fixed partType`() = runTest {
        val vm = makeViewModel(scope = this, partTypes = allPartTypes)
        advanceUntilIdle()

        vm.openPartEditor(makeWeekPlan(futureMonday, parts = emptyList()))
        vm.addPartToEditor(fixedPartType.id)

        assertEquals(0, vm.state.value.partEditorParts.size)
    }

    // ── movePartInEditor ────────────────────────────────────────────────────

    @Test
    fun `movePartInEditor swaps parts and recompacts sortOrders`() = runTest {
        val vm = makeViewModel(scope = this, partTypes = allPartTypes)
        advanceUntilIdle()

        val parts = listOf(
            makeWeeklyPart("p1", editablePartType, sortOrder = 0),
            makeWeeklyPart("p2", editablePartType2, sortOrder = 1),
        )
        vm.openPartEditor(makeWeekPlan(futureMonday, parts = parts))

        vm.movePartInEditor(0, 1)

        val moved = vm.state.value.partEditorParts
        assertEquals("p2", moved[0].id.value)
        assertEquals(0, moved[0].sortOrder)
        assertEquals("p1", moved[1].id.value)
        assertEquals(1, moved[1].sortOrder)
    }

    @Test
    fun `movePartInEditor ignores same index`() = runTest {
        val vm = makeViewModel(scope = this, partTypes = allPartTypes)
        advanceUntilIdle()

        val parts = listOf(
            makeWeeklyPart("p1", editablePartType, sortOrder = 0),
            makeWeeklyPart("p2", editablePartType2, sortOrder = 1),
        )
        vm.openPartEditor(makeWeekPlan(futureMonday, parts = parts))

        vm.movePartInEditor(0, 0)

        assertEquals("p1", vm.state.value.partEditorParts[0].id.value)
        assertEquals("p2", vm.state.value.partEditorParts[1].id.value)
    }

    // ── removePartFromEditor ────────────────────────────────────────────────

    @Test
    fun `removePartFromEditor removes part and recompacts sortOrders`() = runTest {
        val vm = makeViewModel(scope = this, partTypes = allPartTypes)
        advanceUntilIdle()

        val parts = listOf(
            makeWeeklyPart("p1", editablePartType, sortOrder = 0),
            makeWeeklyPart("p2", editablePartType2, sortOrder = 1),
        )
        vm.openPartEditor(makeWeekPlan(futureMonday, parts = parts))

        vm.removePartFromEditor(WeeklyPartId("p1"))

        assertEquals(1, vm.state.value.partEditorParts.size)
        assertEquals("p2", vm.state.value.partEditorParts[0].id.value)
        assertEquals(0, vm.state.value.partEditorParts[0].sortOrder)
    }

    @Test
    fun `removePartFromEditor ignores fixed part`() = runTest {
        val vm = makeViewModel(scope = this, partTypes = allPartTypes)
        advanceUntilIdle()

        val parts = listOf(
            makeWeeklyPart("p-fixed", fixedPartType, sortOrder = 0),
            makeWeeklyPart("p2", editablePartType, sortOrder = 1),
        )
        vm.openPartEditor(makeWeekPlan(futureMonday, parts = parts))

        vm.removePartFromEditor(WeeklyPartId("p-fixed"))

        assertEquals(2, vm.state.value.partEditorParts.size)
    }

    // ── savePartEditor ──────────────────────────────────────────────────────

    @Test
    fun `savePartEditor calls use case and invokes onSuccess`() = runTest {
        val aggiorna = mockk<AggiornaPartiSettimanaUseCase>()
        coEvery { aggiorna(any(), any()) } returns Either.Right(Unit)

        val vm = makeViewModel(scope = this, partTypes = allPartTypes, aggiorna = aggiorna)
        advanceUntilIdle()

        val parts = listOf(makeWeeklyPart("p1", editablePartType, sortOrder = 0))
        vm.openPartEditor(makeWeekPlan(futureMonday, parts = parts))

        var successCalled = false
        vm.savePartEditor(onSuccess = { successCalled = true })
        advanceUntilIdle()

        assertTrue(successCalled)
        assertFalse(vm.state.value.isPartEditorOpen)
        assertFalse(vm.state.value.isSavingPartEditor)
    }

    @Test
    fun `savePartEditor success shows success notice`() = runTest {
        val aggiorna = mockk<AggiornaPartiSettimanaUseCase>()
        coEvery { aggiorna(any(), any()) } returns Either.Right(Unit)

        val vm = makeViewModel(scope = this, partTypes = allPartTypes, aggiorna = aggiorna)
        advanceUntilIdle()

        val parts = listOf(makeWeeklyPart("p1", editablePartType, sortOrder = 0))
        vm.openPartEditor(makeWeekPlan(futureMonday, parts = parts))

        vm.savePartEditor(onSuccess = {})
        advanceUntilIdle()

        assertNotNull(vm.state.value.notice)
        assertEquals(FeedbackBannerKind.SUCCESS, vm.state.value.notice?.kind)
    }

    @Test
    fun `savePartEditor passes ordered partTypeIds to use case`() = runTest {
        val aggiorna = mockk<AggiornaPartiSettimanaUseCase>()
        coEvery { aggiorna(any(), any()) } returns Either.Right(Unit)

        val vm = makeViewModel(scope = this, partTypes = allPartTypes, aggiorna = aggiorna)
        advanceUntilIdle()

        val parts = listOf(
            makeWeeklyPart("p1", editablePartType, sortOrder = 0),
            makeWeeklyPart("p2", editablePartType2, sortOrder = 1),
        )
        val week = makeWeekPlan(futureMonday, parts = parts)
        vm.openPartEditor(week)

        vm.savePartEditor(onSuccess = {})
        advanceUntilIdle()

        coVerify {
            aggiorna(
                WeekPlanId(week.id.value),
                listOf(editablePartType.id, editablePartType2.id),
            )
        }
        Unit
    }

    @Test
    fun `savePartEditor with empty parts shows error notice without calling use case`() = runTest {
        val aggiorna = mockk<AggiornaPartiSettimanaUseCase>()

        val vm = makeViewModel(scope = this, partTypes = allPartTypes, aggiorna = aggiorna)
        advanceUntilIdle()

        vm.openPartEditor(makeWeekPlan(futureMonday, parts = emptyList()))

        vm.savePartEditor(onSuccess = {})
        advanceUntilIdle()

        assertNotNull(vm.state.value.notice)
        assertEquals(FeedbackBannerKind.ERROR, vm.state.value.notice?.kind)
        assertTrue(vm.state.value.notice?.message?.contains("almeno una parte") == true)
        coVerify(exactly = 0) { aggiorna(any(), any()) }
    }

    @Test
    fun `savePartEditor with no editor open does nothing`() = runTest {
        val aggiorna = mockk<AggiornaPartiSettimanaUseCase>()

        val vm = makeViewModel(scope = this, partTypes = allPartTypes, aggiorna = aggiorna)
        advanceUntilIdle()

        assertFalse(vm.state.value.isPartEditorOpen)

        var successCalled = false
        vm.savePartEditor(onSuccess = { successCalled = true })
        advanceUntilIdle()

        assertFalse(successCalled)
        coVerify(exactly = 0) { aggiorna(any(), any()) }
    }

    @Test
    fun `savePartEditor error shows error notice`() = runTest {
        val aggiorna = mockk<AggiornaPartiSettimanaUseCase>()
        coEvery { aggiorna(any(), any()) } returns Either.Left(DomainError.OrdinePartiNonValido)

        val vm = makeViewModel(scope = this, partTypes = allPartTypes, aggiorna = aggiorna)
        advanceUntilIdle()

        val parts = listOf(makeWeeklyPart("p1", editablePartType, sortOrder = 0))
        vm.openPartEditor(makeWeekPlan(futureMonday, parts = parts))

        vm.savePartEditor(onSuccess = {})
        advanceUntilIdle()

        assertNotNull(vm.state.value.notice)
        assertEquals(FeedbackBannerKind.ERROR, vm.state.value.notice?.kind)
    }

    @Test
    fun `savePartEditor does not call use case if already saving`() = runTest {
        val blocker = CompletableDeferred<Unit>()
        val aggiorna = mockk<AggiornaPartiSettimanaUseCase>()
        coEvery { aggiorna(any(), any()) } coAnswers {
            blocker.await()
            Either.Right(Unit)
        }

        val vm = makeViewModel(scope = this, partTypes = allPartTypes, aggiorna = aggiorna)
        advanceUntilIdle()

        val parts = listOf(makeWeeklyPart("p1", editablePartType, sortOrder = 0))
        vm.openPartEditor(makeWeekPlan(futureMonday, parts = parts))

        vm.savePartEditor(onSuccess = {})
        advanceUntilIdle() // runs to blocker.await(), isSavingPartEditor = true

        assertTrue(vm.state.value.isSavingPartEditor)
        vm.savePartEditor(onSuccess = {}) // second call ignored

        blocker.complete(Unit)
        advanceUntilIdle()

        coVerify(exactly = 1) { aggiorna(any(), any()) }
    }

    @Test
    fun `savePartEditor error does not call onSuccess`() = runTest {
        val aggiorna = mockk<AggiornaPartiSettimanaUseCase>()
        coEvery { aggiorna(any(), any()) } returns Either.Left(DomainError.Validation("fail"))

        val vm = makeViewModel(scope = this, partTypes = allPartTypes, aggiorna = aggiorna)
        advanceUntilIdle()

        val parts = listOf(makeWeeklyPart("p1", editablePartType, sortOrder = 0))
        vm.openPartEditor(makeWeekPlan(futureMonday, parts = parts))

        var successCalled = false
        vm.savePartEditor(onSuccess = { successCalled = true })
        advanceUntilIdle()

        assertFalse(successCalled)
    }

    // ── reactivateWeek ──────────────────────────────────────────────────────

    @Test
    fun `reactivateWeek success calls onSuccess and clears notice`() = runTest {
        val impostaStato = mockk<ImpostaStatoSettimanaUseCase>()
        coEvery { impostaStato(any(), any(), any()) } returns Either.Right(Unit)

        val vm = makeViewModel(scope = this, partTypes = allPartTypes, impostaStato = impostaStato)
        advanceUntilIdle()

        val week = makeWeekPlan(futureMonday, status = WeekPlanStatus.SKIPPED)
        var successCalled = false
        vm.reactivateWeek(week, onSuccess = { successCalled = true })
        advanceUntilIdle()

        assertTrue(successCalled)
        assertNull(vm.state.value.notice)
        coVerify { impostaStato(week.id, WeekPlanStatus.ACTIVE, any()) }
        Unit
    }

    @Test
    fun `reactivateWeek error shows error notice and does not call onSuccess`() = runTest {
        val impostaStato = mockk<ImpostaStatoSettimanaUseCase>()
        coEvery { impostaStato(any(), any(), any()) } returns Either.Left(DomainError.Validation("reactivation failed"))

        val vm = makeViewModel(scope = this, partTypes = allPartTypes, impostaStato = impostaStato)
        advanceUntilIdle()

        val week = makeWeekPlan(futureMonday)
        var successCalled = false
        vm.reactivateWeek(week, onSuccess = { successCalled = true })
        advanceUntilIdle()

        assertFalse(successCalled)
        assertNotNull(vm.state.value.notice)
        assertEquals(FeedbackBannerKind.ERROR, vm.state.value.notice?.kind)
        assertTrue(vm.state.value.notice?.message?.contains("riattivazione") == true)
    }

    // ── skipWeek ────────────────────────────────────────────────────────────

    @Test
    fun `skipWeek success calls onSuccess and clears notice`() = runTest {
        val impostaStato = mockk<ImpostaStatoSettimanaUseCase>()
        coEvery { impostaStato(any(), any(), any()) } returns Either.Right(Unit)

        val vm = makeViewModel(scope = this, partTypes = allPartTypes, impostaStato = impostaStato)
        advanceUntilIdle()

        val week = makeWeekPlan(futureMonday)
        var successCalled = false
        vm.skipWeek(week, onSuccess = { successCalled = true })
        advanceUntilIdle()

        assertTrue(successCalled)
        assertNull(vm.state.value.notice)
        coVerify { impostaStato(week.id, WeekPlanStatus.SKIPPED, any()) }
        Unit
    }

    @Test
    fun `skipWeek error shows error notice and does not call onSuccess`() = runTest {
        val impostaStato = mockk<ImpostaStatoSettimanaUseCase>()
        coEvery { impostaStato(any(), any(), any()) } returns Either.Left(DomainError.SettimanaImmutabile)

        val vm = makeViewModel(scope = this, partTypes = allPartTypes, impostaStato = impostaStato)
        advanceUntilIdle()

        val week = makeWeekPlan(futureMonday)
        var successCalled = false
        vm.skipWeek(week, onSuccess = { successCalled = true })
        advanceUntilIdle()

        assertFalse(successCalled)
        assertNotNull(vm.state.value.notice)
        assertEquals(FeedbackBannerKind.ERROR, vm.state.value.notice?.kind)
        assertTrue(vm.state.value.notice?.message?.contains("salto settimana") == true)
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun makeViewModel(
        scope: kotlinx.coroutines.CoroutineScope,
        partTypes: List<PartType> = emptyList(),
        aggiorna: AggiornaPartiSettimanaUseCase = mockk(relaxed = true),
        impostaStato: ImpostaStatoSettimanaUseCase = mockk(relaxed = true),
        cercaTipiParte: CercaTipiParteUseCase = mockk<CercaTipiParteUseCase>().also {
            coEvery { it() } returns partTypes
        },
    ) = PartEditorViewModel(
        scope = scope,
        aggiornaPartiSettimana = aggiorna,
        impostaStatoSettimana = impostaStato,
        cercaTipiParte = cercaTipiParte,
    )

    private fun makeWeekPlan(
        weekStartDate: LocalDate = futureMonday,
        parts: List<WeeklyPart> = listOf(
            makeWeeklyPart("p1", editablePartType, sortOrder = 0),
        ),
        status: WeekPlanStatus = WeekPlanStatus.ACTIVE,
        id: String = "week-1",
    ) = WeekPlan(
        id = WeekPlanId(id),
        weekStartDate = weekStartDate,
        parts = parts,
        status = status,
    )

    private fun makeWeeklyPart(
        id: String,
        partType: PartType,
        sortOrder: Int,
    ) = WeeklyPart(
        id = WeeklyPartId(id),
        partType = partType,
        sortOrder = sortOrder,
    )
}

package org.example.project.ui.proclamatori

import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.example.project.feature.assignments.application.CaricaUltimeAssegnazioniPerParteProclamatoreUseCase
import org.example.project.feature.people.application.AggiornaProclamatoreUseCase
import org.example.project.feature.people.application.CaricaIdoneitaProclamatoreUseCase
import org.example.project.feature.people.application.CaricaProclamatoreUseCase
import org.example.project.feature.people.application.CreaProclamatoreUseCase
import org.example.project.feature.people.application.ImpostaIdoneitaConduzioneUseCase
import org.example.project.feature.people.application.VerificaDuplicatoProclamatoreUseCase
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.domain.Sesso
import org.example.project.feature.weeklyparts.application.PartTypeStore
import org.example.project.feature.weeklyparts.application.PartTypeWithStatus
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.SexRule
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProclamatoreFormViewModelTest {

    @AfterTest
    fun tearDown() = unmockkAll()

    // ── setSesso ──────────────────────────────────────────────────────────────

    @Test
    fun `setSesso a Donna disabilita e deseleziona opzioni UOMO`() = runTest {
        val uomoId = PartTypeId("pt-uomo")
        val partTypeStore = mockk<PartTypeStore>()
        coEvery { partTypeStore.allWithStatus() } returns listOf(
            PartTypeWithStatus(
                partType = PartType(
                    id = uomoId,
                    code = "U",
                    label = "Parte Uomo",
                    peopleCount = 2,
                    sexRule = SexRule.UOMO,
                    fixed = false,
                    sortOrder = 0,
                ),
                active = true,
            ),
        )

        val vm = makeViewModel(scope = this, partTypeStore = partTypeStore)
        vm.prepareForNew()
        advanceUntilIdle()

        // Inizialmente sesso=M, opzione UOMO selezionabile
        assertTrue(vm.uiState.value.leadEligibilityOptions.single().canSelect)

        // Seleziona l'idoneita' per la parte UOMO
        vm.setLeadEligibility(uomoId, true)
        assertTrue(vm.uiState.value.leadEligibilityOptions.single().checked)

        // Cambia a Donna
        vm.setSesso(Sesso.F)

        val option = vm.uiState.value.leadEligibilityOptions.single()
        assertFalse(option.canSelect, "Opzione UOMO deve essere non selezionabile per Donna")
        assertFalse(option.checked, "Opzione UOMO deve essere deselezionata automaticamente")
    }

    @Test
    fun `setSesso a Uomo abilita opzioni UOMO`() = runTest {
        val uomoId = PartTypeId("pt-uomo")
        val partTypeStore = mockk<PartTypeStore>()
        coEvery { partTypeStore.allWithStatus() } returns listOf(
            PartTypeWithStatus(
                partType = PartType(
                    id = uomoId,
                    code = "U",
                    label = "Parte Uomo",
                    peopleCount = 2,
                    sexRule = SexRule.UOMO,
                    fixed = false,
                    sortOrder = 0,
                ),
                active = true,
            ),
        )

        val vm = makeViewModel(scope = this, partTypeStore = partTypeStore)
        vm.prepareForNew()
        advanceUntilIdle()

        // Prima imposta Donna (disabilita)
        vm.setSesso(Sesso.F)
        assertFalse(vm.uiState.value.leadEligibilityOptions.single().canSelect)

        // Torna a Uomo
        vm.setSesso(Sesso.M)
        assertTrue(vm.uiState.value.leadEligibilityOptions.single().canSelect)
    }

    // ── setAllEligibility ─────────────────────────────────────────────────────

    @Test
    fun `setAllEligibility false azzera puoAssistere e tutte le opzioni di conduzione`() = runTest {
        val partTypeStore = mockk<PartTypeStore>()
        coEvery { partTypeStore.allWithStatus() } returns listOf(
            PartTypeWithStatus(
                partType = PartType(
                    id = PartTypeId("pt-1"),
                    code = "P1",
                    label = "Parte 1",
                    peopleCount = 2,
                    sexRule = SexRule.STESSO_SESSO,
                    fixed = false,
                    sortOrder = 0,
                ),
                active = true,
            ),
        )

        val vm = makeViewModel(scope = this, partTypeStore = partTypeStore)
        vm.prepareForNew()
        advanceUntilIdle()

        // Abilita tutto
        vm.setAllEligibility(true)
        assertTrue(vm.uiState.value.puoAssistere)
        assertTrue(vm.uiState.value.leadEligibilityOptions.all { it.checked })

        // Disabilita tutto
        vm.setAllEligibility(false)
        assertFalse(vm.uiState.value.puoAssistere)
        assertTrue(vm.uiState.value.leadEligibilityOptions.all { !it.checked })
    }

    // ── scheduleDuplicateCheck debounce ───────────────────────────────────────

    @Test
    fun `scheduleDuplicateCheck esegue un solo check dopo 250ms di attesa`() = runTest {
        var checkCount = 0
        val verificaDuplicato = mockk<VerificaDuplicatoProclamatoreUseCase>()
        coEvery { verificaDuplicato(any(), any(), any()) } answers {
            checkCount++
            false
        }

        val vm = makeViewModel(scope = this, verificaDuplicato = verificaDuplicato)

        vm.setNome("Mario")
        vm.setCognome("Rossi")

        // Tre chiamate rapide in successione
        vm.scheduleDuplicateCheck(isFormRoute = true, currentEditId = null)
        vm.scheduleDuplicateCheck(isFormRoute = true, currentEditId = null)
        vm.scheduleDuplicateCheck(isFormRoute = true, currentEditId = null)

        // Prima dei 250ms il check non e' ancora partito
        advanceTimeBy(249)
        assertEquals(0, checkCount)

        // Dopo 250ms il check viene eseguito una sola volta (le prime due erano state cancellate)
        advanceTimeBy(1)
        advanceUntilIdle()
        assertEquals(1, checkCount)
    }

    @Test
    fun `scheduleDuplicateCheck imposta duplicateError quando il nome esiste gia'`() = runTest {
        val verificaDuplicato = mockk<VerificaDuplicatoProclamatoreUseCase>()
        coEvery { verificaDuplicato(any(), any(), any()) } returns true

        val vm = makeViewModel(scope = this, verificaDuplicato = verificaDuplicato)
        vm.setNome("Mario")
        vm.setCognome("Rossi")

        vm.scheduleDuplicateCheck(isFormRoute = true, currentEditId = null)
        advanceUntilIdle()

        assertEquals(
            "Esiste già uno studente con questo nome e cognome",
            vm.uiState.value.duplicateError,
        )
        assertFalse(vm.uiState.value.isCheckingDuplicate)
    }

    @Test
    fun `scheduleDuplicateCheck azzera duplicateError quando il nome non esiste`() = runTest {
        val verificaDuplicato = mockk<VerificaDuplicatoProclamatoreUseCase>()
        coEvery { verificaDuplicato(any(), any(), any()) } returns false

        val vm = makeViewModel(scope = this, verificaDuplicato = verificaDuplicato)
        vm.setNome("Mario")
        vm.setCognome("Rossi")

        vm.scheduleDuplicateCheck(isFormRoute = true, currentEditId = null)
        advanceUntilIdle()

        assertNull(vm.uiState.value.duplicateError)
    }

    @Test
    fun `scheduleDuplicateCheck non parte se nome o cognome sono blank`() = runTest {
        var checkCount = 0
        val verificaDuplicato = mockk<VerificaDuplicatoProclamatoreUseCase>()
        coEvery { verificaDuplicato(any(), any(), any()) } answers {
            checkCount++
            false
        }

        val vm = makeViewModel(scope = this, verificaDuplicato = verificaDuplicato)
        vm.setNome("")
        vm.setCognome("Rossi")

        vm.scheduleDuplicateCheck(isFormRoute = true, currentEditId = null)
        advanceUntilIdle()

        assertEquals(0, checkCount)
    }

    @Test
    fun `loadForEdit carica ultima assegnazione per ogni parte`() = runTest {
        val personId = ProclamatoreId("p1")
        val partTypeId = PartTypeId("pt-1")
        val loaded = Proclamatore.of(
            id = personId,
            nome = "Mario",
            cognome = "Rossi",
            sesso = Sesso.M,
            sospeso = false,
            puoAssistere = true,
        ).getOrNull()!!
        val carica = mockk<CaricaProclamatoreUseCase>()
        val caricaIdoneita = mockk<CaricaIdoneitaProclamatoreUseCase>()
        val caricaUltime = mockk<CaricaUltimeAssegnazioniPerParteProclamatoreUseCase>()
        val partTypeStore = mockk<PartTypeStore>()
        coEvery { carica(personId) } returns loaded
        coEvery { caricaIdoneita(personId) } returns emptyList()
        coEvery { partTypeStore.allWithStatus() } returns listOf(
            PartTypeWithStatus(
                partType = PartType(
                    id = partTypeId,
                    code = "P1",
                    label = "Parte 1",
                    peopleCount = 1,
                    sexRule = SexRule.STESSO_SESSO,
                    fixed = false,
                    sortOrder = 0,
                ),
                active = true,
            ),
        )
        coEvery {
            caricaUltime(personId = personId, partTypeIds = setOf(partTypeId))
        } returns mapOf(partTypeId to LocalDate.of(2026, 4, 7))
        coEvery { caricaUltime.lastAssistantDate(personId) } returns null

        val vm = ProclamatoreFormViewModel(
            scope = this,
            carica = carica,
            caricaIdoneita = caricaIdoneita,
            caricaUltimeAssegnazioniPerParte = caricaUltime,
            crea = mockk(relaxed = true),
            aggiorna = mockk(relaxed = true),
            impostaIdoneitaConduzione = mockk(relaxed = true),
            partTypeStore = partTypeStore,
            verificaDuplicato = mockk(relaxed = true),
        )

        vm.loadForEdit(personId, onNotFound = {}, onSuccess = {})
        advanceUntilIdle()

        assertEquals(
            LocalDate.of(2026, 4, 7),
            vm.uiState.value.leadEligibilityOptions.single().lastAssignedOn,
        )
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun makeViewModel(
        scope: kotlinx.coroutines.CoroutineScope,
        partTypeStore: PartTypeStore = mockk<PartTypeStore>(relaxed = true),
        verificaDuplicato: VerificaDuplicatoProclamatoreUseCase = mockk(relaxed = true),
    ) = ProclamatoreFormViewModel(
        scope = scope,
        carica = mockk<CaricaProclamatoreUseCase>(relaxed = true),
        caricaIdoneita = mockk<CaricaIdoneitaProclamatoreUseCase>(relaxed = true),
        caricaUltimeAssegnazioniPerParte = mockk<CaricaUltimeAssegnazioniPerParteProclamatoreUseCase>(relaxed = true),
        crea = mockk<CreaProclamatoreUseCase>(relaxed = true),
        aggiorna = mockk<AggiornaProclamatoreUseCase>(relaxed = true),
        impostaIdoneitaConduzione = mockk<ImpostaIdoneitaConduzioneUseCase>(relaxed = true),
        partTypeStore = partTypeStore,
        verificaDuplicato = verificaDuplicato,
    )
}

package org.example.project.ui.proclamatori

import arrow.core.Either
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.example.project.core.domain.DomainError
import org.example.project.feature.assignments.application.ContaAssegnazioniPersonaUseCase
import org.example.project.feature.people.application.CaricaIdoneitaProclamatoreUseCase
import org.example.project.feature.people.application.CercaProclamatoriUseCase
import org.example.project.feature.people.application.EliminaProclamatoreUseCase
import org.example.project.feature.people.application.LeadEligibility
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.domain.Sesso
import org.example.project.feature.schemas.application.ArchivaAnomalieSchemaUseCase
import org.example.project.feature.schemas.application.SchemaUpdateAnomaly
import org.example.project.feature.schemas.application.SchemaUpdateAnomalyStore
import org.example.project.feature.weeklyparts.application.PartTypeStore
import org.example.project.feature.weeklyparts.application.PartTypeWithStatus
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.SexRule
import org.example.project.ui.components.FeedbackBannerKind
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProclamatoriListViewModelTest {

    @AfterTest
    fun tearDown() = unmockkAll()

    // ── onScreenEntered ──────────────────────────────────────────────────────

    @Test
    fun `onScreenEntered carica la lista e disattiva loading`() = runTest {
        val cerca = mockk<CercaProclamatoriUseCase>()
        coEvery { cerca(any()) } returns listOf(makeProclamatore("1", "Mario", "Rossi"))

        val vm = makeViewModel(scope = this, cerca = cerca)
        vm.onScreenEntered()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.allItems.size)
        assertEquals("Mario", state.allItems[0].nome)
    }

    @Test
    fun `onScreenEntered carica anche le anomalie schema`() = runTest {
        val cerca = mockk<CercaProclamatoriUseCase>()
        val person = makeProclamatore("p1", "Mario", "Rossi")
        coEvery { cerca(any()) } returns listOf(person)

        val anomalyStore = mockk<SchemaUpdateAnomalyStore>()
        coEvery { anomalyStore.listOpen() } returns listOf(
            SchemaUpdateAnomaly(
                id = "a1",
                personId = ProclamatoreId("p1"),
                partTypeId = PartTypeId("pt1"),
                reason = "Parte rimossa",
                schemaVersion = "v2",
                createdAt = "2026-03-01",
            ),
        )

        val partTypeStore = mockk<PartTypeStore>()
        coEvery { partTypeStore.allWithStatus() } returns listOf(
            PartTypeWithStatus(
                partType = PartType(
                    id = PartTypeId("pt1"),
                    code = "LB",
                    label = "Lettura Bibbia",
                    peopleCount = 1,
                    sexRule = SexRule.STESSO_SESSO,
                    fixed = false,
                    sortOrder = 0,
                ),
                active = true,
            ),
        )

        val vm = makeViewModel(
            scope = this,
            cerca = cerca,
            schemaUpdateAnomalyStore = anomalyStore,
            partTypeStore = partTypeStore,
        )
        vm.onScreenEntered()
        advanceUntilIdle()

        val anomalies = vm.uiState.value.schemaUpdateAnomalies
        assertEquals(1, anomalies.size)
        assertEquals("Mario Rossi", anomalies[0].personLabel)
        assertEquals("Lettura Bibbia", anomalies[0].partTypeLabel)
    }

    @Test
    fun `onScreenEntered carica anche il riepilogo capability della pagina corrente`() = runTest {
        val person = makeProclamatore("p1", "Mario", "Rossi")
        val cerca = mockk<CercaProclamatoriUseCase>()
        coEvery { cerca(any()) } returns listOf(person)

        val caricaIdoneita = mockk<CaricaIdoneitaProclamatoreUseCase>()
        coEvery { caricaIdoneita(ProclamatoreId("p1")) } returns listOf(
            LeadEligibility(partTypeId = PartTypeId("pt1"), canLead = true),
        )

        val partTypeStore = mockk<PartTypeStore>()
        coEvery { partTypeStore.allWithStatus() } returns listOf(
            PartTypeWithStatus(
                partType = PartType(
                    id = PartTypeId("pt1"),
                    code = "LB",
                    label = "Lettura Bibbia",
                    peopleCount = 1,
                    sexRule = SexRule.STESSO_SESSO,
                    fixed = false,
                    sortOrder = 0,
                ),
                active = true,
            ),
        )

        val vm = makeViewModel(
            scope = this,
            cerca = cerca,
            caricaIdoneita = caricaIdoneita,
            partTypeStore = partTypeStore,
        )
        vm.onScreenEntered()
        advanceUntilIdle()

        val summary = vm.uiState.value.capabilitySummaryById[ProclamatoreId("p1")]
        assertNotNull(summary)
        assertEquals(listOf("Lettura Bibbia"), summary.leadLabels)
        assertEquals(1, summary.leadCount)
    }

    // ── setSearchTerm / resetSearch ──────────────────────────────────────────

    @Test
    fun `setSearchTerm aggiorna il termine e riesegue la ricerca dopo debounce`() = runTest {
        val cerca = mockk<CercaProclamatoriUseCase>()
        coEvery { cerca(null) } returns emptyList()
        coEvery { cerca("") } returns emptyList()
        coEvery { cerca("Mar") } returns listOf(makeProclamatore("1", "Mario", "Rossi"))

        val vm = makeViewModel(scope = this, cerca = cerca)
        vm.onScreenEntered()
        advanceUntilIdle()

        vm.setSearchTerm("Mar")
        assertEquals("Mar", vm.uiState.value.searchTerm)

        // Before debounce expires, list hasn't changed
        advanceTimeBy(249)

        // After debounce, search executes
        advanceTimeBy(1)
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.allItems.size)
    }

    @Test
    fun `setSearchTerm con chiamate rapide esegue solo l'ultima ricerca`() = runTest {
        var searchCount = 0
        val cerca = mockk<CercaProclamatoriUseCase>()
        coEvery { cerca(any()) } answers {
            searchCount++
            emptyList()
        }

        val vm = makeViewModel(scope = this, cerca = cerca)

        // Reset counter after initial screen load
        vm.onScreenEntered()
        advanceUntilIdle()
        searchCount = 0

        vm.setSearchTerm("M")
        vm.setSearchTerm("Ma")
        vm.setSearchTerm("Mar")

        advanceTimeBy(250)
        advanceUntilIdle()

        // Only the last search should have been executed (debounce cancelled the first two)
        assertEquals(1, searchCount)
    }

    @Test
    fun `resetSearch azzera il termine e riesegue la ricerca`() = runTest {
        val cerca = mockk<CercaProclamatoriUseCase>()
        coEvery { cerca(any()) } returns emptyList()
        coEvery { cerca("Mario") } returns listOf(makeProclamatore("1", "Mario", "Rossi"))

        val vm = makeViewModel(scope = this, cerca = cerca)
        vm.onScreenEntered()
        advanceUntilIdle()

        vm.setSearchTerm("Mario")
        advanceTimeBy(250)
        advanceUntilIdle()
        assertEquals("Mario", vm.uiState.value.searchTerm)

        vm.resetSearch()
        assertEquals("", vm.uiState.value.searchTerm)
    }

    // ── setSort ──────────────────────────────────────────────────────────────

    @Test
    fun `setSort aggiorna il criterio di ordinamento`() = runTest {
        val vm = makeViewModel(scope = this)

        val newSort = ProclamatoriSort(
            field = ProclamatoriSortField.NOME,
            direction = SortDirection.DESC,
        )
        vm.setSort(newSort)

        assertEquals(ProclamatoriSortField.NOME, vm.uiState.value.sort.field)
        assertEquals(SortDirection.DESC, vm.uiState.value.sort.direction)
    }

    // ── selection ────────────────────────────────────────────────────────────

    @Test
    fun `refreshList rimuove le selezioni di ID non piu' presenti`() = runTest {
        val cerca = mockk<CercaProclamatoriUseCase>()
        val p1 = makeProclamatore("1", "Mario", "Rossi")
        coEvery { cerca(any()) } returns listOf(p1)

        val vm = makeViewModel(scope = this, cerca = cerca)
        vm.onScreenEntered()
        advanceUntilIdle()

        // Select two IDs, but only one exists
        vm.setRowSelected(ProclamatoreId("1"), true)
        vm.setRowSelected(ProclamatoreId("gone"), true)
        assertEquals(2, vm.uiState.value.selectedIds.size)

        // After refresh, "gone" is pruned
        vm.refreshList()
        advanceUntilIdle()

        assertEquals(setOf(ProclamatoreId("1")), vm.uiState.value.selectedIds)
    }

    // ── delete single ────────────────────────────────────────────────────────

    @Test
    fun `requestDeleteCandidate imposta il candidato e il conteggio assegnazioni`() = runTest {
        val contaAssegnazioni = mockk<ContaAssegnazioniPersonaUseCase>()
        coEvery { contaAssegnazioni(any()) } returns 5

        val vm = makeViewModel(scope = this, contaAssegnazioni = contaAssegnazioni)
        val person = makeProclamatore("1", "Mario", "Rossi")

        vm.requestDeleteCandidate(person)
        advanceUntilIdle()

        assertEquals(person, vm.uiState.value.deleteCandidate)
        assertEquals(5, vm.uiState.value.deleteAssignmentCount)
    }

    @Test
    fun `requestDeleteCandidate mostra errore se conteggio assegnazioni fallisce`() = runTest {
        val contaAssegnazioni = mockk<ContaAssegnazioniPersonaUseCase>()
        coEvery { contaAssegnazioni(any()) } throws RuntimeException("db error")

        val vm = makeViewModel(scope = this, contaAssegnazioni = contaAssegnazioni)
        val person = makeProclamatore("1", "Mario", "Rossi")

        vm.requestDeleteCandidate(person)
        advanceUntilIdle()

        assertNull(vm.uiState.value.deleteCandidate)
        assertNotNull(vm.uiState.value.notice)
        assertEquals(FeedbackBannerKind.ERROR, vm.uiState.value.notice?.kind)
    }

    @Test
    fun `dismissDeleteCandidate azzera il candidato`() = runTest {
        val contaAssegnazioni = mockk<ContaAssegnazioniPersonaUseCase>()
        coEvery { contaAssegnazioni(any()) } returns 0

        val vm = makeViewModel(scope = this, contaAssegnazioni = contaAssegnazioni)
        val person = makeProclamatore("1", "Mario", "Rossi")

        vm.requestDeleteCandidate(person)
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.deleteCandidate)

        vm.dismissDeleteCandidate()
        assertNull(vm.uiState.value.deleteCandidate)
    }

    @Test
    fun `confirmDeleteCandidate successo rimuove candidato e aggiorna lista`() = runTest {
        val person = makeProclamatore("1", "Mario", "Rossi")
        val cerca = mockk<CercaProclamatoriUseCase>()
        coEvery { cerca(any()) } returns listOf(person) andThen emptyList()

        val elimina = mockk<EliminaProclamatoreUseCase>()
        coEvery { elimina(any()) } returns Either.Right(Unit)

        val contaAssegnazioni = mockk<ContaAssegnazioniPersonaUseCase>()
        coEvery { contaAssegnazioni(any()) } returns 0

        val vm = makeViewModel(scope = this, cerca = cerca, elimina = elimina, contaAssegnazioni = contaAssegnazioni)
        vm.onScreenEntered()
        advanceUntilIdle()

        vm.requestDeleteCandidate(person)
        advanceUntilIdle()

        vm.confirmDeleteCandidate()
        advanceUntilIdle()

        assertNull(vm.uiState.value.deleteCandidate)
        assertFalse(vm.uiState.value.isLoading)
        assertEquals(FeedbackBannerKind.SUCCESS, vm.uiState.value.notice?.kind)
        coVerify(exactly = 1) { elimina(ProclamatoreId("1")) }
    }

    @Test
    fun `confirmDeleteCandidate errore mostra notice di errore`() = runTest {
        val person = makeProclamatore("1", "Mario", "Rossi")
        val cerca = mockk<CercaProclamatoriUseCase>()
        coEvery { cerca(any()) } returns listOf(person)

        val elimina = mockk<EliminaProclamatoreUseCase>()
        coEvery { elimina(any()) } returns Either.Left(DomainError.EliminazioneProclamatoreFallita("db error"))

        val contaAssegnazioni = mockk<ContaAssegnazioniPersonaUseCase>()
        coEvery { contaAssegnazioni(any()) } returns 0

        val vm = makeViewModel(scope = this, cerca = cerca, elimina = elimina, contaAssegnazioni = contaAssegnazioni)
        vm.onScreenEntered()
        advanceUntilIdle()

        vm.requestDeleteCandidate(person)
        advanceUntilIdle()

        vm.confirmDeleteCandidate()
        advanceUntilIdle()

        assertNull(vm.uiState.value.deleteCandidate)
        assertFalse(vm.uiState.value.isLoading)
        assertEquals(FeedbackBannerKind.ERROR, vm.uiState.value.notice?.kind)
    }

    @Test
    fun `confirmDeleteCandidate non fa nulla se deleteCandidate e' null`() = runTest {
        val elimina = mockk<EliminaProclamatoreUseCase>()

        val vm = makeViewModel(scope = this, elimina = elimina)
        vm.confirmDeleteCandidate()
        advanceUntilIdle()

        coVerify(exactly = 0) { elimina(any()) }
    }

    // ── batch delete ─────────────────────────────────────────────────────────

    @Test
    fun `requestBatchDeleteConfirm calcola il totale assegnazioni dei selezionati`() = runTest {
        val contaAssegnazioni = mockk<ContaAssegnazioniPersonaUseCase>()
        coEvery { contaAssegnazioni(ProclamatoreId("1")) } returns 2
        coEvery { contaAssegnazioni(ProclamatoreId("2")) } returns 3

        val vm = makeViewModel(scope = this, contaAssegnazioni = contaAssegnazioni)
        vm.setRowSelected(ProclamatoreId("1"), true)
        vm.setRowSelected(ProclamatoreId("2"), true)

        vm.requestBatchDeleteConfirm()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.showBatchDeleteConfirm)
        assertEquals(5, vm.uiState.value.batchDeleteAssignmentCount)
    }

    @Test
    fun `requestBatchDeleteConfirm non fa nulla se nessun ID selezionato`() = runTest {
        val vm = makeViewModel(scope = this)

        vm.requestBatchDeleteConfirm()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.showBatchDeleteConfirm)
    }

    @Test
    fun `requestBatchDeleteConfirm mostra errore se conteggio fallisce`() = runTest {
        val contaAssegnazioni = mockk<ContaAssegnazioniPersonaUseCase>()
        coEvery { contaAssegnazioni(any()) } throws RuntimeException("db error")

        val vm = makeViewModel(scope = this, contaAssegnazioni = contaAssegnazioni)
        vm.setRowSelected(ProclamatoreId("1"), true)

        vm.requestBatchDeleteConfirm()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.showBatchDeleteConfirm)
        assertEquals(FeedbackBannerKind.ERROR, vm.uiState.value.notice?.kind)
    }

    @Test
    fun `dismissBatchDeleteConfirm chiude il dialogo`() = runTest {
        val contaAssegnazioni = mockk<ContaAssegnazioniPersonaUseCase>()
        coEvery { contaAssegnazioni(any()) } returns 0

        val vm = makeViewModel(scope = this, contaAssegnazioni = contaAssegnazioni)
        vm.setRowSelected(ProclamatoreId("1"), true)
        vm.requestBatchDeleteConfirm()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.showBatchDeleteConfirm)

        vm.dismissBatchDeleteConfirm()
        assertFalse(vm.uiState.value.showBatchDeleteConfirm)
    }

    @Test
    fun `confirmBatchDelete successo rimuove gli studenti e svuota selezione`() = runTest {
        val p1 = makeProclamatore("1", "Mario", "Rossi")
        val p2 = makeProclamatore("2", "Luigi", "Verdi")
        val cerca = mockk<CercaProclamatoriUseCase>()
        coEvery { cerca(any()) } returns listOf(p1, p2) andThen emptyList()

        val elimina = mockk<EliminaProclamatoreUseCase>()
        coEvery { elimina(any()) } returns Either.Right(Unit)

        val contaAssegnazioni = mockk<ContaAssegnazioniPersonaUseCase>()
        coEvery { contaAssegnazioni(any()) } returns 0

        val vm = makeViewModel(scope = this, cerca = cerca, elimina = elimina, contaAssegnazioni = contaAssegnazioni)
        vm.onScreenEntered()
        advanceUntilIdle()

        vm.setRowSelected(ProclamatoreId("1"), true)
        vm.setRowSelected(ProclamatoreId("2"), true)
        vm.requestBatchDeleteConfirm()
        advanceUntilIdle()

        vm.confirmBatchDelete()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.showBatchDeleteConfirm)
        assertFalse(vm.uiState.value.isBatchInProgress)
        assertFalse(vm.uiState.value.isLoading)
        assertEquals(FeedbackBannerKind.SUCCESS, vm.uiState.value.notice?.kind)
        // All succeeded, selection should be cleared (failedIds = empty)
        assertTrue(vm.uiState.value.selectedIds.isEmpty())
    }

    @Test
    fun `confirmBatchDelete con errori parziali mantiene i falliti selezionati`() = runTest {
        val p1 = makeProclamatore("1", "Mario", "Rossi")
        val p2 = makeProclamatore("2", "Luigi", "Verdi")
        val cerca = mockk<CercaProclamatoriUseCase>()
        coEvery { cerca(any()) } returns listOf(p1, p2) andThen listOf(p2)

        val elimina = mockk<EliminaProclamatoreUseCase>()
        coEvery { elimina(ProclamatoreId("1")) } returns Either.Right(Unit)
        coEvery { elimina(ProclamatoreId("2")) } returns Either.Left(DomainError.EliminazioneProclamatoreFallita("db error"))

        val contaAssegnazioni = mockk<ContaAssegnazioniPersonaUseCase>()
        coEvery { contaAssegnazioni(any()) } returns 0

        val vm = makeViewModel(scope = this, cerca = cerca, elimina = elimina, contaAssegnazioni = contaAssegnazioni)
        vm.onScreenEntered()
        advanceUntilIdle()

        vm.setRowSelected(ProclamatoreId("1"), true)
        vm.setRowSelected(ProclamatoreId("2"), true)
        vm.requestBatchDeleteConfirm()
        advanceUntilIdle()

        vm.confirmBatchDelete()
        advanceUntilIdle()

        // Failed IDs stay selected
        assertEquals(setOf(ProclamatoreId("2")), vm.uiState.value.selectedIds)
        assertFalse(vm.uiState.value.isBatchInProgress)
    }

    // ── dismissSchemaUpdateAnomalies ─────────────────────────────────────────

    @Test
    fun `dismissSchemaUpdateAnomalies successo svuota la lista anomalie`() = runTest {
        val archivaAnomalieSchema = mockk<ArchivaAnomalieSchemaUseCase>()
        coEvery { archivaAnomalieSchema() } returns Either.Right(Unit)

        val anomalyStore = mockk<SchemaUpdateAnomalyStore>()
        coEvery { anomalyStore.listOpen() } returns listOf(
            SchemaUpdateAnomaly(
                id = "a1",
                personId = ProclamatoreId("p1"),
                partTypeId = PartTypeId("pt1"),
                reason = "test",
                schemaVersion = null,
                createdAt = "2026-03-01",
            ),
        )

        val cerca = mockk<CercaProclamatoriUseCase>()
        coEvery { cerca(any()) } returns listOf(makeProclamatore("p1", "Mario", "Rossi"))

        val partTypeStore = mockk<PartTypeStore>()
        coEvery { partTypeStore.allWithStatus() } returns listOf(
            PartTypeWithStatus(
                partType = PartType(
                    id = PartTypeId("pt1"), code = "LB", label = "Lettura",
                    peopleCount = 1, sexRule = SexRule.STESSO_SESSO, fixed = false, sortOrder = 0,
                ),
                active = true,
            ),
        )

        val vm = makeViewModel(
            scope = this,
            cerca = cerca,
            archivaAnomalieSchema = archivaAnomalieSchema,
            schemaUpdateAnomalyStore = anomalyStore,
            partTypeStore = partTypeStore,
        )
        vm.onScreenEntered()
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.schemaUpdateAnomalies.size)

        vm.dismissSchemaUpdateAnomalies()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.schemaUpdateAnomalies.isEmpty())
        assertFalse(vm.uiState.value.isDismissingSchemaAnomalies)
    }

    // ── refreshList with page index adjustment ───────────────────────────────

    @Test
    fun `refreshList con resetPage torna a pagina 0`() = runTest {
        val items = (1..25).map { makeProclamatore("$it", "Nome$it", "Cognome$it") }
        val cerca = mockk<CercaProclamatoriUseCase>()
        coEvery { cerca(any()) } returns items

        val vm = makeViewModel(scope = this, cerca = cerca)
        vm.onScreenEntered()
        advanceUntilIdle()

        vm.goToNextPage()
        assertEquals(1, vm.uiState.value.pageIndex)

        vm.refreshList(resetPage = true)
        advanceUntilIdle()

        assertEquals(0, vm.uiState.value.pageIndex)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun makeProclamatore(
        id: String,
        nome: String,
        cognome: String,
        sesso: Sesso = Sesso.M,
    ): Proclamatore = Proclamatore.of(
        id = ProclamatoreId(id),
        nome = nome,
        cognome = cognome,
        sesso = sesso,
    ).getOrNull()!!

    private fun makeViewModel(
        scope: kotlinx.coroutines.CoroutineScope,
        cerca: CercaProclamatoriUseCase = mockk<CercaProclamatoriUseCase>().also {
            coEvery { it(any()) } returns emptyList()
        },
        caricaIdoneita: CaricaIdoneitaProclamatoreUseCase = mockk<CaricaIdoneitaProclamatoreUseCase>().also {
            coEvery { it(any()) } returns emptyList()
        },
        elimina: EliminaProclamatoreUseCase = mockk(relaxed = true),
        contaAssegnazioni: ContaAssegnazioniPersonaUseCase = mockk(relaxed = true),
        archivaAnomalieSchema: ArchivaAnomalieSchemaUseCase = mockk<ArchivaAnomalieSchemaUseCase>().also {
            coEvery { it() } returns Either.Right(Unit)
        },
        schemaUpdateAnomalyStore: SchemaUpdateAnomalyStore = mockk<SchemaUpdateAnomalyStore>().also {
            coEvery { it.listOpen() } returns emptyList()
        },
        partTypeStore: PartTypeStore = mockk<PartTypeStore>().also {
            coEvery { it.allWithStatus() } returns emptyList()
        },
    ) = ProclamatoriListViewModel(
        scope = scope,
        cerca = cerca,
        caricaIdoneita = caricaIdoneita,
        elimina = elimina,
        contaAssegnazioni = contaAssegnazioni,
        archivaAnomalieSchema = archivaAnomalieSchema,
        schemaUpdateAnomalyStore = schemaUpdateAnomalyStore,
        partTypeStore = partTypeStore,
    )
}

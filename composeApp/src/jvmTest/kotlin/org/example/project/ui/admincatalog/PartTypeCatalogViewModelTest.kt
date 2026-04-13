package org.example.project.ui.admincatalog

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.example.project.feature.weeklyparts.application.CaricaCatalogoTipiParteUseCase
import org.example.project.feature.weeklyparts.application.CaricaRevisioniTipoParteUseCase
import org.example.project.feature.weeklyparts.application.PartTypeRevisionRow
import org.example.project.feature.weeklyparts.application.PartTypeStore
import org.example.project.feature.weeklyparts.application.PartTypeWithStatus
import org.example.project.feature.weeklyparts.domain.PartTypeSnapshot
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.SexRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PartTypeCatalogViewModelTest {

    @Test
    fun `onScreenEntered loads items and selects first available item`() = runTest {
        val store = mockk<PartTypeStore>()
        coEvery { store.allWithStatus() } returns listOf(
            PartTypeWithStatus(makePartType("LB", "Lettura Bibbia"), active = true),
            PartTypeWithStatus(makePartType("PG", "Preghiera"), active = false),
        )

        val viewModel = PartTypeCatalogViewModel(
            scope = this,
            caricaCatalogoTipiParte = CaricaCatalogoTipiParteUseCase(store),
            caricaRevisioniTipoParte = CaricaRevisioniTipoParteUseCase(store),
        )
        viewModel.onScreenEntered()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.items.size)
        assertEquals(PartTypeId("LB"), state.selectedId)
        assertNotNull(state.selectedDetail)
        assertEquals("Lettura Bibbia", state.selectedDetail?.label)
    }

    @Test
    fun `selectItem updates selected detail`() = runTest {
        val store = mockk<PartTypeStore>()
        coEvery { store.allWithStatus() } returns listOf(
            PartTypeWithStatus(makePartType("LB", "Lettura Bibbia"), active = true),
            PartTypeWithStatus(makePartType("PG", "Preghiera"), active = true),
        )

        val viewModel = PartTypeCatalogViewModel(
            scope = this,
            caricaCatalogoTipiParte = CaricaCatalogoTipiParteUseCase(store),
            caricaRevisioniTipoParte = CaricaRevisioniTipoParteUseCase(store),
        )
        viewModel.onScreenEntered()
        advanceUntilIdle()

        viewModel.selectItem(PartTypeId("PG"))

        assertEquals(PartTypeId("PG"), viewModel.uiState.value.selectedId)
        assertEquals("Preghiera", viewModel.uiState.value.selectedDetail?.label)
    }

    @Test
    fun `onScreenEntered exposes empty state when catalog has no items`() = runTest {
        val store = mockk<PartTypeStore>()
        coEvery { store.allWithStatus() } returns emptyList()

        val viewModel = PartTypeCatalogViewModel(
            scope = this,
            caricaCatalogoTipiParte = CaricaCatalogoTipiParteUseCase(store),
            caricaRevisioniTipoParte = CaricaRevisioniTipoParteUseCase(store),
        )
        viewModel.onScreenEntered()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.emptyStateVisible)
        assertNull(state.selectedId)
        assertNull(state.selectedDetail)
    }

    @Test
    fun `onScreenEntered sets notice on load failure`() = runTest {
        val store = mockk<PartTypeStore>()
        coEvery { store.allWithStatus() } throws IllegalStateException("db ko")

        val viewModel = PartTypeCatalogViewModel(
            scope = this,
            caricaCatalogoTipiParte = CaricaCatalogoTipiParteUseCase(store),
            caricaRevisioniTipoParte = CaricaRevisioniTipoParteUseCase(store),
        )
        viewModel.onScreenEntered()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.notice)
        assertTrue(shouldShowPartTypeCatalogError(state))
    }

    @Test
    fun `setViewMode Cronologia lazy-loads revisions for current selection`() = runTest {
        val store = mockk<PartTypeStore>()
        coEvery { store.allWithStatus() } returns listOf(
            PartTypeWithStatus(makePartType("LB", "Lettura Bibbia"), active = true),
        )
        coEvery { store.allRevisionsForPartType(PartTypeId("LB")) } returns listOf(
            revisionRow(1, "Lettura"),
            revisionRow(2, "Lettura Bibbia"),
        )

        val viewModel = PartTypeCatalogViewModel(
            scope = this,
            caricaCatalogoTipiParte = CaricaCatalogoTipiParteUseCase(store),
            caricaRevisioniTipoParte = CaricaRevisioniTipoParteUseCase(store),
        )
        viewModel.onScreenEntered()
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.revisionsForSelected.size)

        viewModel.setViewMode(PartTypeDetailViewMode.Cronologia)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(PartTypeDetailViewMode.Cronologia, state.viewMode)
        assertEquals(2, state.revisionsForSelected.size)
        assertEquals(2, state.revisionsForSelected.first().revisionNumber, "Ordine DESC: la più recente prima")
        assertTrue(state.revisionsForSelected.first().isCurrent)
        assertEquals(1, state.cachedRevisions.size)
    }

    @Test
    fun `setViewMode Cronologia secondo switch usa cache`() = runTest {
        val store = mockk<PartTypeStore>()
        coEvery { store.allWithStatus() } returns listOf(
            PartTypeWithStatus(makePartType("LB", "Lettura Bibbia"), active = true),
        )
        var revisionCalls = 0
        coEvery { store.allRevisionsForPartType(PartTypeId("LB")) } answers {
            revisionCalls++
            listOf(revisionRow(1, "Lettura Bibbia"))
        }

        val viewModel = PartTypeCatalogViewModel(
            scope = this,
            caricaCatalogoTipiParte = CaricaCatalogoTipiParteUseCase(store),
            caricaRevisioniTipoParte = CaricaRevisioniTipoParteUseCase(store),
        )
        viewModel.onScreenEntered()
        advanceUntilIdle()
        viewModel.setViewMode(PartTypeDetailViewMode.Cronologia)
        advanceUntilIdle()
        viewModel.setViewMode(PartTypeDetailViewMode.Dettaglio)
        viewModel.setViewMode(PartTypeDetailViewMode.Cronologia)
        advanceUntilIdle()

        assertEquals(1, revisionCalls, "La seconda apertura deve usare la cache")
    }

    @Test
    fun `selectItem in Cronologia carica le revisioni del nuovo selezionato`() = runTest {
        val store = mockk<PartTypeStore>()
        coEvery { store.allWithStatus() } returns listOf(
            PartTypeWithStatus(makePartType("LB", "Lettura Bibbia"), active = true),
            PartTypeWithStatus(makePartType("PG", "Preghiera"), active = true),
        )
        coEvery { store.allRevisionsForPartType(PartTypeId("LB")) } returns listOf(
            revisionRow(1, "Lettura Bibbia"),
        )
        coEvery { store.allRevisionsForPartType(PartTypeId("PG")) } returns listOf(
            revisionRow(1, "Preghiera"),
            revisionRow(2, "Preghiera serale"),
        )

        val viewModel = PartTypeCatalogViewModel(
            scope = this,
            caricaCatalogoTipiParte = CaricaCatalogoTipiParteUseCase(store),
            caricaRevisioniTipoParte = CaricaRevisioniTipoParteUseCase(store),
        )
        viewModel.onScreenEntered()
        advanceUntilIdle()
        viewModel.setViewMode(PartTypeDetailViewMode.Cronologia)
        advanceUntilIdle()

        viewModel.selectItem(PartTypeId("PG"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(PartTypeId("PG"), state.selectedId)
        assertEquals(2, state.revisionsForSelected.size)
        assertEquals(2, state.cachedRevisions.size)
    }

    @Test
    fun `selectItem in Dettaglio non carica revisioni`() = runTest {
        val store = mockk<PartTypeStore>()
        coEvery { store.allWithStatus() } returns listOf(
            PartTypeWithStatus(makePartType("LB", "Lettura Bibbia"), active = true),
            PartTypeWithStatus(makePartType("PG", "Preghiera"), active = true),
        )
        var revisionCalls = 0
        coEvery { store.allRevisionsForPartType(any()) } answers {
            revisionCalls++
            emptyList()
        }

        val viewModel = PartTypeCatalogViewModel(
            scope = this,
            caricaCatalogoTipiParte = CaricaCatalogoTipiParteUseCase(store),
            caricaRevisioniTipoParte = CaricaRevisioniTipoParteUseCase(store),
        )
        viewModel.onScreenEntered()
        advanceUntilIdle()
        viewModel.selectItem(PartTypeId("PG"))
        advanceUntilIdle()

        assertEquals(0, revisionCalls, "In Dettaglio non si carica la cronologia")
    }

    private fun makePartType(
        code: String,
        label: String,
    ): PartType = PartType(
        id = PartTypeId(code),
        code = code,
        label = label,
        peopleCount = 1,
        sexRule = SexRule.STESSO_SESSO,
        fixed = false,
        sortOrder = 0,
    )

    private fun revisionRow(
        number: Int,
        label: String,
    ): PartTypeRevisionRow = PartTypeRevisionRow(
        revisionNumber = number,
        createdAt = java.time.LocalDateTime.of(2026, 1, 1, 9, 0).plusDays(number.toLong()),
        snapshot = PartTypeSnapshot(
            label = label,
            peopleCount = 1,
            sexRule = SexRule.STESSO_SESSO,
            fixed = false,
        ),
    )
}

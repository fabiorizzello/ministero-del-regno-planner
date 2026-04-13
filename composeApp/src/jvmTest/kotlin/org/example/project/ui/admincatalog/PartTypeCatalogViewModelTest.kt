package org.example.project.ui.admincatalog

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.example.project.feature.weeklyparts.application.CaricaCatalogoTipiParteUseCase
import org.example.project.feature.weeklyparts.application.PartTypeStore
import org.example.project.feature.weeklyparts.application.PartTypeWithStatus
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
        )
        viewModel.onScreenEntered()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.notice)
        assertTrue(shouldShowPartTypeCatalogError(state))
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
}

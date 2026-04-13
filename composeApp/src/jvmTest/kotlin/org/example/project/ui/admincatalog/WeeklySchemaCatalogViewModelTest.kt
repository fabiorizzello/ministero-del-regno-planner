package org.example.project.ui.admincatalog

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.example.project.feature.schemas.application.SchemaTemplateStore
import org.example.project.feature.schemas.application.StoredSchemaWeekTemplate
import org.example.project.feature.weeklyparts.application.PartTypeStore
import org.example.project.feature.weeklyparts.application.PartTypeWithStatus
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.SexRule
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WeeklySchemaCatalogViewModelTest {

    @Test
    fun `onScreenEntered loads weeks and selects first week`() = runTest {
        val schemaStore = mockk<SchemaTemplateStore>()
        coEvery { schemaStore.listAll() } returns listOf(
            StoredSchemaWeekTemplate(
                weekStartDate = LocalDate.of(2026, 1, 5),
                partTypeIds = listOf(PartTypeId("LB")),
            ),
        )
        val partTypeStore = mockk<PartTypeStore>()
        coEvery { partTypeStore.allWithStatus() } returns listOf(
            PartTypeWithStatus(makePartType("LB", "Lettura Bibbia"), active = true),
        )

        val viewModel = WeeklySchemaCatalogViewModel(
            scope = this,
            schemaTemplateStore = schemaStore,
            partTypeStore = partTypeStore,
        )
        viewModel.onScreenEntered()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.weeks.size)
        assertEquals(LocalDate.of(2026, 1, 5), state.selectedWeekStartDate)
        assertEquals(1, state.selectedDetail?.rows?.size)
    }

    @Test
    fun `selectWeek updates selected detail`() = runTest {
        val firstWeek = LocalDate.of(2026, 1, 5)
        val secondWeek = LocalDate.of(2026, 1, 12)
        val schemaStore = mockk<SchemaTemplateStore>()
        coEvery { schemaStore.listAll() } returns listOf(
            StoredSchemaWeekTemplate(firstWeek, listOf(PartTypeId("LB"))),
            StoredSchemaWeekTemplate(secondWeek, listOf(PartTypeId("PG"))),
        )
        val partTypeStore = mockk<PartTypeStore>()
        coEvery { partTypeStore.allWithStatus() } returns listOf(
            PartTypeWithStatus(makePartType("LB", "Lettura Bibbia"), active = true),
            PartTypeWithStatus(makePartType("PG", "Preghiera"), active = true),
        )

        val viewModel = WeeklySchemaCatalogViewModel(
            scope = this,
            schemaTemplateStore = schemaStore,
            partTypeStore = partTypeStore,
        )
        viewModel.onScreenEntered()
        advanceUntilIdle()

        viewModel.selectWeek(secondWeek)

        assertEquals(secondWeek, viewModel.uiState.value.selectedWeekStartDate)
        assertEquals("Preghiera", viewModel.uiState.value.selectedDetail?.rows?.single()?.partTypeLabel)
    }

    @Test
    fun `onScreenEntered exposes empty state when no schema exists`() = runTest {
        val schemaStore = mockk<SchemaTemplateStore>()
        coEvery { schemaStore.listAll() } returns emptyList()
        val partTypeStore = mockk<PartTypeStore>()
        coEvery { partTypeStore.allWithStatus() } returns emptyList()

        val viewModel = WeeklySchemaCatalogViewModel(
            scope = this,
            schemaTemplateStore = schemaStore,
            partTypeStore = partTypeStore,
        )
        viewModel.onScreenEntered()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.emptyStateVisible)
    }

    @Test
    fun `onScreenEntered sets notice on load failure`() = runTest {
        val schemaStore = mockk<SchemaTemplateStore>()
        coEvery { schemaStore.listAll() } throws IllegalStateException("db ko")
        val partTypeStore = mockk<PartTypeStore>()
        coEvery { partTypeStore.allWithStatus() } returns emptyList()

        val viewModel = WeeklySchemaCatalogViewModel(
            scope = this,
            schemaTemplateStore = schemaStore,
            partTypeStore = partTypeStore,
        )
        viewModel.onScreenEntered()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.notice)
        assertTrue(shouldShowWeeklySchemaCatalogError(viewModel.uiState.value))
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

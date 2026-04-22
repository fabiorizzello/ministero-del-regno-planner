package org.example.project.ui.workspace

import arrow.core.Either
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.example.project.feature.programs.application.AggiornaProgrammaDaSchemiOperation
import org.example.project.feature.programs.application.CaricaProgrammiAttiviOperation
import org.example.project.feature.programs.application.ProgramSelectionSnapshot
import org.example.project.feature.programs.application.SchemaRefreshPreview
import org.example.project.feature.programs.application.SchemaRefreshReport
import org.example.project.feature.programs.application.WeekRefreshDetail
import org.example.project.feature.programs.application.hasEffectiveChanges
import org.example.project.feature.programs.domain.ProgramMonth
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.schemas.application.AggiornaSchemiOperation
import org.example.project.feature.schemas.application.AggiornaSchemiResult
import org.example.project.feature.schemas.application.SkippedPart
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SchemaManagementViewModelTest {

    // ---- Existing pure-function tests preserved ----

    private val importTime = LocalDateTime.of(2026, 2, 15, 10, 0)

    private fun programMonth(
        createdAt: LocalDateTime,
        templateAppliedAt: LocalDateTime? = null,
    ) = ProgramMonth(
        id = ProgramMonthId("test-id"),
        year = 2026,
        month = 3,
        startDate = LocalDate.of(2026, 3, 2),
        endDate = LocalDate.of(2026, 3, 29),
        templateAppliedAt = templateAppliedAt,
        createdAt = createdAt,
    )

    @Test
    fun `new program created after import does not need refresh`() {
        val program = programMonth(createdAt = importTime.plusHours(1))
        assertFalse(isSchemaRefreshNeeded(importTime, program))
    }

    @Test
    fun `old program created before import needs refresh`() {
        val program = programMonth(createdAt = importTime.minusDays(1))
        assertTrue(isSchemaRefreshNeeded(importTime, program))
    }

    @Test
    fun `template applied after import does not need refresh`() {
        val program = programMonth(
            createdAt = importTime.minusDays(5),
            templateAppliedAt = importTime.plusHours(2),
        )
        assertFalse(isSchemaRefreshNeeded(importTime, program))
    }

    @Test
    fun `template applied before import needs refresh`() {
        val program = programMonth(
            createdAt = importTime.minusDays(5),
            templateAppliedAt = importTime.minusHours(1),
        )
        assertTrue(isSchemaRefreshNeeded(importTime, program))
    }

    @Test
    fun `no future program returns false`() {
        assertFalse(isSchemaRefreshNeeded(importTime, null))
    }

    @Test
    fun `no lastSchemaImport returns false`() {
        val program = programMonth(createdAt = importTime.minusDays(1))
        assertFalse(isSchemaRefreshNeeded(null, program))
    }

    @Test
    fun `schema refresh reference date includes current week`() {
        assertEquals(
            LocalDate.of(2026, 3, 2),
            schemaRefreshReferenceDate(LocalDate.of(2026, 3, 4)),
        )
    }

    @Test
    fun `report with no part delta and no assignment removals has no effective changes`() {
        val report = SchemaRefreshReport(
            weeksUpdated = 1,
            assignmentsPreserved = 2,
            assignmentsRemoved = 0,
            weekDetails = listOf(
                WeekRefreshDetail(
                    weekStartDate = LocalDate.of(2026, 3, 2),
                    partsAdded = 0,
                    partsRemoved = 0,
                    partsKept = 2,
                    assignmentsPreserved = 2,
                    assignmentsRemoved = 0,
                ),
            ),
        )

        assertFalse(report.hasEffectiveChanges())
    }

    @Test
    fun `preview with effective change on any mode requires confirmation`() {
        val unchanged = SchemaRefreshReport(
            weeksUpdated = 1,
            assignmentsPreserved = 2,
            assignmentsRemoved = 0,
            weekDetails = listOf(
                WeekRefreshDetail(
                    weekStartDate = LocalDate.of(2026, 3, 2),
                    partsAdded = 0,
                    partsRemoved = 0,
                    partsKept = 2,
                    assignmentsPreserved = 2,
                    assignmentsRemoved = 0,
                ),
            ),
        )
        val changed = SchemaRefreshReport(
            weeksUpdated = 1,
            assignmentsPreserved = 1,
            assignmentsRemoved = 1,
            weekDetails = listOf(
                WeekRefreshDetail(
                    weekStartDate = LocalDate.of(2026, 3, 9),
                    partsAdded = 1,
                    partsRemoved = 1,
                    partsKept = 1,
                    assignmentsPreserved = 1,
                    assignmentsRemoved = 1,
                ),
            ),
        )

        val preview = SchemaRefreshPreview(
            allChanges = unchanged,
            onlyUnassignedChanges = changed,
        )

        assertTrue(preview.hasEffectiveChanges())
    }

    // ---- Task 19 ViewModel tests — fun interface ports, no stub-store scaffolding ----

    private fun fakeAggiornaSchemi(result: AggiornaSchemiResult): AggiornaSchemiOperation =
        AggiornaSchemiOperation { Either.Right(result) }

    private val fakeAggiornaProgramma: AggiornaProgrammaDaSchemiOperation =
        AggiornaProgrammaDaSchemiOperation { _, _, _, _ ->
            Either.Right(SchemaRefreshReport(0, 0, 0, emptyList()))
        }

    private val fakeCaricaProgrammiAttivi: CaricaProgrammiAttiviOperation =
        CaricaProgrammiAttiviOperation { _ ->
            Either.Right(ProgramSelectionSnapshot(previous = null, current = null, futures = emptyList()))
        }

    private val sampleSkipped = SkippedPart(
        weekStartDate = "2026-03-02",
        mepsDocumentId = 123L,
        label = "Parte sconosciuta",
        detailLine = null,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `result with unknown parts and no program selected opens result dialog`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val vm = SchemaManagementViewModel(
            scope = scope,
            aggiornaSchemi = fakeAggiornaSchemi(
                AggiornaSchemiResult(
                    version = "v1",
                    weekTemplatesImported = 0,
                    skippedUnknownParts = listOf(sampleSkipped),
                    downloadedIssues = emptyList(),
                ),
            ),
            aggiornaProgrammaDaSchemi = fakeAggiornaProgramma,
            caricaProgrammiAttivi = fakeCaricaProgrammiAttivi,
        )

        vm.refreshSchemasAndProgram(selectedProgramId = null)
        scope.advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.showRefreshResultDialog)
        assertEquals(1, state.pendingUnknownParts.size)
        assertTrue(state.pendingDownloadedIssues.isEmpty())
        assertNull(state.pendingRefreshPreview)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `nothing to report does not open dialog`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val vm = SchemaManagementViewModel(
            scope = scope,
            aggiornaSchemi = fakeAggiornaSchemi(
                AggiornaSchemiResult(
                    version = "v1",
                    weekTemplatesImported = 0,
                    skippedUnknownParts = emptyList(),
                    downloadedIssues = emptyList(),
                ),
            ),
            aggiornaProgrammaDaSchemi = fakeAggiornaProgramma,
            caricaProgrammiAttivi = fakeCaricaProgrammiAttivi,
        )

        vm.refreshSchemasAndProgram(selectedProgramId = null)
        scope.advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.showRefreshResultDialog)
        assertTrue(state.pendingUnknownParts.isEmpty())
        assertTrue(state.pendingDownloadedIssues.isEmpty())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `dismiss refresh result dialog clears state`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val vm = SchemaManagementViewModel(
            scope = scope,
            aggiornaSchemi = fakeAggiornaSchemi(
                AggiornaSchemiResult(
                    version = "v1",
                    weekTemplatesImported = 0,
                    skippedUnknownParts = listOf(sampleSkipped),
                    downloadedIssues = listOf("issue-a"),
                ),
            ),
            aggiornaProgrammaDaSchemi = fakeAggiornaProgramma,
            caricaProgrammiAttivi = fakeCaricaProgrammiAttivi,
        )

        vm.refreshSchemasAndProgram(selectedProgramId = null)
        scope.advanceUntilIdle()
        assertTrue(vm.state.value.showRefreshResultDialog)

        vm.dismissRefreshResultDialog()

        val state = vm.state.value
        assertFalse(state.showRefreshResultDialog)
        assertTrue(state.pendingUnknownParts.isEmpty())
        assertTrue(state.pendingDownloadedIssues.isEmpty())
        assertNull(state.pendingRefreshPreview)
        assertNull(state.pendingRefreshProgramId)
    }
}

package org.example.project.ui.workspace

import org.example.project.feature.programs.application.SchemaRefreshPreview
import org.example.project.feature.programs.application.SchemaRefreshReport
import org.example.project.feature.programs.application.WeekRefreshDetail
import org.example.project.feature.programs.application.hasEffectiveChanges
import org.example.project.feature.programs.domain.ProgramMonth
import org.example.project.feature.programs.domain.ProgramMonthId
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SchemaManagementViewModelTest {

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
}

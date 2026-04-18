package org.example.project.ui.workspace

import org.example.project.feature.programs.domain.ProgramMonth
import org.example.project.feature.programs.domain.ProgramMonthId
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
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

    private fun fixtureProgramMonth(
        yearMonth: YearMonth,
        id: String = "program-${yearMonth.year}-${yearMonth.monthValue}",
        templateAppliedAt: LocalDateTime? = null,
        createdAt: LocalDateTime = LocalDateTime.of(yearMonth.year, yearMonth.monthValue, 1, 9, 0),
    ) = ProgramMonth(
        id = ProgramMonthId(id),
        year = yearMonth.year,
        month = yearMonth.monthValue,
        startDate = yearMonth.atDay(1).with(java.time.temporal.TemporalAdjusters.firstInMonth(java.time.DayOfWeek.MONDAY)),
        endDate = yearMonth.atEndOfMonth().with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY)),
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
    fun `impacted future ids include only months with changed template weeks`() {
        val march = fixtureProgramMonth(YearMonth.of(2026, 3), id = "march")
        val april = fixtureProgramMonth(YearMonth.of(2026, 4), id = "april")
        val before = mapOf(
            LocalDate.of(2026, 3, 2) to listOf("A", "B"),
            LocalDate.of(2026, 4, 6) to listOf("A", "B"),
        )
        val after = mapOf(
            LocalDate.of(2026, 3, 2) to listOf("A", "C"),
            LocalDate.of(2026, 4, 6) to listOf("A", "B"),
        )

        val impacted = calculateImpactedProgramIds(
            allPrograms = listOf(march, april),
            before = before,
            after = after,
        )

        assertEquals(setOf(ProgramMonthId("march")), impacted)
    }

    @Test
    fun `no schema delta returns empty impacted ids`() {
        val march = fixtureProgramMonth(YearMonth.of(2026, 3), id = "march")
        val snapshot = mapOf(LocalDate.of(2026, 3, 2) to listOf("A", "B"))

        val impacted = calculateImpactedProgramIds(
            allPrograms = listOf(march),
            before = snapshot,
            after = snapshot,
        )

        assertTrue(impacted.isEmpty())
    }
}

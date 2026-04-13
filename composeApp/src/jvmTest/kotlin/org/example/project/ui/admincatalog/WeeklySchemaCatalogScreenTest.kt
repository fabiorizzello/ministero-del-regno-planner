package org.example.project.ui.admincatalog

import org.example.project.feature.weeklyparts.domain.PartTypeId
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeeklySchemaCatalogScreenTest {

    @Test
    fun `weeklySchemaSummaryLabel includes date and parts count`() {
        val summary = weeklySchemaSummaryLabel(
            weekStartDate = LocalDate.of(2026, 1, 5),
            partsCount = 3,
        )

        assertTrue(summary.contains("3 parti"))
        assertTrue(summary.contains("5 gennaio"))
    }

    @Test
    fun `describeWeeklySchemaRow summarizes row fields`() {
        val row = WeeklySchemaRow(
            position = 1,
            partTypeId = PartTypeId("LB"),
            partTypeCode = "LB",
            partTypeLabel = "Lettura Bibbia",
            peopleCount = 2,
            compositionRuleLabel = "Stesso sesso",
            fixedLabel = "Ordinaria",
        )

        assertEquals(
            "LB · Lettura Bibbia · Stesso sesso",
            describeWeeklySchemaRow(row),
        )
    }
}

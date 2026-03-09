package org.example.project.ui.components

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class DateLabelsTest {
    @Test
    fun `week range label is compact and human readable`() {
        val monday = LocalDate.of(2026, 2, 12)
        val sunday = LocalDate.of(2026, 2, 19)

        assertEquals("12-19 febbraio 2026", formatWeekRangeLabel(monday, sunday))
    }

    @Test
    fun `month label uses italian month name`() {
        assertEquals("febbraio 2026", formatMonthYearLabel(2, 2026))
    }
}

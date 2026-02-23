package org.example.project.ui.components

import org.example.project.core.application.SharedWeekState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class DateLabelsTest {
    @Test
    fun `current week indicator includes month and year`() {
        val monday = SharedWeekState.currentMonday()
        val monthYear = monday.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN))
        assertEquals("Corrente $monthYear", formatWeekIndicatorLabel(monday))
    }

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

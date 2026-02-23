package org.example.project.ui.components

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthYearFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN)
private val monthFormatter = DateTimeFormatter.ofPattern("MMMM", Locale.ITALIAN)
internal val dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ITALIAN)

fun formatMonthYearLabel(month: Int, year: Int): String =
    YearMonth.of(year, month).format(monthYearFormatter)

fun formatWeekRangeLabel(monday: LocalDate, sunday: LocalDate): String = when {
    monday.year == sunday.year && monday.month == sunday.month ->
        "${monday.dayOfMonth}-${sunday.dayOfMonth} ${monday.format(monthYearFormatter)}"
    monday.year == sunday.year ->
        "${monday.dayOfMonth} ${monday.format(monthFormatter)} - ${sunday.dayOfMonth} ${sunday.format(monthFormatter)} ${monday.year}"
    else ->
        "${monday.dayOfMonth} ${monday.format(monthFormatter)} ${monday.year} - ${sunday.dayOfMonth} ${sunday.format(monthFormatter)} ${sunday.year}"
}

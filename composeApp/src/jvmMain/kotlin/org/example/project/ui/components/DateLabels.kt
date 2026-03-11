package org.example.project.ui.components

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthYearFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN)
private val monthFormatter = DateTimeFormatter.ofPattern("MMMM", Locale.ITALIAN)
internal val dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ITALIAN)
internal val timestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ITALIAN)
internal val shortDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ITALIAN)

/** Offset added to sortOrder for user-facing part numbers (meeting program convention). */
const val DISPLAY_NUMBER_OFFSET = 3

fun formatMonthYearLabel(month: Int, year: Int): String =
    YearMonth.of(year, month).format(monthYearFormatter)

fun sundayOf(monday: LocalDate): LocalDate = monday.plusDays(6)

fun formatWeekRangeLabel(monday: LocalDate, sunday: LocalDate): String = when {
    monday.year == sunday.year && monday.month == sunday.month ->
        "${monday.dayOfMonth}-${sunday.dayOfMonth} ${monday.format(monthYearFormatter)}"
    monday.year == sunday.year ->
        "${monday.dayOfMonth} ${monday.format(monthFormatter)} - ${sunday.dayOfMonth} ${sunday.format(monthFormatter)} ${monday.year}"
    else ->
        "${monday.dayOfMonth} ${monday.format(monthFormatter)} ${monday.year} - ${sunday.dayOfMonth} ${sunday.format(monthFormatter)} ${sunday.year}"
}

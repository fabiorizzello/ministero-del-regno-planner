package org.example.project.core.formatting

import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthYearFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN)

/** Formats a date as "d MMMM yyyy" in Italian, e.g. "2 marzo 2026". */
val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ITALIAN)

/** Formats a month+year pair as "MMMM yyyy" in Italian, e.g. "marzo 2026". */
fun formatMonthYearLabel(month: Int, year: Int): String =
    YearMonth.of(year, month).format(monthYearFormatter)

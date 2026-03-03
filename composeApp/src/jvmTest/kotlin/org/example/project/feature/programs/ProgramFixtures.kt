package org.example.project.feature.programs

import org.example.project.feature.programs.domain.ProgramMonth
import org.example.project.feature.programs.domain.ProgramMonthId
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

fun fixtureProgramMonth(
    yearMonth: YearMonth,
    id: String = "program-${yearMonth.year}-${yearMonth.monthValue}",
    startDate: LocalDate = yearMonth.atDay(1).with(java.time.temporal.TemporalAdjusters.firstInMonth(java.time.DayOfWeek.MONDAY)),
    endDate: LocalDate = yearMonth.atEndOfMonth().with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY)),
    templateAppliedAt: LocalDateTime? = null,
    createdAt: LocalDateTime = LocalDateTime.of(yearMonth.year, yearMonth.monthValue, 1, 9, 0),
): ProgramMonth = ProgramMonth(
    id = ProgramMonthId(id),
    year = yearMonth.year,
    month = yearMonth.monthValue,
    startDate = startDate,
    endDate = endDate,
    templateAppliedAt = templateAppliedAt,
    createdAt = createdAt,
)

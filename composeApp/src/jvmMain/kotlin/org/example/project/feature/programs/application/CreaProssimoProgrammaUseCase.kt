package org.example.project.feature.programs.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.feature.programs.domain.ProgramMonth
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.programs.domain.ProgramTimelineStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.UUID

private const val MAX_FUTURE_PROGRAMS = 1

class CreaProssimoProgrammaUseCase(
    private val programStore: ProgramStore,
) {
    suspend operator fun invoke(referenceDate: LocalDate = LocalDate.now()): Either<DomainError, ProgramMonth> = either {
        val existing = programStore.listCurrentAndFuture(referenceDate)
        val futureCount = existing.count { it.timelineStatus(referenceDate) == ProgramTimelineStatus.FUTURE }
        if (futureCount >= MAX_FUTURE_PROGRAMS) {
            raise(DomainError.Validation("Puoi avere al massimo un programma futuro"))
        }

        var candidate = YearMonth.from(referenceDate)
        while (programStore.findByYearMonth(candidate.year, candidate.monthValue) != null) {
            candidate = candidate.plusMonths(1)
        }

        val range = calculateProgramDateRange(candidate)
        val program = ProgramMonth(
            id = ProgramMonthId(UUID.randomUUID().toString()),
            year = candidate.year,
            month = candidate.monthValue,
            startDate = range.first,
            endDate = range.second,
            templateAppliedAt = null,
            createdAt = LocalDateTime.now(),
        )

        programStore.save(program)
        program
    }

    private fun calculateProgramDateRange(yearMonth: YearMonth): Pair<LocalDate, LocalDate> {
        val firstDay = yearMonth.atDay(1)
        val monthEnd = yearMonth.atEndOfMonth()
        val firstMonday = firstDay.with(java.time.temporal.TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY))
        val endSunday = monthEnd.with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        return firstMonday to endSunday
    }
}

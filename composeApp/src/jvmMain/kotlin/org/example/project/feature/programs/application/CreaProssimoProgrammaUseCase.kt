package org.example.project.feature.programs.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionRunner
import org.example.project.feature.programs.domain.ProgramMonth
import org.example.project.feature.programs.domain.ProgramMonthAggregate
import org.example.project.feature.programs.domain.ProgramMonthId
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.UUID

class CreaProssimoProgrammaUseCase(
    private val programStore: ProgramStore,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(
        targetYear: Int,
        targetMonth: Int,
        referenceDate: LocalDate = LocalDate.now(),
    ): Either<DomainError, ProgramMonth> = either {
        val target = runCatching { YearMonth.of(targetYear, targetMonth) }.getOrNull()
            ?: raise(DomainError.MeseTargetNonValido)

        val context = programStore.loadCreationContext(referenceDate)
        ProgramMonthAggregate.validateCreationTarget(
            target = target,
            referenceDate = referenceDate,
            existingByMonth = context.existingByMonth,
            futureMonths = context.futureMonths,
        )?.let { raise(it) }

        val program = createProgram(target)
        Either.catch {
            transactionRunner.runInTransaction { programStore.save(program) }
        }.mapLeft { DomainError.Validation(it.message ?: "Errore creazione programma") }.bind()
        program
    }

    private fun calculateProgramDateRange(yearMonth: YearMonth): Pair<LocalDate, LocalDate> {
        val firstDay = yearMonth.atDay(1)
        val monthEnd = yearMonth.atEndOfMonth()
        val firstMonday = firstDay.with(java.time.temporal.TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY))
        val endSunday = monthEnd.with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        return firstMonday to endSunday
    }

    private fun createProgram(candidate: YearMonth): ProgramMonth {
        val range = calculateProgramDateRange(candidate)
        return ProgramMonth(
            id = ProgramMonthId(UUID.randomUUID().toString()),
            year = candidate.year,
            month = candidate.monthValue,
            startDate = range.first,
            endDate = range.second,
            templateAppliedAt = null,
            createdAt = LocalDateTime.now(),
        )
    }
}

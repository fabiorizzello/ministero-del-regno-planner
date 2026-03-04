package org.example.project.feature.programs.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.feature.programs.domain.ProgramMonth
import org.example.project.feature.programs.domain.ProgramMonthId
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.UUID

private const val MAX_FUTURE_PROGRAMS = 2

class CreaProssimoProgrammaUseCase(
    private val programStore: ProgramStore,
) {
    suspend operator fun invoke(
        targetYear: Int,
        targetMonth: Int,
        referenceDate: LocalDate = LocalDate.now(),
    ): Either<DomainError, ProgramMonth> = either {
        val referenceMonth = YearMonth.from(referenceDate)
        val target = runCatching { YearMonth.of(targetYear, targetMonth) }.getOrNull()
            ?: raise(DomainError.Validation("Mese target non valido"))
        val allowedMax = referenceMonth.plusMonths(2)
        if (target < referenceMonth || target > allowedMax) {
            raise(DomainError.Validation("Puoi creare solo mesi nella finestra corrente..+2"))
        }
        val context = programStore.loadCreationContext(referenceDate)
        if (target in context.existingByMonth) {
            raise(DomainError.Validation("Il programma per ${target.monthValue}/${target.year} esiste già"))
        }

        val creatableTargets = computeCreatableTargets(referenceDate, context)
        if (target !in creatableTargets) {
            raise(
                DomainError.Validation(
                    "Mese non creabile con le regole correnti (finestra consentita o limite futuri)",
                ),
            )
        }

        val program = createProgram(target)
        programStore.save(program)
        program
    }

    private fun computeCreatableTargets(
        referenceDate: LocalDate,
        context: ProgramCreationContext,
    ): List<YearMonth> {
        val referenceMonth = YearMonth.from(referenceDate)
        val window = listOf(referenceMonth, referenceMonth.plusMonths(1), referenceMonth.plusMonths(2))
        val existingByMonth = context.existingByMonth
        val futureMonths = context.futureMonths

        return window.filter { target ->
            if (target in existingByMonth) return@filter false

            val isCurrentTarget = target == referenceMonth
            val futureCount = futureMonths.size + if (isCurrentTarget) 0 else 1
            if (!isCurrentTarget && futureCount > MAX_FUTURE_PROGRAMS) return@filter false

            true
        }
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

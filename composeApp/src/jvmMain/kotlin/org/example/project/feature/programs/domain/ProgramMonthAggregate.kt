package org.example.project.feature.programs.domain

import org.example.project.core.domain.DomainError
import java.time.LocalDate
import java.time.YearMonth

data class ProgramMonthAggregate(
    val program: ProgramMonth,
) {
    fun validateDeletion(referenceDate: LocalDate): DomainError? =
        if (program.timelineStatus(referenceDate) == ProgramTimelineStatus.PAST) {
            DomainError.ProgrammaPassatoNonEliminabile
        } else {
            null
        }

    companion object {
        /** Numero massimo di programmi futuri consentiti contemporaneamente (regola di business). */
        const val MAX_FUTURE_PROGRAMS = 2

        fun validateCreationTarget(
            target: YearMonth,
            referenceDate: LocalDate,
            existingByMonth: Set<YearMonth>,
            futureMonths: Set<YearMonth>,
        ): DomainError? {
            val referenceMonth = YearMonth.from(referenceDate)
            val allowedMin = referenceMonth.minusMonths(1)
            val allowedMax = referenceMonth.plusMonths(2)
            if (target < allowedMin || target > allowedMax) {
                return DomainError.MeseFuoriFinestraCreazione
            }
            if (target in existingByMonth) {
                return DomainError.ProgrammaGiaEsistenteNelMese(month = target.monthValue, year = target.year)
            }

            val isPastTarget = target == allowedMin
            val isCurrentTarget = target == referenceMonth
            if (!isPastTarget && !isCurrentTarget) {
                val futureCount = futureMonths.size + 1
                if (futureCount > MAX_FUTURE_PROGRAMS) {
                    return DomainError.MeseNonCreabile
                }
            }

            if (target == referenceMonth.plusMonths(2) && referenceMonth.plusMonths(1) !in existingByMonth) {
                return DomainError.MeseNonCreabile
            }

            return null
        }
    }
}

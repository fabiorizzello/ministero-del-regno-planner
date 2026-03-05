package org.example.project.feature.programs.application

import org.example.project.feature.programs.domain.ProgramMonth
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.programs.domain.ProgramTimelineStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

data class ProgramCreationContext(
    val existingByMonth: Set<YearMonth>,
    val futureMonths: Set<YearMonth>,
)

/** Numero massimo di programmi futuri consentiti contemporaneamente (regola di business). */
const val MAX_FUTURE_PROGRAMS = 2

interface ProgramStore {
    suspend fun listCurrentAndFuture(referenceDate: LocalDate): List<ProgramMonth>
    suspend fun findById(id: ProgramMonthId): ProgramMonth?
    suspend fun save(program: ProgramMonth)
    suspend fun delete(id: ProgramMonthId)
    suspend fun updateTemplateAppliedAt(id: ProgramMonthId, templateAppliedAt: LocalDateTime)
    suspend fun loadCreationContext(referenceDate: LocalDate): ProgramCreationContext {
        val existing = listCurrentAndFuture(referenceDate)
        val existingByMonth = existing.map { it.yearMonth }.toSet()
        val futureMonths = existing
            .filter { it.timelineStatus(referenceDate) == ProgramTimelineStatus.FUTURE }
            .map { it.yearMonth }
            .toSet()
        return ProgramCreationContext(
            existingByMonth = existingByMonth,
            futureMonths = futureMonths,
        )
    }
}

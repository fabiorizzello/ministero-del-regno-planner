package org.example.project.feature.programs.application

import org.example.project.core.persistence.TransactionScope
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

interface ProgramStore {
    suspend fun listCurrentAndFuture(referenceDate: LocalDate): List<ProgramMonth>
    suspend fun findMostRecentPast(referenceDate: LocalDate): ProgramMonth?
    suspend fun findById(id: ProgramMonthId): ProgramMonth?
    context(tx: TransactionScope) suspend fun save(program: ProgramMonth)
    context(tx: TransactionScope) suspend fun delete(id: ProgramMonthId)
    context(tx: TransactionScope) suspend fun updateTemplateAppliedAt(id: ProgramMonthId, templateAppliedAt: LocalDateTime)
    suspend fun loadCreationContext(referenceDate: LocalDate): ProgramCreationContext {
        val existing = listCurrentAndFuture(referenceDate)
        val past = findMostRecentPast(referenceDate)
        val existingByMonth = buildSet {
            past?.let { add(it.yearMonth) }
            existing.forEach { add(it.yearMonth) }
        }
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

package org.example.project.feature.programs.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

@JvmInline
value class ProgramMonthId(val value: String)

enum class ProgramTimelineStatus {
    PAST,
    CURRENT,
    FUTURE,
}

data class ProgramMonth(
    val id: ProgramMonthId,
    val year: Int,
    val month: Int,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val templateAppliedAt: LocalDateTime?,
    val createdAt: LocalDateTime,
) {
    val yearMonth: YearMonth get() = YearMonth.of(year, month)

    fun timelineStatus(referenceDate: LocalDate): ProgramTimelineStatus = when {
        referenceDate < startDate -> ProgramTimelineStatus.FUTURE
        referenceDate > endDate -> ProgramTimelineStatus.PAST
        else -> ProgramTimelineStatus.CURRENT
    }
}

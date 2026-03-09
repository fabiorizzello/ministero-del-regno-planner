package org.example.project.feature.programs.application

import org.example.project.feature.programs.domain.ProgramMonth
import org.example.project.feature.programs.domain.ProgramMonthAggregate
import org.example.project.feature.programs.domain.ProgramTimelineStatus
import java.time.LocalDate

data class ProgramSelectionSnapshot(
    val current: ProgramMonth?,
    val futures: List<ProgramMonth>,
)

class CaricaProgrammiAttiviUseCase(
    private val programStore: ProgramStore,
) {
    suspend operator fun invoke(referenceDate: LocalDate): ProgramSelectionSnapshot {
        val programs = programStore.listCurrentAndFuture(referenceDate)
        val current = programs.firstOrNull { it.timelineStatus(referenceDate) == ProgramTimelineStatus.CURRENT }
        val futures = programs
            .filter { it.timelineStatus(referenceDate) == ProgramTimelineStatus.FUTURE }
            .sortedBy { it.startDate }
            .take(ProgramMonthAggregate.MAX_FUTURE_PROGRAMS)
        return ProgramSelectionSnapshot(current = current, futures = futures)
    }
}

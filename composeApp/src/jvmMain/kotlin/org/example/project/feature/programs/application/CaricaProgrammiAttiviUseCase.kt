package org.example.project.feature.programs.application

import arrow.core.Either
import org.example.project.core.domain.DomainError
import org.example.project.feature.programs.domain.ProgramMonth
import org.example.project.feature.programs.domain.ProgramMonthAggregate
import org.example.project.feature.programs.domain.ProgramTimelineStatus
import java.time.LocalDate

data class ProgramSelectionSnapshot(
    val previous: ProgramMonth?,
    val current: ProgramMonth?,
    val futures: List<ProgramMonth>,
)

fun interface CaricaProgrammiAttiviOperation {
    suspend operator fun invoke(referenceDate: LocalDate): Either<DomainError, ProgramSelectionSnapshot>
}

class CaricaProgrammiAttiviUseCase(
    private val programStore: ProgramStore,
) : CaricaProgrammiAttiviOperation {
    override suspend operator fun invoke(referenceDate: LocalDate): Either<DomainError, ProgramSelectionSnapshot> =
        Either.catch {
            val programs = programStore.listCurrentAndFuture(referenceDate)
            val current = programs.firstOrNull { it.timelineStatus(referenceDate) == ProgramTimelineStatus.CURRENT }
            val futures = programs
                .filter { it.timelineStatus(referenceDate) == ProgramTimelineStatus.FUTURE }
                .sortedBy { it.startDate }
                .take(ProgramMonthAggregate.MAX_FUTURE_PROGRAMS)
            val previous = programStore.findMostRecentPast(referenceDate)
            ProgramSelectionSnapshot(previous = previous, current = current, futures = futures)
        }.mapLeft { DomainError.Validation(it.message ?: "Errore caricamento programmi") }
}

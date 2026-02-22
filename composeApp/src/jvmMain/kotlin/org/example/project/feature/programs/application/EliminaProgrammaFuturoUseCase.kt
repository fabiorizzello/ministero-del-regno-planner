package org.example.project.feature.programs.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.programs.domain.ProgramTimelineStatus
import java.time.LocalDate

class EliminaProgrammaFuturoUseCase(
    private val programStore: ProgramStore,
) {
    suspend operator fun invoke(
        programId: ProgramMonthId,
        referenceDate: LocalDate = LocalDate.now(),
    ): Either<DomainError, Unit> = either {
        val program = programStore.findById(programId)
            ?: raise(DomainError.Validation("Programma non trovato"))

        if (program.timelineStatus(referenceDate) != ProgramTimelineStatus.FUTURE) {
            raise(DomainError.Validation("Puoi eliminare solo programmi futuri"))
        }

        programStore.delete(programId)
    }
}

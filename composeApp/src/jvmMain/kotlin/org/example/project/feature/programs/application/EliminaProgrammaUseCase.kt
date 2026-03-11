package org.example.project.feature.programs.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionRunner
import org.example.project.feature.programs.domain.ProgramMonthAggregate
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.weeklyparts.application.WeekPlanStore
import java.time.LocalDate

class EliminaProgrammaUseCase(
    private val programStore: ProgramStore,
    private val weekPlanStore: WeekPlanStore,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(
        programId: ProgramMonthId,
        referenceDate: LocalDate = LocalDate.now(),
    ): Either<DomainError, Unit> = either {
        val program = programStore.findById(programId)
            ?: raise(DomainError.NotFound("Programma"))

        ProgramMonthAggregate(program)
            .validateDeletion(referenceDate)
            ?.let { raise(it) }

        Either.catch {
            transactionRunner.runInTransaction {
                weekPlanStore.deleteByProgram(programId)
                programStore.delete(programId)
            }
        }.mapLeft { DomainError.Validation(it.message ?: "Errore eliminazione programma") }.bind()
    }
}

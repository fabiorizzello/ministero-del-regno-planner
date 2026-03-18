package org.example.project.feature.assignments.application

import arrow.core.Either
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionRunner
import org.example.project.feature.programs.domain.ProgramMonthId
import java.time.LocalDate

class SvuotaAssegnazioniProgrammaUseCase(
    private val assignmentRepository: AssignmentRepository,
    private val transactionRunner: TransactionRunner,
) {
    suspend fun count(programId: ProgramMonthId, fromDate: LocalDate): Int =
        assignmentRepository.countByProgramFromDate(programId, fromDate)

    suspend fun execute(programId: ProgramMonthId, fromDate: LocalDate): Either<DomainError, Int> =
        transactionRunner.runInTransactionEither {
            Either.Right(assignmentRepository.deleteByProgramFromDate(programId, fromDate))
        }
}

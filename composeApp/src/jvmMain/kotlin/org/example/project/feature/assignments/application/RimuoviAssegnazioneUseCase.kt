package org.example.project.feature.assignments.application

import arrow.core.Either
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionRunner
import org.example.project.feature.assignments.domain.AssignmentId

class RimuoviAssegnazioneUseCase(
    private val assignmentStore: AssignmentRepository,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(assignmentId: AssignmentId): Either<DomainError, Unit> =
        transactionRunner.runInTransactionEither {
            Either.Right(assignmentStore.remove(assignmentId))
        }
}

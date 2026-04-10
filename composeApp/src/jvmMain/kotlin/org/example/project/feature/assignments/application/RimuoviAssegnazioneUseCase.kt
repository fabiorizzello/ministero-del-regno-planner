package org.example.project.feature.assignments.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionRunner
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.weeklyparts.application.WeekPlanStore

class RimuoviAssegnazioneUseCase(
    private val weekPlanStore: WeekPlanStore,
    private val assignmentRepository: AssignmentRepository,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(assignmentId: AssignmentId): Either<DomainError, Unit> =
        transactionRunner.runInTransactionEither {
            either {
                val weekPlanId = assignmentRepository.findWeekPlanIdByAssignmentId(assignmentId)
                    ?: raise(DomainError.NotFound("Assegnazione"))
                val aggregate = weekPlanStore.loadAggregateById(weekPlanId)
                    ?: raise(DomainError.NotFound("Settimana"))
                val updated = aggregate.removeAssignment(assignmentId).bind()
                weekPlanStore.saveAggregate(updated)
            }
        }
}

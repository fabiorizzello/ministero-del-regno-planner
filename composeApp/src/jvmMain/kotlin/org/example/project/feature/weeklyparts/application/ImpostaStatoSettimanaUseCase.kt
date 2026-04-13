package org.example.project.feature.weeklyparts.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionRunner
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeekPlanStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

class ImpostaStatoSettimanaUseCase(
    private val weekPlanStore: WeekPlanStore,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(
        weekPlanId: WeekPlanId,
        status: WeekPlanStatus,
        referenceDate: LocalDate,
    ): Either<DomainError, Unit> = either {
        val aggregate = weekPlanStore.loadAggregateById(weekPlanId)
            ?: raise(DomainError.NotFound("Settimana"))
        if (aggregate.weekPlan.status != status) {
            val currentMonday = referenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            if (aggregate.weekPlan.weekStartDate < currentMonday) raise(DomainError.SettimanaImmutabile)
        }
        transactionRunner.runInTransactionEither {
            Either.Right(weekPlanStore.saveAggregate(aggregate.setStatus(status)))
        }.bind()
    }
}

package org.example.project.feature.assignments.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionRunner
import org.example.project.feature.weeklyparts.application.WeekPlanStore
import java.time.LocalDate

class RimuoviAssegnazioniSettimanaUseCase(
    private val weekPlanStore: WeekPlanStore,
    private val transactionRunner: TransactionRunner,
) {
    suspend fun count(weekStartDate: LocalDate): Int {
        val aggregate = weekPlanStore.loadAggregateByDate(weekStartDate) ?: return 0
        return aggregate.assignments.size
    }

    suspend operator fun invoke(weekStartDate: LocalDate): Either<DomainError, Unit> = either {
        val aggregate = weekPlanStore.loadAggregateByDate(weekStartDate)
            ?: raise(DomainError.NotFound("Piano settimanale"))
        try {
            transactionRunner.runInTransaction {
                weekPlanStore.saveAggregate(aggregate.clearAssignments())
            }
        } catch (e: Exception) {
            raise(DomainError.RimozioneAssegnazioniFallita(e.message))
        }
    }
}

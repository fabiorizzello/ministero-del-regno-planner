package org.example.project.feature.weeklyparts.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionRunner
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.WeekPlan
import java.time.LocalDate
import java.util.UUID

class AggiungiParteUseCase(
    private val weekPlanStore: WeekPlanStore,
    private val partTypeStore: PartTypeStore,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(
        weekStartDate: LocalDate,
        partTypeId: PartTypeId,
    ): Either<DomainError, WeekPlan> = either {
        val aggregate = weekPlanStore.loadAggregateByDate(weekStartDate)
            ?: raise(DomainError.NotFound("Settimana"))

        val partType = partTypeStore.findById(partTypeId)
            ?: raise(DomainError.NotFound("Tipo parte"))
        val revisionId = partTypeStore.getLatestRevisionId(partTypeId)
        val updated = aggregate.addPart(
            partType = partType,
            partId = WeeklyPartId(UUID.randomUUID().toString()),
            partTypeRevisionId = revisionId,
        ).fold(
            ifLeft = { raise(it) },
            ifRight = { it },
        )
        transactionRunner.runInTransactionEither {
            Either.Right(weekPlanStore.saveAggregate(updated))
        }.bind()

        updated.weekPlan
    }
}

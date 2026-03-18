package org.example.project.feature.weeklyparts.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionRunner
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.LocalDate
import java.util.UUID

class AggiornaPartiSettimanaUseCase(
    private val weekPlanStore: WeekPlanStore,
    private val partTypeStore: PartTypeStore,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(
        weekPlanId: WeekPlanId,
        orderedPartTypeIds: List<PartTypeId>,
        referenceDate: LocalDate = LocalDate.now(),
    ): Either<DomainError, Unit> = either {
        if (orderedPartTypeIds.isEmpty()) {
            raise(DomainError.OrdinePartiNonValido)
        }
        val aggregate = weekPlanStore.loadAggregateById(weekPlanId)
            ?: raise(DomainError.NotFound("Settimana"))
        val partTypesById = partTypeStore.all().associateBy { partType -> partType.id }
        val orderedParts = orderedPartTypeIds.map { partTypeId ->
            val partType = partTypesById[partTypeId]
                ?: raise(DomainError.NotFound("Tipo parte"))
            partType to partTypeStore.getLatestRevisionId(partTypeId)
        }
        val updated = aggregate.replaceParts(orderedParts, referenceDate) {
            WeeklyPartId(UUID.randomUUID().toString())
        }.fold(
            ifLeft = { raise(it) },
            ifRight = { it },
        )
        transactionRunner.runInTransactionEither {
            Either.Right(weekPlanStore.saveAggregate(updated))
        }.bind()
    }
}

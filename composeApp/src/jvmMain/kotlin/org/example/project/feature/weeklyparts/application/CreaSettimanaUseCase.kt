package org.example.project.feature.weeklyparts.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionRunner
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanAggregate
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

class CreaSettimanaUseCase(
    private val weekPlanStore: WeekPlanStore,
    private val partTypeStore: PartTypeStore,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(weekStartDate: LocalDate): Either<DomainError, WeekPlan> = either {
        val existing = weekPlanStore.loadAggregateByDate(weekStartDate)
        if (existing != null) raise(DomainError.SettimanaGiaEsistente)
        if (weekStartDate.dayOfWeek != DayOfWeek.MONDAY) {
            raise(DomainError.DataSettimanaNonLunedi)
        }

        val fixedPartType = partTypeStore.findFixed()
            ?: raise(DomainError.CatalogoTipiNonDisponibile)
        val revisionId = partTypeStore.getLatestRevisionId(fixedPartType.id)

        val aggregate = WeekPlanAggregate.createWeekWithFixedPart(
            weekPlanId = WeekPlanId(UUID.randomUUID().toString()),
            weekStartDate = weekStartDate,
            fixedPartType = fixedPartType,
            fixedPartId = WeeklyPartId(UUID.randomUUID().toString()),
            fixedPartRevisionId = revisionId,
        )
        transactionRunner.runInTransaction {
            weekPlanStore.saveAggregate(aggregate)
        }

        weekPlanStore.loadAggregateByDate(weekStartDate)?.weekPlan
            ?: raise(DomainError.SalvataggioSettimanaFallito)
    }
}

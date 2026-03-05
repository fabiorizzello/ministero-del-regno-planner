package org.example.project.feature.weeklyparts.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionRunner
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.canBeMutated
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

class AggiungiParteUseCase(
    private val weekPlanStore: WeekPlanStore,
    private val partTypeStore: PartTypeStore,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(
        weekStartDate: LocalDate,
        partTypeId: PartTypeId,
        referenceDate: LocalDate = LocalDate.now(),
    ): Either<DomainError, WeekPlan> = either {
        val weekPlan = weekPlanStore.findByDate(weekStartDate)
            ?: raise(DomainError.Validation("Settimana non trovata"))

        val currentMonday = referenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        if (!weekPlan.canBeMutated(currentMonday)) {
            raise(DomainError.Validation("La settimana non è modificabile (passata o saltata)"))
        }

        val nextOrder = (weekPlan.parts.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val revisionId = partTypeStore.getLatestRevisionId(partTypeId)
        transactionRunner.runInTransaction {
            weekPlanStore.addPart(weekPlan.id, partTypeId, nextOrder, partTypeRevisionId = revisionId)
        }

        weekPlanStore.findByDate(weekStartDate)
            ?: raise(DomainError.Validation("Errore nel salvataggio"))
    }
}

package org.example.project.feature.weeklyparts.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import java.time.LocalDate
import java.util.UUID

class CreaSettimanaUseCase(
    private val weekPlanStore: WeekPlanStore,
    private val partTypeStore: PartTypeStore,
) {
    suspend operator fun invoke(weekStartDate: LocalDate): Either<DomainError, WeekPlan> = either {
        val existing = weekPlanStore.findByDate(weekStartDate)
        if (existing != null) raise(DomainError.Validation("La settimana esiste gia'"))

        val fixedPartType = partTypeStore.findFixed()
            ?: raise(DomainError.Validation("Catalogo tipi non disponibile. Aggiorna i dati prima."))

        val weekPlan = WeekPlan(
            id = WeekPlanId(UUID.randomUUID().toString()),
            weekStartDate = weekStartDate,
            parts = emptyList(),
        )
        weekPlanStore.save(weekPlan)
        weekPlanStore.addPart(weekPlan.id, fixedPartType.id, sortOrder = 0)

        weekPlanStore.findByDate(weekStartDate)
            ?: raise(DomainError.Validation("Errore nel salvataggio della settimana"))
    }
}

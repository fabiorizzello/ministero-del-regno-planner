package org.example.project.feature.weeklyparts.application

import org.example.project.feature.weeklyparts.domain.WeeklyPartId

class RiordinaPartiUseCase(
    private val weekPlanStore: WeekPlanStore,
) {
    suspend operator fun invoke(orderedPartIds: List<WeeklyPartId>) {
        val updates = orderedPartIds.mapIndexed { index, id -> id to index }
        weekPlanStore.updateSortOrders(updates)
    }
}

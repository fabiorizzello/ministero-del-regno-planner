package org.example.project.feature.output.application

import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeeklyPartId

class VerificaConsegnaPreAssegnazioneUseCase(
    private val store: SlipDeliveryStore,
) {
    suspend operator fun invoke(weeklyPartId: WeeklyPartId, weekPlanId: WeekPlanId): String? =
        store.findActiveDelivery(weeklyPartId, weekPlanId)?.studentName
}

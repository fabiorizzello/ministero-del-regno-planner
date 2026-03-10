package org.example.project.feature.output.application

import org.example.project.feature.weeklyparts.domain.WeeklyPartId

class VerificaConsegnaPreAssegnazioneUseCase(
    private val store: SlipDeliveryStore,
) {
    suspend operator fun invoke(weeklyPartId: WeeklyPartId, weekPlanId: String): String? =
        store.findActiveDelivery(weeklyPartId, weekPlanId)?.studentName
}

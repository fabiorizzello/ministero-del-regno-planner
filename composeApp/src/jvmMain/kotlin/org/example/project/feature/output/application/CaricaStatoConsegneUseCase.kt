package org.example.project.feature.output.application

import java.time.Instant
import org.example.project.feature.output.domain.SlipDeliveryInfo
import org.example.project.feature.output.domain.SlipDeliveryStatus
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeeklyPartId

class CaricaStatoConsegneUseCase(
    private val store: SlipDeliveryStore,
) {
    suspend operator fun invoke(
        weekPlanIds: List<WeekPlanId>,
    ): Map<Pair<WeeklyPartId, WeekPlanId>, SlipDeliveryInfo> {
        if (weekPlanIds.isEmpty()) return emptyMap()

        val active = store.listActiveDeliveries(weekPlanIds)
        val cancelled = store.listCancelledDeliveries(weekPlanIds)

        val activeByKey = active.associateBy { it.weeklyPartId to it.weekPlanId }
        val cancelledByKey = cancelled
            .groupBy { it.weeklyPartId to it.weekPlanId }
            .mapValues { (_, list) -> list.maxByOrNull { it.cancelledAt ?: Instant.MIN } }

        val allKeys = activeByKey.keys + cancelledByKey.keys
        return allKeys.associateWith { key ->
            val activeDelivery = activeByKey[key]
            val lastCancelled = cancelledByKey[key]

            when {
                activeDelivery != null -> SlipDeliveryInfo(
                    status = SlipDeliveryStatus.INVIATO,
                    activeDelivery = activeDelivery,
                    previousStudentName = null,
                )
                // lastCancelled is guaranteed non-null here: allKeys = activeByKey.keys + cancelledByKey.keys,
                // so every key is in at least one map; if activeDelivery is null, lastCancelled must exist.
                else -> SlipDeliveryInfo(
                    status = SlipDeliveryStatus.DA_REINVIARE,
                    activeDelivery = null,
                    previousStudentName = lastCancelled?.studentName,
                )
            }
        }
    }
}

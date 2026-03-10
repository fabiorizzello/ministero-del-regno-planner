package org.example.project.feature.output.application

import org.example.project.core.persistence.TransactionScope
import org.example.project.feature.output.domain.SlipDelivery
import org.example.project.feature.output.domain.SlipDeliveryId
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.Instant

internal class FakeSlipDeliveryStore : SlipDeliveryStore {
    val inserted = mutableListOf<SlipDelivery>()
    val cancelledIds = mutableListOf<SlipDeliveryId>()
    val activeDeliveries = mutableMapOf<Pair<WeeklyPartId, WeekPlanId>, SlipDelivery>()
    val cancelledDeliveries = mutableListOf<SlipDelivery>()

    override suspend fun findActiveDelivery(weeklyPartId: WeeklyPartId, weekPlanId: WeekPlanId): SlipDelivery? =
        activeDeliveries[weeklyPartId to weekPlanId]

    override suspend fun findLastCancelledDelivery(weeklyPartId: WeeklyPartId, weekPlanId: WeekPlanId): SlipDelivery? =
        cancelledDeliveries
            .filter { it.weeklyPartId == weeklyPartId && it.weekPlanId == weekPlanId }
            .maxByOrNull { requireNotNull(it.cancelledAt) { "cancelled delivery must have cancelledAt" } }

    override suspend fun listActiveDeliveries(weekPlanIds: List<WeekPlanId>): List<SlipDelivery> =
        activeDeliveries.values.filter { it.weekPlanId in weekPlanIds }

    override suspend fun listCancelledDeliveries(weekPlanIds: List<WeekPlanId>): List<SlipDelivery> =
        cancelledDeliveries.filter { it.weekPlanId in weekPlanIds }

    context(tx: TransactionScope)
    override suspend fun insert(delivery: SlipDelivery) {
        inserted += delivery
        activeDeliveries[delivery.weeklyPartId to delivery.weekPlanId] = delivery
    }

    context(tx: TransactionScope)
    override suspend fun cancel(id: SlipDeliveryId, cancelledAt: Instant) {
        cancelledIds += id
        val key = activeDeliveries.entries.find { it.value.id == id }?.key
        if (key != null) {
            val removed = activeDeliveries.remove(key)!!
            cancelledDeliveries += removed.copy(cancelledAt = cancelledAt)
        }
    }
}

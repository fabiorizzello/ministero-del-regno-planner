package org.example.project.feature.output.application

import org.example.project.core.persistence.DefaultTransactionScope
import org.example.project.core.persistence.TransactionRunner
import org.example.project.core.persistence.TransactionScope
import org.example.project.feature.output.domain.SlipDelivery
import org.example.project.feature.output.domain.SlipDeliveryId
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.Instant

internal class FakeSlipDeliveryStore : SlipDeliveryStore {
    val inserted = mutableListOf<SlipDelivery>()
    val cancelledIds = mutableListOf<SlipDeliveryId>()
    val activeDeliveries = mutableMapOf<Pair<WeeklyPartId, String>, SlipDelivery>()
    val cancelledDeliveries = mutableListOf<SlipDelivery>()

    override suspend fun findActiveDelivery(weeklyPartId: WeeklyPartId, weekPlanId: String): SlipDelivery? =
        activeDeliveries[weeklyPartId to weekPlanId]

    override suspend fun findLastCancelledDelivery(weeklyPartId: WeeklyPartId, weekPlanId: String): SlipDelivery? =
        cancelledDeliveries
            .filter { it.weeklyPartId == weeklyPartId && it.weekPlanId == weekPlanId }
            .maxByOrNull { it.cancelledAt!! }

    override suspend fun listActiveDeliveries(weekPlanIds: List<String>): List<SlipDelivery> =
        activeDeliveries.values.filter { it.weekPlanId in weekPlanIds }

    override suspend fun listCancelledDeliveries(weekPlanIds: List<String>): List<SlipDelivery> =
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

internal class ImmediateTransactionRunner : TransactionRunner {
    override suspend fun <T> runInTransaction(block: suspend TransactionScope.() -> T): T =
        with(DefaultTransactionScope) { block() }
}

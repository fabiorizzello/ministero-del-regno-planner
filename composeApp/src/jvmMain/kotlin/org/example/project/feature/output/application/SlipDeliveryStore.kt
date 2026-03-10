package org.example.project.feature.output.application

import org.example.project.core.persistence.TransactionScope
import org.example.project.feature.output.domain.SlipDelivery
import org.example.project.feature.output.domain.SlipDeliveryId
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.Instant

interface SlipDeliveryStore {
    suspend fun findActiveDelivery(weeklyPartId: WeeklyPartId, weekPlanId: WeekPlanId): SlipDelivery?
    suspend fun findLastCancelledDelivery(weeklyPartId: WeeklyPartId, weekPlanId: WeekPlanId): SlipDelivery?
    suspend fun listActiveDeliveries(weekPlanIds: List<WeekPlanId>): List<SlipDelivery>
    suspend fun listCancelledDeliveries(weekPlanIds: List<WeekPlanId>): List<SlipDelivery>

    context(tx: TransactionScope) suspend fun insert(delivery: SlipDelivery)
    context(tx: TransactionScope) suspend fun cancel(id: SlipDeliveryId, cancelledAt: Instant)
}

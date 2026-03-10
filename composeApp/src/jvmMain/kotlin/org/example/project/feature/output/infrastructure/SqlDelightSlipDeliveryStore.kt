package org.example.project.feature.output.infrastructure

import org.example.project.core.persistence.TransactionScope
import org.example.project.db.MinisteroDatabase
import org.example.project.feature.output.application.SlipDeliveryStore
import org.example.project.feature.output.domain.SlipDelivery
import org.example.project.feature.output.domain.SlipDeliveryId
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.Instant

class SqlDelightSlipDeliveryStore(
    private val database: MinisteroDatabase,
) : SlipDeliveryStore {

    override suspend fun findActiveDelivery(weeklyPartId: WeeklyPartId, weekPlanId: String): SlipDelivery? {
        return database.ministeroDatabaseQueries
            .findActiveDelivery(weeklyPartId.value, weekPlanId, ::mapRow)
            .executeAsOneOrNull()
    }

    override suspend fun findLastCancelledDelivery(weeklyPartId: WeeklyPartId, weekPlanId: String): SlipDelivery? {
        return database.ministeroDatabaseQueries
            .findLastCancelledDelivery(weeklyPartId.value, weekPlanId, ::mapRow)
            .executeAsOneOrNull()
    }

    override suspend fun listActiveDeliveries(weekPlanIds: List<String>): List<SlipDelivery> {
        if (weekPlanIds.isEmpty()) return emptyList()
        return database.ministeroDatabaseQueries
            .listActiveDeliveriesByWeekPlanIds(weekPlanIds, ::mapRow)
            .executeAsList()
    }

    override suspend fun listCancelledDeliveries(weekPlanIds: List<String>): List<SlipDelivery> {
        if (weekPlanIds.isEmpty()) return emptyList()
        return database.ministeroDatabaseQueries
            .listCancelledDeliveriesByWeekPlanIds(weekPlanIds, ::mapRow)
            .executeAsList()
    }

    context(tx: TransactionScope)
    override suspend fun insert(delivery: SlipDelivery) {
        database.ministeroDatabaseQueries.insertDelivery(
            id = delivery.id.value,
            weekly_part_id = delivery.weeklyPartId.value,
            week_plan_id = delivery.weekPlanId,
            student_name = delivery.studentName,
            assistant_name = delivery.assistantName,
            sent_at = delivery.sentAt.toString(),
        )
    }

    context(tx: TransactionScope)
    override suspend fun cancel(id: SlipDeliveryId, cancelledAt: Instant) {
        database.ministeroDatabaseQueries.cancelDelivery(
            cancelled_at = cancelledAt.toString(),
            id = id.value,
        )
    }
}

private fun mapRow(
    id: String,
    weekly_part_id: String,
    week_plan_id: String,
    student_name: String,
    assistant_name: String?,
    sent_at: String,
    cancelled_at: String?,
): SlipDelivery = SlipDelivery(
    id = SlipDeliveryId(id),
    weeklyPartId = WeeklyPartId(weekly_part_id),
    weekPlanId = week_plan_id,
    studentName = student_name,
    assistantName = assistant_name,
    sentAt = Instant.parse(sent_at),
    cancelledAt = cancelled_at?.let { Instant.parse(it) },
)

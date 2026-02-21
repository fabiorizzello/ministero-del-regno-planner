package org.example.project.feature.weeklyparts.infrastructure

import org.example.project.db.MinisteroDatabase
import org.example.project.feature.weeklyparts.application.WeekPlanStore
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeekPlanSummary
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.LocalDate
import java.util.UUID

class SqlDelightWeekPlanStore(
    private val database: MinisteroDatabase,
) : WeekPlanStore {

    override suspend fun findByDate(weekStartDate: LocalDate): WeekPlan? {
        val dateStr = weekStartDate.toString()
        val row = database.ministeroDatabaseQueries
            .findWeekPlanByDate(dateStr)
            .executeAsOneOrNull() ?: return null

        val parts = database.ministeroDatabaseQueries
            .partsForWeek(row.id, ::mapWeeklyPartWithTypeRow)
            .executeAsList()

        return WeekPlan(
            id = WeekPlanId(row.id),
            weekStartDate = weekStartDate,
            parts = parts,
        )
    }

    override suspend fun listInRange(startDate: LocalDate, endDate: LocalDate): List<WeekPlanSummary> {
        return database.ministeroDatabaseQueries
            .weekPlansInRange(startDate.toString(), endDate.toString())
            .executeAsList()
            .map { row ->
                WeekPlanSummary(
                    id = WeekPlanId(row.id),
                    weekStartDate = LocalDate.parse(row.week_start_date),
                )
            }
    }

    override suspend fun totalSlotsByWeekInRange(startDate: LocalDate, endDate: LocalDate): Map<WeekPlanId, Int> {
        return database.ministeroDatabaseQueries
            .totalSlotsByWeekInRange(startDate.toString(), endDate.toString())
            .executeAsList()
            .associate { row ->
                WeekPlanId(row.week_plan_id) to row.total_slots.toInt()
            }
    }

    override suspend fun save(weekPlan: WeekPlan) {
        database.ministeroDatabaseQueries.insertWeekPlan(
            id = weekPlan.id.value,
            week_start_date = weekPlan.weekStartDate.toString(),
        )
    }

    override suspend fun delete(weekPlanId: WeekPlanId) {
        database.ministeroDatabaseQueries.deleteWeekPlan(weekPlanId.value)
    }

    override suspend fun addPart(
        weekPlanId: WeekPlanId,
        partTypeId: PartTypeId,
        sortOrder: Int,
    ): WeeklyPartId {
        val id = UUID.randomUUID().toString()
        database.ministeroDatabaseQueries.insertWeeklyPart(
            id = id,
            week_plan_id = weekPlanId.value,
            part_type_id = partTypeId.value,
            sort_order = sortOrder.toLong(),
        )
        return WeeklyPartId(id)
    }

    override suspend fun removePart(weeklyPartId: WeeklyPartId) {
        database.ministeroDatabaseQueries.deleteWeeklyPart(weeklyPartId.value)
    }

    override suspend fun updateSortOrders(parts: List<Pair<WeeklyPartId, Int>>) {
        database.ministeroDatabaseQueries.transaction {
            parts.forEach { (id, order) ->
                database.ministeroDatabaseQueries.updateWeeklyPartSortOrder(
                    sort_order = order.toLong(),
                    id = id.value,
                )
            }
        }
    }

    override suspend fun replaceAllParts(
        weekPlanId: WeekPlanId,
        partTypeIds: List<PartTypeId>,
    ) {
        database.ministeroDatabaseQueries.transaction {
            database.ministeroDatabaseQueries.deleteAllPartsForWeek(weekPlanId.value)
            partTypeIds.forEachIndexed { index, partTypeId ->
                database.ministeroDatabaseQueries.insertWeeklyPart(
                    id = UUID.randomUUID().toString(),
                    week_plan_id = weekPlanId.value,
                    part_type_id = partTypeId.value,
                    sort_order = index.toLong(),
                )
            }
        }
    }
}

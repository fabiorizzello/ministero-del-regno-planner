package org.example.project.feature.weeklyparts.infrastructure

import arrow.core.getOrElse
import org.example.project.db.MinisteroDatabase
import org.example.project.core.persistence.TransactionScope
import org.example.project.feature.assignments.domain.Assignment
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.weeklyparts.application.WeekPlanStore
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanAggregate
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeekPlanStatus
import org.example.project.feature.weeklyparts.domain.WeekPlanSummary
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import org.slf4j.LoggerFactory
import java.time.LocalDate

private val logger = LoggerFactory.getLogger("SqlDelightWeekPlanStore")

private fun parseStatusOrDefault(raw: String): WeekPlanStatus =
    WeekPlanStatus.entries.find { it.name == raw }
        ?: run {
            logger.warn("WeekPlanStatus sconosciuto '{}' -> fallback a ACTIVE", raw)
            WeekPlanStatus.ACTIVE
        }

private data class WeekPlanRow(
    val id: String,
    val weekStartDate: String,
    val programId: String?,
    val status: String,
)

class SqlDelightWeekPlanStore(
    private val database: MinisteroDatabase,
) : WeekPlanStore {

    override suspend fun findByDate(weekStartDate: LocalDate): WeekPlan? =
        loadAggregateByDate(weekStartDate)?.weekPlan

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

    override suspend fun findByDateAndProgram(weekStartDate: LocalDate, programId: ProgramMonthId): WeekPlan? =
        loadAggregateByDateAndProgram(weekStartDate, programId)?.weekPlan

    override suspend fun listByProgram(programId: ProgramMonthId): List<WeekPlan> =
        listAggregatesByProgram(programId).map { aggregate -> aggregate.weekPlan }

    override suspend fun loadAggregateByDate(weekStartDate: LocalDate): WeekPlanAggregate? {
        val row = database.ministeroDatabaseQueries
            .findWeekPlanByDate(weekStartDate.toString()) { id, week_start_date, program_id, status ->
                WeekPlanRow(
                    id = id,
                    weekStartDate = week_start_date,
                    programId = program_id,
                    status = status,
                )
            }
            .executeAsOneOrNull() ?: return null

        return loadAggregate(row)
    }

    override suspend fun loadAggregateById(weekPlanId: WeekPlanId): WeekPlanAggregate? {
        val row = database.ministeroDatabaseQueries
            .findWeekPlanById(weekPlanId.value) { id, week_start_date, program_id, status ->
                WeekPlanRow(
                    id = id,
                    weekStartDate = week_start_date,
                    programId = program_id,
                    status = status,
                )
            }
            .executeAsOneOrNull() ?: return null

        return loadAggregate(row)
    }

    override suspend fun loadAggregateByDateAndProgram(
        weekStartDate: LocalDate,
        programId: ProgramMonthId,
    ): WeekPlanAggregate? {
        val row = database.ministeroDatabaseQueries
            .findWeekPlanByDateAndProgram(weekStartDate.toString(), programId.value) { id, week_start_date, program_id, status ->
                WeekPlanRow(
                    id = id,
                    weekStartDate = week_start_date,
                    programId = program_id,
                    status = status,
                )
            }
            .executeAsOneOrNull() ?: return null

        return loadAggregate(row)
    }

    override suspend fun listAggregatesByProgram(programId: ProgramMonthId): List<WeekPlanAggregate> {
        val rows = database.ministeroDatabaseQueries
            .listWeekPlansByProgram(programId.value) { id, week_start_date, program_id, status ->
                WeekPlanRow(
                    id = id,
                    weekStartDate = week_start_date,
                    programId = program_id,
                    status = status,
                )
            }
            .executeAsList()

        return rows.map { row -> loadAggregate(row) }
    }

    context(tx: TransactionScope)
    override suspend fun saveAggregate(aggregate: WeekPlanAggregate) {
        persistAggregate(aggregate)
    }

    context(tx: TransactionScope)
    override suspend fun replaceProgramAggregates(
        programId: ProgramMonthId,
        aggregates: List<WeekPlanAggregate>,
    ) {
        database.ministeroDatabaseQueries.deleteWeekPlansByProgram(programId.value)
        aggregates.forEach { aggregate ->
            val normalized = aggregate.copy(
                weekPlan = aggregate.weekPlan.copy(programId = programId),
            )
            persistAggregate(normalized)
        }
    }

    context(tx: TransactionScope)
    override suspend fun deleteByProgram(programId: ProgramMonthId) {
        database.ministeroDatabaseQueries.deleteWeekPlansByProgram(programId.value)
    }

    private fun persistAggregate(aggregate: WeekPlanAggregate) {
        val week = aggregate.weekPlan
        database.ministeroDatabaseQueries.upsertWeekPlan(
            id = week.id.value,
            week_start_date = week.weekStartDate.toString(),
            program_id = week.programId?.value,
            status = week.status.name,
        )

        database.ministeroDatabaseQueries.deleteAssignmentsForWeek(week.id.value)
        database.ministeroDatabaseQueries.deleteAllPartsForWeek(week.id.value)

        week.parts.forEach { part ->
            database.ministeroDatabaseQueries.insertWeeklyPart(
                id = part.id.value,
                week_plan_id = week.id.value,
                part_type_id = part.partType.id.value,
                part_type_revision_id = part.partTypeRevisionId,
                sort_order = part.sortOrder.toLong(),
            )
        }

        aggregate.assignments.forEach { assignment ->
            database.ministeroDatabaseQueries.upsertAssignment(
                id = assignment.id.value,
                weekly_part_id = assignment.weeklyPartId.value,
                person_id = assignment.personId.value,
                slot = assignment.slot.toLong(),
            )
        }
    }

    private fun loadAggregate(row: WeekPlanRow): WeekPlanAggregate {
        val weekId = WeekPlanId(row.id)
        val parts = database.ministeroDatabaseQueries
            .partsForWeek(row.id, ::mapWeeklyPartWithTypeRow)
            .executeAsList()

        val assignments = database.ministeroDatabaseQueries
            .assignmentsForWeek(row.id) { id, weekly_part_id, person_id, slot, _, _, _ ->
                Assignment.of(
                    id = AssignmentId(id),
                    weeklyPartId = WeeklyPartId(weekly_part_id),
                    personId = ProclamatoreId(person_id),
                    slot = slot.toInt(),
                ).getOrElse { error("Invalid assignment from DB: $it") }
            }
            .executeAsList()

        val weekPlan = WeekPlan(
            id = weekId,
            weekStartDate = LocalDate.parse(row.weekStartDate),
            parts = parts,
            programId = row.programId?.let(::ProgramMonthId),
            status = parseStatusOrDefault(row.status),
        )

        return WeekPlanAggregate(
            weekPlan = weekPlan,
            assignments = assignments,
        )
    }
}

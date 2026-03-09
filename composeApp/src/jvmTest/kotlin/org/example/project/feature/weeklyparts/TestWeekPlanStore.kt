package org.example.project.feature.weeklyparts

import org.example.project.core.persistence.TransactionScope
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.weeklyparts.application.WeekPlanStore
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanAggregate
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeekPlanSummary
import java.time.LocalDate

open class TestWeekPlanStore : WeekPlanStore {
    override suspend fun findByDate(weekStartDate: LocalDate): WeekPlan? = null

    override suspend fun listInRange(startDate: LocalDate, endDate: LocalDate): List<WeekPlanSummary> = emptyList()

    override suspend fun totalSlotsByWeekInRange(startDate: LocalDate, endDate: LocalDate): Map<WeekPlanId, Int> = emptyMap()

    override suspend fun findByDateAndProgram(weekStartDate: LocalDate, programId: ProgramMonthId): WeekPlan? = null

    override suspend fun listByProgram(programId: ProgramMonthId): List<WeekPlan> = emptyList()

    override suspend fun loadAggregateByDate(weekStartDate: LocalDate): WeekPlanAggregate? =
        findByDate(weekStartDate)?.let { week -> WeekPlanAggregate(weekPlan = week, assignments = emptyList()) }

    override suspend fun loadAggregateById(weekPlanId: WeekPlanId): WeekPlanAggregate? = null

    override suspend fun loadAggregateByDateAndProgram(
        weekStartDate: LocalDate,
        programId: ProgramMonthId,
    ): WeekPlanAggregate? =
        findByDateAndProgram(weekStartDate, programId)?.let { week -> WeekPlanAggregate(weekPlan = week, assignments = emptyList()) }

    override suspend fun listAggregatesByProgram(programId: ProgramMonthId): List<WeekPlanAggregate> =
        listByProgram(programId).map { week -> WeekPlanAggregate(weekPlan = week, assignments = emptyList()) }

    context(tx: TransactionScope)
    override suspend fun saveAggregate(aggregate: WeekPlanAggregate) {}

    context(tx: TransactionScope)
    override suspend fun replaceProgramAggregates(programId: ProgramMonthId, aggregates: List<WeekPlanAggregate>) {}

    context(tx: TransactionScope)
    override suspend fun deleteByProgram(programId: ProgramMonthId) {}
}

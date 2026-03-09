package org.example.project.feature.weeklyparts.application

import org.example.project.core.persistence.TransactionScope
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanAggregate
import org.example.project.feature.weeklyparts.domain.WeekPlanSummary
import java.time.LocalDate

/** Read-only access to week plans. Use cases that never mutate inject this instead of [WeekPlanStore]. */
interface WeekPlanQueries {
    suspend fun findByDate(weekStartDate: LocalDate): WeekPlan?
    suspend fun listInRange(startDate: LocalDate, endDate: LocalDate): List<WeekPlanSummary>
    suspend fun totalSlotsByWeekInRange(startDate: LocalDate, endDate: LocalDate): Map<WeekPlanId, Int>
    suspend fun findByDateAndProgram(weekStartDate: LocalDate, programId: ProgramMonthId): WeekPlan?
    suspend fun listByProgram(programId: ProgramMonthId): List<WeekPlan>
}

interface WeekPlanStore : WeekPlanQueries {
    suspend fun loadAggregateByDate(weekStartDate: LocalDate): WeekPlanAggregate?
    suspend fun loadAggregateById(weekPlanId: WeekPlanId): WeekPlanAggregate?
    suspend fun loadAggregateByDateAndProgram(weekStartDate: LocalDate, programId: ProgramMonthId): WeekPlanAggregate?
    suspend fun listAggregatesByProgram(programId: ProgramMonthId): List<WeekPlanAggregate>
    context(tx: TransactionScope) suspend fun saveAggregate(aggregate: WeekPlanAggregate)
    context(tx: TransactionScope) suspend fun replaceProgramAggregates(programId: ProgramMonthId, aggregates: List<WeekPlanAggregate>)
    context(tx: TransactionScope) suspend fun deleteByProgram(programId: ProgramMonthId)
}

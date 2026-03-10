package org.example.project.feature.output.application

import arrow.core.Either
import arrow.core.right
import org.example.project.core.domain.DomainError
import org.example.project.feature.assignments.application.AssignmentRepository
import org.example.project.feature.output.domain.ProgramDeliverySnapshot
import org.example.project.feature.output.domain.completePartIds
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.weeklyparts.application.WeekPlanQueries
import org.example.project.feature.weeklyparts.domain.WeekPlanStatus
import java.time.LocalDate

class CaricaRiepilogoConsegneProgrammaUseCase(
    private val weekPlanQueries: WeekPlanQueries,
    private val assignmentRepository: AssignmentRepository,
    private val slipDeliveryStore: SlipDeliveryStore,
) {
    suspend operator fun invoke(
        programId: ProgramMonthId,
        referenceDate: LocalDate,
    ): Either<DomainError, ProgramDeliverySnapshot> {
        val weeks = weekPlanQueries.listByProgram(programId)
            .filter { it.weekStartDate >= referenceDate && it.status == WeekPlanStatus.ACTIVE }

        if (weeks.isEmpty()) return ProgramDeliverySnapshot(pending = 0, blocked = 0).right()

        val weekPlanIds = weeks.map { it.id }.toSet()
        val assignmentsByWeek = assignmentRepository.listByWeekPlanIds(weekPlanIds)
        val activeDeliveries = slipDeliveryStore.listActiveDeliveries(weekPlanIds.toList())
        val deliveredPartIds = activeDeliveries.map { it.weeklyPartId }.toSet()

        var pending = 0
        var blocked = 0

        for (week in weeks) {
            val assignments = assignmentsByWeek[week.id].orEmpty()
            val complete = completePartIds(week.parts, assignments)
            for (part in week.parts) {
                if (part.id in complete) {
                    if (part.id !in deliveredPartIds) {
                        pending++
                    }
                } else {
                    blocked++
                }
            }
        }

        return ProgramDeliverySnapshot(pending = pending, blocked = blocked).right()
    }
}

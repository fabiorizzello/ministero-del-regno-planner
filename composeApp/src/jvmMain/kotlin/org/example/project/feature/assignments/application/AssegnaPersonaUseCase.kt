package org.example.project.feature.assignments.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionScope
import org.example.project.core.persistence.TransactionRunner
import org.example.project.feature.assignments.domain.Assignment
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.people.application.ProclamatoriAggregateStore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.weeklyparts.application.WeekPlanStore
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.LocalDate
import java.util.UUID

class AssegnaPersonaUseCase(
    private val weekPlanStore: WeekPlanStore,
    private val transactionRunner: TransactionRunner,
    private val personStore: ProclamatoriAggregateStore,
) {
    suspend operator fun invoke(
        weekStartDate: LocalDate,
        weeklyPartId: WeeklyPartId,
        personId: ProclamatoreId,
        slot: Int,
    ): Either<DomainError, Unit> = transactionRunner.runInTransaction {
        assignWithoutTransaction(
            weekStartDate = weekStartDate,
            weeklyPartId = weeklyPartId,
            personId = personId,
            slot = slot,
        )
    }

    context(tx: TransactionScope)
    internal suspend fun assignWithoutTransaction(
        weekStartDate: LocalDate,
        weeklyPartId: WeeklyPartId,
        personId: ProclamatoreId,
        slot: Int,
    ): Either<DomainError, Unit> = either {
        val aggregate = weekPlanStore.loadAggregateByDate(weekStartDate)
            ?: raise(DomainError.NotFound("Settimana"))

        val persona = personStore.load(personId)
            ?: raise(DomainError.NotFound("Proclamatore"))

        val assignment = Assignment(
            id = AssignmentId(UUID.randomUUID().toString()),
            weeklyPartId = weeklyPartId,
            personId = personId,
            slot = slot,
        )
        val updated = aggregate.addAssignment(assignment, persona.sospeso).fold(
            ifLeft = { raise(it) },
            ifRight = { it },
        )
        weekPlanStore.saveAggregate(updated)
    }
}

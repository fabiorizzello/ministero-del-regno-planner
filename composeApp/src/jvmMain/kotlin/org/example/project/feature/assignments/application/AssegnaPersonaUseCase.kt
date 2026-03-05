package org.example.project.feature.assignments.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionRunner
import org.example.project.feature.assignments.domain.Assignment
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.people.application.ProclamatoriAggregateStore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.weeklyparts.application.WeekPlanQueries
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.LocalDate
import java.util.UUID

class AssegnaPersonaUseCase(
    private val weekPlanStore: WeekPlanQueries,
    private val assignmentStore: AssignmentRepository,
    private val transactionRunner: TransactionRunner,
    private val personStore: ProclamatoriAggregateStore,
) {
    suspend operator fun invoke(
        weekStartDate: LocalDate,
        weeklyPartId: WeeklyPartId,
        personId: ProclamatoreId,
        slot: Int,
    ): Either<DomainError, Unit> = either {
        val plan = weekPlanStore.findByDate(weekStartDate)
            ?: raise(DomainError.Validation("Settimana non trovata"))

        val part = plan.parts.find { it.id == weeklyPartId }
            ?: raise(DomainError.Validation("Parte non trovata"))

        if (slot < 1 || slot > part.partType.peopleCount) {
            raise(DomainError.Validation("Slot non valido"))
        }

        val persona = personStore.load(personId)
            ?: raise(DomainError.Validation("Proclamatore non trovato"))
        if (persona.sospeso) raise(DomainError.Validation("Proclamatore sospeso"))

        if (assignmentStore.isPersonAssignedInWeek(plan.id, personId)) {
            raise(DomainError.Validation("Proclamatore gia' assegnato in questa settimana"))
        }

        try {
            transactionRunner.runInTransaction {
                assignmentStore.save(
                    Assignment(
                        id = AssignmentId(UUID.randomUUID().toString()),
                        weeklyPartId = weeklyPartId,
                        personId = personId,
                        slot = slot,
                    )
                )
            }
        } catch (e: Exception) {
            raise(DomainError.Validation("Errore nel salvataggio: ${e.message}"))
        }
    }
}

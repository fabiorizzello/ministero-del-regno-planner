package org.example.project.feature.assignments.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionRunner
import org.example.project.feature.assignments.domain.Assignment
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.weeklyparts.application.WeekPlanStore
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.LocalDate
import java.util.UUID

class AssegnaPersonaUseCase(
    private val weekPlanStore: WeekPlanStore,
    private val assignmentStore: AssignmentStore,
    private val transactionRunner: TransactionRunner,
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

        try {
            transactionRunner.runInTransaction {
                if (assignmentStore.isPersonAssignedInWeek(plan.id, personId)) {
                    throw IllegalStateException("Proclamatore gia' assegnato in questa settimana")
                }

                assignmentStore.save(
                    Assignment(
                        id = AssignmentId(UUID.randomUUID().toString()),
                        weeklyPartId = weeklyPartId,
                        personId = personId,
                        slot = slot,
                    )
                )
            }
        } catch (e: IllegalStateException) {
            raise(DomainError.Validation(e.message ?: "Errore nell'assegnazione"))
        } catch (e: Exception) {
            raise(DomainError.Validation("Errore nel salvataggio: ${e.message}"))
        }
    }
}

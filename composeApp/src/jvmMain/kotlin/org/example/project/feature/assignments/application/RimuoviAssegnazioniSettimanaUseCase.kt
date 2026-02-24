package org.example.project.feature.assignments.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.feature.weeklyparts.application.WeekPlanStore
import java.time.LocalDate

class RimuoviAssegnazioniSettimanaUseCase(
    private val weekPlanStore: WeekPlanStore,
    private val assignmentStore: AssignmentRepository,
    private val rimuoviAssegnazioneUseCase: RimuoviAssegnazioneUseCase,
) {
    suspend fun count(weekStartDate: LocalDate): Int {
        val plan = weekPlanStore.findByDate(weekStartDate) ?: return 0
        return assignmentStore.countAssignmentsForWeek(plan.id)
    }

    suspend operator fun invoke(weekStartDate: LocalDate): Either<DomainError, Unit> = either {
        try {
            execute(weekStartDate).bind()
        } catch (e: Exception) {
            raise(DomainError.Validation("Errore nella rimozione delle assegnazioni: ${e.message}"))
        }
    }

    private suspend fun execute(weekStartDate: LocalDate): Either<DomainError, Unit> = either {
        val plan = weekPlanStore.findByDate(weekStartDate)
            ?: raise(DomainError.Validation("Piano settimanale non trovato per la data $weekStartDate"))

        val assignments = assignmentStore.listByWeek(plan.id)

        assignments.forEach { assignment ->
            rimuoviAssegnazioneUseCase(assignment.id).bind()
        }
    }
}

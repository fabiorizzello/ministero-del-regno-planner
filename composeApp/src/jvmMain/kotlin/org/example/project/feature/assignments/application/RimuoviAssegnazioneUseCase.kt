package org.example.project.feature.assignments.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.feature.assignments.domain.AssignmentId

class RimuoviAssegnazioneUseCase(
    private val assignmentStore: AssignmentRepository,
) {
    suspend operator fun invoke(assignmentId: AssignmentId): Either<DomainError, Unit> = either {
        try {
            assignmentStore.remove(assignmentId)
        } catch (e: Exception) {
            raise(DomainError.Validation("Errore nella rimozione: ${e.message}"))
        }
    }
}

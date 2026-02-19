package org.example.project.feature.assignments.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError

class RimuoviAssegnazioneUseCase(
    private val assignmentStore: AssignmentStore,
) {
    suspend operator fun invoke(assignmentId: String): Either<DomainError, Unit> = either {
        try {
            assignmentStore.remove(assignmentId)
        } catch (e: Exception) {
            raise(DomainError.Validation("Errore nella rimozione: ${e.message}"))
        }
    }
}

package org.example.project.feature.people.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.feature.people.domain.ProclamatoreId

class ImpostaIdoneitaAssistenzaUseCase(
    private val eligibilityStore: EligibilityStore,
) {
    suspend operator fun invoke(
        personId: ProclamatoreId,
        canAssist: Boolean,
    ): Either<DomainError, Unit> = either {
        eligibilityStore.setCanAssist(personId, canAssist)
    }
}

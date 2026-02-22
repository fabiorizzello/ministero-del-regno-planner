package org.example.project.feature.people.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.weeklyparts.domain.PartTypeId

class ImpostaIdoneitaConduzioneUseCase(
    private val eligibilityStore: EligibilityStore,
) {
    suspend operator fun invoke(
        personId: ProclamatoreId,
        partTypeId: PartTypeId,
        canLead: Boolean,
    ): Either<DomainError, Unit> = either {
        eligibilityStore.setCanLead(personId, partTypeId, canLead)
    }
}

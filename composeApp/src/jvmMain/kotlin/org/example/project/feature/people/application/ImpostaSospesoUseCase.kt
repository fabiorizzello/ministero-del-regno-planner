package org.example.project.feature.people.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.feature.people.domain.ProclamatoreId
import java.time.LocalDate

data class SospensioneOutcome(
    val futureWeeksWhereAssigned: List<LocalDate>,
)

class ImpostaSospesoUseCase(
    private val eligibilityStore: EligibilityStore,
) {
    suspend operator fun invoke(
        personId: ProclamatoreId,
        suspended: Boolean,
        referenceDate: LocalDate,
    ): Either<DomainError, SospensioneOutcome> = either {
        eligibilityStore.setSuspended(personId, suspended)
        val futureWeeks = if (suspended) {
            eligibilityStore.listFutureAssignmentWeeks(personId, referenceDate)
        } else {
            emptyList()
        }
        SospensioneOutcome(futureWeeks)
    }
}

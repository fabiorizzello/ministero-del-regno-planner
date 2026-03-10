package org.example.project.feature.assignments.domain

import arrow.core.Either
import arrow.core.raise.either
import io.konform.validation.Validation
import io.konform.validation.constraints.minimum
import org.example.project.core.domain.DomainError
import org.example.project.core.domain.validate
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.weeklyparts.domain.WeeklyPartId

@JvmInline
value class AssignmentId(val value: String)

private data class AssignmentValidationInput(
    val slot: Int,
)

private val assignmentValidator = Validation<AssignmentValidationInput> {
    AssignmentValidationInput::slot {
        minimum(1)
    }
}

data class Assignment internal constructor(
    val id: AssignmentId,
    val weeklyPartId: WeeklyPartId,
    val personId: ProclamatoreId,
    val slot: Int,
) {
    companion object {
        fun of(
            id: AssignmentId,
            weeklyPartId: WeeklyPartId,
            personId: ProclamatoreId,
            slot: Int,
        ): Either<DomainError.Validation, Assignment> = either {
            assignmentValidator.validate(
                value = AssignmentValidationInput(slot = slot),
                context = "Assignment non valido",
            ).bind()
            Assignment(
                id = id,
                weeklyPartId = weeklyPartId,
                personId = personId,
                slot = slot,
            )
        }
    }

    val roleLabel: String get() = if (slot == 1) "Studente" else "Assistente"
}

package org.example.project.feature.assignments.domain

import io.konform.validation.Validation
import io.konform.validation.constraints.minimum
import org.example.project.core.domain.requireValid
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

data class Assignment(
    val id: AssignmentId,
    val weeklyPartId: WeeklyPartId,
    val personId: ProclamatoreId,
    val slot: Int,
) {
    init {
        assignmentValidator.requireValid(
            value = AssignmentValidationInput(slot = slot),
            context = "Assignment non valido",
        )
    }

    val roleLabel: String get() = if (slot == 1) "Studente" else "Assistente"
}

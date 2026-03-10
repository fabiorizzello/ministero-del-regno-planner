package org.example.project.feature.assignments.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.example.project.core.domain.DomainError
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.weeklyparts.domain.WeeklyPartId

data class AssignmentWithPerson internal constructor(
    val id: AssignmentId,
    val weeklyPartId: WeeklyPartId,
    val personId: ProclamatoreId,
    val slot: Int,
    val proclamatore: Proclamatore,
) {
    companion object {
        fun of(
            id: AssignmentId,
            weeklyPartId: WeeklyPartId,
            personId: ProclamatoreId,
            slot: Int,
            proclamatore: Proclamatore,
        ): Either<DomainError.Validation, AssignmentWithPerson> =
            if (slot >= 1) {
                AssignmentWithPerson(
                    id = id,
                    weeklyPartId = weeklyPartId,
                    personId = personId,
                    slot = slot,
                    proclamatore = proclamatore,
                ).right()
            } else {
                DomainError.Validation("slot deve essere >= 1, ricevuto: $slot").left()
            }
    }

    val fullName: String get() = proclamatore.fullName
    val roleLabel: String get() = if (slot == 1) "Studente" else "Assistente"
    val sex get() = proclamatore.sesso
}

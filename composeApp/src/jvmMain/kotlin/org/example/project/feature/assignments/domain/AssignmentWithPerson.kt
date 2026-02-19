package org.example.project.feature.assignments.domain

import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.domain.Sesso
import org.example.project.feature.weeklyparts.domain.WeeklyPartId

data class AssignmentWithPerson(
    val id: AssignmentId,
    val weeklyPartId: WeeklyPartId,
    val personId: ProclamatoreId,
    val slot: Int,
    val firstName: String,
    val lastName: String,
    val sex: Sesso,
    val active: Boolean,
) {
    init {
        require(slot >= 1) { "slot deve essere >= 1, ricevuto: $slot" }
    }

    val fullName: String get() = "${firstName.trim()} ${lastName.trim()}"
}

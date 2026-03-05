package org.example.project.feature.assignments.domain

import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.weeklyparts.domain.WeeklyPartId

data class AssignmentWithPerson(
    val id: AssignmentId,
    val weeklyPartId: WeeklyPartId,
    val personId: ProclamatoreId,
    val slot: Int,
    val proclamatore: Proclamatore,
) {
    init {
        require(slot >= 1) { "slot deve essere >= 1, ricevuto: $slot" }
    }

    val fullName: String get() = "${proclamatore.nome.trim()} ${proclamatore.cognome.trim()}"
    val sex get() = proclamatore.sesso
}

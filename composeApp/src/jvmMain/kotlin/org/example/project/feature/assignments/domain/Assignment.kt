package org.example.project.feature.assignments.domain

import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.weeklyparts.domain.WeeklyPartId

@JvmInline
value class AssignmentId(val value: String)

data class Assignment(
    val id: AssignmentId,
    val weeklyPartId: WeeklyPartId,
    val personId: ProclamatoreId,
    val slot: Int,
) {
    init {
        require(slot >= 1) { "slot deve essere >= 1, ricevuto: $slot" }
    }
}

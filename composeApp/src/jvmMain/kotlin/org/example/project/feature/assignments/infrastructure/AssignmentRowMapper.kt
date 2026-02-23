package org.example.project.feature.assignments.infrastructure

import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.assignments.domain.AssignmentWithPerson
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.core.util.enumByName
import org.example.project.feature.people.domain.Sesso
import org.example.project.feature.weeklyparts.domain.WeeklyPartId

internal fun mapAssignmentWithPersonRow(
    id: String,
    weekly_part_id: String,
    person_id: String,
    slot: Long,
    first_name: String,
    last_name: String,
    sex: String,
    active: Long,
): AssignmentWithPerson {
    return AssignmentWithPerson(
        id = AssignmentId(id),
        weeklyPartId = WeeklyPartId(weekly_part_id),
        personId = ProclamatoreId(person_id),
        slot = slot.toInt(),
        firstName = first_name,
        lastName = last_name,
        sex = enumByName(sex, Sesso.M),
        active = active == 1L,
    )
}

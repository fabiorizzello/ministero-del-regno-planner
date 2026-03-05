package org.example.project.feature.assignments.infrastructure

import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.assignments.domain.AssignmentWithPerson
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId
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
): AssignmentWithPerson {
    return AssignmentWithPerson(
        id = AssignmentId(id),
        weeklyPartId = WeeklyPartId(weekly_part_id),
        personId = ProclamatoreId(person_id),
        slot = slot.toInt(),
        proclamatore = Proclamatore(
            id = ProclamatoreId(person_id),
            nome = first_name,
            cognome = last_name,
            sesso = runCatching { Sesso.valueOf(sex) }.getOrDefault(Sesso.M),
        ),
    )
}

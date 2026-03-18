package org.example.project.feature.assignments.infrastructure

import arrow.core.getOrElse
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.assignments.domain.AssignmentWithPerson
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.infrastructure.parseSessoOrDefault
import org.example.project.feature.weeklyparts.domain.WeeklyPartId

/**
 * Maps a raw DB row directly to [AssignmentWithPerson].
 *
 * All columns come from controlled writes and schema CHECK constraints
 * (e.g. `CHECK(slot >= 1)`, `CHECK(sex IN ('M','F'))`), so invalid data
 * is an impossible state — we crash with [error] rather than silently
 * skipping rows.
 */
internal fun mapAssignmentWithPersonRow(
    id: String,
    weekly_part_id: String,
    person_id: String,
    slot: Long,
    first_name: String,
    last_name: String,
    sex: String,
): AssignmentWithPerson {
    return AssignmentWithPerson.of(
        id = AssignmentId(id),
        weeklyPartId = WeeklyPartId(weekly_part_id),
        personId = ProclamatoreId(person_id),
        slot = slot.toInt(),
        proclamatore = Proclamatore(
            id = ProclamatoreId(person_id),
            nome = first_name,
            cognome = last_name,
            sesso = parseSessoOrDefault(sex),
        ),
    ).getOrElse { error("Invalid AssignmentWithPerson from DB: $it") }
}

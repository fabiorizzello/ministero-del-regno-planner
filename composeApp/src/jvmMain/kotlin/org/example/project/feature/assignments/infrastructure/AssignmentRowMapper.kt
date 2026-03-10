package org.example.project.feature.assignments.infrastructure

import arrow.core.getOrElse
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.assignments.domain.AssignmentWithPerson
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.domain.Sesso
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("AssignmentRowMapper")

private fun parseSessoOrDefault(sex: String): Sesso =
    Sesso.entries.find { it.name == sex }
        ?: run {
            logger.warn("Sesso sconosciuto '{}' -> fallback a M", sex)
            Sesso.M
        }

private fun mapAssignmentWithPersonRow(
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

/**
 * Intermediate raw row before domain construction. Used to validate slot before
 * constructing [AssignmentWithPerson], preventing crashes on corrupt DB data.
 */
internal data class AssignmentRawRow(
    val id: String,
    val weeklyPartId: String,
    val personId: String,
    val slot: Long,
    val firstName: String,
    val lastName: String,
    val sex: String,
)

internal fun mapAssignmentRawRow(
    id: String,
    weekly_part_id: String,
    person_id: String,
    slot: Long,
    first_name: String,
    last_name: String,
    sex: String,
): AssignmentRawRow = AssignmentRawRow(
    id = id,
    weeklyPartId = weekly_part_id,
    personId = person_id,
    slot = slot,
    firstName = first_name,
    lastName = last_name,
    sex = sex,
)

/**
 * Converts a raw row to [AssignmentWithPerson], returning null and logging a warning
 * if the slot is invalid (< 1). This guards against corrupt DB data.
 */
internal fun AssignmentRawRow.toAssignmentWithPersonOrNull(): AssignmentWithPerson? {
    if (slot < 1) {
        logger.warn("Riga DB con slot non valido: id={}, slot={} — riga ignorata", id, slot)
        return null
    }
    return mapAssignmentWithPersonRow(id, weeklyPartId, personId, slot, firstName, lastName, sex)
}

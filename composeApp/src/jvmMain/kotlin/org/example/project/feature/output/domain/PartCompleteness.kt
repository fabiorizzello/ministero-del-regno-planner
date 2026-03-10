package org.example.project.feature.output.domain

import org.example.project.feature.assignments.domain.AssignmentWithPerson
import org.example.project.feature.weeklyparts.domain.WeeklyPart
import org.example.project.feature.weeklyparts.domain.WeeklyPartId

/**
 * Returns the IDs of parts that have all assignment slots filled.
 * A part is considered complete when the number of assignments
 * is at least equal to the part type's [peopleCount].
 */
fun completePartIds(
    parts: List<WeeklyPart>,
    assignments: List<AssignmentWithPerson>,
): Set<WeeklyPartId> {
    val assignedCountByPart = assignments.groupBy { it.weeklyPartId }.mapValues { it.value.size }
    return parts
        .filter { part -> (assignedCountByPart[part.id] ?: 0) >= part.partType.peopleCount }
        .mapTo(mutableSetOf()) { it.id }
}

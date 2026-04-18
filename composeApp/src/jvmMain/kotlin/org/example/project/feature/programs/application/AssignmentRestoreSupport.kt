package org.example.project.feature.programs.application

import arrow.core.raise.Raise
import org.example.project.core.domain.DomainError
import org.example.project.feature.assignments.domain.Assignment
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.WeekPlanAggregate
import org.example.project.feature.weeklyparts.domain.WeeklyPart
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.util.UUID

data class WeekPartContinuityKey(
    val partTypeId: PartTypeId,
    val occurrenceIndex: Int,
    val slot: Int,
)

data class WeekPartLogicalKey(
    val partTypeId: PartTypeId,
    val occurrenceIndex: Int,
)

internal data class WeekPartOccurrence(
    val part: WeeklyPart,
    val occurrenceIndex: Int,
)

internal fun snapshotAssignmentsByContinuityKey(
    aggregate: WeekPlanAggregate,
): Map<WeekPartContinuityKey, Assignment> {
    val occurrencesByPartId = aggregate.weekPlan.parts
        .orderedOccurrences()
        .associateBy { it.part.id }

    return aggregate.assignments.mapNotNull { assignment ->
        val occurrence = occurrencesByPartId[assignment.weeklyPartId] ?: return@mapNotNull null
        WeekPartContinuityKey(
            partTypeId = occurrence.part.partType.id,
            occurrenceIndex = occurrence.occurrenceIndex,
            slot = assignment.slot,
        ) to assignment
    }.toMap()
}

internal fun calculateAssignmentDelta(
    assignmentSnapshot: Map<WeekPartContinuityKey, Assignment>,
    orderedPartTypes: List<Pair<PartType, String?>>,
): Pair<Int, Int> {
    val preserved = buildContinuityKeys(orderedPartTypes).count { it in assignmentSnapshot }
    return preserved to (assignmentSnapshot.size - preserved)
}

internal fun buildContinuityKeys(
    orderedPartTypes: List<Pair<PartType, String?>>,
): Set<WeekPartContinuityKey> {
    val occurrencesByPartType = mutableMapOf<PartTypeId, Int>()
    return buildSet {
        orderedPartTypes.forEach { (partType, _) ->
            val occurrenceIndex = occurrencesByPartType.getOrDefault(partType.id, 0)
            occurrencesByPartType[partType.id] = occurrenceIndex + 1
            repeat(partType.peopleCount) { slotIndex ->
                add(
                    WeekPartContinuityKey(
                        partTypeId = partType.id,
                        occurrenceIndex = occurrenceIndex,
                        slot = slotIndex + 1,
                    ),
                )
            }
        }
    }
}

internal fun List<Pair<PartType, String?>>.withLogicalKeys(): List<Pair<WeekPartLogicalKey, Pair<PartType, String?>>> {
    val occurrencesByPartType = mutableMapOf<PartTypeId, Int>()
    return map { definition ->
        val partType = definition.first
        val occurrenceIndex = occurrencesByPartType.getOrDefault(partType.id, 0)
        occurrencesByPartType[partType.id] = occurrenceIndex + 1
        WeekPartLogicalKey(
            partTypeId = partType.id,
            occurrenceIndex = occurrenceIndex,
        ) to definition
    }
}

internal fun restoreAssignmentsByContinuityKey(
    snapshot: Map<WeekPartContinuityKey, Assignment>,
    rebuiltParts: List<WeeklyPart>,
): List<Assignment> {
    val occurrences = rebuiltParts.orderedOccurrences()
    return buildList {
        occurrences.forEach { occurrence ->
            repeat(occurrence.part.partType.peopleCount) { slotIndex ->
                val key = WeekPartContinuityKey(
                    partTypeId = occurrence.part.partType.id,
                    occurrenceIndex = occurrence.occurrenceIndex,
                    slot = slotIndex + 1,
                )
                val assignment = snapshot[key] ?: return@repeat
                add(
                    Assignment(
                        id = AssignmentId(UUID.randomUUID().toString()),
                        weeklyPartId = occurrence.part.id,
                        personId = assignment.personId,
                        slot = assignment.slot,
                    ),
                )
            }
        }
    }
}

internal fun List<WeeklyPart>.orderedOccurrences(): List<WeekPartOccurrence> {
    val occurrencesByPartType = mutableMapOf<PartTypeId, Int>()
    return sortedBy { it.sortOrder }.map { part ->
        val occurrenceIndex = occurrencesByPartType.getOrDefault(part.partType.id, 0)
        occurrencesByPartType[part.partType.id] = occurrenceIndex + 1
        WeekPartOccurrence(part = part, occurrenceIndex = occurrenceIndex)
    }
}

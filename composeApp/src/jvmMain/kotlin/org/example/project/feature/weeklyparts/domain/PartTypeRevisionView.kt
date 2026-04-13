package org.example.project.feature.weeklyparts.domain

import java.time.LocalDateTime

data class PartTypeRevisionView(
    val revisionNumber: Int,
    val createdAt: LocalDateTime,
    val isCurrent: Boolean,
    val snapshot: PartTypeSnapshot,
    val deltaFromPrevious: List<PartTypeFieldDelta>,
) {
    val isNoOp: Boolean get() = revisionNumber > 1 && deltaFromPrevious.isEmpty()
}

sealed interface PartTypeFieldDelta {
    data class Label(val from: String, val to: String) : PartTypeFieldDelta
    data class PeopleCount(val from: Int, val to: Int) : PartTypeFieldDelta
    data class Sex(val from: SexRule, val to: SexRule) : PartTypeFieldDelta
    data class Fixed(val from: Boolean, val to: Boolean) : PartTypeFieldDelta
}

fun computePartTypeDelta(
    previous: PartTypeSnapshot?,
    current: PartTypeSnapshot,
): List<PartTypeFieldDelta> {
    if (previous == null) return emptyList()
    return buildList {
        if (previous.label != current.label) {
            add(PartTypeFieldDelta.Label(previous.label, current.label))
        }
        if (previous.peopleCount != current.peopleCount) {
            add(PartTypeFieldDelta.PeopleCount(previous.peopleCount, current.peopleCount))
        }
        if (previous.sexRule != current.sexRule) {
            add(PartTypeFieldDelta.Sex(previous.sexRule, current.sexRule))
        }
        if (previous.fixed != current.fixed) {
            add(PartTypeFieldDelta.Fixed(previous.fixed, current.fixed))
        }
    }
}

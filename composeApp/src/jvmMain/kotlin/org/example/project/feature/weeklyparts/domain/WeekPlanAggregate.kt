package org.example.project.feature.weeklyparts.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.example.project.core.domain.DomainError
import org.example.project.feature.assignments.domain.Assignment
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.programs.domain.ProgramMonthId
import java.time.LocalDate

data class WeekPlanAggregate(
    val weekPlan: WeekPlan,
    val assignments: List<Assignment>,
) {
    fun addPart(
        partType: PartType,
        partId: WeeklyPartId,
        partTypeRevisionId: String? = null,
    ): Either<DomainError, WeekPlanAggregate> {
        if (!weekPlan.canBeEditedManually()) return DomainError.SettimanaImmutabile.left()
        val newPart = WeeklyPart(
            id = partId,
            partType = partType,
            partTypeRevisionId = partTypeRevisionId,
            sortOrder = weekPlan.nextSortOrder(),
        )
        return copy(weekPlan = weekPlan.copy(parts = weekPlan.parts + newPart)).right()
    }

    fun removePart(weeklyPartId: WeeklyPartId): Either<DomainError, WeekPlanAggregate> {
        if (!weekPlan.canBeEditedManually()) return DomainError.SettimanaImmutabile.left()
        val part = weekPlan.findPart(weeklyPartId)
            ?: return DomainError.NotFound("Parte").left()
        if (part.partType.fixed) {
            return DomainError.ParteFissa(part.partType.label).left()
        }

        val remaining = weekPlan.parts.filterNot { it.id == weeklyPartId }
        val recompacted = remaining.mapIndexed { index, existing ->
            existing.copy(sortOrder = index)
        }
        val remainingAssignments = assignments.filterNot { it.weeklyPartId == weeklyPartId }
        return copy(
            weekPlan = weekPlan.copy(parts = recompacted),
            assignments = remainingAssignments,
        ).right()
    }

    fun reorderParts(orderedPartIds: List<WeeklyPartId>): Either<DomainError, WeekPlanAggregate> {
        if (!weekPlan.canBeEditedManually()) return DomainError.SettimanaImmutabile.left()
        val existingIds = weekPlan.parts.map { part -> part.id }
        if (orderedPartIds.size != existingIds.size || orderedPartIds.toSet() != existingIds.toSet()) {
            return DomainError.OrdinePartiNonValido.left()
        }

        val partsById = weekPlan.parts.associateBy { part -> part.id }
        val reordered = orderedPartIds.mapIndexed { index, partId ->
            partsById.getValue(partId).copy(sortOrder = index)
        }
        return copy(weekPlan = weekPlan.copy(parts = reordered)).right()
    }

    fun validateAssignment(
        weeklyPartId: WeeklyPartId,
        personId: ProclamatoreId,
        personSuspended: Boolean,
        slot: Int,
    ): DomainError? {
        val part = weekPlan.findPart(weeklyPartId) ?: return DomainError.NotFound("Parte")
        if (!part.partType.isValidSlot(slot)) {
            return DomainError.SlotNonValido(slot = slot, max = part.partType.peopleCount)
        }
        if (personSuspended) {
            return DomainError.PersonaSospesa
        }
        if (assignments.any { it.personId == personId }) {
            return DomainError.PersonaGiaAssegnata
        }
        return null
    }

    fun replaceParts(
        orderedPartTypes: List<Pair<PartType, String?>>,
        partIdFactory: () -> WeeklyPartId,
    ): Either<DomainError, WeekPlanAggregate> {
        if (!weekPlan.canBeEditedManually()) return DomainError.SettimanaImmutabile.left()
        if (orderedPartTypes.isEmpty()) {
            return DomainError.OrdinePartiNonValido.left()
        }
        val rebuilt = orderedPartTypes.mapIndexed { index, (partType, revisionId) ->
            WeeklyPart(
                id = partIdFactory(),
                partType = partType,
                partTypeRevisionId = revisionId,
                sortOrder = index,
            )
        }
        return copy(
            weekPlan = weekPlan.copy(parts = rebuilt),
            assignments = emptyList(),
        ).right()
    }

    fun addAssignment(
        assignment: Assignment,
        personSuspended: Boolean,
    ): Either<DomainError, WeekPlanAggregate> {
        if (!weekPlan.canBeEditedManually()) return DomainError.SettimanaImmutabile.left()
        validateAssignment(
            weeklyPartId = assignment.weeklyPartId,
            personId = assignment.personId,
            personSuspended = personSuspended,
            slot = assignment.slot,
        )?.let { return it.left() }
        return copy(assignments = assignments + assignment).right()
    }

    fun removeAssignment(assignmentId: AssignmentId): Either<DomainError, WeekPlanAggregate> {
        if (!weekPlan.canBeEditedManually()) return DomainError.SettimanaImmutabile.left()
        val target = assignments.find { it.id == assignmentId }
            ?: return DomainError.NotFound("Assegnazione").left()
        return copy(assignments = assignments - target).right()
    }

    fun clearAssignments(): WeekPlanAggregate = copy(assignments = emptyList())

    fun setStatus(status: WeekPlanStatus): WeekPlanAggregate =
        if (weekPlan.status == status) this else copy(weekPlan = weekPlan.copy(status = status))

    companion object {
        fun createWeekWithFixedPart(
            weekPlanId: WeekPlanId,
            weekStartDate: LocalDate,
            fixedPartType: PartType,
            fixedPartId: WeeklyPartId,
            fixedPartRevisionId: String? = null,
            programId: ProgramMonthId? = null,
            status: WeekPlanStatus = WeekPlanStatus.ACTIVE,
        ): Either<DomainError, WeekPlanAggregate> =
            WeekPlan.of(
                id = weekPlanId,
                weekStartDate = weekStartDate,
                parts = listOf(
                    WeeklyPart(
                        id = fixedPartId,
                        partType = fixedPartType,
                        partTypeRevisionId = fixedPartRevisionId,
                        sortOrder = 0,
                    ),
                ),
                programId = programId,
                status = status,
            ).map { week ->
                WeekPlanAggregate(
                    weekPlan = week,
                    assignments = emptyList(),
                )
            }
    }
}

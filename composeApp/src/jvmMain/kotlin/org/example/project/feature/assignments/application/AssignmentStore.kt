package org.example.project.feature.assignments.application

import org.example.project.feature.assignments.domain.Assignment
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.assignments.domain.AssignmentWithPerson
import org.example.project.feature.assignments.domain.SuggestedProclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import java.time.LocalDate

interface AssignmentRepository {
    suspend fun listByWeek(weekPlanId: WeekPlanId): List<AssignmentWithPerson>
    suspend fun save(assignment: Assignment)
    suspend fun remove(assignmentId: AssignmentId)
    suspend fun isPersonAssignedInWeek(weekPlanId: WeekPlanId, personId: ProclamatoreId): Boolean
    suspend fun countAssignmentsForWeek(weekPlanId: WeekPlanId): Int
}

interface AssignmentRanking {
    suspend fun suggestedProclamatori(
        partTypeId: PartTypeId,
        slot: Int,
        referenceDate: LocalDate,
    ): List<SuggestedProclamatore>
}

interface PersonAssignmentLifecycle {
    suspend fun countAssignmentsForPerson(personId: ProclamatoreId): Int
    suspend fun removeAllForPerson(personId: ProclamatoreId)
}

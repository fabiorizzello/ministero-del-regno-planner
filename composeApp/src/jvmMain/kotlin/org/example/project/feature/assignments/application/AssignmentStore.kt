package org.example.project.feature.assignments.application

import org.example.project.feature.assignments.domain.Assignment
import org.example.project.feature.assignments.domain.AssignmentWithPerson
import org.example.project.feature.assignments.domain.SuggestedProclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.LocalDate

interface AssignmentStore {
    suspend fun listByWeek(weekPlanId: WeekPlanId): List<AssignmentWithPerson>
    suspend fun save(assignment: Assignment)
    suspend fun remove(assignmentId: String)
    suspend fun isPersonAssignedToPart(weeklyPartId: WeeklyPartId, personId: ProclamatoreId): Boolean
    suspend fun suggestedProclamatori(
        partTypeId: PartTypeId,
        slot: Int,
        referenceDate: LocalDate,
    ): List<SuggestedProclamatore>
}

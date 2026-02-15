package org.example.project.feature.assignments.application

import org.example.project.feature.assignments.domain.Assignment

interface AssignmentsRepository {
    suspend fun listByWeek(weekStartDate: String): List<Assignment>
    suspend fun save(assignment: Assignment)
    suspend fun remove(assignmentId: String)
}

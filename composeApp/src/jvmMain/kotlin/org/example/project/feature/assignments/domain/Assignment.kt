package org.example.project.feature.assignments.domain

@JvmInline
value class AssignmentId(val value: String)

data class Assignment(
    val id: AssignmentId,
    val weeklyPartId: String,
    val personId: String,
    val slot: Int,
)

package org.example.project.feature.assignments.domain

import java.time.LocalDate

/**
 * Represents a single assignment in a person's history.
 */
data class AssignmentHistoryEntry(
    val id: AssignmentId,
    val partTypeLabel: String,
    val weekStartDate: LocalDate,
    val slot: Int,
) {
    init {
        require(slot >= 1) { "slot deve essere >= 1, ricevuto: $slot" }
        require(partTypeLabel.isNotBlank()) { "partTypeLabel non può essere vuoto" }
    }

    /**
     * Returns the role for this assignment: "Conduttore" for slot 1, "Assistente" for slot >= 2.
     */
    val role: String get() = if (slot == 1) "Conduttore" else "Assistente"
}

/**
 * Represents the assignment history for a person.
 */
data class PersonAssignmentHistory(
    val entries: List<AssignmentHistoryEntry>,
) {
    /**
     * Returns a map of part type labels to their assignment counts.
     */
    val summaryByPartType: Map<String, Int> get() = entries
        .groupingBy { it.partTypeLabel }
        .eachCount()

    /**
     * Returns the total number of assignments.
     */
    val totalAssignments: Int get() = entries.size

    /**
     * Returns true if there are no assignments in the history.
     */
    val isEmpty: Boolean get() = entries.isEmpty()
}

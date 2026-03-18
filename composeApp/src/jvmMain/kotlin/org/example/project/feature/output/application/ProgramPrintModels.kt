package org.example.project.feature.output.application

import java.time.LocalDate

data class ProgramWeekPrintSlot(
    val roleLabel: String?,
    val assignedTo: String,
    val isAssigned: Boolean,
)

enum class ProgramWeekPrintCardStatus {
    EMPTY,
    PARTIAL,
    ASSIGNED,
}

data class ProgramWeekPrintCard(
    val displayNumber: Int,
    val partLabel: String,
    val status: ProgramWeekPrintCardStatus,
    val statusLabel: String,
    val slots: List<ProgramWeekPrintSlot>,
)

data class ProgramWeekPrintSection(
    val weekStartDate: LocalDate,
    val weekEndDate: LocalDate,
    val statusLabel: String,
    val cards: List<ProgramWeekPrintCard>,
    val emptyStateLabel: String? = null,
)

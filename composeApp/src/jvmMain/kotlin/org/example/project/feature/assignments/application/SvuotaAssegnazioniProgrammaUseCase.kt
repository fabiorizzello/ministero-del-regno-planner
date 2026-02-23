package org.example.project.feature.assignments.application

import java.time.LocalDate

class SvuotaAssegnazioniProgrammaUseCase(
    private val assignmentRepository: AssignmentRepository,
) {
    suspend fun count(programId: String, fromDate: LocalDate): Int =
        assignmentRepository.countByProgramFromDate(programId, fromDate)

    suspend fun execute(programId: String, fromDate: LocalDate): Int =
        assignmentRepository.deleteByProgramFromDate(programId, fromDate)
}

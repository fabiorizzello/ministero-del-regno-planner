package org.example.project.feature.assignments.application

import org.example.project.feature.programs.domain.ProgramMonthId
import java.time.LocalDate

class SvuotaAssegnazioniProgrammaUseCase(
    private val assignmentRepository: AssignmentRepository,
) {
    suspend fun count(programId: ProgramMonthId, fromDate: LocalDate): Int =
        assignmentRepository.countByProgramFromDate(programId, fromDate)

    suspend fun execute(programId: ProgramMonthId, fromDate: LocalDate): Int =
        assignmentRepository.deleteByProgramFromDate(programId, fromDate)
}

package org.example.project.feature.programs

import org.example.project.feature.programs.application.ProgramStore
import org.example.project.feature.programs.domain.ProgramMonth
import org.example.project.feature.programs.domain.ProgramMonthId
import java.time.LocalDate
import java.time.LocalDateTime

internal class InMemoryProgramStore(
    val programs: MutableList<ProgramMonth> = mutableListOf(),
) : ProgramStore {

    override suspend fun listCurrentAndFuture(referenceDate: LocalDate): List<ProgramMonth> =
        programs.sortedBy { it.yearMonth }

    override suspend fun findById(id: ProgramMonthId): ProgramMonth? =
        programs.firstOrNull { it.id == id }

    override suspend fun save(program: ProgramMonth) {
        programs.add(program)
    }

    override suspend fun delete(id: ProgramMonthId) {
        programs.removeIf { it.id == id }
    }

    override suspend fun updateTemplateAppliedAt(id: ProgramMonthId, templateAppliedAt: LocalDateTime) {
        // no-op for tests
    }
}

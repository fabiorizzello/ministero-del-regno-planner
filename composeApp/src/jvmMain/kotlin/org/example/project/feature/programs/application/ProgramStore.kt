package org.example.project.feature.programs.application

import org.example.project.feature.programs.domain.ProgramMonth
import org.example.project.feature.programs.domain.ProgramMonthId
import java.time.LocalDate

interface ProgramStore {
    suspend fun listCurrentAndFuture(referenceDate: LocalDate): List<ProgramMonth>
    suspend fun findByYearMonth(year: Int, month: Int): ProgramMonth?
    suspend fun findById(id: ProgramMonthId): ProgramMonth?
    suspend fun save(program: ProgramMonth)
    suspend fun delete(id: ProgramMonthId)
}

package org.example.project.feature.programs.infrastructure

import org.example.project.db.MinisteroDatabase
import org.example.project.feature.programs.application.ProgramStore
import org.example.project.feature.programs.domain.ProgramMonth
import org.example.project.feature.programs.domain.ProgramMonthId
import java.time.LocalDate
import java.time.LocalDateTime

class SqlDelightProgramStore(
    private val database: MinisteroDatabase,
) : ProgramStore {

    override suspend fun listCurrentAndFuture(referenceDate: LocalDate): List<ProgramMonth> {
        return database.ministeroDatabaseQueries
            .listProgramsCurrentAndFuture(referenceDate.toString(), ::mapProgramRow)
            .executeAsList()
    }

    override suspend fun findByYearMonth(year: Int, month: Int): ProgramMonth? {
        return database.ministeroDatabaseQueries
            .findProgramByYearMonth(year.toLong(), month.toLong(), ::mapProgramRow)
            .executeAsOneOrNull()
    }

    override suspend fun findById(id: ProgramMonthId): ProgramMonth? {
        return database.ministeroDatabaseQueries
            .findProgramById(id.value, ::mapProgramRow)
            .executeAsOneOrNull()
    }

    override suspend fun save(program: ProgramMonth) {
        database.ministeroDatabaseQueries.insertProgramMonthly(
            id = program.id.value,
            year = program.year.toLong(),
            month = program.month.toLong(),
            start_date = program.startDate.toString(),
            end_date = program.endDate.toString(),
            template_applied_at = program.templateAppliedAt?.toString(),
            created_at = program.createdAt.toString(),
        )
    }

    override suspend fun updateTemplateAppliedAt(id: ProgramMonthId, templateAppliedAt: LocalDateTime) {
        database.ministeroDatabaseQueries.updateProgramTemplateAppliedAt(
            template_applied_at = templateAppliedAt.toString(),
            id = id.value,
        )
    }

    override suspend fun delete(id: ProgramMonthId) {
        database.ministeroDatabaseQueries.deleteProgramMonthly(id.value)
    }
}

private fun mapProgramRow(
    id: String,
    year: Long,
    month: Long,
    start_date: String,
    end_date: String,
    template_applied_at: String?,
    created_at: String,
): ProgramMonth {
    return ProgramMonth(
        id = ProgramMonthId(id),
        year = year.toInt(),
        month = month.toInt(),
        startDate = LocalDate.parse(start_date),
        endDate = LocalDate.parse(end_date),
        templateAppliedAt = template_applied_at?.let(LocalDateTime::parse),
        createdAt = LocalDateTime.parse(created_at),
    )
}

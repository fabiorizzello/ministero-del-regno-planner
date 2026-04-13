package org.example.project.feature.diagnostics.infrastructure

import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.LocalDate
import org.example.project.db.MinisteroDatabase
import org.example.project.feature.diagnostics.application.EquitaPersonAggregateRow
import org.example.project.feature.diagnostics.application.EquitaQuery
import org.example.project.feature.people.infrastructure.mapProclamatoreAssignableRow

class SqlDelightEquitaQuery(
    private val database: MinisteroDatabase,
) : EquitaQuery {

    override suspend fun listPersonAggregates(
        sinceDate: LocalDate,
        untilDate: LocalDate,
    ): List<EquitaPersonAggregateRow> {
        return database.ministeroDatabaseQueries
            .equityPersonAggregates(
                sinceDate = sinceDate.toString(),
                untilDate = untilDate.toString(),
            ) { person_id, first_name, last_name, sex, suspended, can_assist, total_in_window, lead_count, assist_count, last_lead_date, last_assist_date, last_any_date, assigned_weeks_csv ->
                EquitaPersonAggregateRow(
                    proclamatore = mapProclamatoreAssignableRow(
                        id = person_id,
                        first_name = first_name,
                        last_name = last_name,
                        sex = sex,
                        suspended = suspended,
                        can_assist = can_assist,
                    ),
                    totaleInFinestra = total_in_window.toInt(),
                    conduzioniInFinestra = (lead_count ?: 0L).toInt(),
                    assistenzeInFinestra = (assist_count ?: 0L).toInt(),
                    ultimaConduzione = last_lead_date?.let(LocalDate::parse),
                    ultimaAssistenza = last_assist_date?.let(LocalDate::parse),
                    ultimaAssegnazione = last_any_date?.let(LocalDate::parse),
                    settimaneAssegnate = parseAssignedWeeks(person_id, assigned_weeks_csv),
                )
            }
            .executeAsList()
    }

    private fun parseAssignedWeeks(
        personId: String,
        assignedWeeksCsv: String?,
    ): Set<LocalDate> {
        if (assignedWeeksCsv.isNullOrBlank()) return emptySet()
        return assignedWeeksCsv
            .split(',')
            .mapNotNull { token ->
                runCatching { LocalDate.parse(token.trim()) }
                    .onFailure { error ->
                        logger.warn(error) { "Equita: data settimana non valida per personId=$personId: '$token'" }
                    }
                    .getOrNull()
            }
            .toSet()
    }

    private companion object {
        val logger = KotlinLogging.logger {}
    }
}

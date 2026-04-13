package org.example.project.feature.diagnostics.application

import java.time.LocalDate
import org.example.project.feature.diagnostics.domain.EquitaProclamatore
import org.example.project.feature.diagnostics.domain.RiepilogoEquita
import org.example.project.feature.people.domain.Proclamatore

data class EquitaPersonAggregateRow(
    val proclamatore: Proclamatore,
    val totaleInFinestra: Int,
    val conduzioniInFinestra: Int,
    val assistenzeInFinestra: Int,
    val ultimaConduzione: LocalDate?,
    val ultimaAssistenza: LocalDate?,
    val ultimaAssegnazione: LocalDate?,
    val settimaneAssegnate: Set<LocalDate>,
)

data class EquitaSnapshot(
    val referenceDate: LocalDate,
    val riepilogo: RiepilogoEquita,
    val righe: List<EquitaProclamatore>,
)

interface EquitaQuery {
    suspend fun listPersonAggregates(
        sinceDate: LocalDate,
        untilDate: LocalDate,
    ): List<EquitaPersonAggregateRow>
}

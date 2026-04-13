package org.example.project.feature.diagnostics.application

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.project.feature.assignments.application.AssignmentSettings
import org.example.project.feature.assignments.application.AssignmentSettingsStore
import org.example.project.feature.assignments.application.RANKING_HISTORY_WEEKS
import org.example.project.feature.diagnostics.domain.EquitaIndicazione
import org.example.project.feature.diagnostics.domain.EquitaProclamatore
import org.example.project.feature.diagnostics.domain.RiepilogoEquita

class CalcolaEquitaProclamatoriUseCase(
    private val equitaQuery: EquitaQuery,
    private val assignmentSettingsStore: AssignmentSettingsStore,
) {
    suspend operator fun invoke(today: LocalDate): EquitaSnapshot = withContext(Dispatchers.IO) {
        val settings = assignmentSettingsStore.load().normalized()
        val sinceDate = today.minusWeeks(RANKING_HISTORY_WEEKS)
        val righeBase = equitaQuery
            .listPersonAggregates(sinceDate = sinceDate, untilDate = today)
            .map { row -> row.toEquitaProclamatore(today, settings) }
        val median = righeBase
            .filterNot { it.proclamatore.sospeso }
            .map { it.totaleInFinestra }
            .sorted()
            .medianOrZero()
        val forgottenThresholdWeeks = settings.leadCooldownWeeks * 2
        val righe = righeBase.map { row ->
            row.copy(
                fermoDaMolto = row.settimaneDallUltima != null && row.settimaneDallUltima > forgottenThresholdWeeks,
                indicazione = classifyIndicazione(row, median),
            )
        }

        EquitaSnapshot(
            referenceDate = today,
            riepilogo = buildRiepilogo(righe, settings),
            righe = righe,
        )
    }

    private fun EquitaPersonAggregateRow.toEquitaProclamatore(
        today: LocalDate,
        settings: AssignmentSettings,
    ): EquitaProclamatore {
        val settimaneDallUltima = ultimaAssegnazione
            ?.let { ChronoUnit.WEEKS.between(it, today).toInt().coerceAtLeast(0) }

        return EquitaProclamatore(
            proclamatore = proclamatore,
            totaleInFinestra = totaleInFinestra,
            conduzioniInFinestra = conduzioniInFinestra,
            assistenzeInFinestra = assistenzeInFinestra,
            ultimaConduzione = ultimaConduzione,
            ultimaAssistenza = ultimaAssistenza,
            ultimaAssegnazione = ultimaAssegnazione,
            settimaneAssegnate = settimaneAssegnate,
            assegnazioniUltime4Settimane = settimaneAssegnate.count { !it.isBefore(today.minusWeeks(4)) },
            settimaneDallUltima = settimaneDallUltima,
            cooldownLeadResiduo = remainingCooldown(settings.leadCooldownWeeks, settimaneDallUltima),
            cooldownAssistResiduo = remainingCooldown(settings.assistCooldownWeeks, settimaneDallUltima),
        )
    }

    private fun buildRiepilogo(
        righe: List<EquitaProclamatore>,
        settings: AssignmentSettings,
    ): RiepilogoEquita {
        val activeRows = righe.filterNot { it.proclamatore.sospeso }
        val totals = activeRows.map { it.totaleInFinestra }.sorted()
        val forgottenThresholdWeeks = settings.leadCooldownWeeks * 2

        return RiepilogoEquita(
            totaleAttivi = activeRows.size,
            maiAssegnati = activeRows.count { it.maiAssegnato },
            fermiDaMolto = activeRows.count { it.fermoDaMolto },
            inCooldownLead = activeRows.count { it.cooldownLeadResiduo > 0 },
            inCooldownAssist = activeRows.count { it.cooldownAssistResiduo > 0 },
            daRecuperare = activeRows.count { it.indicazione == EquitaIndicazione.DA_RECUPERARE },
            daAlleggerire = activeRows.count { it.indicazione == EquitaIndicazione.DA_ALLEGGERIRE },
            minTotale = totals.firstOrNull() ?: 0,
            medianaTotale = totals.medianOrZero(),
            maxTotale = totals.lastOrNull() ?: 0,
            dimenticatiDaTroppo = activeRows
                .filter { row ->
                    row.settimaneDallUltima == null || row.settimaneDallUltima > forgottenThresholdWeeks
                }
                .sortedWith(
                    compareByDescending<EquitaProclamatore> { it.settimaneDallUltima ?: Int.MAX_VALUE }
                        .thenBy { it.proclamatore.cognome.lowercase() }
                        .thenBy { it.proclamatore.nome.lowercase() },
                ),
        )
    }

    private fun remainingCooldown(
        cooldownWeeks: Int,
        settimaneDallUltima: Int?,
    ): Int {
        if (settimaneDallUltima == null) return 0
        return (cooldownWeeks - settimaneDallUltima).coerceAtLeast(0)
    }

    private fun classifyIndicazione(
        row: EquitaProclamatore,
        mediana: Int,
    ): EquitaIndicazione {
        val daRecuperare = row.maiAssegnato || row.totaleInFinestra < mediana
        if (daRecuperare) return EquitaIndicazione.DA_RECUPERARE

        val daAlleggerire = row.totaleInFinestra > mediana && row.assegnazioniUltime4Settimane >= 2
        if (daAlleggerire) return EquitaIndicazione.DA_ALLEGGERIRE

        return EquitaIndicazione.EQUILIBRATO
    }
}

private fun List<Int>.medianOrZero(): Int {
    if (isEmpty()) return 0
    val middle = size / 2
    return if (size % 2 == 1) {
        this[middle]
    } else {
        (this[middle - 1] + this[middle]) / 2
    }
}

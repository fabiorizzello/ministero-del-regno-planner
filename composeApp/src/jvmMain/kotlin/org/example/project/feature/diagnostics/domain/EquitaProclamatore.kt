package org.example.project.feature.diagnostics.domain

import java.time.LocalDate
import org.example.project.feature.people.domain.Proclamatore

enum class EquitaIndicazione {
    DA_RECUPERARE,
    DA_ALLEGGERIRE,
    EQUILIBRATO,
}

data class EquitaProclamatore(
    val proclamatore: Proclamatore,
    val totaleInFinestra: Int,
    val conduzioniInFinestra: Int,
    val assistenzeInFinestra: Int,
    val ultimaConduzione: LocalDate?,
    val ultimaAssistenza: LocalDate?,
    val ultimaAssegnazione: LocalDate?,
    val settimaneAssegnate: Set<LocalDate>,
    val assegnazioniUltime4Settimane: Int,
    val settimaneDallUltima: Int?,
    val cooldownLeadResiduo: Int,
    val cooldownAssistResiduo: Int,
    val fermoDaMolto: Boolean = false,
    val indicazione: EquitaIndicazione = EquitaIndicazione.EQUILIBRATO,
) {
    val maiAssegnato: Boolean
        get() = ultimaAssegnazione == null

    val liberoDaAlmenoUnRuolo: Boolean
        get() = cooldownLeadResiduo == 0 || cooldownAssistResiduo == 0

    val fuoriCooldown: Boolean
        get() = cooldownLeadResiduo == 0 && cooldownAssistResiduo == 0
}

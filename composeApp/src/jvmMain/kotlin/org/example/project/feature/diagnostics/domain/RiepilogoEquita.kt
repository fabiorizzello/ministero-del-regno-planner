package org.example.project.feature.diagnostics.domain

data class RiepilogoEquita(
    val totaleAttivi: Int,
    val maiAssegnati: Int,
    val fermiDaMolto: Int,
    val inCooldownLead: Int,
    val inCooldownAssist: Int,
    val daRecuperare: Int,
    val daAlleggerire: Int,
    val minTotale: Int,
    val medianaTotale: Int,
    val maxTotale: Int,
    val dimenticatiDaTroppo: List<EquitaProclamatore>,
)

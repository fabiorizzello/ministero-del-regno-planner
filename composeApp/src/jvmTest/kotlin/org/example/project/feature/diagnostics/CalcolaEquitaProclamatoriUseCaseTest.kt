package org.example.project.feature.diagnostics

import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.example.project.core.persistence.TransactionScope
import org.example.project.feature.assignments.application.AssignmentSettings
import org.example.project.feature.assignments.application.AssignmentSettingsStore
import org.example.project.feature.diagnostics.application.CalcolaEquitaProclamatoriUseCase
import org.example.project.feature.diagnostics.application.EquitaPersonAggregateRow
import org.example.project.feature.diagnostics.application.EquitaQuery
import org.example.project.feature.diagnostics.domain.EquitaIndicazione
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.domain.Sesso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CalcolaEquitaProclamatoriUseCaseTest {

    @Test
    fun `builds rows and summary excluding suspended people from aggregate stats`() = runTest {
        val today = LocalDate.parse("2026-04-13")
        val query = FakeEquitaQuery(
            listOf(
                aggregateRow(
                    id = "alfa",
                    nome = "Alfa",
                    cognome = "Zero",
                    sesso = Sesso.M,
                    sospeso = false,
                    totale = 0,
                    lead = 0,
                    assist = 0,
                    ultimaLead = null,
                    ultimaAssist = null,
                    ultimaAny = null,
                    assignedWeeks = emptySet(),
                ),
                aggregateRow(
                    id = "beta",
                    nome = "Beta",
                    cognome = "Uno",
                    sesso = Sesso.F,
                    sospeso = false,
                    totale = 4,
                    lead = 3,
                    assist = 1,
                    ultimaLead = today.minusWeeks(2),
                    ultimaAssist = today.minusWeeks(5),
                    ultimaAny = today.minusWeeks(2),
                    assignedWeeks = setOf(
                        today.minusWeeks(2),
                        today.minusWeeks(4),
                        today.minusWeeks(8),
                        today.minusWeeks(11),
                    ),
                ),
                aggregateRow(
                    id = "gamma",
                    nome = "Gamma",
                    cognome = "Due",
                    sesso = Sesso.M,
                    sospeso = false,
                    totale = 1,
                    lead = 0,
                    assist = 1,
                    ultimaLead = null,
                    ultimaAssist = today.minusWeeks(10),
                    ultimaAny = today.minusWeeks(10),
                    assignedWeeks = setOf(today.minusWeeks(10)),
                ),
                aggregateRow(
                    id = "delta",
                    nome = "Delta",
                    cognome = "Tre",
                    sesso = Sesso.M,
                    sospeso = true,
                    totale = 5,
                    lead = 4,
                    assist = 1,
                    ultimaLead = today.minusWeeks(1),
                    ultimaAssist = today.minusWeeks(3),
                    ultimaAny = today.minusWeeks(1),
                    assignedWeeks = setOf(today.minusWeeks(1)),
                ),
            ),
        )
        val useCase = CalcolaEquitaProclamatoriUseCase(query, FakeAssignmentSettingsStore())

        val result = useCase(today)

        assertEquals(today, result.referenceDate)
        assertEquals(4, result.righe.size)
        val alfa = result.righe.first { it.proclamatore.id == ProclamatoreId("alfa") }
        val beta = result.righe.first { it.proclamatore.id == ProclamatoreId("beta") }
        val gamma = result.righe.first { it.proclamatore.id == ProclamatoreId("gamma") }

        assertTrue(alfa.maiAssegnato)
        assertEquals(0, alfa.totaleInFinestra)
        assertEquals(EquitaIndicazione.DA_RECUPERARE, alfa.indicazione)
        assertFalse(beta.maiAssegnato)
        assertEquals(3, beta.conduzioniInFinestra)
        assertEquals(1, beta.assistenzeInFinestra)
        assertEquals(today.minusWeeks(2), beta.ultimaAssegnazione)
        assertEquals(2, beta.assegnazioniUltime4Settimane)
        assertEquals(EquitaIndicazione.DA_ALLEGGERIRE, beta.indicazione)
        assertEquals(1, gamma.settimaneAssegnate.size)
        assertEquals(EquitaIndicazione.EQUILIBRATO, gamma.indicazione)

        assertEquals(3, result.riepilogo.totaleAttivi)
        assertEquals(1, result.riepilogo.maiAssegnati)
        assertEquals(1, result.riepilogo.daRecuperare)
        assertEquals(1, result.riepilogo.daAlleggerire)
        assertEquals(1, result.riepilogo.fermiDaMolto)
        assertEquals(0, result.riepilogo.minTotale)
        assertEquals(1, result.riepilogo.medianaTotale)
        assertEquals(4, result.riepilogo.maxTotale)
        assertEquals(2, result.riepilogo.dimenticatiDaTroppo.size)
        assertNotNull(result.riepilogo.dimenticatiDaTroppo.firstOrNull { it.proclamatore.id == ProclamatoreId("alfa") })
        assertNotNull(result.riepilogo.dimenticatiDaTroppo.firstOrNull { it.proclamatore.id == ProclamatoreId("gamma") })
        assertTrue(result.riepilogo.dimenticatiDaTroppo.none { it.proclamatore.id == ProclamatoreId("delta") })
        assertFalse(alfa.fermoDaMolto)
        assertFalse(beta.fermoDaMolto)
        assertTrue(gamma.fermoDaMolto)
    }
}

private class FakeEquitaQuery(
    private val rows: List<EquitaPersonAggregateRow>,
) : EquitaQuery {
    override suspend fun listPersonAggregates(
        sinceDate: LocalDate,
        untilDate: LocalDate,
    ): List<EquitaPersonAggregateRow> = rows
}

private class FakeAssignmentSettingsStore : AssignmentSettingsStore {
    override suspend fun load(): AssignmentSettings = AssignmentSettings(
        strictCooldown = true,
        leadCooldownWeeks = 4,
        assistCooldownWeeks = 2,
    )

    context(tx: TransactionScope)
    override suspend fun save(settings: AssignmentSettings) = Unit
}

private fun aggregateRow(
    id: String,
    nome: String,
    cognome: String,
    sesso: Sesso,
    sospeso: Boolean,
    totale: Int,
    lead: Int,
    assist: Int,
    ultimaLead: LocalDate?,
    ultimaAssist: LocalDate?,
    ultimaAny: LocalDate?,
    assignedWeeks: Set<LocalDate>,
): EquitaPersonAggregateRow = EquitaPersonAggregateRow(
    proclamatore = Proclamatore(
        id = ProclamatoreId(id),
        nome = nome,
        cognome = cognome,
        sesso = sesso,
        sospeso = sospeso,
        puoAssistere = true,
    ),
    totaleInFinestra = totale,
    conduzioniInFinestra = lead,
    assistenzeInFinestra = assist,
    ultimaConduzione = ultimaLead,
    ultimaAssistenza = ultimaAssist,
    ultimaAssegnazione = ultimaAny,
    settimaneAssegnate = assignedWeeks,
)

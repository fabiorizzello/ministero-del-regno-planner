package org.example.project.feature.people

import arrow.core.Either
import kotlinx.coroutines.runBlocking
import org.example.project.core.persistence.TransactionScope
import org.example.project.feature.people.application.AggiornaProclamatoreUseCase

import org.example.project.feature.people.application.EligibilityCleanupCandidate
import org.example.project.feature.people.application.EligibilityStore
import org.example.project.feature.people.application.LeadEligibility
import org.example.project.feature.people.application.ProclamatoriAggregateStore
import org.example.project.feature.people.application.ProclamatoriQuery
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.domain.Sesso
import org.example.project.feature.weeklyparts.domain.PartTypeId
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Testa la logica di sospensione/riattivazione tramite [AggiornaProclamatoreUseCase],
 * che è il punto di ingresso applicativo per modificare lo stato `sospeso` di un proclamatore.
 * `ImpostaSospesoUseCase` non esiste come use case separato: la spec 001 conferma che
 * il dominio usa solo `sospeso` tramite il form proclamatore (AggiornaProclamatoreUseCase).
 */
class ImpostaSospesoUseCaseTest {

    @Test
    fun `sospensione di persona attiva senza assegnazioni future - futureWeeks empty e persona risulta sospesa`() =
        runBlocking {
            val personId = ProclamatoreId("p1")
            val persona = makePersona(personId, sospeso = false)
            val store = ImpostaSospesoInMemoryStore(listOf(persona))
            val useCase = makeUseCase(store, futureWeeks = emptyList())

            val result = useCase(
                AggiornaProclamatoreUseCase.Command(
                    id = personId,
                    nome = persona.nome,
                    cognome = persona.cognome,
                    sesso = persona.sesso,
                    sospeso = true,
                ),
            )

            val outcome = assertIs<Either.Right<AggiornaProclamatoreUseCase.AggiornamentoOutcome>>(result).value
            assertTrue(outcome.proclamatore.sospeso)
            assertEquals(emptyList(), outcome.futureWeeksWhereAssigned)
        }

    @Test
    fun `sospensione di persona con assegnazioni future - futureWeeks non empty`() = runBlocking {
        val personId = ProclamatoreId("p2")
        val persona = makePersona(personId, sospeso = false)
        val store = ImpostaSospesoInMemoryStore(listOf(persona))
        val settimane = listOf(
            LocalDate.of(2026, 3, 9),
            LocalDate.of(2026, 3, 16),
        )
        val useCase = makeUseCase(store, futureWeeks = settimane)

        val result = useCase(
            AggiornaProclamatoreUseCase.Command(
                id = personId,
                nome = persona.nome,
                cognome = persona.cognome,
                sesso = persona.sesso,
                sospeso = true,
            ),
        )

        val outcome = assertIs<Either.Right<AggiornaProclamatoreUseCase.AggiornamentoOutcome>>(result).value
        assertTrue(outcome.proclamatore.sospeso)
        assertEquals(settimane, outcome.futureWeeksWhereAssigned)
    }

    @Test
    fun `riattivazione di persona sospesa - persona risulta non sospesa`() = runBlocking {
        val personId = ProclamatoreId("p3")
        val persona = makePersona(personId, sospeso = true)
        val store = ImpostaSospesoInMemoryStore(listOf(persona))
        val useCase = makeUseCase(store, futureWeeks = emptyList())

        val result = useCase(
            AggiornaProclamatoreUseCase.Command(
                id = personId,
                nome = persona.nome,
                cognome = persona.cognome,
                sesso = persona.sesso,
                sospeso = false,
            ),
        )

        val outcome = assertIs<Either.Right<AggiornaProclamatoreUseCase.AggiornamentoOutcome>>(result).value
        assertFalse(outcome.proclamatore.sospeso)
        // futureWeeks non viene calcolato alla riattivazione (solo alla sospensione)
        assertEquals(emptyList(), outcome.futureWeeksWhereAssigned)
    }

    @Test
    fun `sospensione di persona gia sospesa - idempotente, nessun errore`() = runBlocking {
        val personId = ProclamatoreId("p4")
        val persona = makePersona(personId, sospeso = true)
        val store = ImpostaSospesoInMemoryStore(listOf(persona))
        val useCase = makeUseCase(store, futureWeeks = emptyList())

        val result = useCase(
            AggiornaProclamatoreUseCase.Command(
                id = personId,
                nome = persona.nome,
                cognome = persona.cognome,
                sesso = persona.sesso,
                sospeso = true,
            ),
        )

        val outcome = assertIs<Either.Right<AggiornaProclamatoreUseCase.AggiornamentoOutcome>>(result).value
        assertTrue(outcome.proclamatore.sospeso)
        // Persona era già sospesa: futureWeeks non viene ricalcolato (corrente.sospeso == true)
        assertEquals(emptyList(), outcome.futureWeeksWhereAssigned)
    }

    // --- helpers ---

    private fun makePersona(id: ProclamatoreId, sospeso: Boolean) = Proclamatore(
        id = id,
        nome = "Mario",
        cognome = "Rossi",
        sesso = Sesso.M,
        sospeso = sospeso,
    )

    private fun makeUseCase(
        store: ImpostaSospesoInMemoryStore,
        futureWeeks: List<LocalDate>,
    ) = AggiornaProclamatoreUseCase(
        query = NoDuplicateQuery,
        store = store,
        eligibilityStore = StubEligibilityStore(futureWeeks),
        transactionRunner = ImmediateTransactionRunner,
    )
}

// --- fakes ---

private object NoDuplicateQuery : ProclamatoriQuery {
    override suspend fun cerca(termine: String?): List<Proclamatore> = emptyList()
    override suspend fun esisteConNomeCognome(nome: String, cognome: String, esclusoId: ProclamatoreId?): Boolean = false
}

private class ImpostaSospesoInMemoryStore(initial: List<Proclamatore> = emptyList()) : ProclamatoriAggregateStore {
    private val byId = initial.associateBy { it.id }.toMutableMap()

    override suspend fun load(id: ProclamatoreId): Proclamatore? = byId[id]
    override suspend fun persist(aggregateRoot: Proclamatore) { byId[aggregateRoot.id] = aggregateRoot }
    context(tx: TransactionScope)
    override suspend fun persistAll(aggregateRoots: Collection<Proclamatore>) {
        aggregateRoots.forEach { byId[it.id] = it }
    }
    override suspend fun remove(id: ProclamatoreId) { byId.remove(id) }
}

private class StubEligibilityStore(private val futureWeeks: List<LocalDate>) : EligibilityStore {
    override suspend fun setSuspended(personId: ProclamatoreId, suspended: Boolean) {}
    override suspend fun setCanAssist(personId: ProclamatoreId, canAssist: Boolean) {}
    override suspend fun setCanLead(personId: ProclamatoreId, partTypeId: PartTypeId, canLead: Boolean) {}
    override suspend fun listLeadEligibility(personId: ProclamatoreId): List<LeadEligibility> = emptyList()
    override suspend fun listLeadEligibilityCandidatesForPartTypes(partTypeIds: Set<PartTypeId>): List<EligibilityCleanupCandidate> = emptyList()
    override suspend fun preloadLeadEligibilityByPartType(partTypeIds: Set<PartTypeId>): Map<PartTypeId, Set<ProclamatoreId>> = emptyMap()
    override suspend fun deleteLeadEligibilityForPartTypes(partTypeIds: Set<PartTypeId>) {}
    override suspend fun listFutureAssignmentWeeks(personId: ProclamatoreId, fromDate: LocalDate): List<LocalDate> = futureWeeks
}

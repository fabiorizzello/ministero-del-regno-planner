package org.example.project.feature.people

import arrow.core.Either
import kotlinx.coroutines.runBlocking
import org.example.project.core.PassthroughTransactionRunner
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionScope
import org.example.project.feature.people.application.AggiornaProclamatoreUseCase
import org.example.project.feature.people.application.CreaProclamatoreUseCase
import org.example.project.feature.people.application.EligibilityCleanupCandidate
import org.example.project.feature.people.application.EligibilityStore
import org.example.project.feature.people.application.ImportaProclamatoriDaJsonUseCase
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DomainErrorPeopleUseCaseTest {

    @Test
    fun `crea proclamatore returns NomeObbligatorio`() = runBlocking {
        val useCase = CreaProclamatoreUseCase(
            query = FakeProclamatoriQuery(),
            store = InMemoryProclamatoriStore(),
            transactionRunner = PassthroughTransactionRunner,
        )

        val result = useCase(
            CreaProclamatoreUseCase.Command(
                nome = " ",
                cognome = "Rossi",
                sesso = Sesso.M,
            ),
        )

        val left = assertIs<Either.Left<DomainError>>(result).value
        assertEquals(DomainError.NomeObbligatorio, left)
    }

    @Test
    fun `aggiorna proclamatore returns NotFound when missing`() = runBlocking {
        val useCase = AggiornaProclamatoreUseCase(
            query = FakeProclamatoriQuery(),
            store = InMemoryProclamatoriStore(),
            eligibilityStore = NoopEligibilityStore,
            transactionRunner = PassthroughTransactionRunner,
        )

        val result = useCase(
            AggiornaProclamatoreUseCase.Command(
                id = ProclamatoreId("missing"),
                nome = "Mario",
                cognome = "Rossi",
                sesso = Sesso.M,
            ),
        )

        val left = assertIs<Either.Left<DomainError>>(result).value
        assertEquals(DomainError.NotFound("Proclamatore"), left)
    }

    @Test
    fun `crea proclamatore returns ProclamatoreDuplicato when nome e cognome esistono gia`() = runBlocking {
        val useCase = CreaProclamatoreUseCase(
            query = FakeProclamatoriQuery(duplicate = true),
            store = InMemoryProclamatoriStore(),
            transactionRunner = PassthroughTransactionRunner,
        )

        val result = useCase(
            CreaProclamatoreUseCase.Command(
                nome = "Mario",
                cognome = "Rossi",
                sesso = Sesso.M,
            ),
        )

        val left = assertIs<Either.Left<DomainError>>(result).value
        assertEquals(DomainError.ProclamatoreDuplicato, left)
    }

    @Test
    fun `aggiorna proclamatore happy path restituisce proclamatore aggiornato`() = runBlocking {
        val personId = ProclamatoreId("p1")
        val esistente = Proclamatore(
            id = personId,
            nome = "Mario",
            cognome = "Rossi",
            sesso = Sesso.M,
        )
        val store = InMemoryProclamatoriStore(initial = listOf(esistente))
        val useCase = AggiornaProclamatoreUseCase(
            query = FakeProclamatoriQuery(duplicate = false, people = listOf(esistente)),
            store = store,
            eligibilityStore = NoopEligibilityStore,
            transactionRunner = PassthroughTransactionRunner,
        )

        val result = useCase(
            AggiornaProclamatoreUseCase.Command(
                id = personId,
                nome = "Luigi",
                cognome = "Verdi",
                sesso = Sesso.F,
                puoAssistere = true,
            ),
        )

        val outcome = assertIs<Either.Right<AggiornaProclamatoreUseCase.AggiornamentoOutcome>>(result).value
        assertEquals("Luigi", outcome.proclamatore.nome)
        assertEquals("Verdi", outcome.proclamatore.cognome)
        assertEquals(Sesso.F, outcome.proclamatore.sesso)
        assertTrue(outcome.proclamatore.puoAssistere)
        assertEquals(emptyList(), outcome.futureWeeksWhereAssigned)
    }

    @Test
    fun `importa proclamatori returns ImportJsonNonValido for invalid payload`() = runBlocking {
        val useCase = ImportaProclamatoriDaJsonUseCase(
            query = FakeProclamatoriQuery(),
            store = InMemoryProclamatoriStore(),
            transactionRunner = PassthroughTransactionRunner,
        )

        val result = useCase("not-a-json")

        val left = assertIs<Either.Left<DomainError>>(result).value
        assertEquals(DomainError.ImportJsonNonValido, left)
    }

}

private class FakeProclamatoriQuery(
    private val duplicate: Boolean = false,
    private val people: List<Proclamatore> = emptyList(),
) : ProclamatoriQuery {
    override suspend fun cerca(termine: String?): List<Proclamatore> = people

    override suspend fun esisteConNomeCognome(nome: String, cognome: String, esclusoId: ProclamatoreId?): Boolean = duplicate
}

private class InMemoryProclamatoriStore(
    initial: List<Proclamatore> = emptyList(),
) : ProclamatoriAggregateStore {
    private val byId = initial.associateBy { it.id }.toMutableMap()

    override suspend fun load(id: ProclamatoreId): Proclamatore? = byId[id]

    override suspend fun persist(aggregateRoot: Proclamatore) {
        byId[aggregateRoot.id] = aggregateRoot
    }

    context(tx: TransactionScope)
    override suspend fun persistAll(aggregateRoots: Collection<Proclamatore>) {
        aggregateRoots.forEach { byId[it.id] = it }
    }

    override suspend fun remove(id: ProclamatoreId) {
        byId.remove(id)
    }
}

private object NoopEligibilityStore : EligibilityStore {
    override suspend fun setSuspended(personId: ProclamatoreId, suspended: Boolean) {}
    override suspend fun setCanAssist(personId: ProclamatoreId, canAssist: Boolean) {}
    override suspend fun setCanLead(personId: ProclamatoreId, partTypeId: PartTypeId, canLead: Boolean) {}
    override suspend fun listLeadEligibility(personId: ProclamatoreId): List<LeadEligibility> = emptyList()
    override suspend fun listLeadEligibilityCandidatesForPartTypes(partTypeIds: Set<PartTypeId>): List<EligibilityCleanupCandidate> = emptyList()
    override suspend fun preloadLeadEligibilityByPartType(partTypeIds: Set<PartTypeId>): Map<PartTypeId, Set<ProclamatoreId>> = emptyMap()
    override suspend fun deleteLeadEligibilityForPartTypes(partTypeIds: Set<PartTypeId>) {}
    override suspend fun listFutureAssignmentWeeks(personId: ProclamatoreId, fromDate: LocalDate): List<LocalDate> = emptyList()
}

package org.example.project.feature.people

import arrow.core.Either
import kotlinx.coroutines.runBlocking
import org.example.project.core.domain.DomainError
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

class DomainErrorPeopleUseCaseTest {

    @Test
    fun `crea proclamatore returns NomeObbligatorio`() = runBlocking {
        val useCase = CreaProclamatoreUseCase(
            query = FakeProclamatoriQuery(),
            store = InMemoryProclamatoriStore(),
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
    fun `importa proclamatori returns ImportJsonNonValido for invalid payload`() = runBlocking {
        val useCase = ImportaProclamatoriDaJsonUseCase(
            query = FakeProclamatoriQuery(),
            store = InMemoryProclamatoriStore(),
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
    override suspend fun deleteLeadEligibilityForPartTypes(partTypeIds: Set<PartTypeId>) {}
    override suspend fun listFutureAssignmentWeeks(personId: ProclamatoreId, fromDate: LocalDate): List<LocalDate> = emptyList()
}

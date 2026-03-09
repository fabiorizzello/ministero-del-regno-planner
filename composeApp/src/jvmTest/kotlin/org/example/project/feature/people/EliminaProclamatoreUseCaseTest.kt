package org.example.project.feature.people

import arrow.core.Either
import kotlinx.coroutines.runBlocking
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionRunner
import org.example.project.core.persistence.TransactionScope
import org.example.project.feature.assignments.application.PersonAssignmentLifecycle
import org.example.project.feature.people.application.EliminaProclamatoreUseCase
import org.example.project.feature.people.application.ProclamatoriAggregateStore
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.domain.Sesso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EliminaProclamatoreUseCaseTest {

    @Test
    fun `returns NotFound when person does not exist`() = runBlocking {
        val tx = TrackingTransactionRunner()
        val store = InMemoryPeopleStore()
        val assignments = TrackingPersonAssignmentLifecycle()
        val useCase = EliminaProclamatoreUseCase(store, assignments, tx)

        val result = useCase(ProclamatoreId("missing"))

        val left = assertIs<Either.Left<DomainError>>(result).value
        assertEquals(DomainError.NotFound("Proclamatore"), left)
        assertEquals(0, tx.invocationCount)
        assertTrue(assignments.removedPersonIds.isEmpty())
    }

    @Test
    fun `deletes assignments and person in a single transaction`() = runBlocking {
        val personId = ProclamatoreId("p1")
        val tx = TrackingTransactionRunner()
        val store = InMemoryPeopleStore(
            initial = listOf(
                Proclamatore(
                    id = personId,
                    nome = "Mario",
                    cognome = "Rossi",
                    sesso = Sesso.M,
                ),
            ),
        )
        val assignments = TrackingPersonAssignmentLifecycle()
        val useCase = EliminaProclamatoreUseCase(store, assignments, tx)

        val result = useCase(personId)

        assertIs<Either.Right<Unit>>(result)
        assertEquals(1, tx.invocationCount)
        assertEquals(listOf(personId), assignments.removedPersonIds)
        assertEquals(emptyList(), store.currentIds())
    }
}

private class TrackingTransactionRunner : TransactionRunner {
    var invocationCount: Int = 0
        private set

    override suspend fun <T> runInTransaction(block: suspend org.example.project.core.persistence.TransactionScope.() -> T): T {
        invocationCount += 1
        return with(org.example.project.core.persistence.DefaultTransactionScope) { block() }
    }
}

private class TrackingPersonAssignmentLifecycle : PersonAssignmentLifecycle {
    val removedPersonIds = mutableListOf<ProclamatoreId>()

    override suspend fun countAssignmentsForPerson(personId: ProclamatoreId): Int = 0

    override suspend fun removeAllForPerson(personId: ProclamatoreId) {
        removedPersonIds += personId
    }
}

private class InMemoryPeopleStore(
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

    fun currentIds(): List<ProclamatoreId> = byId.keys.toList()
}

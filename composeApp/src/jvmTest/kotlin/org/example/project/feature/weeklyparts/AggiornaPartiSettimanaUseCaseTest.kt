package org.example.project.feature.weeklyparts

import arrow.core.Either
import kotlinx.coroutines.test.runTest
import org.example.project.core.domain.DomainError
import org.example.project.core.PassthroughTransactionRunner
import org.example.project.core.persistence.TransactionScope
import org.example.project.feature.assignments.domain.Assignment
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.weeklyparts.application.AggiornaPartiSettimanaUseCase
import org.example.project.feature.weeklyparts.application.PartTypeStore
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.SexRule
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanAggregate
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeeklyPart
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AggiornaPartiSettimanaUseCaseTest {

    private val weekDate = LocalDate.of(2026, 3, 2) // Monday

    // 1. Aggiunta parte a settimana mutabile → parte aggiunta correttamente
    @Test
    fun `replace parts on mutable week succeeds and stores new parts`() = runTest {
        val existingPartType = makePartType("pt-1", "LETTURA", fixed = false)
        val newPartType = makePartType("pt-2", "DISCORSO", fixed = false)

        val store = AggiornaPartiWeekPlanStore(weekDate = weekDate, initialPartTypes = listOf(existingPartType))
        val partTypeStore = InMemoryPartTypeStore(listOf(existingPartType, newPartType))
        val txRunner = PassthroughTransactionRunner

        val useCase = AggiornaPartiSettimanaUseCase(
            weekPlanStore = store,
            partTypeStore = partTypeStore,
            transactionRunner = txRunner,
        )

        val result = useCase(
            weekPlanId = store.weekPlanId,
            orderedPartTypeIds = listOf(newPartType.id),
        )

        assertIs<Either.Right<Unit>>(result)
        val savedParts = store.savedAggregate!!.weekPlan.parts
        assertEquals(1, savedParts.size)
        assertEquals(newPartType.id, savedParts.single().partType.id)
        Unit
    }

    // 2. Aggiunta parte a settimana passata attiva → consentita per modifica storico
    @Test
    fun `replace parts on past active week succeeds`() = runTest {
        val partType = makePartType("pt-1", "LETTURA", fixed = false)
        val pastWeekDate = LocalDate.of(2026, 2, 16) // Monday in the past
        val store = AggiornaPartiWeekPlanStore(weekDate = pastWeekDate, initialPartTypes = listOf(partType))
        val partTypeStore = InMemoryPartTypeStore(listOf(partType))
        val txRunner = PassthroughTransactionRunner

        val useCase = AggiornaPartiSettimanaUseCase(
            weekPlanStore = store,
            partTypeStore = partTypeStore,
            transactionRunner = txRunner,
        )

        val result = useCase(
            weekPlanId = store.weekPlanId,
            orderedPartTypeIds = listOf(partType.id),
        )

        assertIs<Either.Right<Unit>>(result)
        assertEquals(partType.id, store.savedAggregate!!.weekPlan.parts.single().partType.id)
    }

    // 3. Rimozione parte da settimana mutabile → parte rimossa, sort order ricompattato
    // replaceParts replaces all parts at once — after replacement only the listed parts remain,
    // with sort orders starting from 0 contiguously.
    @Test
    fun `replacing parts recompacts sort orders starting from zero`() = runTest {
        val pt1 = makePartType("pt-1", "LETTURA", fixed = false)
        val pt2 = makePartType("pt-2", "DISCORSO", fixed = false)

        val store = AggiornaPartiWeekPlanStore(weekDate = weekDate, initialPartTypes = listOf(pt1, pt2))
        val partTypeStore = InMemoryPartTypeStore(listOf(pt1, pt2))
        val txRunner = PassthroughTransactionRunner

        val useCase = AggiornaPartiSettimanaUseCase(
            weekPlanStore = store,
            partTypeStore = partTypeStore,
            transactionRunner = txRunner,
        )

        // Replace with both, in reverse order
        val result = useCase(
            weekPlanId = store.weekPlanId,
            orderedPartTypeIds = listOf(pt2.id, pt1.id),
        )

        assertIs<Either.Right<Unit>>(result)
        val parts = store.savedAggregate!!.weekPlan.parts
        assertEquals(2, parts.size)
        assertEquals(listOf(0, 1), parts.map { it.sortOrder })
        assertEquals(pt2.id, parts[0].partType.id)
        assertEquals(pt1.id, parts[1].partType.id)
        Unit
    }

    // 4. Lista parte fissa → OrdinePartiNonValido (empty list) or SettimanaImmutabile:
    // The use-case checks isEmpty() before calling replaceParts, returning OrdinePartiNonValido.
    @Test
    fun `empty part list returns OrdinePartiNonValido`() = runTest {
        val partType = makePartType("pt-1", "LETTURA", fixed = false)
        val store = AggiornaPartiWeekPlanStore(weekDate = weekDate, initialPartTypes = listOf(partType))
        val partTypeStore = InMemoryPartTypeStore(listOf(partType))
        val txRunner = PassthroughTransactionRunner

        val useCase = AggiornaPartiSettimanaUseCase(
            weekPlanStore = store,
            partTypeStore = partTypeStore,
            transactionRunner = txRunner,
        )

        val result = useCase(
            weekPlanId = store.weekPlanId,
            orderedPartTypeIds = emptyList(),
        )

        val left = assertIs<Either.Left<DomainError>>(result).value
        assertEquals(DomainError.OrdinePartiNonValido, left)
        Unit
    }

    @Test
    fun `use case preserves matching assignments when appending a new weekly part`() = runTest {
        val keptType = makePartType("pt-1", "LETTURA", fixed = false)
        val newType = makePartType("pt-2", "DISCORSO", fixed = false)
        val existingAssignment = Assignment(
            id = AssignmentId("a-1"),
            weeklyPartId = WeeklyPartId("part-0"),
            personId = ProclamatoreId("person-1"),
            slot = 1,
        )

        val store = AggiornaPartiWeekPlanStore(
            weekDate = weekDate,
            initialPartTypes = listOf(keptType),
            initialAssignments = listOf(existingAssignment),
        )
        val partTypeStore = InMemoryPartTypeStore(listOf(keptType, newType))
        val useCase = AggiornaPartiSettimanaUseCase(
            weekPlanStore = store,
            partTypeStore = partTypeStore,
            transactionRunner = PassthroughTransactionRunner,
        )

        val result = useCase(store.weekPlanId, listOf(keptType.id, newType.id))

        assertIs<Either.Right<Unit>>(result)
        assertEquals(1, store.savedAggregate!!.assignments.size)
        assertEquals(ProclamatoreId("person-1"), store.savedAggregate!!.assignments.single().personId)
    }

    @Test
    fun `use case drops only assignments whose weekly part is removed`() = runTest {
        val repeatedType = makePartType("pt-1", "LETTURA", fixed = false)
        val assignments = listOf(
            Assignment(
                id = AssignmentId("a-1"),
                weeklyPartId = WeeklyPartId("part-0"),
                personId = ProclamatoreId("person-1"),
                slot = 1,
            ),
            Assignment(
                id = AssignmentId("a-2"),
                weeklyPartId = WeeklyPartId("part-1"),
                personId = ProclamatoreId("person-2"),
                slot = 1,
            ),
        )

        val store = AggiornaPartiWeekPlanStore(
            weekDate = weekDate,
            initialPartTypes = listOf(repeatedType, repeatedType),
            initialAssignments = assignments,
        )
        val partTypeStore = InMemoryPartTypeStore(listOf(repeatedType))
        val useCase = AggiornaPartiSettimanaUseCase(
            weekPlanStore = store,
            partTypeStore = partTypeStore,
            transactionRunner = PassthroughTransactionRunner,
        )

        val result = useCase(store.weekPlanId, listOf(repeatedType.id))

        assertIs<Either.Right<Unit>>(result)
        assertEquals(1, store.savedAggregate!!.assignments.size)
        assertEquals(ProclamatoreId("person-1"), store.savedAggregate!!.assignments.single().personId)
    }
}

// ---- fakes ----

private fun makePartType(id: String, code: String, fixed: Boolean) = PartType(
    id = PartTypeId(id),
    code = code,
    label = code.lowercase().replaceFirstChar { it.uppercase() },
    peopleCount = 1,
    sexRule = SexRule.STESSO_SESSO,
    fixed = fixed,
    sortOrder = 0,
)

private class AggiornaPartiWeekPlanStore(
    weekDate: LocalDate,
    initialPartTypes: List<PartType>,
    initialAssignments: List<Assignment> = emptyList(),
) : TestWeekPlanStore() {

    val weekPlanId = WeekPlanId("w-test")
    var savedAggregate: WeekPlanAggregate? = null

    private val initialParts = initialPartTypes.mapIndexed { index, pt ->
        WeeklyPart(id = WeeklyPartId("part-$index"), partType = pt, sortOrder = index)
    }
    private var aggregate = WeekPlanAggregate(
        weekPlan = WeekPlan(id = weekPlanId, weekStartDate = weekDate, parts = initialParts),
        assignments = initialAssignments,
    )

    override suspend fun loadAggregateById(weekPlanId: WeekPlanId): WeekPlanAggregate? =
        if (aggregate.weekPlan.id == weekPlanId) aggregate else null

    context(tx: TransactionScope)
    override suspend fun saveAggregate(aggregate: WeekPlanAggregate) {
        savedAggregate = aggregate
        this.aggregate = aggregate
    }
}

private class InMemoryPartTypeStore(
    private val partTypes: List<PartType>,
) : PartTypeStore {
    override suspend fun all(): List<PartType> = partTypes
    override suspend fun findByCode(code: String): PartType? = partTypes.firstOrNull { it.code == code }
    override suspend fun findFixed(): PartType? = partTypes.firstOrNull { it.fixed }
    context(tx: TransactionScope)
    override suspend fun upsertAll(partTypes: List<PartType>) {}
}

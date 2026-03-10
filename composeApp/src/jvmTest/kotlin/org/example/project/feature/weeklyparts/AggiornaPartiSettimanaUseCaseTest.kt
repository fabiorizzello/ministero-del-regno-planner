package org.example.project.feature.weeklyparts

import arrow.core.Either
import kotlinx.coroutines.test.runTest
import org.example.project.core.domain.DomainError
import org.example.project.core.PassthroughTransactionRunner
import org.example.project.core.persistence.TransactionScope
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

    // referenceDate = same week as the week plan → mutable
    private val weekDate = LocalDate.of(2026, 3, 2) // Monday
    private val referenceDate = LocalDate.of(2026, 3, 4) // Wednesday same week

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
            referenceDate = referenceDate,
        )

        assertIs<Either.Right<Unit>>(result)
        val savedParts = store.savedAggregate!!.weekPlan.parts
        assertEquals(1, savedParts.size)
        assertEquals(newPartType.id, savedParts.single().partType.id)
        Unit
    }

    // 2. Aggiunta parte a settimana passata/immutabile → DomainError.SettimanaImmutabile
    @Test
    fun `replace parts on past week returns SettimanaImmutabile`() = runTest {
        val partType = makePartType("pt-1", "LETTURA", fixed = false)
        // week in the past
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
            referenceDate = referenceDate,
        )

        val left = assertIs<Either.Left<DomainError>>(result).value
        assertEquals(DomainError.SettimanaImmutabile, left)
        Unit
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
            referenceDate = referenceDate,
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
            referenceDate = referenceDate,
        )

        val left = assertIs<Either.Left<DomainError>>(result).value
        assertEquals(DomainError.OrdinePartiNonValido, left)
        Unit
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
) : TestWeekPlanStore() {

    val weekPlanId = WeekPlanId("w-test")
    var savedAggregate: WeekPlanAggregate? = null

    private val initialParts = initialPartTypes.mapIndexed { index, pt ->
        WeeklyPart(id = WeeklyPartId("part-$index"), partType = pt, sortOrder = index)
    }
    private var aggregate = WeekPlanAggregate(
        weekPlan = WeekPlan(id = weekPlanId, weekStartDate = weekDate, parts = initialParts),
        assignments = emptyList(),
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

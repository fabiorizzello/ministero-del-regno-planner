package org.example.project.feature.weeklyparts

import arrow.core.Either
import kotlinx.coroutines.runBlocking
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.DefaultTransactionScope
import org.example.project.core.persistence.TransactionRunner
import org.example.project.core.persistence.TransactionScope
import org.example.project.feature.weeklyparts.application.RimuoviParteUseCase
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
import kotlin.test.assertNull

class RimuoviParteUseCaseTest {

    private val weekDate = LocalDate.of(2026, 3, 2)    // Monday
    private val referenceDate = LocalDate.of(2026, 3, 4) // Wednesday same week → mutable

    // 1. Happy path: parte rimovibile → Either.Right, aggregate salvato senza la parte
    @Test
    fun `happy path removes part and saves aggregate without it`() = runBlocking {
        val removable = makePartType("pt-1", "LETTURA", fixed = false)
        val store = RimuoviParteTestWeekPlanStore(
            weekDate = weekDate,
            parts = listOf(
                WeeklyPart(id = WeeklyPartId("part-1"), partType = removable, sortOrder = 0),
            ),
        )
        val useCase = RimuoviParteUseCase(weekPlanStore = store, transactionRunner = passthroughRunner())

        val result = useCase(
            weekStartDate = weekDate,
            weeklyPartId = WeeklyPartId("part-1"),
            referenceDate = referenceDate,
        )

        assertIs<Either.Right<Unit>>(result)
        val saved = store.savedAggregate!!
        assertEquals(0, saved.weekPlan.parts.size)
        Unit
    }

    // 2. Parte fissa → Either.Left<DomainError.ParteFissa>
    @Test
    fun `fixed part returns ParteFissa`() = runBlocking {
        val fixed = makePartType("pt-fixed", "FISSA", fixed = true, label = "Parte Fissa")
        val store = RimuoviParteTestWeekPlanStore(
            weekDate = weekDate,
            parts = listOf(
                WeeklyPart(id = WeeklyPartId("part-fixed"), partType = fixed, sortOrder = 0),
            ),
        )
        val useCase = RimuoviParteUseCase(weekPlanStore = store, transactionRunner = passthroughRunner())

        val result = useCase(
            weekStartDate = weekDate,
            weeklyPartId = WeeklyPartId("part-fixed"),
            referenceDate = referenceDate,
        )

        val left = assertIs<Either.Left<DomainError>>(result).value
        assertIs<DomainError.ParteFissa>(left)
        assertEquals("Parte Fissa", (left as DomainError.ParteFissa).label)
        Unit
    }

    // 3. Settimana non trovata → Either.Left<DomainError.NotFound>
    @Test
    fun `missing week returns NotFound`() = runBlocking {
        val store = RimuoviParteTestWeekPlanStore(
            weekDate = weekDate,
            parts = emptyList(),
        )
        val useCase = RimuoviParteUseCase(weekPlanStore = store, transactionRunner = passthroughRunner())

        val result = useCase(
            weekStartDate = LocalDate.of(2026, 1, 5), // different date → not found
            weeklyPartId = WeeklyPartId("part-1"),
            referenceDate = referenceDate,
        )

        val left = assertIs<Either.Left<DomainError>>(result).value
        assertIs<DomainError.NotFound>(left)
        Unit
    }

    // 4. Sort order delle parti rimanenti è ricompattato dopo la rimozione
    @Test
    fun `sort orders are recompacted after removal`() = runBlocking {
        val pt1 = makePartType("pt-1", "LETTURA", fixed = false)
        val pt2 = makePartType("pt-2", "DISCORSO", fixed = false)
        val pt3 = makePartType("pt-3", "STUDIO", fixed = false)
        val store = RimuoviParteTestWeekPlanStore(
            weekDate = weekDate,
            parts = listOf(
                WeeklyPart(id = WeeklyPartId("part-1"), partType = pt1, sortOrder = 0),
                WeeklyPart(id = WeeklyPartId("part-2"), partType = pt2, sortOrder = 1),
                WeeklyPart(id = WeeklyPartId("part-3"), partType = pt3, sortOrder = 2),
            ),
        )
        val useCase = RimuoviParteUseCase(weekPlanStore = store, transactionRunner = passthroughRunner())

        val result = useCase(
            weekStartDate = weekDate,
            weeklyPartId = WeeklyPartId("part-2"), // remove the middle one
            referenceDate = referenceDate,
        )

        assertIs<Either.Right<Unit>>(result)
        val parts = store.savedAggregate!!.weekPlan.parts
        assertEquals(2, parts.size)
        assertEquals(listOf(0, 1), parts.map { it.sortOrder })
        assertEquals(WeeklyPartId("part-1"), parts[0].id)
        assertEquals(WeeklyPartId("part-3"), parts[1].id)
        Unit
    }
}

// ---- fakes ----

private fun makePartType(
    id: String,
    code: String,
    fixed: Boolean,
    label: String = code.lowercase().replaceFirstChar { it.uppercase() },
) = PartType(
    id = PartTypeId(id),
    code = code,
    label = label,
    peopleCount = 1,
    sexRule = SexRule.STESSO_SESSO,
    fixed = fixed,
    sortOrder = 0,
)

private class RimuoviParteTestWeekPlanStore(
    private val weekDate: LocalDate,
    parts: List<WeeklyPart>,
) : TestWeekPlanStore() {

    var savedAggregate: WeekPlanAggregate? = null

    private var aggregate: WeekPlanAggregate? = if (parts.isNotEmpty()) {
        WeekPlanAggregate(
            weekPlan = WeekPlan(
                id = WeekPlanId("w-test"),
                weekStartDate = weekDate,
                parts = parts,
            ),
            assignments = emptyList(),
        )
    } else {
        null
    }

    override suspend fun loadAggregateByDate(weekStartDate: LocalDate): WeekPlanAggregate? =
        if (aggregate?.weekPlan?.weekStartDate == weekStartDate) aggregate else null

    context(tx: TransactionScope)
    override suspend fun saveAggregate(aggregate: WeekPlanAggregate) {
        savedAggregate = aggregate
        this.aggregate = aggregate
    }
}

private fun passthroughRunner(): TransactionRunner = object : TransactionRunner {
    override suspend fun <T> runInTransaction(block: suspend TransactionScope.() -> T): T =
        with(DefaultTransactionScope) { block() }
}

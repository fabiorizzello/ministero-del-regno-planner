package org.example.project.feature.weeklyparts

import arrow.core.Either
import kotlinx.coroutines.runBlocking
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionRunner
import org.example.project.feature.weeklyparts.application.CreaSettimanaUseCase
import org.example.project.feature.weeklyparts.application.PartTypeStore
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.SexRule
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanAggregate
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CreaSettimanaUseCaseTest {

    @Test
    fun `returns typed error when date is not monday`() = runBlocking {
        val weekStore = InMemoryWeekPlanStore()
        val partTypeStore = SingleFixedPartTypeStore()
        val useCase = CreaSettimanaUseCase(
            weekPlanStore = weekStore,
            partTypeStore = partTypeStore,
            transactionRunner = CreaSettimanaTxRunner(),
        )

        val result = useCase(LocalDate.of(2026, 3, 3)) // Tuesday

        val left = assertIs<Either.Left<DomainError>>(result).value
        assertIs<DomainError.DataSettimanaNonLunedi>(left)
        assertTrue(weekStore.savedWeeks.isEmpty())
    }
}

private class CreaSettimanaTxRunner : TransactionRunner {
    override suspend fun <T> runInTransaction(block: suspend org.example.project.core.persistence.TransactionScope.() -> T): T = with(org.example.project.core.persistence.DefaultTransactionScope) { block() }
}

private class SingleFixedPartTypeStore : PartTypeStore {
    private val fixed = PartType(
        id = PartTypeId("fixed-1"),
        code = "FIXED",
        label = "Parte Fissa",
        peopleCount = 1,
        sexRule = SexRule.STESSO_SESSO,
        fixed = true,
        sortOrder = 0,
    )

    override suspend fun all(): List<PartType> = listOf(fixed)

    override suspend fun findByCode(code: String): PartType? = if (code == fixed.code) fixed else null

    override suspend fun findFixed(): PartType? = fixed

    override suspend fun upsertAll(partTypes: List<PartType>) {
        // no-op
    }
}

private class InMemoryWeekPlanStore : TestWeekPlanStore() {
    val savedWeeks = mutableListOf<WeekPlan>()

    override suspend fun loadAggregateByDate(weekStartDate: LocalDate): WeekPlanAggregate? =
        savedWeeks.firstOrNull { it.weekStartDate == weekStartDate }?.let { week ->
            WeekPlanAggregate(weekPlan = week, assignments = emptyList())
        }

    context(tx: org.example.project.core.persistence.TransactionScope)
    override suspend fun saveAggregate(aggregate: WeekPlanAggregate) {
        savedWeeks.removeAll { week -> week.id == aggregate.weekPlan.id }
        savedWeeks.add(aggregate.weekPlan)
    }

    override suspend fun loadAggregateById(weekPlanId: WeekPlanId): WeekPlanAggregate? =
        savedWeeks.firstOrNull { week -> week.id == weekPlanId }?.let { week ->
            WeekPlanAggregate(weekPlan = week, assignments = emptyList())
        }
}

package org.example.project.feature.weeklyparts

import arrow.core.Either
import kotlinx.coroutines.runBlocking
import org.example.project.core.domain.DomainError
import org.example.project.core.PassthroughTransactionRunner
import org.example.project.core.persistence.TransactionScope
import org.example.project.feature.weeklyparts.application.AggiungiParteUseCase
import org.example.project.feature.weeklyparts.application.PartTypeStore
import org.example.project.feature.weeklyparts.application.PartTypeWithStatus
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

class DomainErrorMappingWeeklyPartsUseCaseTest {

    @Test
    fun `aggiungi parte returns NotFound when week is missing`() = runBlocking {
        val useCase = AggiungiParteUseCase(
            weekPlanStore = EmptyWeekPlanStore(),
            partTypeStore = NoopPartTypeStore,
            transactionRunner = PassthroughTransactionRunner,
        )

        val result = useCase(
            weekStartDate = LocalDate.of(2026, 3, 2),
            partTypeId = PartTypeId("pt-1"),
        )

        val left = assertIs<Either.Left<DomainError>>(result).value
        assertEquals(DomainError.NotFound("Settimana"), left)
    }

    @Test
    fun `rimuovi parte returns ParteFissa for fixed part`() = runBlocking {
        val weekStore = FixedPartWeekPlanStore()
        val useCase = RimuoviParteUseCase(
            weekPlanStore = weekStore,
            transactionRunner = PassthroughTransactionRunner,
        )

        val result = useCase(
            weekStartDate = weekStore.week.weekStartDate,
            weeklyPartId = weekStore.fixedPart.id,
            referenceDate = weekStore.week.weekStartDate,
        )

        val left = assertIs<Either.Left<DomainError>>(result).value
        assertEquals(DomainError.ParteFissa("Parte Fissa"), left)
    }
}

private object NoopPartTypeStore : PartTypeStore {
    override suspend fun all(): List<PartType> = emptyList()
    override suspend fun allWithStatus(): List<PartTypeWithStatus> = emptyList()
    override suspend fun findByCode(code: String): PartType? = null
    override suspend fun findFixed(): PartType? = null
    context(tx: TransactionScope)
    override suspend fun upsertAll(partTypes: List<PartType>) {}
}

private class EmptyWeekPlanStore : TestWeekPlanStore()

private class FixedPartWeekPlanStore : TestWeekPlanStore() {
    private val fixedType = PartType(
        id = PartTypeId("fixed-type"),
        code = "FIXED",
        label = "Parte Fissa",
        peopleCount = 1,
        sexRule = SexRule.STESSO_SESSO,
        fixed = true,
        sortOrder = 0,
    )
    val fixedPart = WeeklyPart(
        id = WeeklyPartId("fixed-part"),
        partType = fixedType,
        sortOrder = 0,
    )
    val week = WeekPlan(
        id = WeekPlanId("week-1"),
        weekStartDate = LocalDate.of(2026, 3, 2),
        parts = listOf(fixedPart),
    )

    override suspend fun loadAggregateByDate(weekStartDate: LocalDate): WeekPlanAggregate? =
        if (weekStartDate == week.weekStartDate) {
            WeekPlanAggregate(weekPlan = week, assignments = emptyList())
        } else {
            null
        }
}

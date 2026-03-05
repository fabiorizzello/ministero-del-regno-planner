package org.example.project.feature.weeklyparts

import arrow.core.Either
import kotlinx.coroutines.runBlocking
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionRunner
import org.example.project.feature.weeklyparts.application.CreaSettimanaUseCase
import org.example.project.feature.weeklyparts.application.PartTypeStore
import org.example.project.feature.weeklyparts.application.WeekPlanStore
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.SexRule
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeekPlanSummary
import org.example.project.feature.weeklyparts.domain.WeekPlanStatus
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CreaSettimanaUseCaseTest {

    @Test
    fun `returns validation error when date is not monday`() = runBlocking {
        val weekStore = InMemoryWeekPlanStore()
        val partTypeStore = SingleFixedPartTypeStore()
        val useCase = CreaSettimanaUseCase(
            weekPlanStore = weekStore,
            partTypeStore = partTypeStore,
            transactionRunner = PassthroughTransactionRunner(),
        )

        val result = useCase(LocalDate.of(2026, 3, 3)) // Tuesday

        val left = assertIs<Either.Left<DomainError>>(result).value
        val validation = assertIs<DomainError.Validation>(left)
        assertTrue(validation.message.contains("lunedi", ignoreCase = true))
        assertTrue(weekStore.savedWeeks.isEmpty())
    }
}

private class PassthroughTransactionRunner : TransactionRunner {
    override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
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

private class InMemoryWeekPlanStore : WeekPlanStore {
    val savedWeeks = mutableListOf<WeekPlan>()

    override suspend fun findByDate(weekStartDate: LocalDate): WeekPlan? =
        savedWeeks.firstOrNull { it.weekStartDate == weekStartDate }

    override suspend fun listInRange(startDate: LocalDate, endDate: LocalDate): List<WeekPlanSummary> = emptyList()

    override suspend fun totalSlotsByWeekInRange(startDate: LocalDate, endDate: LocalDate): Map<WeekPlanId, Int> = emptyMap()

    override suspend fun save(weekPlan: WeekPlan) {
        savedWeeks.add(weekPlan)
    }

    override suspend fun delete(weekPlanId: WeekPlanId) {}

    override suspend fun addPart(weekPlanId: WeekPlanId, partTypeId: PartTypeId, sortOrder: Int, partTypeRevisionId: String?): WeeklyPartId =
        WeeklyPartId("part-1")

    override suspend fun removePart(weeklyPartId: WeeklyPartId) {}

    override suspend fun updateSortOrders(parts: List<Pair<WeeklyPartId, Int>>) {}

    override suspend fun replaceAllParts(weekPlanId: WeekPlanId, partTypeIds: List<PartTypeId>, revisionIds: List<String?>) {}

    override suspend fun saveWithProgram(weekPlan: WeekPlan, programId: ProgramMonthId, status: WeekPlanStatus) {
        save(weekPlan)
    }
}


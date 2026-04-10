package org.example.project.feature.weeklyparts

import arrow.core.Either
import kotlinx.coroutines.test.runTest
import org.example.project.core.domain.DomainError
import org.example.project.core.PassthroughTransactionRunner
import org.example.project.feature.weeklyparts.application.RiordinaPartiUseCase
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

class RiordinaPartiUseCaseTest {

    @Test
    fun `maps part ids to contiguous indexes`() {
        runTest {
            val store = TrackingSortWeekPlanStore()
            val useCase = RiordinaPartiUseCase(store, PassthroughTransactionRunner)

            val result = useCase(
                weekStartDate = store.weekDate,
                orderedPartIds = listOf(
                    WeeklyPartId("p2"),
                    WeeklyPartId("p1"),
                ),
            )

            assertIs<Either.Right<Unit>>(result)
            assertEquals(
                listOf(
                    WeeklyPartId("p2") to 0,
                    WeeklyPartId("p1") to 1,
                ),
                store.latestUpdates,
            )
        }
    }

    @Test
    fun `returns typed error when update fails`() {
        runTest {
            val store = TrackingSortWeekPlanStore(throwOnSave = true)
            val useCase = RiordinaPartiUseCase(store, PassthroughTransactionRunner)

            val result = useCase(
                weekStartDate = store.weekDate,
                orderedPartIds = listOf(WeeklyPartId("p1"), WeeklyPartId("p2")),
            )

            val left = assertIs<Either.Left<DomainError>>(result).value
            assertIs<DomainError.Validation>(left)
        }
    }
}

private class TrackingSortWeekPlanStore(
    private val throwOnSave: Boolean = false,
) : TestWeekPlanStore() {
    val weekDate: LocalDate = LocalDate.of(2026, 3, 2)
    private var aggregate: WeekPlanAggregate = WeekPlanAggregate(
        weekPlan = WeekPlan(
            id = WeekPlanId("week-1"),
            weekStartDate = weekDate,
            parts = listOf(
                WeeklyPart(
                    id = WeeklyPartId("p1"),
                    partType = partType("A", "pt-1"),
                    sortOrder = 0,
                ),
                WeeklyPart(
                    id = WeeklyPartId("p2"),
                    partType = partType("B", "pt-2"),
                    sortOrder = 1,
                ),
            ),
        ),
        assignments = emptyList(),
    )

    var latestUpdates: List<Pair<WeeklyPartId, Int>> = emptyList()
        private set

    override suspend fun loadAggregateByDate(weekStartDate: LocalDate): WeekPlanAggregate? =
        if (aggregate.weekPlan.weekStartDate == weekStartDate) aggregate else null

    context(tx: org.example.project.core.persistence.TransactionScope)
    override suspend fun saveAggregate(aggregate: WeekPlanAggregate) {
        if (throwOnSave) error("boom")
        this.aggregate = aggregate
        latestUpdates = aggregate.weekPlan.parts
            .sortedBy { part -> part.sortOrder }
            .map { part -> part.id to part.sortOrder }
    }

    override suspend fun loadAggregateById(weekPlanId: WeekPlanId): WeekPlanAggregate? =
        if (aggregate.weekPlan.id == weekPlanId) aggregate else null

    private fun partType(label: String, id: String): PartType = PartType(
        id = PartTypeId(id),
        code = "CODE-$id",
        label = label,
        peopleCount = 1,
        sexRule = SexRule.STESSO_SESSO,
        fixed = false,
        sortOrder = 0,
    )
}

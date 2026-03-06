package org.example.project.feature.weeklyparts.domain

import org.example.project.core.domain.DomainError
import org.example.project.feature.assignments.domain.Assignment
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.people.domain.ProclamatoreId
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class WeekPlanAggregateTest {

    @Test
    fun `validate assignment returns slot error when outside part range`() {
        val aggregate = aggregateWithSinglePart(peopleCount = 2)

        val error = aggregate.validateAssignment(
            weeklyPartId = WeeklyPartId("part-1"),
            personId = ProclamatoreId("p1"),
            personSuspended = false,
            slot = 3,
        )

        assertEquals(DomainError.SlotNonValido(slot = 3, max = 2), error)
    }

    @Test
    fun `validate assignment returns duplicate error when person already assigned`() {
        val aggregate = aggregateWithSinglePart(
            peopleCount = 2,
            assignments = listOf(
                Assignment(
                    id = AssignmentId("a1"),
                    weeklyPartId = WeeklyPartId("part-1"),
                    personId = ProclamatoreId("p1"),
                    slot = 1,
                ),
            ),
        )

        val error = aggregate.validateAssignment(
            weeklyPartId = WeeklyPartId("part-1"),
            personId = ProclamatoreId("p1"),
            personSuspended = false,
            slot = 2,
        )

        assertEquals(DomainError.PersonaGiaAssegnata, error)
    }

    @Test
    fun `validate assignment returns null when command is valid`() {
        val aggregate = aggregateWithSinglePart(peopleCount = 2)

        val error = aggregate.validateAssignment(
            weeklyPartId = WeeklyPartId("part-1"),
            personId = ProclamatoreId("p2"),
            personSuspended = false,
            slot = 1,
        )

        assertNull(error)
    }

    @Test
    fun `remove part returns ParteFissa when target is fixed`() {
        val fixed = PartType(
            id = PartTypeId("fixed"),
            code = "FIXED",
            label = "Parte Fissa",
            peopleCount = 1,
            sexRule = SexRule.STESSO_SESSO,
            fixed = true,
            sortOrder = 0,
        )
        val aggregate = aggregateWithParts(
            listOf(
                WeeklyPart(
                    id = WeeklyPartId("fixed-part"),
                    partType = fixed,
                    sortOrder = 0,
                ),
            ),
        )

        val result = aggregate.removePart(WeeklyPartId("fixed-part"), LocalDate.of(2026, 3, 2))
        val left = assertIs<arrow.core.Either.Left<DomainError>>(result).value
        assertEquals(DomainError.ParteFissa("Parte Fissa"), left)
    }

    @Test
    fun `remove part recompacts sort order after successful deletion`() {
        val firstType = partType(peopleCount = 1, id = "a")
        val secondType = partType(peopleCount = 1, id = "b")
        val aggregate = aggregateWithParts(
            listOf(
                WeeklyPart(id = WeeklyPartId("part-a"), partType = firstType, sortOrder = 0),
                WeeklyPart(id = WeeklyPartId("part-b"), partType = secondType, sortOrder = 1),
            ),
        )

        val result = aggregate.removePart(WeeklyPartId("part-a"), LocalDate.of(2026, 3, 2))
        val updated = assertIs<arrow.core.Either.Right<WeekPlanAggregate>>(result).value

        assertEquals(1, updated.weekPlan.parts.size)
        assertEquals(0, updated.weekPlan.parts.single().sortOrder)
        assertEquals(WeeklyPartId("part-b"), updated.weekPlan.parts.single().id)
    }

    @Test
    fun `reorder parts returns OrdinePartiNonValido when ids do not match existing set`() {
        val aggregate = aggregateWithParts(
            listOf(
                WeeklyPart(id = WeeklyPartId("part-a"), partType = partType(id = "a"), sortOrder = 0),
                WeeklyPart(id = WeeklyPartId("part-b"), partType = partType(id = "b"), sortOrder = 1),
            ),
        )

        val result = aggregate.reorderParts(
            orderedPartIds = listOf(WeeklyPartId("part-a"), WeeklyPartId("missing")),
            referenceDate = LocalDate.of(2026, 3, 2),
        )

        val left = assertIs<arrow.core.Either.Left<DomainError>>(result).value
        assertEquals(DomainError.OrdinePartiNonValido, left)
    }

    @Test
    fun `reorder parts applies contiguous sort orders`() {
        val aggregate = aggregateWithParts(
            listOf(
                WeeklyPart(id = WeeklyPartId("part-a"), partType = partType(id = "a"), sortOrder = 0),
                WeeklyPart(id = WeeklyPartId("part-b"), partType = partType(id = "b"), sortOrder = 1),
            ),
        )

        val result = aggregate.reorderParts(
            orderedPartIds = listOf(WeeklyPartId("part-b"), WeeklyPartId("part-a")),
            referenceDate = LocalDate.of(2026, 3, 2),
        )

        val updated = assertIs<arrow.core.Either.Right<WeekPlanAggregate>>(result).value
        assertEquals(listOf(WeeklyPartId("part-b"), WeeklyPartId("part-a")), updated.weekPlan.parts.map { it.id })
        assertEquals(listOf(0, 1), updated.weekPlan.parts.map { it.sortOrder })
    }

    @Test
    fun `add part appends new part with next sort order`() {
        val aggregate = aggregateWithParts(
            listOf(
                WeeklyPart(id = WeeklyPartId("part-a"), partType = partType(id = "a"), sortOrder = 0),
            ),
        )

        val updated = assertIs<arrow.core.Either.Right<WeekPlanAggregate>>(
            aggregate.addPart(
                partType = partType(id = "new"),
                partId = WeeklyPartId("part-new"),
                referenceDate = LocalDate.of(2026, 3, 2),
            ),
        ).value

        assertEquals(2, updated.weekPlan.parts.size)
        assertEquals(WeeklyPartId("part-new"), updated.weekPlan.parts.last().id)
        assertEquals(1, updated.weekPlan.parts.last().sortOrder)
    }

    private fun aggregateWithSinglePart(
        peopleCount: Int,
        assignments: List<Assignment> = emptyList(),
    ): WeekPlanAggregate {
        val partType = partType(peopleCount = peopleCount)
        val weekPlan = WeekPlan(
            id = WeekPlanId("w1"),
            weekStartDate = LocalDate.of(2026, 3, 2),
            parts = listOf(
                WeeklyPart(
                    id = WeeklyPartId("part-1"),
                    partType = partType,
                    sortOrder = 0,
                ),
            ),
        )
        return WeekPlanAggregate(
            weekPlan = weekPlan,
            assignments = assignments,
        )
    }

    private fun aggregateWithParts(parts: List<WeeklyPart>): WeekPlanAggregate =
        WeekPlanAggregate(
            weekPlan = WeekPlan(
                id = WeekPlanId("w1"),
                weekStartDate = LocalDate.of(2026, 3, 2),
                parts = parts,
            ),
            assignments = emptyList(),
        )

    private fun partType(
        peopleCount: Int = 2,
        id: String = "pt-1",
    ): PartType = PartType(
        id = PartTypeId(id),
        code = "PT-$id",
        label = "Parte",
        peopleCount = peopleCount,
        sexRule = SexRule.STESSO_SESSO,
        fixed = false,
        sortOrder = 0,
    )
}

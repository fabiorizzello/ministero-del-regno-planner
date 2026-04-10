package org.example.project.feature.weeklyparts.domain

import arrow.core.Either
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

        val result = aggregate.removePart(WeeklyPartId("fixed-part"))
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

        val result = aggregate.removePart(WeeklyPartId("part-a"))
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
            ),
        ).value

        assertEquals(2, updated.weekPlan.parts.size)
        assertEquals(WeeklyPartId("part-new"), updated.weekPlan.parts.last().id)
        assertEquals(1, updated.weekPlan.parts.last().sortOrder)
    }

    // --- replaceParts tests ---

    @Test
    fun `replaceParts on past active week succeeds for historical edits`() {
        val pastMonday = LocalDate.of(2026, 2, 23) // a past Monday
        val aggregate = WeekPlanAggregate(
            weekPlan = WeekPlan(
                id = WeekPlanId("w1"),
                weekStartDate = pastMonday,
                parts = listOf(
                    WeeklyPart(id = WeeklyPartId("old"), partType = partType(), sortOrder = 0),
                ),
            ),
            assignments = emptyList(),
        )

        val result = aggregate.replaceParts(
            orderedPartTypes = listOf(partType() to null),
            partIdFactory = { WeeklyPartId("new-id") },
        )

        val updated = assertIs<Either.Right<WeekPlanAggregate>>(result).value
        assertEquals(1, updated.weekPlan.parts.size)
        assertEquals(WeeklyPartId("new-id"), updated.weekPlan.parts.single().id)
    }

    @Test
    fun `replaceParts happy path replaces parts with new IDs and clears assignments`() {
        val futureMonday = LocalDate.of(2026, 3, 9)
        val existingAssignment = Assignment(
            id = AssignmentId("a1"),
            weeklyPartId = WeeklyPartId("old-part"),
            personId = ProclamatoreId("p1"),
            slot = 1,
        )
        val aggregate = WeekPlanAggregate(
            weekPlan = WeekPlan(
                id = WeekPlanId("w1"),
                weekStartDate = futureMonday,
                parts = listOf(
                    WeeklyPart(id = WeeklyPartId("old-part"), partType = partType(id = "a"), sortOrder = 0),
                ),
            ),
            assignments = listOf(existingAssignment),
        )

        var counter = 0
        val newPartTypeA = partType(id = "x")
        val newPartTypeB = partType(id = "y")
        val result = aggregate.replaceParts(
            orderedPartTypes = listOf(newPartTypeA to "rev-1", newPartTypeB to null),
            partIdFactory = { WeeklyPartId("gen-${counter++}") },
        )

        val updated = assertIs<Either.Right<WeekPlanAggregate>>(result).value
        assertEquals(2, updated.weekPlan.parts.size)
        assertEquals(WeeklyPartId("gen-0"), updated.weekPlan.parts[0].id)
        assertEquals(WeeklyPartId("gen-1"), updated.weekPlan.parts[1].id)
        assertEquals(0, updated.weekPlan.parts[0].sortOrder)
        assertEquals(1, updated.weekPlan.parts[1].sortOrder)
        assertEquals("rev-1", updated.weekPlan.parts[0].partTypeRevisionId)
        assertEquals(null, updated.weekPlan.parts[1].partTypeRevisionId)
        assertEquals(emptyList(), updated.assignments)
    }

    @Test
    fun `replaceParts with empty list returns OrdinePartiNonValido`() {
        val futureMonday = LocalDate.of(2026, 3, 9)
        val aggregate = WeekPlanAggregate(
            weekPlan = WeekPlan(
                id = WeekPlanId("w1"),
                weekStartDate = futureMonday,
                parts = listOf(
                    WeeklyPart(id = WeeklyPartId("old"), partType = partType(), sortOrder = 0),
                ),
            ),
            assignments = emptyList(),
        )

        val result = aggregate.replaceParts(
            orderedPartTypes = emptyList(),
            partIdFactory = { WeeklyPartId("unused") },
        )

        val left = assertIs<Either.Left<DomainError>>(result).value
        assertEquals(DomainError.OrdinePartiNonValido, left)
    }

    // --- WeekPlan.of() smart constructor error path tests ---

    @Test
    fun `WeekPlan of returns Left when date is not a Monday`() {
        val result = WeekPlan.of(
            id = WeekPlanId("w1"),
            weekStartDate = LocalDate.of(2026, 3, 3), // Tuesday
        )

        assertIs<Either.Left<DomainError>>(result)
        assertIs<DomainError.DataSettimanaNonLunedi>(result.value)
        Unit
    }

    @Test
    fun `WeekPlan of returns Left when id is blank`() {
        val result = WeekPlan.of(
            id = WeekPlanId(""),
            weekStartDate = LocalDate.of(2026, 3, 2), // Monday
        )

        assertIs<Either.Left<DomainError>>(result)
        assertIs<DomainError.Validation>(result.value)
        Unit
    }

    @Test
    fun `WeekPlan of returns Right for valid inputs`() {
        val monday = LocalDate.of(2026, 3, 2)
        val result = WeekPlan.of(
            id = WeekPlanId("w1"),
            weekStartDate = monday,
        )

        val weekPlan = assertIs<Either.Right<WeekPlan>>(result).value
        assertEquals(WeekPlanId("w1"), weekPlan.id)
        assertEquals(monday, weekPlan.weekStartDate)
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

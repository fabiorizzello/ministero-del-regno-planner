package org.example.project.feature.output.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.assignments.domain.AssignmentWithPerson
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.domain.Sesso
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.SexRule
import org.example.project.feature.weeklyparts.domain.WeeklyPart
import org.example.project.feature.weeklyparts.domain.WeeklyPartId

class PartCompletenessTest {

    @Test
    fun `part with enough assignments is complete`() {
        val part = weeklyPart("p1", peopleCount = 2)
        val assignments = listOf(
            assignment("a1", part.id, slot = 1),
            assignment("a2", part.id, slot = 2),
        )

        val result = completePartIds(listOf(part), assignments)

        assertEquals(setOf(part.id), result)
    }

    @Test
    fun `part with fewer assignments than needed is not complete`() {
        val part = weeklyPart("p1", peopleCount = 2)
        val assignments = listOf(
            assignment("a1", part.id, slot = 1),
        )

        val result = completePartIds(listOf(part), assignments)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `no assignments yields empty set`() {
        val part = weeklyPart("p1", peopleCount = 1)

        val result = completePartIds(listOf(part), emptyList())

        assertTrue(result.isEmpty())
    }

    @Test
    fun `empty parts list yields empty set`() {
        val assignments = listOf(
            assignment("a1", WeeklyPartId("orphan"), slot = 1),
        )

        val result = completePartIds(emptyList(), assignments)

        assertTrue(result.isEmpty())
    }

    private fun weeklyPart(id: String, peopleCount: Int) = WeeklyPart(
        id = WeeklyPartId(id),
        partType = PartType(
            id = PartTypeId("pt-$id"),
            code = "PT-$id",
            label = "Part $id",
            peopleCount = peopleCount,
            sexRule = SexRule.STESSO_SESSO,
            fixed = false,
            sortOrder = 0,
        ),
        sortOrder = 0,
    )

    private fun assignment(
        assignmentId: String,
        partId: WeeklyPartId,
        slot: Int,
    ) = AssignmentWithPerson(
        id = AssignmentId(assignmentId),
        weeklyPartId = partId,
        personId = ProclamatoreId("person-$assignmentId"),
        slot = slot,
        proclamatore = Proclamatore(
            id = ProclamatoreId("person-$assignmentId"),
            nome = "Nome",
            cognome = "Cognome",
            sesso = Sesso.M,
        ),
    )
}

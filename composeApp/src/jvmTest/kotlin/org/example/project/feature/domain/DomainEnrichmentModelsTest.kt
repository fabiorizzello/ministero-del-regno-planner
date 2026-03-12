package org.example.project.feature.domain

import org.example.project.feature.assignments.domain.Assignment
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.assignments.domain.AssignmentWithPerson
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.domain.Sesso
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.SexRule
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeeklyPart
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import org.example.project.feature.weeklyparts.domain.allowsCandidate
import org.example.project.feature.weeklyparts.domain.isMismatch
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DomainEnrichmentModelsTest {

    @Test
    fun `week plan exposes pure helpers for sorting and lookup`() {
        val partType = partType()
        val partA = WeeklyPart(
            id = WeeklyPartId("part-a"),
            partType = partType,
            sortOrder = 7,
        )
        val partB = WeeklyPart(
            id = WeeklyPartId("part-b"),
            partType = partType,
            sortOrder = 2,
        )
        val week = WeekPlan(
            id = WeekPlanId("week-1"),
            weekStartDate = LocalDate.of(2026, 3, 2),
            parts = listOf(partA, partB),
        )

        assertEquals(8, week.nextSortOrder())
        assertEquals(
            listOf(
                WeeklyPartId("part-a") to 0,
                WeeklyPartId("part-b") to 1,
            ),
            week.recompactedSortOrders(),
        )
        assertEquals(partB, week.findPart(WeeklyPartId("part-b")))
        assertNull(week.findPart(WeeklyPartId("missing")))
    }

    @Test
    fun `part type validates slot boundaries`() {
        val partType = partType(peopleCount = 3)

        assertFalse(partType.isValidSlot(0))
        assertTrue(partType.isValidSlot(1))
        assertTrue(partType.isValidSlot(3))
        assertFalse(partType.isValidSlot(4))
    }

    @Test
    fun `sex rule exposes candidate and mismatch logic`() {
        assertTrue(SexRule.UOMO.allowsCandidate(Sesso.M))
        assertFalse(SexRule.UOMO.allowsCandidate(Sesso.F))

        assertTrue(SexRule.STESSO_SESSO.allowsCandidate(Sesso.M))
        assertTrue(SexRule.STESSO_SESSO.allowsCandidate(Sesso.F))

        assertFalse(SexRule.STESSO_SESSO.isMismatch(candidateSex = Sesso.M, requiredSex = null))
        assertFalse(SexRule.STESSO_SESSO.isMismatch(candidateSex = Sesso.M, requiredSex = Sesso.M))
        assertTrue(SexRule.STESSO_SESSO.isMismatch(candidateSex = Sesso.F, requiredSex = Sesso.M))
        assertFalse(SexRule.UOMO.isMismatch(candidateSex = Sesso.F, requiredSex = Sesso.M))
    }

    @Test
    fun `assignment and person expose role label and full name`() {
        val person = Proclamatore(
            id = ProclamatoreId("p1"),
            nome = "Mario",
            cognome = "Rossi",
            sesso = Sesso.M,
        )
        val assignment = Assignment(
            id = AssignmentId("a1"),
            weeklyPartId = WeeklyPartId("part-1"),
            personId = person.id,
            slot = 2,
        )
        val assignmentWithPerson = AssignmentWithPerson(
            id = AssignmentId("a1"),
            weeklyPartId = WeeklyPartId("part-1"),
            personId = person.id,
            slot = 1,
            proclamatore = person,
        )

        assertEquals("Assistente", assignment.roleLabel)
        assertEquals("Mario Rossi", person.fullName)
        assertEquals(person.fullName, assignmentWithPerson.fullName)
    }

    private fun partType(peopleCount: Int = 2): PartType = PartType(
        id = PartTypeId("pt-1"),
        code = "PT",
        label = "Parte",
        peopleCount = peopleCount,
        sexRule = SexRule.STESSO_SESSO,
        fixed = false,
        sortOrder = 0,
    )
}

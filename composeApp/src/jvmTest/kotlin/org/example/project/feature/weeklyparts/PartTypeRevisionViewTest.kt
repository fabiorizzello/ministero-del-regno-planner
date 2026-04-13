package org.example.project.feature.weeklyparts

import org.example.project.feature.weeklyparts.domain.PartTypeFieldDelta
import org.example.project.feature.weeklyparts.domain.PartTypeSnapshot
import org.example.project.feature.weeklyparts.domain.SexRule
import org.example.project.feature.weeklyparts.domain.computePartTypeDelta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PartTypeRevisionViewTest {

    private fun baseSnapshot() = PartTypeSnapshot(
        label = "Lettura",
        peopleCount = 1,
        sexRule = SexRule.STESSO_SESSO,
        fixed = false,
    )

    @Test
    fun `delta vuoto quando previous e null`() {
        val delta = computePartTypeDelta(previous = null, current = baseSnapshot())
        assertTrue(delta.isEmpty())
    }

    @Test
    fun `delta vuoto quando snapshot identici`() {
        val delta = computePartTypeDelta(previous = baseSnapshot(), current = baseSnapshot())
        assertTrue(delta.isEmpty())
    }

    @Test
    fun `delta label catturato`() {
        val delta = computePartTypeDelta(
            previous = baseSnapshot(),
            current = baseSnapshot().copy(label = "Lettura della Bibbia"),
        )
        assertEquals(1, delta.size)
        val change = assertIs<PartTypeFieldDelta.Label>(delta.first())
        assertEquals("Lettura", change.from)
        assertEquals("Lettura della Bibbia", change.to)
    }

    @Test
    fun `delta peopleCount catturato`() {
        val delta = computePartTypeDelta(
            previous = baseSnapshot(),
            current = baseSnapshot().copy(peopleCount = 3),
        )
        val change = assertIs<PartTypeFieldDelta.PeopleCount>(delta.single())
        assertEquals(1, change.from)
        assertEquals(3, change.to)
    }

    @Test
    fun `delta sexRule catturato`() {
        val delta = computePartTypeDelta(
            previous = baseSnapshot(),
            current = baseSnapshot().copy(sexRule = SexRule.UOMO),
        )
        val change = assertIs<PartTypeFieldDelta.Sex>(delta.single())
        assertEquals(SexRule.STESSO_SESSO, change.from)
        assertEquals(SexRule.UOMO, change.to)
    }

    @Test
    fun `delta fixed catturato`() {
        val delta = computePartTypeDelta(
            previous = baseSnapshot(),
            current = baseSnapshot().copy(fixed = true),
        )
        val change = assertIs<PartTypeFieldDelta.Fixed>(delta.single())
        assertEquals(false, change.from)
        assertEquals(true, change.to)
    }

    @Test
    fun `delta multi-campo cattura tutti i cambiamenti nell'ordine canonico`() {
        val delta = computePartTypeDelta(
            previous = baseSnapshot(),
            current = baseSnapshot().copy(
                label = "X",
                peopleCount = 2,
                sexRule = SexRule.UOMO,
                fixed = true,
            ),
        )
        assertEquals(4, delta.size)
        assertIs<PartTypeFieldDelta.Label>(delta[0])
        assertIs<PartTypeFieldDelta.PeopleCount>(delta[1])
        assertIs<PartTypeFieldDelta.Sex>(delta[2])
        assertIs<PartTypeFieldDelta.Fixed>(delta[3])
    }
}

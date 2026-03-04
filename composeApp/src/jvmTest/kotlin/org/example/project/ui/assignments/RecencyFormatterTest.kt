package org.example.project.ui.assignments

import kotlin.test.Test
import kotlin.test.assertEquals

class RecencyFormatterTest {

    @Test
    fun `returns never for null recency`() {
        assertEquals("Mai assegnato", formatRecencyLabel(days = null, weeks = null, inFuture = false))
    }

    @Test
    fun `returns today for zero days`() {
        assertEquals("Oggi", formatRecencyLabel(days = 0, weeks = 0, inFuture = false))
    }

    @Test
    fun `uses past wording for recent past days`() {
        assertEquals("7 giorni fa", formatRecencyLabel(days = 7, weeks = 1, inFuture = false))
    }

    @Test
    fun `uses past wording for past weeks`() {
        assertEquals("3 settimane fa", formatRecencyLabel(days = 21, weeks = 3, inFuture = false))
    }

    @Test
    fun `uses future wording for near future days`() {
        assertEquals("Tra 7 giorni", formatRecencyLabel(days = 7, weeks = 1, inFuture = true))
    }

    @Test
    fun `uses future wording for future weeks`() {
        assertEquals("Tra 3 settimane", formatRecencyLabel(days = 21, weeks = 3, inFuture = true))
    }
}

package org.example.project.ui.assignments

import kotlin.test.Test
import kotlin.test.assertEquals

class RecencyFormatterTest {

    @Test
    fun `returns never when both before and after are missing`() {
        assertEquals("Mai assegnato", formatRecencyLabel(beforeWeeks = null, afterWeeks = null))
    }

    @Test
    fun `formats before and after with compact weeks wording`() {
        assertEquals("5 sett prima - 2 sett dopo", formatRecencyLabel(beforeWeeks = 5, afterWeeks = 2))
    }

    @Test
    fun `formats missing after as dash`() {
        assertEquals("5 sett prima - n/d dopo", formatRecencyLabel(beforeWeeks = 5, afterWeeks = null))
    }

    @Test
    fun `formats missing before as dash`() {
        assertEquals("n/d prima - 2 sett dopo", formatRecencyLabel(beforeWeeks = null, afterWeeks = 2))
    }
}

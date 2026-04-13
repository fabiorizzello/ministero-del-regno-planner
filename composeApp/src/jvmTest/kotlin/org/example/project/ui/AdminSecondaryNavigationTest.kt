package org.example.project.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdminSecondaryNavigationTest {

    @Test
    fun `visiblePrimaryNavigationSections keeps only top level operational tabs`() {
        val visible = visiblePrimaryNavigationSections(AppSection.entries.toList())

        assertEquals(listOf(AppSection.PLANNING, AppSection.PROCLAMATORI, AppSection.EQUITA), visible)
        assertTrue(AppSection.DIAGNOSTICS !in visible)
    }
}

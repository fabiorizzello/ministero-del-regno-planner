package org.example.project.ui.proclamatori

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class ProclamatoriScreenTest {

    @Test
    fun `studentiEmptyStateLabel usa copy esplicita per ricerca senza risultati`() {
        assertEquals("Nessun risultato per questa ricerca", studentiEmptyStateLabel(hasActiveSearch = true))
    }

    @Test
    fun `studentiEmptyStateLabel usa copy esplicita quando la lista e' vuota`() {
        assertEquals("Nessuno studente disponibile", studentiEmptyStateLabel(hasActiveSearch = false))
    }

    @Test
    fun `studentCardActionBarMinHeight resta costante per la vista card`() {
        assertEquals(36.dp, studentCardActionBarMinHeight())
    }
}

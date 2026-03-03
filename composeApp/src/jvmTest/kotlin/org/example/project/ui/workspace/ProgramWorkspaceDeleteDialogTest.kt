package org.example.project.ui.workspace

import kotlin.test.Test
import kotlin.test.assertEquals

class ProgramWorkspaceDeleteDialogTest {

    @Test
    fun `delete confirmation message includes month and impact counts`() {
        val message = buildDeleteProgramImpactMessage(
            DeleteProgramImpact(
                year = 2026,
                month = 3,
                weeksCount = 4,
                assignmentsCount = 12,
            ),
        )

        assertEquals(
            "Confermi eliminazione del mese marzo 2026? Verranno rimosse 4 settimane e 12 assegnazioni.",
            message,
        )
    }
}

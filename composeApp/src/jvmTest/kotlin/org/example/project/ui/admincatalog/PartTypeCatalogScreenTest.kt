package org.example.project.ui.admincatalog

import org.example.project.feature.weeklyparts.domain.PartTypeId
import kotlin.test.Test
import kotlin.test.assertEquals

class PartTypeCatalogScreenTest {

    @Test
    fun `partTypeStatusLabel returns italian labels`() {
        assertEquals("Attivo", partTypeStatusLabel(true))
        assertEquals("Disattivo", partTypeStatusLabel(false))
    }

    @Test
    fun `partTypeDetailRows expose all read only fields`() {
        val rows = partTypeDetailRows(
            PartTypeCatalogDetail(
                id = PartTypeId("LB"),
                code = "LB",
                label = "Lettura Bibbia",
                peopleCount = 2,
                sexRuleLabel = "Stesso sesso",
                fixedLabel = "Parte ordinaria",
                activeLabel = "Attivo",
                readonlyNotice = ADMIN_READONLY_HINT,
            ),
        )

        assertEquals(
            listOf("Codice", "Nome", "Persone richieste", "Composizione", "Tipo", "Stato"),
            rows.map { it.first },
        )
        assertEquals("LB", rows.first().second)
        assertEquals("Attivo", rows.last().second)
    }
}

package org.example.project.core.cli

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GenerateWolEfficaciCatalogBuildTest {
    @Test
    fun `build catalog always adds lettura bibbia point 3 with required part type metadata`() {
        val catalog = buildCatalog(
            scrapedWeeks = listOf(
                ScrapedWeek(
                    weekStartDate = LocalDate.parse("2026-03-02"),
                    meetingsUrl = "https://example.com/meetings",
                    vitaMinisteroUrl = "https://example.com/article",
                    efficaciParts = listOf(
                        EfficaciPart(number = 4, title = "Iniziare una conversazione"),
                        EfficaciPart(number = 5, title = "Coltivare l'interesse"),
                    ),
                ),
            ),
            basePartTypes = emptyList(),
        )

        val weekCodes = catalog.weeks.first().parts.map { it.partTypeCode }
        assertEquals(
            listOf("LETTURA_DELLA_BIBBIA", "INIZIARE_UNA_CONVERSAZIONE", "COLTIVARE_L_INTERESSE"),
            weekCodes,
        )

        val lettura = catalog.partTypes.firstOrNull { it.code == "LETTURA_DELLA_BIBBIA" }
        assertNotNull(lettura)
        assertEquals("Lettura della Bibbia", lettura.label)
        assertEquals(1, lettura.peopleCount)
        assertEquals("UOMO", lettura.sexRule)
        assertEquals(true, lettura.fixed)

        val iniziare = catalog.partTypes.firstOrNull { it.code == "INIZIARE_UNA_CONVERSAZIONE" }
        assertNotNull(iniziare)
        assertEquals("STESSO_SESSO", iniziare.sexRule)
    }

    @Test
    fun `build catalog normalizes existing lettura biblica title and avoids duplicates`() {
        val catalog = buildCatalog(
            scrapedWeeks = listOf(
                ScrapedWeek(
                    weekStartDate = LocalDate.parse("2026-03-09"),
                    meetingsUrl = "https://example.com/meetings",
                    vitaMinisteroUrl = "https://example.com/article",
                    efficaciParts = listOf(
                        EfficaciPart(number = 3, title = "Lettura biblica"),
                        EfficaciPart(number = 4, title = "Iniziare una conversazione"),
                    ),
                ),
            ),
            basePartTypes = listOf(
                OutputPartType(
                    code = "LETTURA_DELLA_BIBBIA",
                    label = "Lettura biblica",
                    peopleCount = 2,
                    sexRule = "STESSO_SESSO",
                    fixed = true,
                ),
            ),
        )

        val weekCodes = catalog.weeks.first().parts.map { it.partTypeCode }
        assertEquals(listOf("LETTURA_DELLA_BIBBIA", "INIZIARE_UNA_CONVERSAZIONE"), weekCodes)
        assertEquals(1, weekCodes.count { it == "LETTURA_DELLA_BIBBIA" })

        val lettura = catalog.partTypes.firstOrNull { it.code == "LETTURA_DELLA_BIBBIA" }
        assertNotNull(lettura)
        assertEquals("Lettura della Bibbia", lettura.label)
        assertEquals(1, lettura.peopleCount)
        assertEquals("UOMO", lettura.sexRule)
        assertEquals(true, lettura.fixed)
    }

    @Test
    fun `build catalog forces discorso and lettura to uomo and keeps only allowed sex rules`() {
        val catalog = buildCatalog(
            scrapedWeeks = listOf(
                ScrapedWeek(
                    weekStartDate = LocalDate.parse("2026-04-20"),
                    meetingsUrl = "https://example.com/meetings",
                    vitaMinisteroUrl = "https://example.com/article",
                    efficaciParts = listOf(
                        EfficaciPart(number = 4, title = "Iniziare una conversazione"),
                        EfficaciPart(number = 5, title = "Discorso"),
                    ),
                ),
            ),
            basePartTypes = listOf(
                OutputPartType(
                    code = "DISCORSO",
                    label = "Discorso",
                    peopleCount = 2,
                    sexRule = "STESSO_SESSO",
                    fixed = false,
                ),
            ),
        )

        val discorso = catalog.partTypes.firstOrNull { it.code == "DISCORSO" }
        assertNotNull(discorso)
        assertEquals(1, discorso.peopleCount)
        assertEquals("UOMO", discorso.sexRule)

        val lettura = catalog.partTypes.firstOrNull { it.code == "LETTURA_DELLA_BIBBIA" }
        assertNotNull(lettura)
        assertEquals(1, lettura.peopleCount)
        assertEquals("UOMO", lettura.sexRule)
        assertEquals(true, lettura.fixed)

        assertTrue(catalog.partTypes.all { it.sexRule == "UOMO" || it.sexRule == "STESSO_SESSO" })
        assertTrue(catalog.partTypes.any { it.sexRule == "STESSO_SESSO" })
    }
}

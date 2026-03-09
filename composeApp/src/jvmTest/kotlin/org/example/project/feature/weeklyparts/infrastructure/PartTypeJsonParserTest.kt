package org.example.project.feature.weeklyparts.infrastructure

import arrow.core.Either
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.example.project.core.domain.DomainError
import org.example.project.feature.weeklyparts.domain.PartType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PartTypeJsonParserTest {

    @Test
    fun `parsePartTypeFromJson returns ImportContenutoNonValido when code is missing`() {
        val obj = Json.parseToJsonElement(
            """
            {
              "label": "Lettura",
              "peopleCount": 1,
              "sexRule": "STESSO_SESSO"
            }
            """.trimIndent(),
        ).jsonObject

        val result = parsePartTypeFromJson(obj, 0)

        val left = assertIs<Either.Left<DomainError.ImportContenutoNonValido>>(result).value
        assertTrue(left.details.contains("campo 'code' mancante"))
    }

    @Test
    fun `parsePartTypeFromJson parses valid payload`() {
        val obj = Json.parseToJsonElement(
            """
            {
              "code": "LETTURA",
              "label": "Lettura",
              "peopleCount": 1,
              "sexRule": "STESSO_SESSO",
              "fixed": true
            }
            """.trimIndent(),
        ).jsonObject

        val result = parsePartTypeFromJson(obj, 3)

        val right = assertIs<Either.Right<PartType>>(result).value
        assertEquals("LETTURA", right.code)
        assertEquals(1, right.peopleCount)
        assertEquals(true, right.fixed)
        assertEquals(3, right.sortOrder)
    }
}

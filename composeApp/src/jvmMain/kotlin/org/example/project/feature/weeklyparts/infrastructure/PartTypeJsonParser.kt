package org.example.project.feature.weeklyparts.infrastructure

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import org.example.project.core.domain.DomainError
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.SexRule
import org.slf4j.LoggerFactory
import java.util.UUID

private val logger = LoggerFactory.getLogger("PartTypeJsonParser")

internal fun parseSexRule(value: String): SexRule =
    SexRule.entries.find { it.name == value }
        ?: run {
            logger.warn("SexRule sconosciuto '{}' -> fallback a STESSO_SESSO", value)
            SexRule.STESSO_SESSO
        }

/**
 * Parses a single [PartType] from a JSON object.
 * Shared with the CLI seed tool.
 */
internal fun parsePartTypeFromJson(
    obj: JsonObject,
    index: Int,
): Either<DomainError.ImportContenutoNonValido, PartType> = either {
    val code = ensureNotNull(obj["code"]?.jsonPrimitive?.content) {
        DomainError.ImportContenutoNonValido("partTypes[$index]: campo 'code' mancante")
    }
    val label = ensureNotNull(obj["label"]?.jsonPrimitive?.content) {
        DomainError.ImportContenutoNonValido("partTypes[$index]: campo 'label' mancante")
    }
    val peopleCountRaw = ensureNotNull(obj["peopleCount"]?.jsonPrimitive?.content) {
        DomainError.ImportContenutoNonValido("partTypes[$index]: campo 'peopleCount' mancante")
    }
    val peopleCount = ensureNotNull(peopleCountRaw.toIntOrNull()) {
        DomainError.ImportContenutoNonValido("partTypes[$index]: campo 'peopleCount' non valido")
    }
    val sexRuleStr = ensureNotNull(obj["sexRule"]?.jsonPrimitive?.content) {
        DomainError.ImportContenutoNonValido("partTypes[$index]: campo 'sexRule' mancante")
    }
    PartType(
        id = PartTypeId(UUID.randomUUID().toString()),
        code = code,
        label = label,
        peopleCount = peopleCount,
        sexRule = parseSexRule(sexRuleStr),
        fixed = obj["fixed"]?.jsonPrimitive?.boolean ?: false,
        sortOrder = index,
    )
}

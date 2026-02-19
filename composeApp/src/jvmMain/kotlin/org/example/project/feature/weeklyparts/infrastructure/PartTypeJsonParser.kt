package org.example.project.feature.weeklyparts.infrastructure

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.SexRule
import java.util.UUID

/**
 * Parses a single [PartType] from a JSON object.
 * Shared between [GitHubDataSource] and the CLI seed tool.
 */
internal fun parsePartTypeFromJson(obj: JsonObject, index: Int): PartType {
    val code = obj["code"]?.jsonPrimitive?.content
        ?: error("partTypes[$index]: campo 'code' mancante")
    val label = obj["label"]?.jsonPrimitive?.content
        ?: error("partTypes[$index]: campo 'label' mancante")
    val peopleCount = obj["peopleCount"]?.jsonPrimitive?.int
        ?: error("partTypes[$index]: campo 'peopleCount' mancante")
    val sexRuleStr = obj["sexRule"]?.jsonPrimitive?.content
        ?: error("partTypes[$index]: campo 'sexRule' mancante")
    return PartType(
        id = PartTypeId(UUID.randomUUID().toString()),
        code = code,
        label = label,
        peopleCount = peopleCount,
        sexRule = SexRule.valueOf(sexRuleStr),
        fixed = obj["fixed"]?.jsonPrimitive?.boolean ?: false,
        sortOrder = index,
    )
}

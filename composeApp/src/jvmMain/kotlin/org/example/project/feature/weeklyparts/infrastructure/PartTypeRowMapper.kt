package org.example.project.feature.weeklyparts.infrastructure

import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.SexRule

internal fun mapPartTypeRow(
    id: String,
    code: String,
    label: String,
    people_count: Long,
    sex_rule: String,
    fixed: Long,
    sort_order: Long,
): PartType {
    return PartType(
        id = PartTypeId(id),
        code = code,
        label = label,
        peopleCount = people_count.toInt(),
        sexRule = runCatching { SexRule.valueOf(sex_rule) }.getOrDefault(SexRule.LIBERO),
        fixed = fixed == 1L,
        sortOrder = sort_order.toInt(),
    )
}

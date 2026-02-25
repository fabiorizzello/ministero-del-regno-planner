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

internal fun mapPartTypeExtendedRow(
    id: String,
    code: String,
    label: String,
    people_count: Long,
    sex_rule: String,
    fixed: Long,
    active: Long,
    sort_order: Long,
    current_revision_id: String?,
): PartTypeExtendedRecord {
    return PartTypeExtendedRecord(
        partType = mapPartTypeRow(
            id = id,
            code = code,
            label = label,
            people_count = people_count,
            sex_rule = sex_rule,
            fixed = fixed,
            sort_order = sort_order,
        ),
        active = active == 1L,
    )
}

internal data class PartTypeExtendedRecord(
    val partType: PartType,
    val active: Boolean,
)

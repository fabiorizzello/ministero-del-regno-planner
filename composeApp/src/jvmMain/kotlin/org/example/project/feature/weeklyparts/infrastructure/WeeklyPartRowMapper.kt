package org.example.project.feature.weeklyparts.infrastructure

import org.example.project.feature.weeklyparts.domain.WeeklyPart
import org.example.project.feature.weeklyparts.domain.WeeklyPartId

internal fun mapWeeklyPartWithTypeRow(
    id: String,
    _weekPlanId: String,
    part_type_id: String,
    sort_order: Long,
    part_type_code: String,
    part_type_label: String,
    part_type_people_count: Long,
    part_type_sex_rule: String,
    part_type_fixed: Long,
    part_type_sort_order: Long,
): WeeklyPart {
    return WeeklyPart(
        id = WeeklyPartId(id),
        partType = mapPartTypeRow(
            id = part_type_id,
            code = part_type_code,
            label = part_type_label,
            people_count = part_type_people_count,
            sex_rule = part_type_sex_rule,
            fixed = part_type_fixed,
            sort_order = part_type_sort_order,
        ),
        sortOrder = sort_order.toInt(),
    )
}

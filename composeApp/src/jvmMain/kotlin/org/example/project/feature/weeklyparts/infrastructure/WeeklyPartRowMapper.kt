package org.example.project.feature.weeklyparts.infrastructure

import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.SexRule
import org.example.project.feature.weeklyparts.domain.WeeklyPart
import org.example.project.feature.weeklyparts.domain.WeeklyPartId

internal fun mapWeeklyPartWithTypeRow(
    id: String,
    week_plan_id: String,
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
        partType = PartType(
            id = PartTypeId(part_type_id),
            code = part_type_code,
            label = part_type_label,
            peopleCount = part_type_people_count.toInt(),
            sexRule = SexRule.valueOf(part_type_sex_rule),
            fixed = part_type_fixed == 1L,
            sortOrder = part_type_sort_order.toInt(),
        ),
        sortOrder = sort_order.toInt(),
    )
}

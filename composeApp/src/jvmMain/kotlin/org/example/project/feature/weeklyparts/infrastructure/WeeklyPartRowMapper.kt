package org.example.project.feature.weeklyparts.infrastructure

import org.example.project.feature.weeklyparts.domain.PartTypeSnapshot
import org.example.project.feature.weeklyparts.domain.WeeklyPart
import org.example.project.feature.weeklyparts.domain.WeeklyPartId

internal fun mapWeeklyPartWithTypeRow(
    id: String,
    _weekPlanId: String,
    part_type_id: String,
    part_type_revision_id: String?,
    sort_order: Long,
    part_type_code: String,
    part_type_label: String,
    part_type_people_count: Long,
    part_type_sex_rule: String,
    part_type_fixed: Long,
    part_type_sort_order: Long,
    snapshot_label: String?,
    snapshot_people_count: Long?,
    snapshot_sex_rule: String?,
    snapshot_fixed: Long?,
): WeeklyPart {
    val snapshot = if (snapshot_label != null && snapshot_people_count != null && snapshot_sex_rule != null && snapshot_fixed != null) {
        PartTypeSnapshot(
            label = snapshot_label,
            peopleCount = snapshot_people_count.toInt(),
            sexRule = parseSexRule(snapshot_sex_rule),
            fixed = snapshot_fixed == 1L,
        )
    } else null

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
        snapshot = snapshot,
        partTypeRevisionId = part_type_revision_id,
        sortOrder = sort_order.toInt(),
    )
}

package org.example.project.feature.weeklyparts.domain

@JvmInline
value class WeeklyPartId(val value: String)

data class WeeklyPart(
    val id: WeeklyPartId,
    val partType: PartType,
    val sortOrder: Int,
)

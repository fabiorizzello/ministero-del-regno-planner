package org.example.project.feature.weeklyparts.domain

@JvmInline
value class WeeklyPartId(val value: String)

enum class WeeklyPartSexRule {
    UOMO,
    LIBERO,
}

data class WeeklyPart(
    val id: WeeklyPartId,
    val weekStartDate: String,
    val title: String,
    val numberOfPeople: Int,
    val sexRule: WeeklyPartSexRule,
    val order: Int,
)

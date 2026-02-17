package org.example.project.feature.weeklyparts.domain

@JvmInline
value class PartTypeId(val value: String)

data class PartType(
    val id: PartTypeId,
    val code: String,
    val label: String,
    val peopleCount: Int,
    val sexRule: SexRule,
    val fixed: Boolean,
    val sortOrder: Int,
)

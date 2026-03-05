package org.example.project.feature.weeklyparts.domain

data class PartTypeSnapshot(
    val label: String,
    val peopleCount: Int,
    val sexRule: SexRule,
    val fixed: Boolean,
)

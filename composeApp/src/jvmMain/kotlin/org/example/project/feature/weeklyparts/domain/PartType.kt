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
) {
    init {
        require(code.isNotBlank()) { "code non può essere vuoto" }
        require(label.isNotBlank()) { "label non può essere vuoto" }
        require(peopleCount >= 1) { "peopleCount deve essere >= 1, ricevuto: $peopleCount" }
    }
}

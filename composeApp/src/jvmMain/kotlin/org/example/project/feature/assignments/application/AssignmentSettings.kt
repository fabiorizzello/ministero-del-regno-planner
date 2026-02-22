package org.example.project.feature.assignments.application

data class AssignmentSettings(
    val strictCooldown: Boolean = true,
    val leadWeight: Int = 2,
    val assistWeight: Int = 1,
    val leadCooldownWeeks: Int = 4,
    val assistCooldownWeeks: Int = 2,
) {
    fun normalized(): AssignmentSettings {
        return copy(
            leadWeight = leadWeight.coerceAtLeast(1),
            assistWeight = assistWeight.coerceAtLeast(1),
            leadCooldownWeeks = leadCooldownWeeks.coerceAtLeast(0),
            assistCooldownWeeks = assistCooldownWeeks.coerceAtLeast(0),
        )
    }
}

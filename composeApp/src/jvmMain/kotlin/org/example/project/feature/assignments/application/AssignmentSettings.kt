package org.example.project.feature.assignments.application

/** Penalità di punteggio applicata a chi è in cooldown (spec 005, US2, scenario 3). */
const val COOLDOWN_PENALTY = 10_000

data class AssignmentSettings(
    val strictCooldown: Boolean = true,
    val leadCooldownWeeks: Int = 4,
    val assistCooldownWeeks: Int = 2,
) {
    fun normalized(): AssignmentSettings {
        return copy(
            leadCooldownWeeks = leadCooldownWeeks.coerceAtLeast(0),
            assistCooldownWeeks = assistCooldownWeeks.coerceAtLeast(0),
        )
    }
}

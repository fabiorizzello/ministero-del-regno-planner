package org.example.project.feature.assignments.application

/** Penalità di punteggio applicata a chi è in cooldown (spec 005, US2, scenario 3). */
const val COOLDOWN_PENALTY = 10_000

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

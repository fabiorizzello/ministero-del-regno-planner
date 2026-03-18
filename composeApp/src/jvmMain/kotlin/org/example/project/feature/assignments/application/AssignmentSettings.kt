package org.example.project.feature.assignments.application

/** Penalità di punteggio applicata a chi è in cooldown (spec 005, US2, scenario 3). */
const val COOLDOWN_PENALTY = 10_000

/** Peso penalità per ogni assegnazione nella finestra temporale. */
const val COUNT_PENALTY_WEIGHT = 1

/** Finestra temporale (settimane all'indietro) per il conteggio assegnazioni. */
const val COUNT_WINDOW_WEEKS = 26

/**
 * Finestra temporale (settimane all'indietro dalla prima data di riferimento) per la
 * query di ranking. Deve essere >= COUNT_WINDOW_WEEKS per coprire il conteggio, più un
 * margine ampio per differenziare i candidati nel ranking. 52 settimane (1 anno) copre
 * il count window (26 sett.) più 26 settimane extra di contesto.
 */
const val RANKING_HISTORY_WEEKS = 52L

/** Penalità (settimane equivalenti) quando l'ultimo slot coincide con il target slot. */
const val SLOT_REPEAT_PENALTY = 4

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

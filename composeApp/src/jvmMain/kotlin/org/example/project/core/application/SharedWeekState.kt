package org.example.project.core.application

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

class SharedWeekState {
    private val _currentMonday = MutableStateFlow(currentMonday())

    val currentMonday: StateFlow<LocalDate> = _currentMonday.asStateFlow()

    fun navigateToPreviousWeek() {
        _currentMonday.update { date ->
            val prev = date.minusWeeks(1)
            if (prev.year >= MIN_YEAR) prev else date
        }
    }

    fun navigateToNextWeek() {
        _currentMonday.update { date ->
            val next = date.plusWeeks(1)
            if (next.year <= MAX_YEAR) next else date
        }
    }

    fun navigateToCurrentWeek() {
        _currentMonday.value = currentMonday()
    }

    companion object {
        private const val MIN_YEAR = 2020
        private const val MAX_YEAR = 2099

        /** Returns the Monday of the current week. */
        fun currentMonday(): LocalDate =
            LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
}

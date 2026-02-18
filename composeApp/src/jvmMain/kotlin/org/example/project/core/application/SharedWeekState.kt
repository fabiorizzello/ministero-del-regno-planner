package org.example.project.core.application

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

class SharedWeekState {
    private val _currentMonday = MutableStateFlow(
        LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    )
    val currentMonday: StateFlow<LocalDate> = _currentMonday.asStateFlow()

    fun navigateToPreviousWeek() {
        _currentMonday.value = _currentMonday.value.minusWeeks(1)
    }

    fun navigateToNextWeek() {
        _currentMonday.value = _currentMonday.value.plusWeeks(1)
    }
}

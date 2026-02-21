package org.example.project.ui.planning

import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.core.application.SharedWeekState
import org.example.project.feature.planning.application.CaricaPanoramicaPianificazioneFutura
import org.example.project.feature.planning.application.PlanningWeekStatus
import org.example.project.feature.planning.domain.PlanningAlert
import org.example.project.ui.components.FeedbackBannerKind
import org.example.project.ui.components.FeedbackBannerModel

private const val ALERT_WEEKS = 4

internal enum class PlanningHorizonOption(
    val label: String,
    val weeks: Int,
) {
    ONE_MONTH("1 mese", 4),
    TWO_MONTHS("2 mesi", 8),
    THREE_MONTHS("3 mesi", 12),
}

internal data class PlanningDashboardUiState(
    val currentMonday: LocalDate = SharedWeekState.currentMonday(),
    val horizon: PlanningHorizonOption = PlanningHorizonOption.TWO_MONTHS,
    val isLoading: Boolean = true,
    val weeks: List<PlanningWeekStatus> = emptyList(),
    val plannedThrough: LocalDate? = null,
    val alerts: List<PlanningAlert> = emptyList(),
    val notice: FeedbackBannerModel? = null,
)

internal class PlanningDashboardViewModel(
    private val scope: CoroutineScope,
    private val sharedWeekState: SharedWeekState,
    private val caricaPanoramica: CaricaPanoramicaPianificazioneFutura,
) {
    private val _state = MutableStateFlow(PlanningDashboardUiState())
    val state: StateFlow<PlanningDashboardUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        scope.launch {
            sharedWeekState.currentMonday.collect { monday ->
                _state.update { it.copy(currentMonday = monday) }
                loadOverview()
            }
        }
    }

    fun onScreenEntered() {
        loadOverview()
    }

    fun setHorizon(option: PlanningHorizonOption) {
        if (option == _state.value.horizon) return
        _state.update { it.copy(horizon = option) }
        loadOverview()
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }

    private fun loadOverview() {
        loadJob?.cancel()
        loadJob = scope.launch {
            _state.update { it.copy(isLoading = true) }
            runCatching {
                val snapshot = _state.value
                caricaPanoramica(snapshot.currentMonday, snapshot.horizon.weeks, alertWeeks = ALERT_WEEKS)
            }.onSuccess { overview ->
                val plannedThroughDate = overview.progress.plannedThroughWeekKey?.let { LocalDate.parse(it) }
                _state.update {
                    it.copy(
                        isLoading = false,
                        weeks = overview.weeks,
                        plannedThrough = plannedThroughDate,
                        alerts = overview.alerts.distinctBy { alert -> alert.weekKeys.joinToString("|") },
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        notice = FeedbackBannerModel("Errore nel caricamento cruscotto: ${error.message}", FeedbackBannerKind.ERROR),
                    )
                }
            }
        }
    }
}

package org.example.project.ui.weeklyparts

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.example.project.core.domain.DomainError
import org.example.project.feature.weeklyparts.application.AggiungiParteUseCase
import org.example.project.feature.weeklyparts.application.AggiornaDatiRemotiUseCase
import org.example.project.feature.weeklyparts.application.CaricaSettimanaUseCase
import org.example.project.feature.weeklyparts.application.CercaTipiParteUseCase
import org.example.project.feature.weeklyparts.application.CreaSettimanaUseCase
import org.example.project.feature.weeklyparts.application.RimuoviParteUseCase
import org.example.project.feature.weeklyparts.application.RiordinaPartiUseCase
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import org.example.project.feature.weeklyparts.application.RemoteWeekSchema
import org.example.project.ui.components.FeedbackBannerKind
import org.example.project.ui.components.FeedbackBannerModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class WeekTimeIndicator { PASSATA, CORRENTE, FUTURA }

internal data class WeeklyPartsUiState(
    val currentMonday: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
    val weekPlan: WeekPlan? = null,
    val isLoading: Boolean = true,
    val partTypes: List<PartType> = emptyList(),
    val notice: FeedbackBannerModel? = null,
    val isImporting: Boolean = false,
    val weeksNeedingConfirmation: List<RemoteWeekSchema> = emptyList(),
) {
    val weekIndicator: WeekTimeIndicator
        get() {
            val thisMonday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            return when {
                currentMonday == thisMonday -> WeekTimeIndicator.CORRENTE
                currentMonday.isAfter(thisMonday) -> WeekTimeIndicator.FUTURA
                else -> WeekTimeIndicator.PASSATA
            }
        }

    val sundayDate: LocalDate get() = currentMonday.plusDays(6)
}

internal class WeeklyPartsViewModel(
    private val scope: CoroutineScope,
    private val caricaSettimana: CaricaSettimanaUseCase,
    private val creaSettimana: CreaSettimanaUseCase,
    private val aggiungiParte: AggiungiParteUseCase,
    private val rimuoviParte: RimuoviParteUseCase,
    private val riordinaParti: RiordinaPartiUseCase,
    private val cercaTipiParte: CercaTipiParteUseCase,
    private val aggiornaDatiRemoti: AggiornaDatiRemotiUseCase,
) {
    private val _state = MutableStateFlow(WeeklyPartsUiState())
    val state: StateFlow<WeeklyPartsUiState> = _state.asStateFlow()

    init {
        loadWeek()
        loadPartTypes()
    }

    fun navigateToPreviousWeek() {
        _state.update { it.copy(currentMonday = it.currentMonday.minusWeeks(1)) }
        loadWeek()
    }

    fun navigateToNextWeek() {
        _state.update { it.copy(currentMonday = it.currentMonday.plusWeeks(1)) }
        loadWeek()
    }

    fun createWeek() {
        scope.launch {
            _state.update { it.copy(isLoading = true) }
            creaSettimana(_state.value.currentMonday).fold(
                ifLeft = { error -> showError(error) },
                ifRight = { weekPlan -> _state.update { it.copy(isLoading = false, weekPlan = weekPlan) } },
            )
        }
    }

    fun addPart(partTypeId: PartTypeId) {
        scope.launch {
            aggiungiParte(_state.value.currentMonday, partTypeId).fold(
                ifLeft = { error -> showError(error) },
                ifRight = { weekPlan -> _state.update { it.copy(weekPlan = weekPlan) } },
            )
        }
    }

    fun removePart(weeklyPartId: WeeklyPartId) {
        scope.launch {
            rimuoviParte(_state.value.currentMonday, weeklyPartId).fold(
                ifLeft = { error -> showError(error) },
                ifRight = { weekPlan -> _state.update { it.copy(weekPlan = weekPlan) } },
            )
        }
    }

    fun movePart(fromIndex: Int, toIndex: Int) {
        val currentPlan = _state.value.weekPlan ?: return
        val parts = currentPlan.parts.toMutableList()
        if (fromIndex !in parts.indices || toIndex !in parts.indices) return
        val moved = parts.removeAt(fromIndex)
        parts.add(toIndex, moved)
        val reordered = parts.mapIndexed { i, p -> p.copy(sortOrder = i) }
        _state.update { it.copy(weekPlan = currentPlan.copy(parts = reordered)) }
        scope.launch {
            riordinaParti(reordered.map { it.id })
        }
    }

    fun syncRemoteData() {
        scope.launch {
            _state.update { it.copy(isImporting = true, weeksNeedingConfirmation = emptyList()) }
            aggiornaDatiRemoti.fetchAndImport().fold(
                ifLeft = { error ->
                    _state.update { it.copy(isImporting = false) }
                    showError(error)
                },
                ifRight = { result ->
                    if (result.weeksNeedingConfirmation.isNotEmpty()) {
                        _state.update { it.copy(
                            isImporting = false,
                            weeksNeedingConfirmation = result.weeksNeedingConfirmation,
                        ) }
                    } else {
                        _state.update { it.copy(
                            isImporting = false,
                            notice = FeedbackBannerModel(
                                "Aggiornamento completato: ${result.partTypesImported} tipi, ${result.weeksImported} settimane",
                                FeedbackBannerKind.SUCCESS,
                            ),
                        ) }
                        loadWeek()
                        loadPartTypes()
                    }
                },
            )
        }
    }

    fun confirmOverwrite() {
        val pending = _state.value.weeksNeedingConfirmation
        scope.launch {
            _state.update { it.copy(isImporting = true, weeksNeedingConfirmation = emptyList()) }
            aggiornaDatiRemoti.importSchemas(pending).fold(
                ifLeft = { error ->
                    _state.update { it.copy(isImporting = false) }
                    showError(error)
                },
                ifRight = { count ->
                    _state.update { it.copy(
                        isImporting = false,
                        notice = FeedbackBannerModel(
                            "Sovrascritte $count settimane",
                            FeedbackBannerKind.SUCCESS,
                        ),
                    ) }
                    loadWeek()
                },
            )
        }
    }

    fun dismissConfirmation() {
        _state.update { it.copy(weeksNeedingConfirmation = emptyList()) }
        loadWeek()
        loadPartTypes()
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }

    private fun loadWeek() {
        scope.launch {
            _state.update { it.copy(isLoading = true) }
            val weekPlan = caricaSettimana(_state.value.currentMonday)
            _state.update { it.copy(isLoading = false, weekPlan = weekPlan) }
        }
    }

    private fun loadPartTypes() {
        scope.launch {
            val types = cercaTipiParte()
            _state.update { it.copy(partTypes = types) }
        }
    }

    private fun showError(error: DomainError) {
        val message = when (error) {
            is DomainError.Validation -> error.message
            is DomainError.NotImplemented -> "Non implementato: ${error.area}"
        }
        _state.update { it.copy(
            isLoading = false,
            notice = FeedbackBannerModel(message, FeedbackBannerKind.ERROR),
        ) }
    }
}

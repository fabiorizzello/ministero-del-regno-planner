package org.example.project.ui.weeklyparts

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.example.project.core.application.SharedWeekState
import org.example.project.core.domain.DomainError
import org.example.project.core.domain.toMessage
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
import org.example.project.ui.components.WeekTimeIndicator
import org.example.project.ui.components.computeWeekIndicator
import org.example.project.ui.components.sundayOf
import java.time.LocalDate

internal data class WeeklyPartsUiState(
    val currentMonday: LocalDate = SharedWeekState.currentMonday(),
    val weekPlan: WeekPlan? = null,
    val isLoading: Boolean = true,
    val partTypes: List<PartType> = emptyList(),
    val notice: FeedbackBannerModel? = null,
    val isImporting: Boolean = false,
    val weeksNeedingConfirmation: List<RemoteWeekSchema> = emptyList(),
    val removePartCandidate: WeeklyPartId? = null,
    val partTypesLoadFailed: Boolean = false,
) {
    val weekIndicator: WeekTimeIndicator get() = computeWeekIndicator(currentMonday)

    val sundayDate: LocalDate get() = sundayOf(currentMonday)
}

internal class WeeklyPartsViewModel(
    private val scope: CoroutineScope,
    private val sharedWeekState: SharedWeekState,
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
    private var loadJob: Job? = null

    init {
        scope.launch {
            sharedWeekState.currentMonday.collect { monday ->
                _state.update { it.copy(currentMonday = monday) }
                try {
                    loadWeek()
                } catch (e: Exception) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            notice = FeedbackBannerModel("Errore nel caricamento: ${e.message}", FeedbackBannerKind.ERROR),
                        )
                    }
                }
            }
        }
        loadPartTypes()
    }

    fun navigateToPreviousWeek() {
        sharedWeekState.navigateToPreviousWeek()
    }

    fun navigateToNextWeek() {
        sharedWeekState.navigateToNextWeek()
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

    fun requestRemovePart(weeklyPartId: WeeklyPartId) {
        _state.update { it.copy(removePartCandidate = weeklyPartId) }
    }

    fun confirmRemovePart() {
        val partId = _state.value.removePartCandidate ?: return
        _state.update { it.copy(removePartCandidate = null) }
        scope.launch {
            rimuoviParte(_state.value.currentMonday, partId).fold(
                ifLeft = { error -> showError(error) },
                ifRight = { weekPlan -> _state.update { it.copy(weekPlan = weekPlan) } },
            )
        }
    }

    fun dismissRemovePart() {
        _state.update { it.copy(removePartCandidate = null) }
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
            riordinaParti(reordered.map { it.id }).fold(
                ifLeft = { error ->
                    _state.update { it.copy(weekPlan = currentPlan) }
                    showError(error)
                },
                ifRight = { /* optimistic update already applied */ },
            )
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
                        val message = buildString {
                            append("Aggiornamento completato: ${result.partTypesImported} tipi, ${result.weeksImported} settimane")
                            if (result.unresolvedPartTypeCodes.isNotEmpty()) {
                                append(" | Codici non risolti: ${result.unresolvedPartTypeCodes.joinToString(", ")}")
                            }
                        }
                        val kind = if (result.unresolvedPartTypeCodes.isEmpty()) {
                            FeedbackBannerKind.SUCCESS
                        } else {
                            FeedbackBannerKind.ERROR
                        }
                        _state.update { it.copy(
                            isImporting = false,
                            notice = FeedbackBannerModel(message, kind),
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
        loadJob?.cancel()
        loadJob = scope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val weekPlan = caricaSettimana(_state.value.currentMonday)
                _state.update { it.copy(isLoading = false, weekPlan = weekPlan) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        notice = FeedbackBannerModel("Errore nel caricamento: ${e.message}", FeedbackBannerKind.ERROR),
                    )
                }
            }
        }
    }

    private fun loadPartTypes() {
        scope.launch {
            try {
                val types = cercaTipiParte()
                _state.update { it.copy(partTypes = types, partTypesLoadFailed = false) }
            } catch (_: Exception) {
                _state.update { it.copy(partTypesLoadFailed = true) }
            }
        }
    }

    private fun showError(error: DomainError) {
        _state.update { it.copy(
            isLoading = false,
            notice = FeedbackBannerModel(error.toMessage(), FeedbackBannerKind.ERROR),
        ) }
    }
}

package org.example.project.ui.admincatalog

import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.feature.diagnostics.application.CalcolaEquitaProclamatoriUseCase
import org.example.project.feature.diagnostics.domain.EquitaProclamatore
import org.example.project.feature.diagnostics.domain.RiepilogoEquita
import org.example.project.ui.components.executeAsyncOperation

internal class EquitaViewModel(
    private val scope: CoroutineScope,
    private val calcolaEquitaProclamatori: CalcolaEquitaProclamatoriUseCase,
) {
    private val _uiState = MutableStateFlow(EquitaUiState())
    val uiState: StateFlow<EquitaUiState> = _uiState.asStateFlow()

    private var referenceDateRaw: LocalDate? = null
    private var riepilogoRaw: RiepilogoEquita? = null
    private var righeRaw: List<EquitaProclamatore> = emptyList()

    fun onScreenEntered() {
        reload()
    }

    fun reload() {
        scope.launch {
            _uiState.executeAsyncOperation(
                loadingUpdate = { it.copy(isLoading = true, error = null) },
                successUpdate = { state, snapshot ->
                    referenceDateRaw = snapshot.referenceDate
                    riepilogoRaw = snapshot.riepilogo
                    righeRaw = snapshot.righe
                    buildState(
                        state.copy(
                            isLoading = false,
                            error = null,
                            referenceDate = snapshot.referenceDate,
                            riepilogo = snapshot.riepilogo,
                        ),
                    )
                },
                errorUpdate = { state, _ ->
                    referenceDateRaw = null
                    riepilogoRaw = null
                    righeRaw = emptyList()
                    state.copy(
                        isLoading = false,
                        error = "Impossibile calcolare l'equita.",
                        referenceDate = null,
                        riepilogo = null,
                        righe = emptyList(),
                    )
                },
                operation = { calcolaEquitaProclamatori(LocalDate.now()) },
            )
        }
    }

    fun onSearchChange(value: String) {
        _uiState.update { buildState(it.copy(filtroRicerca = value)) }
    }

    fun onToggleSoloLiberi() {
        _uiState.update { buildState(it.copy(soloLiberi = !it.soloLiberi)) }
    }

    fun onToggleIncludiSospesi() {
        _uiState.update { buildState(it.copy(includiSospesi = !it.includiSospesi)) }
    }

    fun onSortChange(sortMode: EquitaSortMode) {
        _uiState.update { buildState(it.copy(sortMode = sortMode)) }
    }

    private fun buildState(base: EquitaUiState): EquitaUiState = base.copy(
        righePanoramica = righeRaw.filterNot { it.proclamatore.sospeso },
        righe = applyFiltersAndSort(
            righe = righeRaw,
            filtroRicerca = base.filtroRicerca,
            soloLiberi = base.soloLiberi,
            includiSospesi = base.includiSospesi,
            sortMode = base.sortMode,
        ),
        riepilogo = riepilogoRaw,
    )
}

private fun applyFiltersAndSort(
    righe: List<EquitaProclamatore>,
    filtroRicerca: String,
    soloLiberi: Boolean,
    includiSospesi: Boolean,
    sortMode: EquitaSortMode,
): List<EquitaProclamatore> {
    val query = filtroRicerca.trim().lowercase()
    return righe
        .asSequence()
        .filter { includiSospesi || !it.proclamatore.sospeso }
        .filter { !soloLiberi || it.fuoriCooldown }
        .filter { row ->
            query.isBlank() || "${row.proclamatore.nome} ${row.proclamatore.cognome}"
                .lowercase()
                .contains(query)
        }
        .sortedWith(sortComparator(sortMode))
        .toList()
}

private fun sortComparator(sortMode: EquitaSortMode): Comparator<EquitaProclamatore> = when (sortMode) {
    EquitaSortMode.MENO_USATI -> compareBy<EquitaProclamatore> { it.totaleInFinestra }
        .thenByDescending { it.settimaneDallUltima ?: Int.MAX_VALUE }
        .thenBy { it.proclamatore.cognome.lowercase() }
        .thenBy { it.proclamatore.nome.lowercase() }

    EquitaSortMode.PIU_USATI -> compareByDescending<EquitaProclamatore> { it.totaleInFinestra }
        .thenBy { it.settimaneDallUltima ?: Int.MAX_VALUE }
        .thenBy { it.proclamatore.cognome.lowercase() }
        .thenBy { it.proclamatore.nome.lowercase() }

    EquitaSortMode.ALFABETICO -> compareBy<EquitaProclamatore> { it.proclamatore.cognome.lowercase() }
        .thenBy { it.proclamatore.nome.lowercase() }
}

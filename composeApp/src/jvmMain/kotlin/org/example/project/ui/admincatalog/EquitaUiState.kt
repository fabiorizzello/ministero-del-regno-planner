package org.example.project.ui.admincatalog

import java.time.LocalDate
import org.example.project.feature.diagnostics.domain.EquitaProclamatore
import org.example.project.feature.diagnostics.domain.RiepilogoEquita

internal data class EquitaUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val referenceDate: LocalDate? = null,
    val riepilogo: RiepilogoEquita? = null,
    val righePanoramica: List<EquitaProclamatore> = emptyList(),
    val righe: List<EquitaProclamatore> = emptyList(),
    val filtroRicerca: String = "",
    val soloLiberi: Boolean = false,
    val includiSospesi: Boolean = false,
    val sortMode: EquitaSortMode = EquitaSortMode.MENO_USATI,
)

internal enum class EquitaSortMode {
    MENO_USATI,
    PIU_USATI,
    ALFABETICO,
}

internal val EquitaUiState.emptyStateVisible: Boolean
    get() = !isLoading && error == null && (riepilogo?.totaleAttivi ?: 0) == 0

package org.example.project.ui.admincatalog

import java.time.LocalDate
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.ui.components.FeedbackBannerModel

internal data class PartTypeCatalogUiState(
    val isLoading: Boolean = true,
    val items: List<PartTypeCatalogItem> = emptyList(),
    val selectedId: PartTypeId? = null,
    val selectedDetail: PartTypeCatalogDetail? = null,
    val notice: FeedbackBannerModel? = null,
) {
    val emptyStateVisible: Boolean = !isLoading && items.isEmpty() && notice == null
}

internal data class WeeklySchemaCatalogUiState(
    val isLoading: Boolean = true,
    val weeks: List<WeeklySchemaListItem> = emptyList(),
    val selectedWeekStartDate: LocalDate? = null,
    val selectedDetail: WeeklySchemaDetail? = null,
    val cachedDetails: Map<LocalDate, WeeklySchemaDetail> = emptyMap(),
    val notice: FeedbackBannerModel? = null,
) {
    val emptyStateVisible: Boolean = !isLoading && weeks.isEmpty() && notice == null
}

internal fun resolveSelectedPartTypeId(
    previousSelectedId: PartTypeId?,
    items: List<PartTypeCatalogItem>,
): PartTypeId? = previousSelectedId?.takeIf { currentId ->
    items.any { it.id == currentId }
} ?: items.firstOrNull()?.id

internal fun resolveSelectedWeekStartDate(
    previousSelectedDate: LocalDate?,
    items: List<WeeklySchemaListItem>,
): LocalDate? = previousSelectedDate?.takeIf { currentDate ->
    items.any { it.weekStartDate == currentDate }
} ?: items.firstOrNull()?.weekStartDate

internal fun shouldShowPartTypeCatalogLoading(
    state: PartTypeCatalogUiState,
): Boolean = state.isLoading && state.items.isEmpty()

internal fun shouldShowPartTypeCatalogError(
    state: PartTypeCatalogUiState,
): Boolean = !state.isLoading && state.items.isEmpty() && state.notice != null

internal fun shouldShowWeeklySchemaCatalogLoading(
    state: WeeklySchemaCatalogUiState,
): Boolean = state.isLoading && state.weeks.isEmpty()

internal fun shouldShowWeeklySchemaCatalogError(
    state: WeeklySchemaCatalogUiState,
): Boolean = !state.isLoading && state.weeks.isEmpty() && state.notice != null

package org.example.project.ui.admincatalog

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import org.example.project.feature.schemas.application.StoredSchemaWeekTemplate
import org.example.project.feature.weeklyparts.application.PartTypeWithStatus
import org.example.project.feature.weeklyparts.domain.PartTypeId

internal data class WeeklySchemaListItem(
    val weekStartDate: LocalDate,
    val partsCount: Int,
    val summaryLabel: String,
)

internal data class WeeklySchemaRow(
    val position: Int,
    val partTypeId: PartTypeId,
    val partTypeCode: String,
    val partTypeLabel: String,
    val peopleCount: Int,
    val compositionRuleLabel: String,
    val fixedLabel: String,
)

internal data class WeeklySchemaDetail(
    val weekStartDate: LocalDate,
    val rows: List<WeeklySchemaRow>,
    val readonlyNotice: String,
)

private val weeklySchemaDateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
    .withLocale(Locale.ITALIAN)

internal fun buildWeeklySchemaCatalogItems(
    templates: List<StoredSchemaWeekTemplate>,
): List<WeeklySchemaListItem> = templates
    .sortedBy { it.weekStartDate }
    .map { template ->
        WeeklySchemaListItem(
            weekStartDate = template.weekStartDate,
            partsCount = template.partTypeIds.size,
            summaryLabel = weeklySchemaSummaryLabel(template.weekStartDate, template.partTypeIds.size),
        )
    }

internal fun buildWeeklySchemaCatalogDetailMap(
    templates: List<StoredSchemaWeekTemplate>,
    partTypes: List<PartTypeWithStatus>,
): Map<LocalDate, WeeklySchemaDetail> {
    val partTypesById = partTypes.associateBy { it.partType.id }
    return templates.associate { template ->
        template.weekStartDate to WeeklySchemaDetail(
            weekStartDate = template.weekStartDate,
            rows = template.partTypeIds.mapIndexed { index, partTypeId ->
                val partType = partTypesById[partTypeId]
                WeeklySchemaRow(
                    position = index + 1,
                    partTypeId = partTypeId,
                    partTypeCode = partType?.partType?.code ?: partTypeId.value,
                    partTypeLabel = partType?.partType?.label ?: "Tipo parte non trovato",
                    peopleCount = partType?.partType?.peopleCount ?: 0,
                    compositionRuleLabel = partType?.partType?.sexRule?.toDisplayLabel() ?: "Regola non disponibile",
                    fixedLabel = when {
                        partType == null -> "Catalogo mancante"
                        partType.partType.fixed -> "Fissa"
                        else -> "Ordinaria"
                    },
                )
            },
            readonlyNotice = ADMIN_READONLY_HINT,
        )
    }
}

internal fun weeklySchemaSummaryLabel(
    weekStartDate: LocalDate,
    partsCount: Int,
): String = "${weekStartDate.format(weeklySchemaDateFormatter)} · $partsCount parti"

internal fun describeWeeklySchemaRow(
    row: WeeklySchemaRow,
): String = "${row.partTypeCode} · ${row.partTypeLabel} · ${row.compositionRuleLabel}"

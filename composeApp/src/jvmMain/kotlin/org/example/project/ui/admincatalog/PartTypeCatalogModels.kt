package org.example.project.ui.admincatalog

import org.example.project.feature.weeklyparts.application.PartTypeWithStatus
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.SexRule

internal data class PartTypeCatalogItem(
    val id: PartTypeId,
    val code: String,
    val label: String,
    val peopleCount: Int,
    val sexRule: SexRule,
    val fixed: Boolean,
    val active: Boolean,
)

internal data class PartTypeCatalogDetail(
    val id: PartTypeId,
    val code: String,
    val label: String,
    val peopleCount: Int,
    val sexRuleLabel: String,
    val fixedLabel: String,
    val activeLabel: String,
    val readonlyNotice: String,
)

internal fun PartTypeWithStatus.toPartTypeCatalogItem(): PartTypeCatalogItem = PartTypeCatalogItem(
    id = partType.id,
    code = partType.code,
    label = partType.label,
    peopleCount = partType.peopleCount,
    sexRule = partType.sexRule,
    fixed = partType.fixed,
    active = active,
)

internal fun PartTypeCatalogItem.toDetail(): PartTypeCatalogDetail = PartTypeCatalogDetail(
    id = id,
    code = code,
    label = label,
    peopleCount = peopleCount,
    sexRuleLabel = sexRule.toDisplayLabel(),
    fixedLabel = if (fixed) "Parte fissa" else "Parte ordinaria",
    activeLabel = partTypeStatusLabel(active),
    readonlyNotice = ADMIN_READONLY_HINT,
)

internal fun partTypeStatusLabel(
    active: Boolean,
): String = if (active) "Attivo" else "Disattivo"

internal fun SexRule.toDisplayLabel(): String = when (this) {
    SexRule.UOMO -> "Solo uomini"
    SexRule.STESSO_SESSO -> "Stesso sesso"
}

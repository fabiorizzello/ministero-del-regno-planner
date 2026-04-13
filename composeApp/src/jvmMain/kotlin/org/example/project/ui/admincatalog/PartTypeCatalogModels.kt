package org.example.project.ui.admincatalog

import java.time.format.DateTimeFormatter
import java.util.Locale
import org.example.project.feature.weeklyparts.application.PartTypeWithStatus
import org.example.project.feature.weeklyparts.domain.PartTypeFieldDelta
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.PartTypeRevisionView
import org.example.project.feature.weeklyparts.domain.SexRule

internal enum class PartTypeDetailViewMode { Dettaglio, Cronologia }

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

internal data class PartTypeRevisionListItem(
    val revisionNumber: Int,
    val timestampLabel: String,
    val isCurrent: Boolean,
    val isNoOp: Boolean,
    val isGenesis: Boolean,
    val deltaLines: List<String>,
)

private val revisionTimestampFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ITALIAN)

internal fun PartTypeRevisionView.toListItem(): PartTypeRevisionListItem = PartTypeRevisionListItem(
    revisionNumber = revisionNumber,
    timestampLabel = createdAt.format(revisionTimestampFormatter),
    isCurrent = isCurrent,
    isNoOp = isNoOp,
    isGenesis = revisionNumber == 1,
    deltaLines = deltaFromPrevious.map { it.toDisplayLine() },
)

private fun PartTypeFieldDelta.toDisplayLine(): String = when (this) {
    is PartTypeFieldDelta.Label -> "Nome: «$from» → «$to»"
    is PartTypeFieldDelta.PeopleCount -> "Persone: $from → $to"
    is PartTypeFieldDelta.Sex -> "Regola sesso: ${from.toDisplayLabel()} → ${to.toDisplayLabel()}"
    is PartTypeFieldDelta.Fixed -> if (to) "Resa fissa" else "Resa ordinaria"
}

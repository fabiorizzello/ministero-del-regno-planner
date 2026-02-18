package org.example.project.ui.proclamatori

import arrow.core.Either
import java.awt.FileDialog
import java.awt.Frame
import java.io.FilenameFilter
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.example.project.core.domain.DomainError
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.ui.components.FeedbackBannerKind
import org.example.project.ui.components.FeedbackBannerModel

private val successTimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

internal enum class ProclamatoriSortField { NOME, COGNOME, SESSO, ATTIVO }
internal enum class SortDirection { ASC, DESC }
internal data class ProclamatoriSort(
    val field: ProclamatoriSortField = ProclamatoriSortField.COGNOME,
    val direction: SortDirection = SortDirection.ASC,
)

internal fun personDetails(nome: String, cognome: String): String {
    val fullName = listOf(nome.trim(), cognome.trim())
        .filter { it.isNotEmpty() }
        .joinToString(" ")
        .ifBlank { "-" }
    return "Proclamatore: $fullName"
}

private fun detailsWithTimestamp(details: String? = null): String {
    val timestamp = LocalDateTime.now().format(successTimestampFormatter)
    return if (details.isNullOrBlank()) {
        "Ora: $timestamp"
    } else {
        "$details | Ora: $timestamp"
    }
}

internal fun successNotice(details: String): FeedbackBannerModel {
    return FeedbackBannerModel(
        message = "Operazione completata",
        kind = FeedbackBannerKind.SUCCESS,
        details = detailsWithTimestamp(details),
    )
}

internal fun errorNotice(details: String): FeedbackBannerModel {
    return FeedbackBannerModel(
        message = "Operazione non completata",
        kind = FeedbackBannerKind.ERROR,
        details = detailsWithTimestamp(details),
    )
}

internal fun partialNotice(details: String): FeedbackBannerModel {
    return FeedbackBannerModel(
        message = "Operazione parziale",
        kind = FeedbackBannerKind.ERROR,
        details = detailsWithTimestamp(details),
    )
}

internal data class MultiActionResult(
    val completedCount: Int,
    val failedCount: Int,
    val failedIds: Set<ProclamatoreId>,
)

internal suspend fun runMultiAction(
    ids: Collection<ProclamatoreId>,
    action: suspend (ProclamatoreId) -> Either<DomainError, Unit>,
): MultiActionResult {
    var completedCount = 0
    var failedCount = 0
    val failedIds = mutableSetOf<ProclamatoreId>()

    ids.forEach { id ->
        action(id).fold(
            ifLeft = {
                failedCount++
                failedIds += id
            },
            ifRight = {
                completedCount++
            },
        )
    }

    return MultiActionResult(
        completedCount = completedCount,
        failedCount = failedCount,
        failedIds = failedIds,
    )
}

internal fun noticeForMultiAction(
    result: MultiActionResult,
    completedLabel: String,
    noneCompletedLabel: String,
): FeedbackBannerModel {
    return when {
        result.completedCount > 0 && result.failedCount == 0 -> {
            successNotice("$completedLabel: ${result.completedCount}")
        }
        result.completedCount == 0 -> {
            errorNotice(noneCompletedLabel)
        }
        else -> {
            partialNotice("$completedLabel: ${result.completedCount} | Errori: ${result.failedCount}")
        }
    }
}

internal fun sortFieldForColumn(index: Int): ProclamatoriSortField? {
    return when (index) {
        1 -> ProclamatoriSortField.NOME
        2 -> ProclamatoriSortField.COGNOME
        3 -> ProclamatoriSortField.SESSO
        4 -> ProclamatoriSortField.ATTIVO
        else -> null
    }
}

internal fun sortIndicatorForColumn(index: Int, sort: ProclamatoriSort): String? {
    val field = sortFieldForColumn(index) ?: return null
    if (field != sort.field) return null
    return if (sort.direction == SortDirection.ASC) "^" else "v"
}

internal fun toggleSort(current: ProclamatoriSort, field: ProclamatoriSortField): ProclamatoriSort {
    return if (current.field == field) {
        current.copy(direction = if (current.direction == SortDirection.ASC) SortDirection.DESC else SortDirection.ASC)
    } else {
        ProclamatoriSort(field = field, direction = SortDirection.ASC)
    }
}

internal fun List<Proclamatore>.applySort(sort: ProclamatoriSort): List<Proclamatore> {
    val comparator = when (sort.field) {
        ProclamatoriSortField.NOME -> compareBy<Proclamatore> { it.nome.lowercase() }
            .thenBy { it.cognome.lowercase() }
        ProclamatoriSortField.COGNOME -> compareBy<Proclamatore> { it.cognome.lowercase() }
            .thenBy { it.nome.lowercase() }
        ProclamatoriSortField.SESSO -> compareBy<Proclamatore> { it.sesso.name }
            .thenBy { it.cognome.lowercase() }
            .thenBy { it.nome.lowercase() }
        ProclamatoriSortField.ATTIVO -> compareBy<Proclamatore> { if (it.attivo) 0 else 1 }
            .thenBy { it.cognome.lowercase() }
            .thenBy { it.nome.lowercase() }
    }
    val sorted = this.sortedWith(comparator)
    return if (sort.direction == SortDirection.ASC) sorted else sorted.reversed()
}

internal fun selectJsonFileForImport(): File? {
    val dialog = FileDialog(null as Frame?, "Seleziona file JSON proclamatori", FileDialog.LOAD).apply {
        directory = File(System.getProperty("user.home") ?: ".").absolutePath
        filenameFilter = FilenameFilter { _, name -> name.endsWith(".json", ignoreCase = true) }
        isVisible = true
    }
    val selectedName = dialog.file ?: return null
    val selectedDirectory = dialog.directory ?: return null
    val selected = File(selectedDirectory, selectedName)
    if (!selected.isFile) return null
    if (!selected.name.endsWith(".json", ignoreCase = true)) return null
    return selected
}

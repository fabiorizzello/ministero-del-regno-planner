package org.example.project.ui.components

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val timestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

private fun detailsWithTimestamp(details: String? = null): String {
    val timestamp = LocalDateTime.now().format(timestampFormatter)
    return if (details.isNullOrBlank()) {
        "Ora: $timestamp"
    } else {
        "$details | Ora: $timestamp"
    }
}

fun successNotice(details: String): FeedbackBannerModel {
    return FeedbackBannerModel(
        message = "Operazione completata",
        kind = FeedbackBannerKind.SUCCESS,
        details = detailsWithTimestamp(details),
    )
}

fun errorNotice(details: String): FeedbackBannerModel {
    return FeedbackBannerModel(
        message = "Operazione non completata",
        kind = FeedbackBannerKind.ERROR,
        details = detailsWithTimestamp(details),
    )
}

fun partialNotice(details: String): FeedbackBannerModel {
    return FeedbackBannerModel(
        message = "Operazione parziale",
        kind = FeedbackBannerKind.ERROR,
        details = detailsWithTimestamp(details),
    )
}

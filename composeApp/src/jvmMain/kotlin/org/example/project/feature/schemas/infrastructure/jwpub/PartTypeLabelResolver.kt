package org.example.project.feature.schemas.infrastructure.jwpub

import java.text.Normalizer
import java.util.Locale

object PartTypeLabelResolver {

    sealed interface ResolveOutcome {
        data class Mapped(val code: String) : ResolveOutcome
        data object NotEfficaci : ResolveOutcome
        data class Unknown(val normalizedTitle: String) : ResolveOutcome
    }

    private val EFFICACI_LABELS: Map<String, String> = mapOf(
        "lettura biblica" to "LETTURA_DELLA_BIBBIA",
        "iniziare una conversazione" to "INIZIARE_CONVERSAZIONE",
        "coltivare l interesse" to "COLTIVARE_INTERESSE",
        "fare discepoli" to "FARE_DISCEPOLI",
        "discorso" to "DISCORSO",
    )

    private val NON_EFFICACI_SUBSTRINGS = listOf(
        "cantico",
        "commenti introduttivi",
        "commenti conclusivi",
        "gemme spirituali",
        "studio biblico di congregazione",
        "bisogni locali",
        "tema stagionale",
        "rapporto di servizio",
    )

    fun resolve(rawTitle: String, detailLine: String?): ResolveOutcome {
        val normalized = normalize(stripLeadingNumber(rawTitle))
        NON_EFFICACI_SUBSTRINGS.forEach { sub ->
            if (normalized.contains(sub)) return ResolveOutcome.NotEfficaci
        }

        if (normalized == "spiegare quello in cui si crede") {
            val detailNormalized = detailLine?.let(::normalize).orEmpty()
            return when {
                detailNormalized.contains("dimostrazione") ->
                    ResolveOutcome.Mapped("SPIEGARE_CIO_CHE_SI_CREDE")
                detailNormalized.contains("discorso") ->
                    ResolveOutcome.Mapped("SPIEGARE_CIO_CHE_SI_CREDE_DISCORSO")
                else -> ResolveOutcome.Unknown(normalized)
            }
        }

        EFFICACI_LABELS[normalized]?.let { return ResolveOutcome.Mapped(it) }
        return ResolveOutcome.Unknown(normalized)
    }

    private fun stripLeadingNumber(value: String): String =
        value.replace(Regex("^\\s*\\d+[\\.\\)]\\s*"), "")

    private fun normalize(value: String): String {
        val noAccent = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        return noAccent
            .replace('’', '\'')
            .replace('\'', ' ')
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
    }
}

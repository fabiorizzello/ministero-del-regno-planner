package org.example.project.feature.schemas.infrastructure.jwpub

import arrow.core.getOrElse
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.SexRule

/**
 * Canonical part types for the Meeting Workbook pipeline. IDs are derived
 * deterministically from the code so that repeated `AggiornaSchemiUseCase` runs
 * produce the same ID for a given code, regardless of `PartTypeStore.upsertAll`'s
 * insert strategy.
 *
 * Upstream: `PartTypeStore.upsertAll` currently matches by UNIQUE(code) so the ID
 * is only used for initial insert. The deterministic ID is belt-and-suspenders for
 * future refactors.
 */
object StaticMeetingWorkbookPartTypes {
    fun all(): List<PartType> = listOf(
        build("LETTURA_DELLA_BIBBIA", "Lettura Biblica", 1, SexRule.UOMO, fixed = true, sortOrder = 0),
        build("INIZIARE_CONVERSAZIONE", "Iniziare una conversazione", 2, SexRule.STESSO_SESSO, fixed = false, sortOrder = 1),
        build("COLTIVARE_INTERESSE", "Coltivare l'interesse", 2, SexRule.STESSO_SESSO, fixed = false, sortOrder = 2),
        build("FARE_DISCEPOLI", "Fare discepoli", 2, SexRule.STESSO_SESSO, fixed = false, sortOrder = 3),
        build("DISCORSO", "Discorso", 1, SexRule.UOMO, fixed = false, sortOrder = 4),
        build("SPIEGARE_CIO_CHE_SI_CREDE", "Spiegare quello in cui si crede - Dimostrazione", 2, SexRule.STESSO_SESSO, fixed = false, sortOrder = 5),
        build("SPIEGARE_CIO_CHE_SI_CREDE_DISCORSO", "Spiegare quello in cui si crede - Discorso", 1, SexRule.UOMO, fixed = false, sortOrder = 6),
    )

    private fun deterministicId(code: String): PartTypeId {
        // Stable across runs: UUIDv3 namespace = DNS; name = "jwpub.static:$code"
        val bytes = ("jwpub.static:$code").toByteArray(Charsets.UTF_8)
        return PartTypeId(java.util.UUID.nameUUIDFromBytes(bytes).toString())
    }

    private fun build(code: String, label: String, peopleCount: Int, sexRule: SexRule, fixed: Boolean, sortOrder: Int): PartType =
        PartType.of(
            id = deterministicId(code),
            code = code,
            label = label,
            peopleCount = peopleCount,
            sexRule = sexRule,
            fixed = fixed,
            sortOrder = sortOrder,
        ).getOrElse { error("Static part type '$code' failed validation: $it") }
}

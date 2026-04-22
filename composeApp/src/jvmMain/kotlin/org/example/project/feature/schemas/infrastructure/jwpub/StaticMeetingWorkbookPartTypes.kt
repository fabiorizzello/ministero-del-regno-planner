package org.example.project.feature.schemas.infrastructure.jwpub

import arrow.core.getOrElse
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.SexRule

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

    private fun build(code: String, label: String, peopleCount: Int, sexRule: SexRule, fixed: Boolean, sortOrder: Int): PartType =
        PartType.of(
            id = PartTypeId(""),
            code = code,
            label = label,
            peopleCount = peopleCount,
            sexRule = sexRule,
            fixed = fixed,
            sortOrder = sortOrder,
        ).getOrElse { error("Static part type '$code' failed validation: $it") }
}

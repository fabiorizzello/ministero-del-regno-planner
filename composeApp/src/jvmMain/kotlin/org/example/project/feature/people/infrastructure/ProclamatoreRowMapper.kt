package org.example.project.feature.people.infrastructure

import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.domain.Sesso
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("ProclamatoreRowMapper")

internal fun parseSessoOrDefault(sex: String): Sesso =
    Sesso.entries.find { it.name == sex }
        ?: run {
            logger.warn("Sesso sconosciuto '{}' -> fallback a M", sex)
            Sesso.M
        }

internal fun mapProclamatoreAssignableRow(
    id: String,
    first_name: String,
    last_name: String,
    sex: String,
    suspended: Long,
    can_assist: Long,
): Proclamatore {
    return Proclamatore(
        id = ProclamatoreId(id),
        nome = first_name,
        cognome = last_name,
        sesso = parseSessoOrDefault(sex),
        sospeso = suspended == 1L,
        puoAssistere = can_assist == 1L,
    )
}

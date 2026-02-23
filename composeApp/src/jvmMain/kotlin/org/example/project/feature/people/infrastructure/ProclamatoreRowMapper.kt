package org.example.project.feature.people.infrastructure

import org.example.project.core.util.enumByName
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.domain.Sesso

internal fun mapProclamatoreRow(
    id: String,
    first_name: String,
    last_name: String,
    sex: String,
    active: Long,
): Proclamatore {
    return Proclamatore(
        id = ProclamatoreId(id),
        nome = first_name,
        cognome = last_name,
        sesso = enumByName(sex, Sesso.M),
        attivo = active == 1L,
    )
}

internal fun mapProclamatoreAssignableRow(
    id: String,
    first_name: String,
    last_name: String,
    sex: String,
    active: Long,
    suspended: Long,
    can_assist: Long,
): Proclamatore {
    return Proclamatore(
        id = ProclamatoreId(id),
        nome = first_name,
        cognome = last_name,
        sesso = enumByName(sex, Sesso.M),
        attivo = active == 1L,
        sospeso = suspended == 1L,
        puoAssistere = can_assist == 1L,
    )
}

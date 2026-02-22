package org.example.project.feature.schemas.domain

import java.time.LocalDate

/** Template di schema settimana persistito localmente e usato per creare/aggiornare programmi. */
data class SchemaWeekTemplate(
    val weekStartDate: LocalDate,
    val partTypeCodes: List<String>,
)

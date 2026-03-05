package org.example.project.feature.schemas.application

import arrow.core.Either
import arrow.core.raise.either
import com.russhwolf.settings.Settings
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionRunner
import org.example.project.feature.people.application.EligibilityStore
import org.example.project.feature.weeklyparts.application.PartTypeStore
import java.time.LocalDate
import java.time.LocalDateTime

data class AggiornaSchemiResult(
    val version: String?,
    val partTypesImported: Int,
    val weekTemplatesImported: Int,
    val eligibilityAnomalies: Int,
)

class AggiornaSchemiUseCase(
    private val remoteSource: SchemaCatalogRemoteSource,
    private val partTypeStore: PartTypeStore,
    private val eligibilityStore: EligibilityStore,
    private val schemaTemplateStore: SchemaTemplateStore,
    private val schemaUpdateAnomalyStore: SchemaUpdateAnomalyStore,
    private val transactionRunner: TransactionRunner,
    private val settings: Settings,
) {
    suspend operator fun invoke(): Either<DomainError, AggiornaSchemiResult> = either {
        val catalog = runCatching { remoteSource.fetchCatalog() }
            .getOrElse { raise(DomainError.Network("Errore nel download schemi: ${it.message}")) }

        val availableCodes = catalog.partTypes.map { it.code }.toSet()
        val invalidWeek = catalog.weeks.firstOrNull { week ->
            week.partTypeCodes.any { it !in availableCodes }
        }
        if (invalidWeek != null) {
            raise(
                DomainError.Validation(
                    "Schema settimana ${invalidWeek.weekStartDate} contiene partTypeCode non presenti nel catalogo",
                ),
            )
        }

        val missingPartTypes = partTypeStore.all().filter { it.code !in availableCodes }
        val missingPartTypeIds = missingPartTypes.map { it.id }.toSet()

        val eligibilityCleanupCandidates = eligibilityStore
            .listLeadEligibilityCandidatesForPartTypes(missingPartTypeIds)

        // Validate dates before entering the transaction so errors surface as DomainError.
        val weekStartDates = catalog.weeks.map { remoteWeek ->
            runCatching { LocalDate.parse(remoteWeek.weekStartDate) }
                .getOrNull() ?: raise(DomainError.Validation("Data schema non valida: ${remoteWeek.weekStartDate}"))
        }

        transactionRunner.runInTransaction {
            if (eligibilityCleanupCandidates.isNotEmpty()) {
                eligibilityStore.deleteLeadEligibilityForPartTypes(missingPartTypeIds)
                schemaUpdateAnomalyStore.append(
                    eligibilityCleanupCandidates.map { candidate ->
                        SchemaUpdateAnomalyDraft(
                            personId = candidate.personId,
                            partTypeId = candidate.partTypeId,
                            reason = "Idoneita conduzione rimossa dopo aggiornamento schemi",
                            schemaVersion = catalog.version,
                            createdAt = LocalDateTime.now().toString(),
                        )
                    },
                )
            }

            partTypeStore.upsertAll(catalog.partTypes)
            partTypeStore.deactivateMissingCodes(availableCodes)

            val storedTemplates = catalog.weeks.zip(weekStartDates).map { (remoteWeek, weekStartDate) ->
                val partTypeIds = remoteWeek.partTypeCodes.map { code ->
                    // All codes were validated against availableCodes above; this lookup should not be null.
                    checkNotNull(partTypeStore.findByCode(code)) { "Part type non trovato dopo import: $code" }.id
                }
                StoredSchemaWeekTemplate(
                    weekStartDate = weekStartDate,
                    partTypeIds = partTypeIds,
                )
            }
            schemaTemplateStore.replaceAll(storedTemplates)

            // Keep metadata update aligned with schema write transaction.
            settings.putString("last_schema_import_at", LocalDateTime.now().toString())
        }

        AggiornaSchemiResult(
            version = catalog.version,
            partTypesImported = catalog.partTypes.size,
            weekTemplatesImported = catalog.weeks.size,
            eligibilityAnomalies = eligibilityCleanupCandidates.size,
        )
    }
}

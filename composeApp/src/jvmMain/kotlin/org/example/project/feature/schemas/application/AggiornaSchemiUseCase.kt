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
import java.time.format.DateTimeParseException

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

            val storedTemplates = catalog.weeks.map { remoteWeek ->
                val weekStartDate = try {
                    LocalDate.parse(remoteWeek.weekStartDate)
                } catch (_: DateTimeParseException) {
                    throw IllegalArgumentException("Data schema non valida: ${remoteWeek.weekStartDate}")
                }
                val partTypeIds = remoteWeek.partTypeCodes.map { code ->
                    val partType = partTypeStore.findByCode(code)
                        ?: throw IllegalArgumentException("Part type non trovato dopo import: $code")
                    partType.id
                }
                StoredSchemaWeekTemplate(
                    weekStartDate = weekStartDate,
                    partTypeIds = partTypeIds,
                )
            }
            schemaTemplateStore.replaceAll(storedTemplates)
        }

        settings.putString("last_schema_import_at", LocalDateTime.now().toString())

        AggiornaSchemiResult(
            version = catalog.version,
            partTypesImported = catalog.partTypes.size,
            weekTemplatesImported = catalog.weeks.size,
            eligibilityAnomalies = eligibilityCleanupCandidates.size,
        )
    }
}

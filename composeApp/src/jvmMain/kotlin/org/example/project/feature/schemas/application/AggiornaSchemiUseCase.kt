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
    val skippedUnknownParts: List<SkippedPart> = emptyList(),
    val downloadedIssues: List<String> = emptyList(),
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
        val catalog = remoteSource.fetchCatalog().bind()

        val availableCodes = catalog.partTypes.map { it.code }.toSet()
        val invalidWeek = catalog.weeks.firstOrNull { week ->
            week.partTypeCodes.any { it !in availableCodes }
        }
        if (invalidWeek != null) {
            raise(DomainError.CatalogoSchemiIncoerente(invalidWeek.weekStartDate))
        }

        val missingPartTypes = partTypeStore.all().filter { it.code !in availableCodes }
        val missingPartTypeIds = missingPartTypes.map { it.id }.toSet()

        val eligibilityCleanupCandidates = eligibilityStore
            .listLeadEligibilityCandidatesForPartTypes(missingPartTypeIds)

        // Validate dates before entering the transaction so errors surface as DomainError.
        val weekStartDates = catalog.weeks.map { remoteWeek ->
            runCatching { LocalDate.parse(remoteWeek.weekStartDate) }
                .getOrNull() ?: raise(DomainError.DataSchemaNonValida(remoteWeek.weekStartDate))
        }

        transactionRunner.runInTransactionEither {
            Either.Right(run {
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
                        // All codes were validated against availableCodes above and upserted just above.
                        // If findByCode returns null here it is a programming error (upsertAll did not
                        // persist the code). error() escapes the lambda, TransactionRunner catches it
                        // and triggers rollback before re-throwing.
                        partTypeStore.findByCode(code)?.id
                            ?: error("PartType con codice $code non trovato dopo upsertAll — stato impossibile")
                    }
                    StoredSchemaWeekTemplate(
                        weekStartDate = weekStartDate,
                        partTypeIds = partTypeIds,
                    )
                }
                schemaTemplateStore.replaceAll(storedTemplates)
            })
        }.bind()

        settings.putString("last_schema_import_at", LocalDateTime.now().toString())

        AggiornaSchemiResult(
            version = catalog.version,
            partTypesImported = catalog.partTypes.size,
            weekTemplatesImported = catalog.weeks.size,
            eligibilityAnomalies = eligibilityCleanupCandidates.size,
            skippedUnknownParts = catalog.skippedUnknownParts,
            downloadedIssues = catalog.downloadedIssues,
        )
    }
}

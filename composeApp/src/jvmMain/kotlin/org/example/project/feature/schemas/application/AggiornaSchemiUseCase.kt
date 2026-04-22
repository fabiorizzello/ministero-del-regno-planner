package org.example.project.feature.schemas.application

import arrow.core.Either
import arrow.core.raise.either
import com.russhwolf.settings.Settings
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionRunner
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

fun interface AggiornaSchemiOperation {
    suspend operator fun invoke(): Either<DomainError, AggiornaSchemiResult>
}

/**
 * PartTypes are frozen via SQLDelight migration (see `1.sqm`): the 7 canonical
 * Meeting Workbook part types are seeded/upserted at schema migration time and
 * are no longer touched by this use case. "Aggiorna catalogo" only downloads
 * remote week templates, validates they reference known partType codes, and
 * replaces the stored week templates atomically.
 */
class AggiornaSchemiUseCase(
    private val remoteSource: SchemaCatalogRemoteSource,
    private val partTypeStore: PartTypeStore,
    private val schemaTemplateStore: SchemaTemplateStore,
    private val transactionRunner: TransactionRunner,
    private val settings: Settings,
) : AggiornaSchemiOperation {
    override suspend operator fun invoke(): Either<DomainError, AggiornaSchemiResult> = either {
        val catalog = remoteSource.fetchCatalog().bind()

        // partTypes are frozen via DB migration — just validate that week templates
        // reference known codes.
        val availableCodes = partTypeStore.all().map { it.code }.toSet()
        val invalidWeek = catalog.weeks.firstOrNull { week ->
            week.partTypeCodes.any { it !in availableCodes }
        }
        if (invalidWeek != null) {
            raise(DomainError.CatalogoSchemiIncoerente(invalidWeek.weekStartDate))
        }

        // Validate dates before entering the transaction so errors surface as DomainError.
        val weekStartDates = catalog.weeks.map { remoteWeek ->
            runCatching { LocalDate.parse(remoteWeek.weekStartDate) }
                .getOrNull() ?: raise(DomainError.DataSchemaNonValida(remoteWeek.weekStartDate))
        }

        transactionRunner.runInTransactionEither {
            Either.Right(run {
                val storedTemplates = catalog.weeks.zip(weekStartDates).map { (remoteWeek, weekStartDate) ->
                    val partTypeIds = remoteWeek.partTypeCodes.map { code ->
                        // All codes were validated against availableCodes above.
                        // If findByCode returns null here it is a programming error.
                        partTypeStore.findByCode(code)?.id
                            ?: error("PartType con codice $code non trovato — stato impossibile")
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
            partTypesImported = 0, // partTypes are no longer imported via catalog — seeded via migration
            weekTemplatesImported = catalog.weeks.size,
            eligibilityAnomalies = 0, // anomalies flow removed
            skippedUnknownParts = catalog.skippedUnknownParts,
            downloadedIssues = catalog.downloadedIssues,
        )
    }
}

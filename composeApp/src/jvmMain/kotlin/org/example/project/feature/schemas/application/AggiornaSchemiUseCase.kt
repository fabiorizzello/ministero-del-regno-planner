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
    val weekTemplatesImported: Int,
    val weekTemplatesChanged: Int = 0,
    val weekTemplatesUnchanged: Int = 0,
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

        // partTypes are frozen via DB migration - just validate that week templates
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

        val existingTemplates = schemaTemplateStore.listAll()
        val storedTemplates = catalog.weeks.zip(weekStartDates).map { (remoteWeek, weekStartDate) ->
            val partTypeIds = remoteWeek.partTypeCodes.map { code ->
                // All codes were validated against availableCodes above.
                // If findByCode returns null here it is a programming error.
                partTypeStore.findByCode(code)?.id
                    ?: error("PartType con codice $code non trovato - stato impossibile")
            }
            StoredSchemaWeekTemplate(
                weekStartDate = weekStartDate,
                partTypeIds = partTypeIds,
            )
        }
        val changeSummary = summarizeTemplateChanges(
            existing = existingTemplates,
            incoming = storedTemplates,
        )

        transactionRunner.runInTransactionEither {
            Either.Right(run {
                // Guard: an empty remote catalog (e.g. transient JW CDN 404 cascade,
                // or start-of-year before any fascicolo is published) must NOT wipe
                // stored week templates. The import simply succeeds as a no-op.
                if (storedTemplates.isNotEmpty()) {
                    schemaTemplateStore.replaceAll(storedTemplates)
                }
            })
        }.bind()

        settings.putString("last_schema_import_at", LocalDateTime.now().toString())

        AggiornaSchemiResult(
            version = catalog.version,
            weekTemplatesImported = catalog.weeks.size,
            weekTemplatesChanged = changeSummary.changed,
            weekTemplatesUnchanged = changeSummary.unchanged,
            skippedUnknownParts = catalog.skippedUnknownParts,
            downloadedIssues = catalog.downloadedIssues,
        )
    }
}

private data class TemplateChangeSummary(
    val changed: Int,
    val unchanged: Int,
)

private fun summarizeTemplateChanges(
    existing: List<StoredSchemaWeekTemplate>,
    incoming: List<StoredSchemaWeekTemplate>,
): TemplateChangeSummary {
    if (incoming.isEmpty()) {
        return TemplateChangeSummary(
            changed = 0,
            unchanged = existing.size,
        )
    }
    val existingByWeek = existing.associateBy { it.weekStartDate }
    val incomingByWeek = incoming.associateBy { it.weekStartDate }
    val allWeekDates = existingByWeek.keys + incomingByWeek.keys

    var changed = 0
    var unchanged = 0
    for (weekDate in allWeekDates) {
        val existingTemplate = existingByWeek[weekDate]
        val incomingTemplate = incomingByWeek[weekDate]
        if (existingTemplate != null && incomingTemplate != null &&
            existingTemplate.partTypeIds == incomingTemplate.partTypeIds
        ) {
            unchanged++
        } else {
            changed++
        }
    }
    return TemplateChangeSummary(
        changed = changed,
        unchanged = unchanged,
    )
}

package org.example.project.feature.schemas

import arrow.core.Either
import com.russhwolf.settings.PreferencesSettings
import java.util.UUID
import java.util.prefs.Preferences
import kotlinx.coroutines.test.runTest
import org.example.project.core.PassthroughTransactionRunner
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionScope
import org.example.project.feature.people.application.EligibilityCleanupCandidate
import org.example.project.feature.people.application.EligibilityStore
import org.example.project.feature.people.application.LeadEligibility
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.schemas.application.AggiornaSchemiResult
import org.example.project.feature.schemas.application.AggiornaSchemiUseCase
import org.example.project.feature.schemas.application.RemoteSchemaCatalog
import org.example.project.feature.schemas.application.RemoteWeekSchemaTemplate
import org.example.project.feature.schemas.application.SchemaCatalogRemoteSource
import org.example.project.feature.schemas.application.SchemaTemplateStore
import org.example.project.feature.schemas.application.SchemaUpdateAnomaly
import org.example.project.feature.schemas.application.SchemaUpdateAnomalyDraft
import org.example.project.feature.schemas.application.SchemaUpdateAnomalyStore
import org.example.project.feature.schemas.application.StoredSchemaWeekTemplate
import org.example.project.feature.weeklyparts.application.PartTypeStore
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.SexRule
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AggiornaSchemiUseCaseTest {

    // 1. Import di schemi validi → schemi salvati correttamente nel store
    @Test
    fun `valid catalog imports part types and week templates`() = runTest {
        val pt = makePartType("pt-1", "LETTURA")
        val templateStore = InMemorySchemaTemplateStore2()
        val partTypeStore = InMemoryPartTypeStore2()
        val useCase = buildUseCase(
            partTypeStore = partTypeStore,
            templateStore = templateStore,
            catalog = RemoteSchemaCatalog(
                version = "v1",
                partTypes = listOf(pt),
                weeks = listOf(
                    RemoteWeekSchemaTemplate(
                        weekStartDate = "2026-03-02",
                        partTypeCodes = listOf(pt.code),
                    ),
                ),
            ),
        )

        val result = useCase()

        val right = assertIs<Either.Right<AggiornaSchemiResult>>(result).value
        assertEquals(1, right.partTypesImported)
        assertEquals(1, right.weekTemplatesImported)
        assertEquals(1, templateStore.templates.size)
        assertEquals(LocalDate.of(2026, 3, 2), templateStore.templates.single().weekStartDate)
        Unit
    }

    // 2. Incoerenza catalogo: week references a code not in partTypes → DomainError.CatalogoSchemiIncoerente
    @Test
    fun `catalog with unknown part type code returns CatalogoSchemiIncoerente`() = runTest {
        val pt = makePartType("pt-1", "LETTURA")
        val useCase = buildUseCase(
            catalog = RemoteSchemaCatalog(
                version = "v1",
                partTypes = listOf(pt),
                weeks = listOf(
                    RemoteWeekSchemaTemplate(
                        weekStartDate = "2026-03-02",
                        partTypeCodes = listOf("UNKNOWN_CODE"),
                    ),
                ),
            ),
        )

        val result = useCase()

        val left = assertIs<Either.Left<DomainError>>(result).value
        assertIs<DomainError.CatalogoSchemiIncoerente>(left)
        Unit
    }

    // 3. replaceAll force-replaces existing templates
    @Test
    fun `invoking twice replaces previous templates`() = runTest {
        val pt1 = makePartType("pt-1", "LETTURA")
        val pt2 = makePartType("pt-2", "DISCORSO")
        val templateStore = InMemorySchemaTemplateStore2()
        // Use a shared partTypeStore so the second import can resolve pt2 after upsert
        val sharedPartTypeStore = InMemoryPartTypeStore2()

        // First import: one week with pt1
        buildUseCase(
            partTypeStore = sharedPartTypeStore,
            templateStore = templateStore,
            catalog = RemoteSchemaCatalog(
                version = "v1",
                partTypes = listOf(pt1),
                weeks = listOf(
                    RemoteWeekSchemaTemplate("2026-03-02", listOf(pt1.code)),
                ),
            ),
        )()

        // Second import: different week with pt2 — replaceAll should overwrite
        buildUseCase(
            partTypeStore = sharedPartTypeStore,
            templateStore = templateStore,
            catalog = RemoteSchemaCatalog(
                version = "v2",
                partTypes = listOf(pt2),
                weeks = listOf(
                    RemoteWeekSchemaTemplate("2026-03-09", listOf(pt2.code)),
                ),
            ),
        )()

        // replaceAll replaces everything — only second import's week remains
        assertEquals(1, templateStore.templates.size)
        assertEquals(LocalDate.of(2026, 3, 9), templateStore.templates.single().weekStartDate)
        Unit
    }

    // 4. Date non-lunedì nel catalogo → DomainError.DataSchemaNonValida (data non parsabile)
    // Note: the use-case validates date parsing but NOT the day-of-week.
    // A non-Monday parseable date would be accepted (no day-of-week check in source).
    // An unparseable date string → DataSchemaNonValida.
    @Test
    fun `unparseable date string in week returns DataSchemaNonValida`() = runTest {
        val pt = makePartType("pt-1", "LETTURA")
        val useCase = buildUseCase(
            catalog = RemoteSchemaCatalog(
                version = "v1",
                partTypes = listOf(pt),
                weeks = listOf(
                    RemoteWeekSchemaTemplate(
                        weekStartDate = "NOT-A-DATE",
                        partTypeCodes = listOf(pt.code),
                    ),
                ),
            ),
        )

        val result = useCase()

        val left = assertIs<Either.Left<DomainError>>(result).value
        assertIs<DomainError.DataSchemaNonValida>(left)
        Unit
    }

    // 5. Version is forwarded in result
    @Test
    fun `result contains catalog version`() = runTest {
        val pt = makePartType("pt-1", "LETTURA")
        val useCase = buildUseCase(
            catalog = RemoteSchemaCatalog(
                version = "schema-2026-01",
                partTypes = listOf(pt),
                weeks = listOf(RemoteWeekSchemaTemplate("2026-03-02", listOf(pt.code))),
            ),
        )

        val result = useCase()

        val right = assertIs<Either.Right<AggiornaSchemiResult>>(result).value
        assertEquals("schema-2026-01", right.version)
        Unit
    }

    // 6. Removed part types trigger eligibility cleanup and anomaly recording
    @Test
    fun `removed part types trigger eligibility cleanup and anomaly recording`() = runTest {
        val ptKeep = makePartType("pt-keep", "LETTURA")
        val ptOld = makePartType("pt-old", "VECCHIO")

        val person1 = ProclamatoreId("person-1")
        val person2 = ProclamatoreId("person-2")

        // PartTypeStore pre-populated with the old part type so it appears as "missing"
        val partTypeStore = PrePopulatedPartTypeStore(listOf(ptOld))

        // EligibilityStore returns cleanup candidates for the old part type
        val eligibilityStore = RecordingEligibilityStore(
            candidatesByPartType = mapOf(
                ptOld.id to listOf(
                    EligibilityCleanupCandidate(personId = person1, partTypeId = ptOld.id),
                    EligibilityCleanupCandidate(personId = person2, partTypeId = ptOld.id),
                ),
            ),
        )

        // Recording anomaly store to verify append calls
        val anomalyStore = RecordingSchemaUpdateAnomalyStore()

        val useCase = buildUseCase(
            catalog = RemoteSchemaCatalog(
                version = "v3",
                partTypes = listOf(ptKeep), // ptOld is NOT in catalog → removed
                weeks = listOf(
                    RemoteWeekSchemaTemplate("2026-03-02", listOf(ptKeep.code)),
                ),
            ),
            partTypeStore = partTypeStore,
            eligibilityStore = eligibilityStore,
            schemaUpdateAnomalyStore = anomalyStore,
        )

        val result = useCase()

        val right = assertIs<Either.Right<AggiornaSchemiResult>>(result).value

        // a. deleteLeadEligibilityForPartTypes was called with the removed part type IDs
        assertEquals(1, eligibilityStore.deleteCallArgs.size)
        assertEquals(setOf(ptOld.id), eligibilityStore.deleteCallArgs.single())

        // b. schemaUpdateAnomalyStore.append was called with correct number of drafts
        assertEquals(1, anomalyStore.appendedBatches.size)
        val drafts = anomalyStore.appendedBatches.single()
        assertEquals(2, drafts.size)
        assertTrue(drafts.any { it.personId == person1 && it.partTypeId == ptOld.id })
        assertTrue(drafts.any { it.personId == person2 && it.partTypeId == ptOld.id })
        assertEquals("v3", drafts.first().schemaVersion)

        // c. Result eligibilityAnomalies count matches candidates count
        assertEquals(2, right.eligibilityAnomalies)
        Unit
    }

    @Test
    fun `propagates skippedUnknownParts and downloadedIssues from source`() = runTest {
        val pt = makePartType("pt-1", "LETTURA")
        val templateStore = InMemorySchemaTemplateStore2()
        val partTypeStore = InMemoryPartTypeStore2()
        val useCase = buildUseCase(
            partTypeStore = partTypeStore,
            templateStore = templateStore,
            catalog = RemoteSchemaCatalog(
                version = "v1",
                partTypes = listOf(pt),
                weeks = emptyList(),
                skippedUnknownParts = listOf(
                    org.example.project.feature.schemas.application.SkippedPart(
                        weekStartDate = "2026-03-02",
                        mepsDocumentId = 123L,
                        label = "Parte sconosciuta",
                        detailLine = null,
                    ),
                ),
                downloadedIssues = listOf("202603"),
            ),
        )

        val result = assertIs<Either.Right<AggiornaSchemiResult>>(useCase()).value

        assertEquals(1, result.skippedUnknownParts.size)
        assertEquals("Parte sconosciuta", result.skippedUnknownParts.first().label)
        assertEquals(listOf("202603"), result.downloadedIssues)
        Unit
    }
}

// ---- helpers ----

private fun makePartType(id: String, code: String) = PartType(
    id = PartTypeId(id),
    code = code,
    label = code.lowercase().replaceFirstChar { it.uppercase() },
    peopleCount = 1,
    sexRule = SexRule.STESSO_SESSO,
    fixed = false,
    sortOrder = 0,
)

private fun buildUseCase(
    catalog: RemoteSchemaCatalog,
    partTypeStore: PartTypeStore = InMemoryPartTypeStore2(),
    templateStore: SchemaTemplateStore = InMemorySchemaTemplateStore2(),
    eligibilityStore: EligibilityStore = NoopEligibilityStore2(),
    schemaUpdateAnomalyStore: SchemaUpdateAnomalyStore = NoopSchemaUpdateAnomalyStore2(),
): AggiornaSchemiUseCase = AggiornaSchemiUseCase(
    remoteSource = object : SchemaCatalogRemoteSource {
        override suspend fun fetchCatalog(): Either<DomainError, RemoteSchemaCatalog> = Either.Right(catalog)
    },
    partTypeStore = partTypeStore,
    eligibilityStore = eligibilityStore,
    schemaTemplateStore = templateStore,
    schemaUpdateAnomalyStore = schemaUpdateAnomalyStore,
    transactionRunner = PassthroughTransactionRunner,
    settings = PreferencesSettings(Preferences.userRoot().node("aggiorna-schemi-uc-test-${UUID.randomUUID()}")),
)

private class InMemoryPartTypeStore2 : PartTypeStore {
    private val byCode = linkedMapOf<String, PartType>()
    override suspend fun all(): List<PartType> = byCode.values.toList()
    override suspend fun findByCode(code: String): PartType? = byCode[code]
    override suspend fun findFixed(): PartType? = byCode.values.firstOrNull { it.fixed }
    context(tx: TransactionScope)
    override suspend fun upsertAll(partTypes: List<PartType>) { partTypes.forEach { byCode[it.code] = it } }
    context(tx: TransactionScope)
    override suspend fun deactivateMissingCodes(codes: Set<String>) {}
}

private class InMemorySchemaTemplateStore2 : SchemaTemplateStore {
    var templates: List<StoredSchemaWeekTemplate> = emptyList()
    context(tx: TransactionScope)
    override suspend fun replaceAll(templates: List<StoredSchemaWeekTemplate>) { this.templates = templates }
    override suspend fun listAll(): List<StoredSchemaWeekTemplate> = templates
    override suspend fun findByWeekStartDate(weekStartDate: LocalDate): StoredSchemaWeekTemplate? =
        templates.firstOrNull { it.weekStartDate == weekStartDate }
    override suspend fun isEmpty(): Boolean = templates.isEmpty()
}

private class NoopEligibilityStore2 : EligibilityStore {
    context(tx: TransactionScope) override suspend fun setCanAssist(personId: ProclamatoreId, canAssist: Boolean) {}
    context(tx: TransactionScope) override suspend fun setCanLead(personId: ProclamatoreId, partTypeId: PartTypeId, canLead: Boolean) {}
    override suspend fun listLeadEligibility(personId: ProclamatoreId): List<LeadEligibility> = emptyList()
    override suspend fun listLeadEligibilityCandidatesForPartTypes(partTypeIds: Set<PartTypeId>): List<EligibilityCleanupCandidate> = emptyList()
    override suspend fun preloadLeadEligibilityByPartType(partTypeIds: Set<PartTypeId>): Map<PartTypeId, Set<ProclamatoreId>> = emptyMap()
    context(tx: TransactionScope) override suspend fun deleteLeadEligibilityForPartTypes(partTypeIds: Set<PartTypeId>) {}
    override suspend fun listFutureAssignmentWeeks(personId: ProclamatoreId, fromDate: LocalDate): List<LocalDate> = emptyList()
}

private class NoopSchemaUpdateAnomalyStore2 : SchemaUpdateAnomalyStore {
    context(tx: TransactionScope)
    override suspend fun append(items: List<SchemaUpdateAnomalyDraft>) {}
    override suspend fun listOpen(): List<SchemaUpdateAnomaly> = emptyList()
    context(tx: TransactionScope)
    override suspend fun dismissAllOpen() {}
}

/** PartTypeStore pre-populated with initial part types (returned by [all]). */
private class PrePopulatedPartTypeStore(initial: List<PartType>) : PartTypeStore {
    private val byCode = linkedMapOf<String, PartType>().apply {
        initial.forEach { put(it.code, it) }
    }
    override suspend fun all(): List<PartType> = byCode.values.toList()
    override suspend fun findByCode(code: String): PartType? = byCode[code]
    override suspend fun findFixed(): PartType? = byCode.values.firstOrNull { it.fixed }
    context(tx: TransactionScope)
    override suspend fun upsertAll(partTypes: List<PartType>) { partTypes.forEach { byCode[it.code] = it } }
    context(tx: TransactionScope)
    override suspend fun deactivateMissingCodes(codes: Set<String>) {}
}

/** EligibilityStore that returns pre-configured candidates and records delete calls. */
private class RecordingEligibilityStore(
    private val candidatesByPartType: Map<PartTypeId, List<EligibilityCleanupCandidate>> = emptyMap(),
) : EligibilityStore {
    val deleteCallArgs = mutableListOf<Set<PartTypeId>>()

    context(tx: TransactionScope) override suspend fun setCanAssist(personId: ProclamatoreId, canAssist: Boolean) {}
    context(tx: TransactionScope) override suspend fun setCanLead(personId: ProclamatoreId, partTypeId: PartTypeId, canLead: Boolean) {}
    override suspend fun listLeadEligibility(personId: ProclamatoreId): List<LeadEligibility> = emptyList()

    override suspend fun listLeadEligibilityCandidatesForPartTypes(
        partTypeIds: Set<PartTypeId>,
    ): List<EligibilityCleanupCandidate> =
        partTypeIds.flatMap { candidatesByPartType[it].orEmpty() }

    override suspend fun preloadLeadEligibilityByPartType(partTypeIds: Set<PartTypeId>): Map<PartTypeId, Set<ProclamatoreId>> = emptyMap()

    context(tx: TransactionScope)
    override suspend fun deleteLeadEligibilityForPartTypes(partTypeIds: Set<PartTypeId>) {
        deleteCallArgs.add(partTypeIds)
    }

    override suspend fun listFutureAssignmentWeeks(personId: ProclamatoreId, fromDate: LocalDate): List<LocalDate> = emptyList()
}

/** SchemaUpdateAnomalyStore that records all append calls. */
private class RecordingSchemaUpdateAnomalyStore : SchemaUpdateAnomalyStore {
    val appendedBatches = mutableListOf<List<SchemaUpdateAnomalyDraft>>()

    context(tx: TransactionScope)
    override suspend fun append(items: List<SchemaUpdateAnomalyDraft>) {
        appendedBatches.add(items)
    }
    override suspend fun listOpen(): List<SchemaUpdateAnomaly> = emptyList()
    context(tx: TransactionScope)
    override suspend fun dismissAllOpen() {}
}

package org.example.project.feature.schemas

import arrow.core.Either
import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import kotlinx.coroutines.runBlocking
import org.example.project.core.persistence.TransactionRunner
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
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AggiornaSchemiUseCaseTransactionTest {

    @Test
    fun `stores last schema import timestamp inside transaction`() = runBlocking {
        val txRunner = TrackingTransactionRunner()
        val settings = TrackingSettings(txRunner)
        val partType = PartType(
            id = PartTypeId("pt-1"),
            code = "LETTURA",
            label = "Lettura",
            peopleCount = 1,
            sexRule = SexRule.STESSO_SESSO,
            fixed = false,
            sortOrder = 0,
        )
        val remote = FakeSchemaCatalogRemoteSource(
            RemoteSchemaCatalog(
                version = "v1",
                partTypes = listOf(partType),
                weeks = listOf(
                    RemoteWeekSchemaTemplate(
                        weekStartDate = "2026-03-02",
                        partTypeCodes = listOf(partType.code),
                    ),
                ),
            ),
        )
        val useCase = AggiornaSchemiUseCase(
            remoteSource = remote,
            partTypeStore = InMemoryPartTypeStore(),
            eligibilityStore = NoopEligibilityStore(),
            schemaTemplateStore = InMemorySchemaTemplateStore(),
            schemaUpdateAnomalyStore = NoopSchemaUpdateAnomalyStore(),
            transactionRunner = txRunner,
            settings = settings,
        )

        val result = useCase()

        assertIs<Either.Right<AggiornaSchemiResult>>(result)
        assertEquals(1, txRunner.invocationCount)
        assertTrue(settings.putTimestampInsideTransaction)
    }
}

private class TrackingTransactionRunner : TransactionRunner {
    var invocationCount: Int = 0
        private set
    var isInsideTransaction: Boolean = false
        private set

    override suspend fun <T> runInTransaction(block: suspend org.example.project.core.persistence.TransactionScope.() -> T): T {
        invocationCount++
        isInsideTransaction = true
        return try {
            with(org.example.project.core.persistence.DefaultTransactionScope) { block() }
        } finally {
            isInsideTransaction = false
        }
    }
}

private class TrackingSettings(
    private val txRunner: TrackingTransactionRunner,
    private val delegate: Settings = PreferencesSettings(
        Preferences.userRoot().node("aggiorna-schemi-test-${UUID.randomUUID()}"),
    ),
) : Settings by delegate {
    var putTimestampInsideTransaction: Boolean = false
        private set

    override fun putString(key: String, value: String) {
        if (key == "last_schema_import_at") {
            putTimestampInsideTransaction = txRunner.isInsideTransaction
        }
        delegate.putString(key, value)
    }
}

private class FakeSchemaCatalogRemoteSource(
    private val catalog: RemoteSchemaCatalog,
) : SchemaCatalogRemoteSource {
    override suspend fun fetchCatalog(): RemoteSchemaCatalog = catalog
}

private class InMemoryPartTypeStore : PartTypeStore {
    private val byCode = linkedMapOf<String, PartType>()

    override suspend fun all(): List<PartType> = byCode.values.toList()

    override suspend fun findByCode(code: String): PartType? = byCode[code]

    override suspend fun findFixed(): PartType? = byCode.values.firstOrNull { it.fixed }

    override suspend fun upsertAll(partTypes: List<PartType>) {
        partTypes.forEach { byCode[it.code] = it }
    }

    override suspend fun deactivateMissingCodes(codes: Set<String>) {
        // no-op for this test
    }
}

private class InMemorySchemaTemplateStore : SchemaTemplateStore {
    private var templates: List<StoredSchemaWeekTemplate> = emptyList()

    override suspend fun replaceAll(templates: List<StoredSchemaWeekTemplate>) {
        this.templates = templates
    }

    override suspend fun listAll(): List<StoredSchemaWeekTemplate> = templates

    override suspend fun findByWeekStartDate(weekStartDate: LocalDate): StoredSchemaWeekTemplate? =
        templates.firstOrNull { it.weekStartDate == weekStartDate }

    override suspend fun isEmpty(): Boolean = templates.isEmpty()
}

private class NoopEligibilityStore : EligibilityStore {
    override suspend fun setSuspended(personId: ProclamatoreId, suspended: Boolean) {}
    override suspend fun setCanAssist(personId: ProclamatoreId, canAssist: Boolean) {}
    override suspend fun setCanLead(personId: ProclamatoreId, partTypeId: PartTypeId, canLead: Boolean) {}
    override suspend fun listLeadEligibility(personId: ProclamatoreId): List<LeadEligibility> = emptyList()
    override suspend fun listLeadEligibilityCandidatesForPartTypes(partTypeIds: Set<PartTypeId>): List<EligibilityCleanupCandidate> =
        emptyList()

    override suspend fun deleteLeadEligibilityForPartTypes(partTypeIds: Set<PartTypeId>) {}
    override suspend fun listFutureAssignmentWeeks(personId: ProclamatoreId, fromDate: LocalDate): List<LocalDate> = emptyList()
}

private class NoopSchemaUpdateAnomalyStore : SchemaUpdateAnomalyStore {
    override suspend fun append(items: List<SchemaUpdateAnomalyDraft>) {}
    override suspend fun listOpen(): List<SchemaUpdateAnomaly> = emptyList()
    override suspend fun dismissAllOpen() {}
}

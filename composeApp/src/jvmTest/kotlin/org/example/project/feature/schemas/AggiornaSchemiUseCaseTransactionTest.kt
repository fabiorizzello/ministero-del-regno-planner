package org.example.project.feature.schemas

import arrow.core.Either
import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import kotlinx.coroutines.test.runTest
import org.example.project.core.persistence.TransactionRunner
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
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AggiornaSchemiUseCaseTransactionTest {

    @Test
    fun `stores last schema import timestamp after transaction succeeds`() = runTest {
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
        assertTrue(settings.getStringOrNull("last_schema_import_at") != null)
        assertTrue(!settings.putTimestampInsideTransaction)
    }

    @Test
    fun `rolls back part types and templates when replaceAll fails mid-transaction`() = runTest {
        val existingPartType = PartType(
            id = PartTypeId("pt-existing"),
            code = "ESISTENTE",
            label = "Esistente",
            peopleCount = 1,
            sexRule = SexRule.STESSO_SESSO,
            fixed = false,
            sortOrder = 0,
        )
        val importedPartType = PartType(
            id = PartTypeId("pt-imported"),
            code = "NUOVO",
            label = "Nuovo",
            peopleCount = 1,
            sexRule = SexRule.STESSO_SESSO,
            fixed = false,
            sortOrder = 0,
        )
        val existingTemplate = StoredSchemaWeekTemplate(
            weekStartDate = LocalDate.of(2026, 3, 2),
            partTypeIds = listOf(existingPartType.id),
        )
        val partTypeStore = RollbackAwarePartTypeStore(listOf(existingPartType))
        val templateStore = RollbackAwareSchemaTemplateStore(
            initialTemplates = listOf(existingTemplate),
            failOnReplace = true,
        )
        val settings = RollbackAwareSettings()
        val txRunner = SnapshotTransactionRunner(partTypeStore, templateStore, settings)
        val remote = FakeSchemaCatalogRemoteSource(
            RemoteSchemaCatalog(
                version = "v2",
                partTypes = listOf(importedPartType),
                weeks = listOf(
                    RemoteWeekSchemaTemplate(
                        weekStartDate = "2026-03-09",
                        partTypeCodes = listOf(importedPartType.code),
                    ),
                ),
            ),
        )
        val useCase = AggiornaSchemiUseCase(
            remoteSource = remote,
            partTypeStore = partTypeStore,
            eligibilityStore = NoopEligibilityStore(),
            schemaTemplateStore = templateStore,
            schemaUpdateAnomalyStore = NoopSchemaUpdateAnomalyStore(),
            transactionRunner = txRunner,
            settings = settings,
        )

        val result = useCase()

        assertIs<Either.Left<*>>(result)
        assertEquals(listOf(existingPartType.code), partTypeStore.all().map { it.code })
        assertEquals(listOf(existingTemplate.weekStartDate), templateStore.listAll().map { it.weekStartDate })
        assertTrue(settings.getStringOrNull("last_schema_import_at") == null)
        Unit
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

    context(tx: TransactionScope)
    override suspend fun upsertAll(partTypes: List<PartType>) {
        partTypes.forEach { byCode[it.code] = it }
    }

    context(tx: TransactionScope)
    override suspend fun deactivateMissingCodes(codes: Set<String>) {
        // no-op for this test
    }
}

private class InMemorySchemaTemplateStore : SchemaTemplateStore {
    private var templates: List<StoredSchemaWeekTemplate> = emptyList()

    context(tx: TransactionScope)
    override suspend fun replaceAll(templates: List<StoredSchemaWeekTemplate>) {
        this.templates = templates
    }

    override suspend fun listAll(): List<StoredSchemaWeekTemplate> = templates

    override suspend fun findByWeekStartDate(weekStartDate: LocalDate): StoredSchemaWeekTemplate? =
        templates.firstOrNull { it.weekStartDate == weekStartDate }

    override suspend fun isEmpty(): Boolean = templates.isEmpty()
}

private class NoopEligibilityStore : EligibilityStore {
    context(tx: TransactionScope) override suspend fun setSuspended(personId: ProclamatoreId, suspended: Boolean) {}
    context(tx: TransactionScope) override suspend fun setCanAssist(personId: ProclamatoreId, canAssist: Boolean) {}
    context(tx: TransactionScope) override suspend fun setCanLead(personId: ProclamatoreId, partTypeId: PartTypeId, canLead: Boolean) {}
    override suspend fun listLeadEligibility(personId: ProclamatoreId): List<LeadEligibility> = emptyList()
    override suspend fun listLeadEligibilityCandidatesForPartTypes(partTypeIds: Set<PartTypeId>): List<EligibilityCleanupCandidate> =
        emptyList()

    override suspend fun preloadLeadEligibilityByPartType(partTypeIds: Set<PartTypeId>): Map<PartTypeId, Set<ProclamatoreId>> = emptyMap()
    context(tx: TransactionScope) override suspend fun deleteLeadEligibilityForPartTypes(partTypeIds: Set<PartTypeId>) {}
    override suspend fun listFutureAssignmentWeeks(personId: ProclamatoreId, fromDate: LocalDate): List<LocalDate> = emptyList()
}

private class NoopSchemaUpdateAnomalyStore : SchemaUpdateAnomalyStore {
    context(tx: TransactionScope)
    override suspend fun append(items: List<SchemaUpdateAnomalyDraft>) {}
    override suspend fun listOpen(): List<SchemaUpdateAnomaly> = emptyList()
    context(tx: TransactionScope)
    override suspend fun dismissAllOpen() {}
}

private interface SnapshotParticipant {
    fun snapshot()
    fun rollback()
}

private class SnapshotTransactionRunner(
    vararg participants: SnapshotParticipant,
) : TransactionRunner {
    private val participants = participants.toList()

    override suspend fun <T> runInTransaction(block: suspend org.example.project.core.persistence.TransactionScope.() -> T): T {
        participants.forEach { it.snapshot() }
        return try {
            with(org.example.project.core.persistence.DefaultTransactionScope) { block() }
        } catch (error: Throwable) {
            participants.forEach { it.rollback() }
            throw error
        }
    }
}

private class RollbackAwarePartTypeStore(
    initial: List<PartType>,
) : PartTypeStore, SnapshotParticipant {
    private val byCode = linkedMapOf<String, PartType>()
    private var snapshotByCode: Map<String, PartType> = emptyMap()

    init {
        initial.forEach { byCode[it.code] = it }
    }

    override fun snapshot() {
        snapshotByCode = LinkedHashMap(byCode)
    }

    override fun rollback() {
        byCode.clear()
        byCode.putAll(snapshotByCode)
    }

    override suspend fun all(): List<PartType> = byCode.values.toList()

    override suspend fun findByCode(code: String): PartType? = byCode[code]

    override suspend fun findFixed(): PartType? = byCode.values.firstOrNull { it.fixed }

    context(tx: TransactionScope)
    override suspend fun upsertAll(partTypes: List<PartType>) {
        partTypes.forEach { byCode[it.code] = it }
    }
}

private class RollbackAwareSchemaTemplateStore(
    initialTemplates: List<StoredSchemaWeekTemplate>,
    private val failOnReplace: Boolean,
) : SchemaTemplateStore, SnapshotParticipant {
    private var templates: List<StoredSchemaWeekTemplate> = initialTemplates
    private var snapshotTemplates: List<StoredSchemaWeekTemplate> = initialTemplates

    override fun snapshot() {
        snapshotTemplates = templates.toList()
    }

    override fun rollback() {
        templates = snapshotTemplates.toList()
    }

    context(tx: TransactionScope)
    override suspend fun replaceAll(templates: List<StoredSchemaWeekTemplate>) {
        if (failOnReplace) {
            throw IllegalStateException("forced replaceAll failure")
        }
        this.templates = templates
    }

    override suspend fun listAll(): List<StoredSchemaWeekTemplate> = templates

    override suspend fun findByWeekStartDate(weekStartDate: LocalDate): StoredSchemaWeekTemplate? =
        templates.firstOrNull { it.weekStartDate == weekStartDate }

    override suspend fun isEmpty(): Boolean = templates.isEmpty()
}

private class RollbackAwareSettings(
    private val delegate: Settings = PreferencesSettings(
        Preferences.userRoot().node("aggiorna-schemi-rollback-${UUID.randomUUID()}"),
    ),
) : Settings by delegate, SnapshotParticipant {
    private var snapshotLastImportAt: String? = null

    override fun snapshot() {
        snapshotLastImportAt = delegate.getStringOrNull("last_schema_import_at")
    }

    override fun rollback() {
        val value = snapshotLastImportAt
        if (value == null) {
            delegate.remove("last_schema_import_at")
        } else {
            delegate.putString("last_schema_import_at", value)
        }
    }
}

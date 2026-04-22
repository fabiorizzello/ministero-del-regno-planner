package org.example.project.feature.schemas

import arrow.core.Either
import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import kotlinx.coroutines.test.runTest
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionRunner
import org.example.project.core.persistence.TransactionScope
import org.example.project.feature.schemas.application.AggiornaSchemiResult
import org.example.project.feature.schemas.application.AggiornaSchemiUseCase
import org.example.project.feature.schemas.application.RemoteSchemaCatalog
import org.example.project.feature.schemas.application.RemoteWeekSchemaTemplate
import org.example.project.feature.schemas.application.SchemaCatalogRemoteSource
import org.example.project.feature.schemas.application.SchemaTemplateStore
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
                partTypes = emptyList(),
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
            partTypeStore = InMemoryPartTypeStore(listOf(partType)),
            schemaTemplateStore = InMemorySchemaTemplateStore(),
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
    fun `rolls back templates when replaceAll fails mid-transaction`() = runTest {
        val existingPartType = PartType(
            id = PartTypeId("pt-existing"),
            code = "ESISTENTE",
            label = "Esistente",
            peopleCount = 1,
            sexRule = SexRule.STESSO_SESSO,
            fixed = false,
            sortOrder = 0,
        )
        val existingTemplate = StoredSchemaWeekTemplate(
            weekStartDate = LocalDate.of(2026, 3, 2),
            partTypeIds = listOf(existingPartType.id),
        )
        val partTypeStore = InMemoryPartTypeStore(listOf(existingPartType))
        val templateStore = RollbackAwareSchemaTemplateStore(
            initialTemplates = listOf(existingTemplate),
            failOnReplace = true,
        )
        val settings = RollbackAwareSettings()
        val txRunner = SnapshotTransactionRunner(templateStore, settings)
        val remote = FakeSchemaCatalogRemoteSource(
            RemoteSchemaCatalog(
                version = "v2",
                partTypes = emptyList(),
                weeks = listOf(
                    RemoteWeekSchemaTemplate(
                        weekStartDate = "2026-03-09",
                        partTypeCodes = listOf(existingPartType.code),
                    ),
                ),
            ),
        )
        val useCase = AggiornaSchemiUseCase(
            remoteSource = remote,
            partTypeStore = partTypeStore,
            schemaTemplateStore = templateStore,
            transactionRunner = txRunner,
            settings = settings,
        )

        val result = useCase()

        assertIs<Either.Left<*>>(result)
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
    override suspend fun fetchCatalog(): Either<DomainError, RemoteSchemaCatalog> = Either.Right(catalog)
}

private class InMemoryPartTypeStore(initial: List<PartType> = emptyList()) : PartTypeStore {
    private val byCode = linkedMapOf<String, PartType>().apply {
        initial.forEach { put(it.code, it) }
    }

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

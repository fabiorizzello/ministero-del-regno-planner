package org.example.project.feature.schemas

import arrow.core.Either
import com.russhwolf.settings.PreferencesSettings
import java.util.UUID
import java.util.prefs.Preferences
import kotlinx.coroutines.test.runTest
import org.example.project.core.PassthroughTransactionRunner
import org.example.project.core.domain.DomainError
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AggiornaSchemiUseCaseTest {

    // 1. Import di schemi validi → schemi salvati correttamente nel store
    @Test
    fun `valid catalog imports week templates`() = runTest {
        val pt = makePartType("pt-1", "LETTURA")
        val templateStore = InMemorySchemaTemplateStore2()
        val partTypeStore = PrePopulatedPartTypeStore(listOf(pt))
        val useCase = buildUseCase(
            partTypeStore = partTypeStore,
            templateStore = templateStore,
            catalog = RemoteSchemaCatalog(
                version = "v1",
                partTypes = emptyList(),
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
        // partTypes are no longer imported via the catalog — seeded via migration
        assertEquals(0, right.partTypesImported)
        assertEquals(1, right.weekTemplatesImported)
        assertEquals(1, templateStore.templates.size)
        assertEquals(LocalDate.of(2026, 3, 2), templateStore.templates.single().weekStartDate)
        Unit
    }

    // 2. Incoerenza catalogo: week references a code not in partTypeStore → DomainError.CatalogoSchemiIncoerente
    @Test
    fun `catalog with unknown part type code returns CatalogoSchemiIncoerente`() = runTest {
        val pt = makePartType("pt-1", "LETTURA")
        val useCase = buildUseCase(
            partTypeStore = PrePopulatedPartTypeStore(listOf(pt)),
            catalog = RemoteSchemaCatalog(
                version = "v1",
                partTypes = emptyList(),
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
        val sharedPartTypeStore = PrePopulatedPartTypeStore(listOf(pt1, pt2))

        // First import: one week with pt1
        buildUseCase(
            partTypeStore = sharedPartTypeStore,
            templateStore = templateStore,
            catalog = RemoteSchemaCatalog(
                version = "v1",
                partTypes = emptyList(),
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
                partTypes = emptyList(),
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

    // 4. Date non parsabile → DomainError.DataSchemaNonValida
    @Test
    fun `unparseable date string in week returns DataSchemaNonValida`() = runTest {
        val pt = makePartType("pt-1", "LETTURA")
        val useCase = buildUseCase(
            partTypeStore = PrePopulatedPartTypeStore(listOf(pt)),
            catalog = RemoteSchemaCatalog(
                version = "v1",
                partTypes = emptyList(),
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
            partTypeStore = PrePopulatedPartTypeStore(listOf(pt)),
            catalog = RemoteSchemaCatalog(
                version = "schema-2026-01",
                partTypes = emptyList(),
                weeks = listOf(RemoteWeekSchemaTemplate("2026-03-02", listOf(pt.code))),
            ),
        )

        val result = useCase()

        val right = assertIs<Either.Right<AggiornaSchemiResult>>(result).value
        assertEquals("schema-2026-01", right.version)
        Unit
    }

    @Test
    fun `propagates skippedUnknownParts and downloadedIssues from source`() = runTest {
        val pt = makePartType("pt-1", "LETTURA")
        val templateStore = InMemorySchemaTemplateStore2()
        val partTypeStore = PrePopulatedPartTypeStore(listOf(pt))
        val useCase = buildUseCase(
            partTypeStore = partTypeStore,
            templateStore = templateStore,
            catalog = RemoteSchemaCatalog(
                version = "v1",
                partTypes = emptyList(),
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
    partTypeStore: PartTypeStore = PrePopulatedPartTypeStore(emptyList()),
    templateStore: SchemaTemplateStore = InMemorySchemaTemplateStore2(),
): AggiornaSchemiUseCase = AggiornaSchemiUseCase(
    remoteSource = object : SchemaCatalogRemoteSource {
        override suspend fun fetchCatalog(): Either<DomainError, RemoteSchemaCatalog> = Either.Right(catalog)
    },
    partTypeStore = partTypeStore,
    schemaTemplateStore = templateStore,
    transactionRunner = PassthroughTransactionRunner,
    settings = PreferencesSettings(Preferences.userRoot().node("aggiorna-schemi-uc-test-${UUID.randomUUID()}")),
)

private class InMemorySchemaTemplateStore2 : SchemaTemplateStore {
    var templates: List<StoredSchemaWeekTemplate> = emptyList()
    context(tx: TransactionScope)
    override suspend fun replaceAll(templates: List<StoredSchemaWeekTemplate>) { this.templates = templates }
    override suspend fun listAll(): List<StoredSchemaWeekTemplate> = templates
    override suspend fun findByWeekStartDate(weekStartDate: LocalDate): StoredSchemaWeekTemplate? =
        templates.firstOrNull { it.weekStartDate == weekStartDate }
    override suspend fun isEmpty(): Boolean = templates.isEmpty()
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

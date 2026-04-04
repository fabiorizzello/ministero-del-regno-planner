package org.example.project.feature.diagnostics

import arrow.core.Either
import kotlinx.coroutines.test.runTest
import org.example.project.core.PassthroughTransactionRunner
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionScope
import org.example.project.feature.diagnostics.application.ImportaSeedApplicazioneDaJsonUseCase
import org.example.project.feature.people.application.EligibilityCleanupCandidate
import org.example.project.feature.people.application.EligibilityStore
import org.example.project.feature.people.application.LeadEligibility
import org.example.project.feature.people.application.ProclamatoriAggregateStore
import org.example.project.feature.people.application.ProclamatoriQuery
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.domain.Sesso
import org.example.project.feature.weeklyparts.application.PartTypeStore
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ImportaSeedApplicazioneDaJsonUseCaseTest {

    @Test
    fun `valid seed json imports part types students and eligibility`() = runTest {
        val studentStore = InMemoryStudentStore()
        val partTypeStore = InMemorySeedPartTypeStore()
        val eligibilityStore = RecordingEligibilityStore(partTypeStore)
        val useCase = buildUseCase(
            query = EmptyStudentQuery(),
            studentStore = studentStore,
            partTypeStore = partTypeStore,
            eligibilityStore = eligibilityStore,
        )

        val json = """
            {
              "version": 1,
              "partTypes": [
                { "code": "preghiera", "label": "Preghiera", "peopleCount": 1, "sexRule": "UOMO", "fixed": true, "sortOrder": 0 },
                { "code": "lettura", "label": "Lettura", "peopleCount": 2, "sexRule": "STESSO_SESSO", "sortOrder": 1 }
              ],
              "students": [
                {
                  "nome": "Mario",
                  "cognome": "Rossi",
                  "sesso": "M",
                  "sospeso": false,
                  "puoAssistere": true,
                  "canLeadPartTypeCodes": ["preghiera", "lettura"]
                },
                {
                  "nome": "Anna",
                  "cognome": "Bianchi",
                  "sesso": "F",
                  "sospeso": true,
                  "puoAssistere": false,
                  "canLeadPartTypeCodes": ["lettura"]
                }
              ]
            }
        """.trimIndent()

        val result = useCase(json)

        val right = assertIs<Either.Right<ImportaSeedApplicazioneDaJsonUseCase.Result>>(result).value
        assertEquals(2, right.importedPartTypes)
        assertEquals(2, right.importedStudents)
        assertEquals(3, right.importedLeadEligibility)
        assertEquals(2, studentStore.persisted.size)
        assertEquals(2, partTypeStore.all().size)
        assertEquals(setOf("PREGHIERA", "LETTURA"), partTypeStore.deactivatedToCodes.single())
        assertEquals(3, eligibilityStore.recorded.size)
        assertTrue(eligibilityStore.recorded.any { it.partTypeCode == "PREGHIERA" })
        assertEquals(2, eligibilityStore.recorded.count { it.partTypeCode == "LETTURA" })
        Unit
    }

    @Test
    fun `existing students block seed import`() = runTest {
        val existing = listOf(
            Proclamatore(
                id = ProclamatoreId("existing-1"),
                nome = "Luca",
                cognome = "Verdi",
                sesso = Sesso.M,
            ),
        )
        val useCase = buildUseCase(query = ExistingStudentQuery(existing))

        val json = """
            {
              "version": 1,
              "partTypes": [
                { "code": "LETTURA", "label": "Lettura", "peopleCount": 2, "sexRule": "STESSO_SESSO" }
              ],
              "students": [
                { "nome": "Mario", "cognome": "Rossi", "sesso": "M" }
              ]
            }
        """.trimIndent()

        val result = useCase(json)

        val left = assertIs<Either.Left<DomainError>>(result).value
        assertEquals(DomainError.ImportArchivioNonVuoto, left)
        Unit
    }

    @Test
    fun `student with unknown part type code returns validation error`() = runTest {
        val useCase = buildUseCase()

        val json = """
            {
              "version": 1,
              "partTypes": [
                { "code": "LETTURA", "label": "Lettura", "peopleCount": 2, "sexRule": "STESSO_SESSO" }
              ],
              "students": [
                {
                  "nome": "Mario",
                  "cognome": "Rossi",
                  "sesso": "M",
                  "canLeadPartTypeCodes": ["PREGHIERA"]
                }
              ]
            }
        """.trimIndent()

        val result = useCase(json)

        val left = assertIs<Either.Left<DomainError>>(result).value
        val error = assertIs<DomainError.ImportContenutoNonValido>(left)
        assertTrue(error.details.contains("tipo parte non definito"))
        Unit
    }

    @Test
    fun `female student cannot lead uomo part type`() = runTest {
        val useCase = buildUseCase()

        val json = """
            {
              "version": 1,
              "partTypes": [
                { "code": "PREGHIERA", "label": "Preghiera", "peopleCount": 1, "sexRule": "UOMO", "fixed": true }
              ],
              "students": [
                {
                  "nome": "Anna",
                  "cognome": "Bianchi",
                  "sesso": "F",
                  "canLeadPartTypeCodes": ["PREGHIERA"]
                }
              ]
            }
        """.trimIndent()

        val result = useCase(json)

        val left = assertIs<Either.Left<DomainError>>(result).value
        val error = assertIs<DomainError.ImportContenutoNonValido>(left)
        assertTrue(error.details.contains("non puo' condurre PREGHIERA"))
        Unit
    }
}

private fun buildUseCase(
    query: ProclamatoriQuery = EmptyStudentQuery(),
    studentStore: ProclamatoriAggregateStore = InMemoryStudentStore(),
    partTypeStore: PartTypeStore = InMemorySeedPartTypeStore(),
    eligibilityStore: EligibilityStore = RecordingEligibilityStore(partTypeStore),
): ImportaSeedApplicazioneDaJsonUseCase = ImportaSeedApplicazioneDaJsonUseCase(
    proclamatoriQuery = query,
    proclamatoriStore = studentStore,
    partTypeStore = partTypeStore,
    eligibilityStore = eligibilityStore,
    transactionRunner = PassthroughTransactionRunner,
)

private class EmptyStudentQuery : ProclamatoriQuery {
    override suspend fun cerca(termine: String?): List<Proclamatore> = emptyList()
    override suspend fun esisteConNomeCognome(nome: String, cognome: String, esclusoId: ProclamatoreId?): Boolean = false
}

private class ExistingStudentQuery(
    private val students: List<Proclamatore>,
) : ProclamatoriQuery {
    override suspend fun cerca(termine: String?): List<Proclamatore> = students
    override suspend fun esisteConNomeCognome(nome: String, cognome: String, esclusoId: ProclamatoreId?): Boolean = false
}

private class InMemoryStudentStore : ProclamatoriAggregateStore {
    val persisted = mutableListOf<Proclamatore>()

    override suspend fun load(id: ProclamatoreId): Proclamatore? = persisted.firstOrNull { it.id == id }

    context(tx: TransactionScope)
    override suspend fun persist(aggregateRoot: Proclamatore) {
        persisted += aggregateRoot
    }

    context(tx: TransactionScope)
    override suspend fun persistAll(aggregateRoots: Collection<Proclamatore>) {
        persisted += aggregateRoots
    }

    context(tx: TransactionScope)
    override suspend fun remove(id: ProclamatoreId) {
        persisted.removeIf { it.id == id }
    }
}

private class InMemorySeedPartTypeStore : PartTypeStore {
    private val byCode = linkedMapOf<String, PartType>()
    val deactivatedToCodes = mutableListOf<Set<String>>()

    override suspend fun all(): List<PartType> = byCode.values.toList()

    override suspend fun findByCode(code: String): PartType? = byCode[code]

    override suspend fun findFixed(): PartType? = byCode.values.firstOrNull { it.fixed }

    context(tx: TransactionScope)
    override suspend fun upsertAll(partTypes: List<PartType>) {
        partTypes.forEach { byCode[it.code] = it }
    }

    context(tx: TransactionScope)
    override suspend fun deactivateMissingCodes(codes: Set<String>) {
        deactivatedToCodes += codes
        byCode.entries.removeIf { it.key !in codes }
    }
}

private class RecordingEligibilityStore(
    private val partTypeStore: PartTypeStore,
) : EligibilityStore {
    data class RecordedEligibility(
        val personId: ProclamatoreId,
        val partTypeId: PartTypeId,
        val partTypeCode: String,
    )

    val recorded = mutableListOf<RecordedEligibility>()

    context(tx: TransactionScope)
    override suspend fun setCanAssist(personId: ProclamatoreId, canAssist: Boolean) {}

    context(tx: TransactionScope)
    override suspend fun setCanLead(personId: ProclamatoreId, partTypeId: PartTypeId, canLead: Boolean) {
        if (!canLead) return
        val code = partTypeStore.all().firstOrNull { it.id == partTypeId }?.code ?: partTypeId.value
        recorded += RecordedEligibility(
            personId = personId,
            partTypeId = partTypeId,
            partTypeCode = code,
        )
    }

    override suspend fun listLeadEligibility(personId: ProclamatoreId): List<LeadEligibility> = emptyList()

    override suspend fun listLeadEligibilityCandidatesForPartTypes(partTypeIds: Set<PartTypeId>): List<EligibilityCleanupCandidate> = emptyList()

    override suspend fun preloadLeadEligibilityByPartType(partTypeIds: Set<PartTypeId>): Map<PartTypeId, Set<ProclamatoreId>> = emptyMap()

    context(tx: TransactionScope)
    override suspend fun deleteLeadEligibilityForPartTypes(partTypeIds: Set<PartTypeId>) {}

    override suspend fun listFutureAssignmentWeeks(personId: ProclamatoreId, fromDate: LocalDate): List<LocalDate> = emptyList()
}

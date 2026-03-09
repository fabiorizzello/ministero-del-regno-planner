package org.example.project.feature.people

import arrow.core.Either
import kotlinx.coroutines.runBlocking
import org.example.project.core.domain.DomainError
import org.example.project.feature.people.application.ImportaProclamatoriDaJsonUseCase
import org.example.project.feature.people.application.ProclamatoriAggregateStore
import org.example.project.feature.people.application.ProclamatoriQuery
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.domain.Sesso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ImportaProclamatoriDaJsonUseCaseTest {

    // 1. Happy path: JSON valido con version=1 e array proclamatori → tutti salvati nello store
    @Test
    fun `valid json with version 1 imports all proclamatori`() = runBlocking {
        val store = ImportaJsonInMemoryStore()
        val useCase = buildUseCase(store = store)

        val jsonContent = "{\"version\":1,\"proclamatori\":[{\"nome\":\"Mario\",\"cognome\":\"Rossi\",\"sesso\":\"M\"},{\"nome\":\"Luisa\",\"cognome\":\"Bianchi\",\"sesso\":\"F\"}]}"

        val result = useCase(jsonContent)

        val right = assertIs<Either.Right<ImportaProclamatoriDaJsonUseCase.Result>>(result).value
        assertEquals(2, right.importati)
        assertEquals(0, right.errori)
        assertEquals(2, store.persisted.size)
        Unit
    }

    // 2. Archivio non vuoto → ImportArchivioNonVuoto
    @Test
    fun `non-empty archive returns ImportArchivioNonVuoto`() = runBlocking {
        val existing = listOf(
            Proclamatore(id = ProclamatoreId("p1"), nome = "Marco", cognome = "Verdi", sesso = Sesso.M),
        )
        val store = ImportaJsonInMemoryStore()
        val useCase = buildUseCase(store = store, existingPeople = existing)

        val json = """{"version": 1, "proclamatori": [{"nome": "Luigi", "cognome": "Neri", "sesso": "M"}]}"""

        val result = useCase(json)

        val left = assertIs<Either.Left<DomainError>>(result).value
        assertEquals(DomainError.ImportArchivioNonVuoto, left)
        Unit
    }

    // 3. Array proclamatori vuoto nel JSON → ImportSenzaProclamatori
    @Test
    fun `empty proclamatori array returns ImportSenzaProclamatori`() = runBlocking {
        val store = ImportaJsonInMemoryStore()
        val useCase = buildUseCase(store = store)

        val json = """{"version": 1, "proclamatori": []}"""

        val result = useCase(json)

        val left = assertIs<Either.Left<DomainError>>(result).value
        assertEquals(DomainError.ImportSenzaProclamatori, left)
        Unit
    }

    // 4. JSON con elemento duplicato (stesso nome+cognome) → ImportContenutoNonValido con posizione duplicato
    @Test
    fun `duplicate nome cognome returns ImportContenutoNonValido mentioning position`() = runBlocking {
        val store = ImportaJsonInMemoryStore()
        val useCase = buildUseCase(store = store)

        val json = """
            {
              "version": 1,
              "proclamatori": [
                {"nome": "Mario", "cognome": "Rossi", "sesso": "M"},
                {"nome": "mario", "cognome": "ROSSI", "sesso": "M"}
              ]
            }
        """.trimIndent()

        val result = useCase(json)

        val left = assertIs<Either.Left<DomainError>>(result).value
        val error = assertIs<DomainError.ImportContenutoNonValido>(left)
        // The error message should mention position 2 (the duplicate is the second element)
        assert(error.details.contains("#2")) {
            "Expected details to contain '#2' (duplicate position), but was: ${error.details}"
        }
        Unit
    }

    // 5. Versione schema != 1 → ImportVersioneSchemaNonSupportata
    @Test
    fun `version other than 1 returns ImportVersioneSchemaNonSupportata`() = runBlocking {
        val store = ImportaJsonInMemoryStore()
        val useCase = buildUseCase(store = store)

        val json = """{"version": 2, "proclamatori": [{"nome": "Mario", "cognome": "Rossi", "sesso": "M"}]}"""

        val result = useCase(json)

        val left = assertIs<Either.Left<DomainError>>(result).value
        assertIs<DomainError.ImportVersioneSchemaNonSupportata>(left)
        Unit
    }
}

// ---- fakes ----

private fun buildUseCase(
    store: ProclamatoriAggregateStore,
    existingPeople: List<Proclamatore> = emptyList(),
): ImportaProclamatoriDaJsonUseCase = ImportaProclamatoriDaJsonUseCase(
    query = FakeQuery(existingPeople),
    store = store,
    transactionRunner = ImmediateTransactionRunner,
)

private class FakeQuery(private val people: List<Proclamatore>) : ProclamatoriQuery {
    override suspend fun cerca(termine: String?): List<Proclamatore> = people
    override suspend fun esisteConNomeCognome(nome: String, cognome: String, esclusoId: ProclamatoreId?): Boolean = false
}

private class ImportaJsonInMemoryStore : ProclamatoriAggregateStore {
    val persisted = mutableListOf<Proclamatore>()

    override suspend fun load(id: ProclamatoreId): Proclamatore? = persisted.firstOrNull { it.id == id }
    override suspend fun persist(aggregateRoot: Proclamatore) { persisted.add(aggregateRoot) }
    override suspend fun persistAll(aggregateRoots: Collection<Proclamatore>) { persisted.addAll(aggregateRoots) }
    override suspend fun remove(id: ProclamatoreId) { persisted.removeIf { it.id == id } }
}

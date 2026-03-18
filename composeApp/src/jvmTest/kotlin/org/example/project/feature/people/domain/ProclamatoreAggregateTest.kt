package org.example.project.feature.people.domain

import arrow.core.Either
import org.example.project.core.domain.DomainError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProclamatoreAggregateTest {

    @Test
    fun `update profile returns new aggregate with updated person`() {
        val aggregate = ProclamatoreAggregate(
            Proclamatore(
                id = ProclamatoreId("p1"),
                nome = "Mario",
                cognome = "Rossi",
                sesso = Sesso.M,
            ),
        )

        val updated = assertIs<Either.Right<ProclamatoreAggregate>>(
            aggregate.updateProfile(
                nome = "Luigi",
                cognome = "Verdi",
                sesso = Sesso.M,
                sospeso = true,
                puoAssistere = true,
            ),
        ).value

        assertEquals("Luigi", updated.person.nome)
        assertEquals("Verdi", updated.person.cognome)
        assertTrue(updated.person.sospeso)
        assertTrue(updated.person.puoAssistere)
    }

    @Test
    fun `create and updateProfile reject blank required fields`() {
        val createBlankNome = ProclamatoreAggregate.create(
            id = ProclamatoreId("p1"), nome = "   ", cognome = "Rossi", sesso = Sesso.M,
        )
        assertEquals(
            DomainError.NomeObbligatorio,
            assertIs<Either.Left<DomainError>>(createBlankNome).value,
        )

        val createBlankCognome = ProclamatoreAggregate.create(
            id = ProclamatoreId("p1"), nome = "Mario", cognome = "   ", sesso = Sesso.M,
        )
        assertEquals(
            DomainError.CognomeObbligatorio,
            assertIs<Either.Left<DomainError>>(createBlankCognome).value,
        )

        val aggregate = ProclamatoreAggregate(
            Proclamatore(
                id = ProclamatoreId("p1"), nome = "Mario", cognome = "Rossi", sesso = Sesso.M,
            ),
        )
        val updateBlankNome = aggregate.updateProfile(
            nome = "", cognome = "Rossi", sesso = Sesso.M, sospeso = false, puoAssistere = false,
        )
        assertEquals(
            DomainError.NomeObbligatorio,
            assertIs<Either.Left<DomainError>>(updateBlankNome).value,
        )
        Unit
    }

    @Test
    fun `create happy path returns aggregate with correct data`() {
        val result = ProclamatoreAggregate.create(
            id = ProclamatoreId("p42"),
            nome = "  Lucia  ",
            cognome = "Bianchi",
            sesso = Sesso.F,
            puoAssistere = true,
        )

        val aggregate = assertIs<Either.Right<ProclamatoreAggregate>>(result).value
        assertEquals("Lucia", aggregate.person.nome)
        assertEquals("Bianchi", aggregate.person.cognome)
        assertEquals(Sesso.F, aggregate.person.sesso)
        assertTrue(aggregate.person.puoAssistere)
        assertFalse(aggregate.person.sospeso)
        assertEquals(ProclamatoreId("p42"), aggregate.person.id)
    }

    // --- max-length boundary tests ---

    @Test
    fun `create succeeds when nome is exactly 100 chars after trim`() {
        val nome100 = "A".repeat(100)
        val result = ProclamatoreAggregate.create(
            id = ProclamatoreId("p1"),
            nome = "  $nome100  ",
            cognome = "Rossi",
            sesso = Sesso.M,
        )

        val aggregate = assertIs<Either.Right<ProclamatoreAggregate>>(result).value
        assertEquals(100, aggregate.person.nome.length)
    }

    @Test
    fun `create returns NomeTroppoLungo when nome exceeds 100 chars after trim`() {
        val nome101 = "A".repeat(101)
        val result = ProclamatoreAggregate.create(
            id = ProclamatoreId("p1"),
            nome = nome101,
            cognome = "Rossi",
            sesso = Sesso.M,
        )

        val left = assertIs<Either.Left<DomainError>>(result).value
        assertEquals(DomainError.NomeTroppoLungo(100), left)
    }

    @Test
    fun `create returns CognomeTroppoLungo when cognome exceeds 100 chars after trim`() {
        val cognome101 = "B".repeat(101)
        val result = ProclamatoreAggregate.create(
            id = ProclamatoreId("p1"),
            nome = "Mario",
            cognome = cognome101,
            sesso = Sesso.M,
        )

        val left = assertIs<Either.Left<DomainError>>(result).value
        assertEquals(DomainError.CognomeTroppoLungo(100), left)
    }

    @Test
    fun `Proclamatore of delegates to aggregate and rejects nome over 100 chars`() {
        val nome101 = "A".repeat(101)
        val result = Proclamatore.of(
            id = ProclamatoreId("p1"),
            nome = nome101,
            cognome = "Rossi",
            sesso = Sesso.M,
        )

        val left = assertIs<Either.Left<DomainError>>(result).value
        assertEquals(DomainError.NomeTroppoLungo(100), left)
    }

    // --- suspend / reactivate ---

    @Test
    fun `suspend and reactivate are idempotent transitions`() {
        val aggregate = ProclamatoreAggregate(
            Proclamatore(
                id = ProclamatoreId("p1"),
                nome = "Mario",
                cognome = "Rossi",
                sesso = Sesso.M,
            ),
        )

        val suspended = aggregate.suspendPerson()
        val reactivated = suspended.reactivate()

        assertTrue(suspended.person.sospeso)
        assertFalse(reactivated.person.sospeso)
    }
}

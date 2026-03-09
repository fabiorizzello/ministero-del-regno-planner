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
    fun `create returns NomeObbligatorio when nome is blank`() {
        val result = ProclamatoreAggregate.create(
            id = ProclamatoreId("p1"),
            nome = "   ",
            cognome = "Rossi",
            sesso = Sesso.M,
        )

        val left = assertIs<Either.Left<DomainError>>(result).value
        assertEquals(DomainError.NomeObbligatorio, left)
    }

    @Test
    fun `create returns CognomeObbligatorio when cognome is blank`() {
        val result = ProclamatoreAggregate.create(
            id = ProclamatoreId("p1"),
            nome = "Mario",
            cognome = "   ",
            sesso = Sesso.M,
        )

        val left = assertIs<Either.Left<DomainError>>(result).value
        assertEquals(DomainError.CognomeObbligatorio, left)
    }

    @Test
    fun `updateProfile returns NomeObbligatorio when nome is blank`() {
        val aggregate = ProclamatoreAggregate(
            Proclamatore(
                id = ProclamatoreId("p1"),
                nome = "Mario",
                cognome = "Rossi",
                sesso = Sesso.M,
            ),
        )

        val result = aggregate.updateProfile(
            nome = "",
            cognome = "Rossi",
            sesso = Sesso.M,
            sospeso = false,
            puoAssistere = false,
        )

        val left = assertIs<Either.Left<DomainError>>(result).value
        assertEquals(DomainError.NomeObbligatorio, left)
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

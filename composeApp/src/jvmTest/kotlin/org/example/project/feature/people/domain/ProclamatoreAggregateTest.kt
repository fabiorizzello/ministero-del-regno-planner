package org.example.project.feature.people.domain

import arrow.core.Either
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

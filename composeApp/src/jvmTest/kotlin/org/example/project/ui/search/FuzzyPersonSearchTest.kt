package org.example.project.ui.search

import kotlin.test.Test
import kotlin.test.assertEquals

class FuzzyPersonSearchTest {

    @Test
    fun `rankPeopleByQuery tollera refusi lievi`() {
        val ranked = rankPeopleByQuery(
            query = "Mrio",
            candidates = listOf(
                candidate("p1", "Mario", "Rossi"),
                candidate("p2", "Luigi", "Verdi"),
            ),
        )

        assertEquals(listOf("p1"), ranked)
    }

    @Test
    fun `rankPeopleByQuery usa tie break alfabetico stabile`() {
        val ranked = rankPeopleByQuery(
            query = "Anna",
            candidates = listOf(
                candidate("p2", "Anna", "Verdi"),
                candidate("p1", "Anna", "Bianchi"),
            ),
        )

        assertEquals(listOf("p1", "p2"), ranked)
    }

    @Test
    fun `rankPeopleByQuery con query vuota ordina alfabeticamente`() {
        val ranked = rankPeopleByQuery(
            query = "",
            candidates = listOf(
                candidate("p2", "Luigi", "Verdi"),
                candidate("p1", "Mario", "Bianchi"),
            ),
        )

        assertEquals(listOf("p1", "p2"), ranked)
    }

    private fun candidate(
        id: String,
        firstName: String,
        lastName: String,
    ) = FuzzySearchCandidate(
        value = id,
        firstName = firstName,
        lastName = lastName,
    )
}

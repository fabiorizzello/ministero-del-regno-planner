package org.example.project.ui.search

import java.text.Normalizer
import kotlin.math.min

data class FuzzySearchCandidate<T>(
    val value: T,
    val firstName: String,
    val lastName: String,
)

private data class RankedCandidate<T>(
    val candidate: FuzzySearchCandidate<T>,
    val exactPrefix: Boolean,
    val contains: Boolean,
    val distance: Int,
)

fun normalizeSearchTerm(value: String): String {
    val normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
        .lowercase()
    return normalized.replace("\\s+".toRegex(), " ")
}

fun <T> rankPeopleByQuery(
    query: String,
    candidates: List<FuzzySearchCandidate<T>>,
): List<T> {
    val normalizedQuery = normalizeSearchTerm(query)
    if (normalizedQuery.isBlank()) {
        return candidates.sortedWith(
            compareBy<FuzzySearchCandidate<T>>(
                { normalizeSearchTerm(it.lastName) },
                { normalizeSearchTerm(it.firstName) },
            ),
        ).map { it.value }
    }

    return candidates
        .map { candidate ->
            val first = normalizeSearchTerm(candidate.firstName)
            val last = normalizeSearchTerm(candidate.lastName)
            val full = "$first $last".trim()
            val exactPrefix = first.startsWith(normalizedQuery) ||
                last.startsWith(normalizedQuery) ||
                full.startsWith(normalizedQuery)
            val contains = first.contains(normalizedQuery) ||
                last.contains(normalizedQuery) ||
                full.contains(normalizedQuery)
            val distance = listOf(
                levenshteinDistance(normalizedQuery, first),
                levenshteinDistance(normalizedQuery, last),
                levenshteinDistance(normalizedQuery, full),
            ).min()
            RankedCandidate(candidate, exactPrefix, contains, distance)
        }
        .filter { ranked ->
            ranked.exactPrefix ||
                ranked.contains ||
                ranked.distance <= maxDistanceThreshold(normalizedQuery)
        }
        .sortedWith(
            compareByDescending<RankedCandidate<T>> { it.exactPrefix }
                .thenByDescending { it.contains }
                .thenBy { it.distance }
                .thenBy { normalizeSearchTerm(it.candidate.lastName) }
                .thenBy { normalizeSearchTerm(it.candidate.firstName) },
        )
        .map { it.candidate.value }
}

private fun maxDistanceThreshold(query: String): Int = maxOf(1, query.length / 2)

private fun levenshteinDistance(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length

    val previous = IntArray(b.length + 1) { it }
    val current = IntArray(b.length + 1)

    for (i in a.indices) {
        current[0] = i + 1
        for (j in b.indices) {
            val substitutionCost = if (a[i] == b[j]) 0 else 1
            current[j + 1] = min(
                min(current[j] + 1, previous[j + 1] + 1),
                previous[j] + substitutionCost,
            )
        }
        for (index in previous.indices) {
            previous[index] = current[index]
        }
    }

    return previous[b.length]
}

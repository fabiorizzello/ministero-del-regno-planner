package org.example.project.feature.updates

object UpdateVersionComparator {
    fun isNewer(current: String, latest: String): Boolean {
        return compare(current, latest) < 0
    }

    fun compare(current: String, latest: String): Int {
        val currentParts = normalize(current)
        val latestParts = normalize(latest)
        val max = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until max) {
            val a = currentParts.getOrElse(i) { 0 }
            val b = latestParts.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return 0
    }

    private fun normalize(version: String): List<Int> {
        val cleaned = version.trim().removePrefix("v").removePrefix("V")
        val matches = Regex("\\d+").findAll(cleaned).map { it.value.toInt() }.toList()
        return matches.ifEmpty { listOf(0) }
    }
}

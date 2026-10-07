package dev.dovie.shabbosmode

/** Accept natural city/region input while preserving every word as a location or qualifier. */
object CitySearch {
    private val whitespace = Regex("[\\s\\u00a0]+")

    private fun candidates(query: String): List<String> {
        val normalized = query.split(',').map { it.trim().replace(whitespace, " ") }
            .filter(String::isNotBlank).joinToString(", ")
        if (normalized.length < 2) return emptyList()
        // An explicit qualifier must never be dropped in favor of an unrelated city.
        if (',' in normalized) return listOf(normalized)
        val words = normalized.split(' ')
        return listOf(normalized) + (1..minOf(4, words.size - 1)).map { suffixSize ->
            words.dropLast(suffixSize).joinToString(" ") + ", " +
                words.takeLast(suffixSize).joinToString(" ")
        }
    }

    suspend fun <T> find(query: String, fetch: suspend (String) -> List<T>): List<T> {
        for (candidate in candidates(query)) {
            val results = fetch(candidate)
            if (results.isNotEmpty()) return results
        }
        return emptyList()
    }
}

package dev.dovie.shabbosmode

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class CitySearchTest {
    private val springValley = "Spring Valley, New York, United States"

    @Test fun cityAndStateWithoutCommaFindTheQualifiedCity() = runBlocking {
        val requests = mutableListOf<String>()
        val results = CitySearch.find("  spring   valley ny  ") {
            requests += it
            if (it == "spring valley, ny") listOf(springValley) else emptyList()
        }
        assertEquals(listOf(springValley), results)
        assertEquals(listOf("spring valley ny", "spring valley, ny"), requests)
    }

    @Test fun fullStateAndCountryNamesAreAccepted() = runBlocking {
        for ((query, qualified) in listOf(
            "Spring Valley New York" to "Spring Valley, New York",
            "Jerusalem Israel" to "Jerusalem, Israel",
            "London United Kingdom" to "London, United Kingdom"
        )) {
            assertEquals(listOf(qualified), CitySearch.find(query) {
                if (it == qualified) listOf(it) else emptyList()
            })
        }
    }

    @Test fun existingCityNamesAndPostalCodesAvoidExtraRequests() = runBlocking {
        for (query in listOf("New York", "Tel Aviv", "10977")) {
            var requests = 0
            assertEquals(listOf(query), CitySearch.find(query) { requests++; listOf(it) })
            assertEquals(1, requests)
        }
    }

    @Test fun explicitRegionIsNormalizedAndNeverDiscarded() = runBlocking {
        val requests = mutableListOf<String>()
        assertTrue(CitySearch.find<String>(" spring valley , zz ") {
            requests += it
            emptyList()
        }.isEmpty())
        assertEquals(listOf("spring valley, zz"), requests)
    }

    @Test fun failedSearchNeverFallsBackToAnUnqualifiedCity() = runBlocking {
        val requests = mutableListOf<String>()
        assertTrue(CitySearch.find<String>("Spring Valley NY") {
            requests += it
            // A broad city-only search might return Spring Valley, Nevada.
            if (it == "Spring Valley") listOf("Wrong state") else emptyList()
        }.isEmpty())
        assertTrue(requests.all { it.endsWith("NY") })
        assertTrue(requests.size <= 5)
    }

    @Test fun blankAndSingleCharacterQueriesMakeNoRequests() = runBlocking {
        for (query in listOf("", "  ,  ", "a")) {
            assertTrue(CitySearch.find<String>(query) { error("Unexpected request") }.isEmpty())
        }
    }

    @Test fun networkErrorsAreReportedWithoutTryingOtherLocations() = runBlocking {
        var requests = 0
        val failure = runCatching {
            CitySearch.find<String>("Spring Valley NY") { requests++; throw IOException("Offline") }
        }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertEquals(1, requests)
    }
}

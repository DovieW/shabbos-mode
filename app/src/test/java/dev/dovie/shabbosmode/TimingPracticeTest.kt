package dev.dovie.shabbosmode

import org.junit.Assert.*
import org.junit.Test

class TimingPracticeTest {
    @Test fun localCandleDefaultsUsePlaceAndRemainIndependentOfCalendarPreference() {
        val israel = AppSettings(countryCode = "IL", zoneId = "Asia/Jerusalem")
        assertEquals(18, TimingPractice.candleLead(AppSettings(city = "New York")))
        assertEquals(20, TimingPractice.candleLead(israel.copy(locality = "Tel Aviv")))
        assertEquals(40, TimingPractice.candleLead(israel.copy(locality = "Jerusalem", israelCalendar = false)))
        assertEquals(30, TimingPractice.candleLead(israel.copy(locality = "Haifa")))
        assertEquals(30, TimingPractice.candleLead(israel.copy(locality = "Zikhron Ya‘aqov")))
        assertEquals(18, TimingPractice.candleLead(israel.copy(locality = "Jerusalem", candleMinutes = 18)))
        assertEquals(40, TimingPractice.candleLead(AppSettings(city = "Jerusalem, Israel", zoneId = "Asia/Jerusalem")))
    }

    @Test fun nightfallAndFixedMinutesNeverSetConflictingApiFlags() {
        val nightfall = TimingPractice.shabbatParameters(AppSettings())
        assertTrue(nightfall.contains("&M=on"))
        assertFalse(nightfall.contains("&m="))
        for (minutes in listOf(42, 50, 72, 90)) {
            val fixed = TimingPractice.shabbatParameters(AppSettings(havdalahMinutes = minutes, israelCalendar = true))
            assertTrue(fixed.contains("&m=$minutes"))
            assertTrue(fixed.contains("&i=on"))
            assertFalse(fixed.contains("&M="))
        }
    }

    @Test fun prayerTraditionDoesNotChooseHavdalah() {
        ZmanTradition.entries.forEach { tradition ->
            assertEquals(TimingPractice.shabbatParameters(AppSettings()),
                TimingPractice.shabbatParameters(AppSettings(tradition = tradition)))
        }
        val mga = TimingPractice.zmanSources(ZmanTradition.MGA)
        assertEquals("sofZmanShmaMGA", mga.getValue("sofZmanShma").first)
        assertEquals("sofZmanTfillaMGA", mga.getValue("sofZmanTfilla").first)
        val chabad = TimingPractice.zmanSources(ZmanTradition.CHABAD)
        assertEquals("sofZmanShmaBaalHatanya", chabad.getValue("sofZmanShma").first)
        assertEquals("sofZmanTfilaBaalHatanya", chabad.getValue("sofZmanTfilla").first)
        assertEquals("plagHaminchaBaalHatanya", chabad.getValue("plagHaMincha").first)
    }

    @Test fun unsureKeepsBothDeadlinesDistinctAndExplicitlyLabelled() {
        val both = TimingPractice.zmanSources(ZmanTradition.BOTH)
        assertEquals("sofZmanShma", both.getValue("sofZmanShma").first)
        assertEquals("sofZmanShmaMGA", both.getValue("sofZmanShmaMGA").first)
        assertEquals("Shema · Gra", both.getValue("sofZmanShma").second)
        assertEquals("Shema · MGA", both.getValue("sofZmanShmaMGA").second)
    }
}

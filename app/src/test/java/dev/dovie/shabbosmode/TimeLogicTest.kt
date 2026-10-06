package dev.dovie.shabbosmode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TimeLogicTest {
    private val zone = ZoneId.of("America/New_York")

    @Test fun displayedBoundariesOnlyUseOverridesForTheirWeek() {
        val events = listOf(
            TimeEvent("start", "Shabbos starts", 100, "2026-10-02", 0),
            TimeEvent("end", "Shabbos ends", 200, "2026-10-02", 0),
            TimeEvent("sunset", "Sunset", 150, "2026-10-02", 0)
        )
        val settings = AppSettings(overrideWeek = "2026-10-02", overrideStart = 110, overrideEnd = 210)
        assertEquals(listOf(110L, 210L, 150L), effectiveEvents(events, settings).map { it.atMillis })
        assertEquals(events, effectiveEvents(events, settings.copy(overrideWeek = "2026-09-25")))
    }

    @Test fun displayedTimeHonorsTwelveAndTwentyFourHourPreferences() {
        assertEquals("9:05 AM", formatWallTime(9, 5, false))
        assertEquals("21:05", formatWallTime(21, 5, true))
        val at = TimeLogic.at(LocalDate.of(2026, 10, 2), 21, 5, zone)
        assertEquals("9:05 PM", formatLocalTime(at, zone, false))
        assertEquals("21:05", formatLocalTime(at, zone, true))
        assertEquals("9:05 PM", formatNextTime(at, at - 3600_000, zone, false))
        assertEquals("Fri 21:05", formatNextTime(at, at - 86400_000, zone, true))
    }

    @Test fun fridayStaysCurrentOnSaturdayThenAdvancesOnSunday() {
        assertEquals(
            "2026-09-25",
            TimeLogic.weekKey(Instant.parse("2026-09-26T15:00:00Z"), zone)
        )
        assertEquals(
            "2026-10-02",
            TimeLogic.weekKey(Instant.parse("2026-09-27T15:00:00Z"), zone)
        )
    }

    @Test fun weeklyAlarmKeepsLocalTimeAcrossDaylightSaving() {
        val alarm = AlarmItem(hour = 8, minute = 0, repeatDay = 6)
        val before = TimeLogic.nextAlarm(
            alarm, Instant.parse("2027-03-13T01:00:00Z"), zone
        )!!
        val after = TimeLogic.nextAlarm(
            alarm, Instant.parse("2027-03-14T01:00:00Z"), zone
        )!!
        assertEquals(8, Instant.ofEpochMilli(before).atZone(zone).hour)
        assertEquals(8, Instant.ofEpochMilli(after).atZone(zone).hour)
        assertEquals(LocalDate.of(2027, 3, 13), Instant.ofEpochMilli(before).atZone(zone).toLocalDate())
        assertEquals(LocalDate.of(2027, 3, 20), Instant.ofEpochMilli(after).atZone(zone).toLocalDate())
    }

    @Test fun nextMinyanSkipsPastTimesAndUnknownShuls() {
        val friday = LocalDate.of(2026, 10, 2)
        val now = Instant.ofEpochMilli(TimeLogic.at(friday, 19, 0, zone))
        val shuls = listOf(ShulItem(1, "Main"))
        val minyanim = listOf(
            MinyanItem(1, 1, "Mincha", 5, 18, 0),
            MinyanItem(2, 1, "Shacharis", 6, 9, 0),
            MinyanItem(3, 99, "Other", 5, 20, 0)
        )
        assertEquals(2L, TimeLogic.nextMinyan(minyanim, shuls, now, zone)?.first?.id)
        assertNull(TimeLogic.nextMinyan(emptyList(), shuls, now, zone))
    }

    @Test fun nextMinyanRollsToFollowingWeekAfterSaturday() {
        val now = Instant.ofEpochMilli(TimeLogic.at(LocalDate.of(2026, 10, 3), 11, 0, zone))
        val next = TimeLogic.nextMinyan(
            listOf(MinyanItem(1, 2, "Shacharis", 6, 9, 0)),
            listOf(ShulItem(2, "Main")), now, zone
        )!!
        assertEquals(LocalDate.of(2026, 10, 10),
            Instant.ofEpochMilli(next.second).atZone(zone).toLocalDate())
    }
}

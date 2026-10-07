package dev.dovie.shabbosmode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TimeLogicTest {
    private val zone = ZoneId.of("America/New_York")

    @Test fun candleOffsetsUseConfiguredBoundaryAndOverride() {
        val friday = LocalDate.of(2026, 10, 9)
        val candles = TimeLogic.at(friday, 18, 0, zone)
        val now = Instant.ofEpochMilli(candles - 3600_000)
        val events = listOf(TimeEvent("start", "Shabbos starts", candles, friday.toString(), 0))
        val minyan = MinyanItem(1, 1, "Mincha", 5, 9, 0, -15)
        assertEquals(candles - 900_000, TimeLogic.nextMinyanTime(minyan, now, zone, events))
        assertEquals(candles + 1800_000, TimeLogic.nextMinyanTime(minyan.copy(candleOffsetMinutes = 30), now, zone, events))
        val settings = AppSettings(overrideWeek = friday.toString(), overrideStart = candles + 600_000)
        assertEquals(candles - 300_000, TimeLogic.nextMinyanTime(minyan, now, zone, events, settings))
        assertEquals(candles, TimeLogic.nextMinyanTime(minyan.copy(candleOffsetMinutes = 0), now, zone, events))
    }

    @Test fun relativeMinyanNeverGuessesMissingOrPassedWeeks() {
        val candles = TimeLogic.at(LocalDate.of(2026, 10, 9), 18, 0, zone)
        val minyan = MinyanItem(1, 1, "Mincha", 5, 9, 0, -15)
        val events = listOf(TimeEvent("start", "Shabbos starts", candles, "2026-10-09", 0))
        val passed = Instant.ofEpochMilli(candles - 900_000)
        assertNull(TimeLogic.nextMinyanTime(minyan, passed, zone, events))
        assertNull(TimeLogic.nextMinyanTime(minyan, passed.minusSeconds(60), zone))
        assertNull(TimeLogic.nextMinyanTime(minyan, passed.minusSeconds(60), zone, events,
            AppSettings(onboardingDeferred = true)))
        val following = TimeLogic.at(LocalDate.of(2026, 10, 16), 17, 49, zone)
        val nextEvents = events + TimeEvent("start", "Shabbos starts", following, "2026-10-16", 0)
        assertEquals(following - 900_000, TimeLogic.nextMinyanTime(minyan, passed, zone, nextEvents))
        assertNull(TimeLogic.nextMinyanTime(minyan, Instant.ofEpochMilli(following - 3600_000), zone, events))
    }

    @Test fun relativeAndFixedMinyanimShareNextSelectionAcrossDst() {
        val candles = TimeLogic.at(LocalDate.of(2026, 11, 6), 16, 30, zone)
        val events = listOf(TimeEvent("start", "Shabbos starts", candles, "2026-11-06", 0))
        val relative = MinyanItem(1, 1, "Mincha", 5, 9, 0, -15)
        val fixed = MinyanItem(2, 1, "Shacharis", 6, 9, 0)
        val shuls = listOf(ShulItem(1, "Main"))
        assertEquals(candles - 900_000, TimeLogic.nextMinyan(listOf(relative, fixed), shuls,
            Instant.parse("2026-11-01T16:00:00Z"), zone, events)?.second)
        assertEquals(2L, TimeLogic.nextMinyan(listOf(relative, fixed), shuls,
            Instant.ofEpochMilli(candles), zone, events)?.first?.id)
    }

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

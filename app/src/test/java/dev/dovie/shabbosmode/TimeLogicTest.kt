package dev.dovie.shabbosmode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TimeLogicTest {
    private val zone = ZoneId.of("America/New_York")

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

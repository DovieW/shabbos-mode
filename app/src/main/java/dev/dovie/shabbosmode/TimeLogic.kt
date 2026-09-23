package dev.dovie.shabbosmode

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

object TimeLogic {
    fun zone(settings: AppSettings): ZoneId =
        runCatching { ZoneId.of(settings.zoneId) }.getOrDefault(ZoneId.systemDefault())

    fun friday(now: Instant, zone: ZoneId): LocalDate {
        val today = now.atZone(zone).toLocalDate()
        return if (today.dayOfWeek == DayOfWeek.SATURDAY) today.minusDays(1)
        else today.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
    }

    fun weekKey(now: Instant, zone: ZoneId): String = friday(now, zone).toString()

    fun at(date: LocalDate, hour: Int, minute: Int, zone: ZoneId): Long =
        ZonedDateTime.of(date, LocalTime.of(hour, minute), zone).toInstant().toEpochMilli()

    fun nextAlarm(item: AlarmItem, now: Instant, zone: ZoneId): Long? {
        if (!item.enabled) return null
        if (item.repeatDay == 0) return item.oneTimeMillis.takeIf { it > now.toEpochMilli() }
        val day = DayOfWeek.of(item.repeatDay)
        var date = now.atZone(zone).toLocalDate().with(TemporalAdjusters.nextOrSame(day))
        var at = at(date, item.hour, item.minute, zone)
        if (at <= now.toEpochMilli()) {
            date = date.plusWeeks(1)
            at = at(date, item.hour, item.minute, zone)
        }
        return at
    }

    fun nextMinyan(
        minyanim: List<MinyanItem>,
        shuls: List<ShulItem>,
        now: Instant,
        zone: ZoneId
    ): Pair<MinyanItem, Long>? {
        val friday = friday(now, zone)
        return minyanim.mapNotNull { item ->
            if (shuls.none { it.id == item.shulId }) return@mapNotNull null
            val date = when (item.day) {
                5 -> friday
                6 -> friday.plusDays(1)
                else -> return@mapNotNull null
            }
            var at = at(date, item.hour, item.minute, zone)
            if (at <= now.toEpochMilli()) {
                at = at(date.plusWeeks(1), item.hour, item.minute, zone)
            }
            item to at
        }.minByOrNull { it.second }
    }
}

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

    // All displays and Tasker use the same resolution. Never project a cached
    // candle time into another week: that week's boundary must be available.
    fun nextMinyanTime(
        item: MinyanItem,
        now: Instant,
        zone: ZoneId,
        events: List<TimeEvent> = emptyList(),
        settings: AppSettings = AppSettings()
    ): Long? {
        val offset = item.candleOffsetMinutes
        if (offset != null) {
            if (settings.onboardingDeferred) return null
            val week = weekKey(now, zone)
            return effectiveEvents(events, settings).asSequence()
                .filter { it.key == "start" && it.week >= week }
                .map { it.atMillis + offset * 60_000L }
                .filter { it > now.toEpochMilli() }
                .minOrNull()
        }
        val date = when (item.day) {
            5 -> friday(now, zone)
            6 -> friday(now, zone).plusDays(1)
            else -> return null
        }
        val time = at(date, item.hour, item.minute, zone)
        return if (time > now.toEpochMilli()) time else at(date.plusWeeks(1), item.hour, item.minute, zone)
    }

    fun nextMinyan(
        minyanim: List<MinyanItem>,
        shuls: List<ShulItem>,
        now: Instant,
        zone: ZoneId,
        events: List<TimeEvent> = emptyList(),
        settings: AppSettings = AppSettings()
    ): Pair<MinyanItem, Long>? = minyanim.mapNotNull { item ->
        if (shuls.none { it.id == item.shulId }) return@mapNotNull null
        nextMinyanTime(item, now, zone, events, settings)?.let { item to it }
    }.minByOrNull { it.second }
}

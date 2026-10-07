package dev.dovie.shabbosmode

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.OffsetDateTime

data class CityResult(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val zoneId: String,
    val countryCode: String = "",
    val locality: String = name.substringBefore(',')
)

class RemoteData(private val dao: AppDao) {
    private suspend fun json(url: String): JSONObject = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.setRequestProperty("User-Agent", "ShabbosMode/0.1 (personal beta)")
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Network response ${connection.responseCode}")
            }
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    suspend fun findCities(query: String): List<CityResult> = CitySearch.find(query) { candidate ->
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=" +
            Uri.encode(candidate) + "&count=5&language=en&format=json"
        val array = json(url).optJSONArray("results") ?: return@find emptyList()
        val items = (0 until array.length()).map { array.getJSONObject(it) }
        fun label(item: JSONObject, county: Boolean = false) = listOf(
            item.optString("name"), if (county) item.optString("admin2") else "",
            item.optString("admin1"), item.optString("country")
        ).filter { it.isNotBlank() }.distinct().joinToString(", ")
        val duplicates = items.groupingBy { label(it) }.eachCount()
        items.map { item ->
            CityResult(
                name = label(item, county = duplicates.getValue(label(item)) > 1),
                latitude = item.getDouble("latitude"),
                longitude = item.getDouble("longitude"),
                zoneId = item.getString("timezone"),
                countryCode = item.optString("country_code"),
                locality = item.optString("name")
            )
        }
    }

    suspend fun locationZone(latitude: Double, longitude: Double): String =
        json("https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&timezone=auto&forecast_days=1")
            .getString("timezone")

    suspend fun refreshTimes(settings: AppSettings, now: Instant = Instant.now()) {
        val lat = settings.latitude ?: return
        val lon = settings.longitude ?: return
        val zone = TimeLogic.zone(settings)
        val friday = TimeLogic.friday(now, zone)
        val saturday = friday.plusDays(1)
        val week = friday.toString()
        val location = "&latitude=$lat&longitude=$lon&tzid=${Uri.encode(zone.id)}"
        val shabbat = json(
            "https://www.hebcal.com/shabbat?cfg=json$location&gy=${friday.year}&gm=${friday.monthValue}&gd=${friday.dayOfMonth}" +
                TimingPractice.shabbatParameters(settings)
        )
        val zmanim = json(
            "https://www.hebcal.com/zmanim?cfg=json$location&date=$saturday"
        ).getJSONObject("times")
        val shabbatItems = shabbat.getJSONArray("items")
        var start: Long? = null
        var end: Long? = null
        for (i in 0 until shabbatItems.length()) {
            val item = shabbatItems.getJSONObject(i)
            val time = runCatching { parseTime(item.getString("date")) }.getOrNull() ?: continue
            val date = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
            if (item.optString("category") == "candles" && date == friday && start == null) start = time
            if (item.optString("category") == "havdalah" && date == saturday) end = time
        }
        val fridayZmanim = json(
            "https://www.hebcal.com/zmanim?cfg=json$location&date=$friday"
        ).getJSONObject("times")
        val fridaySunset = parseTime(fridayZmanim.getString("sunset"))
        // Missing astronomical times (e.g. polar night) must never become a guessed boundary.
        val calculatedEnd = if (settings.havdalahMinutes == 0) {
            parseTime(zmanim.getString("tzeit85deg"))
        } else parseTime(zmanim.getString("sunset")) + settings.havdalahMinutes * 60_000L
        val fetched = now.toEpochMilli()
        val events = mutableListOf(
            TimeEvent("start", "Shabbos starts", start ?: fridaySunset - TimingPractice.candleLead(settings) * 60_000L, week, fetched),
            TimeEvent("end", "Shabbos ends", end ?: calculatedEnd, week, fetched)
        )
        for ((key, source) in TimingPractice.zmanSources(settings.tradition)) {
            val (apiKey, label) = source
            val time = runCatching { parseTime(zmanim.getString(apiKey)) }.getOrNull() ?: continue
            events += TimeEvent(key, label, time, week, fetched)
        }
        dao.saveEvents(events)
        dao.removeOldEvents(week)
    }

    suspend fun refreshWeather(settings: AppSettings, now: Instant = Instant.now()) {
        val lat = settings.latitude ?: return
        val lon = settings.longitude ?: return
        val zone = TimeLogic.zone(settings)
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&hourly=temperature_2m,precipitation_probability,weather_code" +
            "&temperature_unit=fahrenheit&timezone=${Uri.encode(zone.id)}" +
            "&timeformat=unixtime&forecast_days=7"
        val hourly = json(url).getJSONObject("hourly")
        val times = hourly.getJSONArray("time")
        val temperatures = hourly.getJSONArray("temperature_2m")
        val rain = hourly.getJSONArray("precipitation_probability")
        val codes = hourly.getJSONArray("weather_code")
        val fetched = now.toEpochMilli()
        val items = (0 until times.length()).map { i ->
            val at = times.getLong(i) * 1000L
            WeatherHour(at, temperatures.optDouble(i), rain.optInt(i), codes.optInt(i), fetched)
        }
        dao.saveWeather(items)
        dao.removeOldWeather(now.toEpochMilli() - 3600_000L)
    }

    private fun parseTime(raw: String): Long = OffsetDateTime.parse(raw).toInstant().toEpochMilli()
}

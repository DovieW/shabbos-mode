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
    val zoneId: String
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

    suspend fun findCities(query: String): List<CityResult> {
        if (query.trim().length < 2) return emptyList()
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=" +
            Uri.encode(query.trim()) + "&count=5&language=en&format=json"
        val array = json(url).optJSONArray("results") ?: return emptyList()
        return (0 until array.length()).map { array.getJSONObject(it) }.map { item ->
            CityResult(
                name = listOf(item.optString("name"), item.optString("admin1"),
                    item.optString("country")).filter { it.isNotBlank() }.joinToString(", "),
                latitude = item.getDouble("latitude"),
                longitude = item.getDouble("longitude"),
                zoneId = item.getString("timezone")
            )
        }
    }

    suspend fun refreshTimes(settings: AppSettings, now: Instant = Instant.now()) {
        val lat = settings.latitude ?: return
        val lon = settings.longitude ?: return
        val zone = TimeLogic.zone(settings)
        val friday = TimeLogic.friday(now, zone)
        val saturday = friday.plusDays(1)
        val week = friday.toString()
        val location = "&latitude=$lat&longitude=$lon&tzid=${Uri.encode(zone.id)}"
        val shabbat = json(
            "https://www.hebcal.com/shabbat?cfg=json$location&date=$friday&b=18&M=on"
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
        val saturdaySunset = parseTime(zmanim.getString("sunset"))
        val fetched = now.toEpochMilli()
        val events = mutableListOf(
            TimeEvent("start", "Shabbos starts", start ?: fridaySunset - 18 * 60_000L, week, fetched),
            TimeEvent("end", "Shabbos ends", end ?: saturdaySunset + 50 * 60_000L, week, fetched)
        )
        val labels = mapOf(
            "sunrise" to "Sunrise",
            "sofZmanShma" to "Sof zman Shema",
            "chatzot" to "Chatzot",
            "minchaGedola" to "Mincha gedola",
            "plagHaMincha" to "Plag hamincha",
            "sunset" to "Sunset",
            "tzeit7083deg" to "Tzeit"
        )
        for ((key, label) in labels) {
            if (zmanim.has(key)) {
                events += TimeEvent(key, label, parseTime(zmanim.getString(key)), week, fetched)
            }
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
